package io.hengam.lib

import android.content.Context
import androidx.work.*
import androidx.work.NetworkType
import io.hengam.lib.LogTag.T_DEBUG
import io.hengam.lib.internal.*
import io.hengam.lib.internal.task.*
import io.hengam.lib.messages.common.ApplicationDetailJsonAdapter
import io.hengam.lib.messages.downstream.RunDebugCommandMessage
import io.hengam.lib.messaging.*
import io.hengam.lib.messaging.fcm.FcmServiceManager
import io.hengam.lib.messaging.fcm.FcmTokenStore
import io.hengam.lib.messaging.fcm.TokenState
import io.hengam.lib.tasks.RegistrationTask
import io.hengam.lib.tasks.UpstreamFlushTask
import io.hengam.lib.tasks.UpstreamSenderTask
import io.hengam.lib.utils.*
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.rx.justDo
import io.hengam.lib.utils.rx.subscribeBy
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import io.reactivex.Completable
import io.reactivex.Single
import javax.inject.Inject

class DebugCommands @Inject constructor(
        private val registrationManager: RegistrationManager,
        private val appManifest: AppManifest,
        private val postOffice: PostOffice,
        private val taskScheduler: TaskScheduler,
        private val hengamConfig: HengamConfig,
        private val messageStore: MessageStore,
        private val context: Context,
        private val moshi: HengamMoshi,
        private val topicManager: TopicManager,
        private val applicationInfoHelper: ApplicationInfoHelper,
        private val deviceIdHelper: DeviceIDHelper,
        private val fcmServiceManager: FcmServiceManager,
        private val hengamLifecycle: HengamLifecycle,
        private val fcmTokenStore: FcmTokenStore,
        private val tagManager: TagManager,
        private val geoUtils: GeoUtils
) : DebugCommandProvider {
    private val loremIpsum: String = "Lorem ipsum dolor sit amet, consectetur adipiscing elit".repeat(4)

    override val commands: Map<String, Any>
        get() =
            mapOf(
                    "Registration & FCM" to mapOf(
                            "restart Hengam" to "restart_hengam",
                            "Register with Server" to "register",
                            "Am I Registered" to "is_registered",
                            "Log FCM Details" to "log_fcm",
                            "Revoke FCM Token" to "revoke_fcm"
                    ),
                    "Messaging" to mapOf(
                            "Upstream Tests" to mapOf(
                                    "Send Single Message (immediate)" to "send_msg_single",
                                    "Send Single Message (buffer)" to "send_msg_buff",
                                    "Send Very Large Message" to "send_msg_lg",
                                    "Send Lots of Small Messages" to "send_msg_lots_sm"
                            ),
                            "Schedule Upstream Sender" to "upstream_send",
                            "Change Parcel Size Limit" to "parcel_size_limit",
                            "Print State" to "msg_stats",
                            "List In Memory Messages" to "list_memory_msg",
                            "List Persisted Messages" to "list_persisted_msg"

                    ),
                    "Topic" to mapOf(
                            "Subscribe to Topic" to "topic_subscribe",
                            "Subscribe to Topic Globally" to "topic_subscribe_globally",
                            "Unsubscribe from Topic" to "topic_unsubscribe",
                            "Unsubscribe from Topic Globally" to "topic_unsubscribe_globally",
                            "List Subscribed Topics" to "topic_list"
                    ),
                    "Tag" to mapOf(
                            "Add Tag" to "tag_subscribe",
                            "Remove Tag" to "tag_unsubscribe",
                            "List Added Tags" to "tag_list",
                            "Remove all tags" to "tag_remove_all"
                    ),
                    "User & Device Info" to mapOf(
                            "Get Advertising Id" to "get_gaid",
                            "Get Android_Id" to "get_aid",
                            "Get Custom Id" to "get_cid",
                            "Set Custom Id" to "set_cid",
                            "Get User Email" to "get_email",
                            "Set User Email" to "set_email",
                            "Get User Phone Number" to "get_phone",
                            "Set User Phone Number" to "set_phone"
                    ),
                    "Location" to mapOf(
                            "Last location available" to "is_last_available",
                            "Get last known" to "get_last_known",
                            "Request for location" to "request_location"
                    ),
                    "Tasks" to mapOf(
                            "WorkManager Status" to "workmanager_status",
                            "Cancel All Tasks" to "cancel_tasks",
                            "Schedule task with 3 attempts" to "sched_retrying_task"
                    ),
                    "Misc" to mapOf(
                            "App Details" to "app_details",
                            "Log Storage" to "log_storage"
                    )
            )

    override fun handleCommand(commandId: String, input: DebugInput): Boolean {
        when (commandId) {
            "restart_hengam" -> {
                Plog.debug(T_DEBUG, "Clearing Hengam data...")
                val messageStore = context.getSharedPreferences(MessageStore.MESSAGE_STORE_NAME, Context.MODE_PRIVATE)
                val hengamStorage = context.getSharedPreferences(HengamStorage.SHARED_PREF_NAME, Context.MODE_PRIVATE)
                val configStorage = context.getSharedPreferences(HengamConfig.HENGAM_CONFIG_STORE, Context.MODE_PRIVATE)
                messageStore.edit().clear().apply()
                hengamStorage.edit().clear().apply()
                configStorage.edit().clear().apply()
                fcmServiceManager.clearFirebase()
                Plog.debug(T_DEBUG, "Cancelling all Hengam tasks...")
                WorkManager.getInstance().cancelAllWorkByTag("hengam")
                WorkManager.getInstance().pruneWork()
                Plog.debug(T_DEBUG, "Initializing SDK...")
                hengamLifecycle.forgetRegistration()
                /* Initialize Messaging Services */
                fcmServiceManager.initializeFirebase()

                /* Perform registration */
//                registrationManager.checkRegistration()
                fcmTokenStore.revalidateTokenState()
                        .justDo(LogTag.T_FCM, LogTag.T_REGISTER) {
                            Plog.debug(LogTag.T_REGISTER, "Token state is $it")

                            if (it == TokenState.SYNCING) {
                                Plog.info(LogTag.T_REGISTER, "Previous registration was not completed, performing registration")
                                taskScheduler.scheduleTask(
                                        RegistrationTask.Options(), taskDataOf(
                                        RegistrationTask.DATA_REGISTRATION_CAUSE to "init")
                                )
                            }
                        }

                /* Flush upstream messages every 24h */
                taskScheduler.schedulePeriodicTask(UpstreamFlushTask.Options())

                // Start tasks with the saved (or Initial) time value.
                hengamLifecycle.waitForRegistration()
                        .justDo("datalytics") {
                            HengamDebug().handleCommand(RunDebugCommandMessage("reschedule_collections"))
                        }
            }
            "register" -> {
                Plog.debug(T_DEBUG, "Triggering registration")
                registrationManager.performRegistration("admin")
            }
            "is_registered" -> {
                if (Hengam.isRegistered()) {
                    Plog.debug(T_DEBUG, "You are registered")
                } else {
                    Plog.debug(T_DEBUG, "You are not registered")
                }
            }
            "log_fcm" -> {
                val senderId = appManifest.fcmSenderId
                Plog.debug(T_DEBUG, "Fcm details",
                        "Sender Id" to senderId,
                        "Fcm Token" to fcmServiceManager.firebaseInstanceId?.getToken(senderId, "FCM"),
                        "Instance id" to fcmServiceManager.firebaseInstanceId?.id,
                        "Creation Time" to fcmServiceManager.firebaseInstanceId?.creationTime
                )
            }
            "revoke_fcm" -> {
                val senderId = appManifest.fcmSenderId
                Plog.debug(T_DEBUG, "Revoking fcm token",
                        "Sender Id" to senderId,
                        "Previous Token" to fcmServiceManager.firebaseInstanceId?.getToken(senderId, "FCM")
                )
                fcmServiceManager.firebaseInstanceId?.deleteToken(senderId, "FCM")
            }
            "send_msg_single" -> {
                Plog.debug(T_DEBUG, "Sending single message")
                val map = mutableMapOf<String, String>()
                map["Lorem Ipsum"] = loremIpsum
                val message = UpstreamMapMessage(map)
                postOffice.sendMessage(message, sendPriority = SendPriority.IMMEDIATE)
            }
            "send_msg_buff" -> {
                Plog.debug(T_DEBUG, "Sending single message")
                val map = mutableMapOf<String, String>()
                map["Lorem Ipsum"] = loremIpsum
                val message = UpstreamMapMessage(map)
                postOffice.sendMessage(message, sendPriority = SendPriority.BUFFER)
            }
            "send_msg_lg" -> {
                Plog.debug(T_DEBUG, "One very large messing on the way")
                val map = mutableMapOf<String, String>()
                for (i in 1..100) {
                    map[i.toString()] = loremIpsum
                }
                val message = UpstreamMapMessage(map)
                postOffice.sendMessage(message, sendPriority = SendPriority.IMMEDIATE)
            }
            "send_msg_lots_sm" -> {
                Plog.debug(T_DEBUG, "Sending lots of messages, wait for it")
                val map = mutableMapOf<String, String>()
                map["Lorem Ipsum"] = loremIpsum
                for (i in 1..50) {
                    val message = UpstreamMapMessage(map, (Math.random() * 3).toInt() + 200)
                    postOffice.sendMessage(message, sendPriority = SendPriority.SOON)
                }
            }
            "upstream_send" -> {
                Plog.debug(T_DEBUG, "Scheduling upstream sender")
                taskScheduler.scheduleTask(UpstreamSenderTask.Options)
            }
            "parcel_size_limit" -> {
                input.promptNumber("Enter parcel size limit", "Limit (bytes)", hengamConfig.upstreamMaxParcelSize.toLong())
                        .subscribeBy { limit ->
                            hengamConfig.updateConfig("upstream_max_parcel_size", limit)
                            Plog.debug(T_DEBUG, "New parcel size limit set to $limit. This will reset to default on app restart")
                        }
            }
            "msg_stats" -> {
                val stats = mapOf(
                        "Created" to messageStore.allMessages.filter { it.messageState is UpstreamMessageState.Created }.size,
                        "Stored" to messageStore.allMessages.filter { it.messageState is UpstreamMessageState.Stored }.size,
                        "In-Flight" to messageStore.allMessages.filter { it.messageState is UpstreamMessageState.InFlight }.size,
                        "Sent" to messageStore.allMessages.filter { it.messageState is UpstreamMessageState.Sent }.size
                )
                Plog.debug(T_DEBUG, "Message Store Stats",
                        "In-Memory Messages" to messageStore.allMessages.size,
                        "Persisted Messages" to context.getSharedPreferences(MessageStore.MESSAGE_STORE_NAME, Context.MODE_PRIVATE).all.size,
                        "In-Memory Message Stats" to stats
                )
            }
            "list_memory_msg" -> {
                moshi.adapter(Any::class.java)
                val stateAdapter = UpstreamMessageState.Adapter()
                val data = messageStore.allMessages.map {
                    mapOf(
                            "type" to it.message.messageType,
                            "size" to it.messageSize,
                            "state" to stateAdapter.toJson(it.messageState),
                            "attempts" to it.sendAttempts
                    )
                }
                Plog.debug(T_DEBUG, "Message Store in-memory messages", "Store" to data)
            }
            "list_persisted_msg" -> {
                val persistedAdapter = PersistedUpstreamMessageWrapperJsonAdapter(moshi.moshi)
                val stateAdapter = UpstreamMessageState.Adapter()
                val data = context
                        .getSharedPreferences(MessageStore.MESSAGE_STORE_NAME, Context.MODE_PRIVATE)
                        .all
                        .values
                        .map { persistedAdapter.fromJson(it.toString())!! }
                        .map {
                            mapOf(
                                    "type" to it.messageType,
                                    "size" to it.messageSize,
                                    "state" to stateAdapter.toJson(it.messageState),
                                    "attempts" to it.sendAttempts
                            )
                        }
                Plog.debug(T_DEBUG, "Message Store persisted messages", "Store" to data)
            }
            "topic_subscribe" -> {
                input.prompt("Subscribe to Topic", "Topic", "mytopic")
                        .subscribeBy { topic ->
                            topicManager.subscribe(topic)
                                    .subscribeBy(
                                            onComplete = { Plog.debug(T_DEBUG, "Topic $topic subscribed") },
                                            onError = { Plog.error(T_DEBUG, it) }
                                    )
                        }
            }
            "topic_subscribe_globally" -> {
                input.prompt("Subscribe Globally to Topic", "Topic", "mytopic")
                        .subscribeBy { topic ->
                            topicManager.subscribe(topic, addSuffix = false)
                                    .subscribeBy(
                                            onComplete = { Plog.debug(T_DEBUG, "Topic $topic subscribed") },
                                            onError = { Plog.error(T_DEBUG, it) }
                                    )
                        }
            }
            "topic_unsubscribe" -> {
                input.prompt("Unsubscribe from Topic", "Topic", "mytopic")
                        .subscribeBy { topic ->
                            topicManager.unsubscribe(topic)
                                    .subscribeBy(
                                            onComplete = { Plog.debug(T_DEBUG, "Topic $topic unsubscribed") },
                                            onError = { Plog.error(T_DEBUG, it) }
                                    )
                        }

            }
            "topic_unsubscribe_globally" -> {
                input.prompt("Unsubscribe Globally from Topic", "Topic", "mytopic")
                        .subscribeBy { topic ->
                            topicManager.unsubscribe(topic, addSuffix = false)
                                    .subscribeBy(
                                            onComplete = { Plog.debug(T_DEBUG, "Topic $topic unsubscribed") },
                                            onError = { Plog.error(T_DEBUG, it) }
                                    )
                        }

            }
            "topic_list" -> {
                if (topicManager.subscribedTopics.isEmpty()) {
                    Plog.info(T_DEBUG, "No topics have been subscribed")
                } else {
                    val adapter = moshi.adapter(Any::class.java)
                    Plog.info(T_DEBUG, "Subscribed Topics",
                            "Topics" to adapter.toJson(topicManager.subscribedTopics.toTypedArray()))
                    Plog.info(T_DEBUG, Hengam.getSubscribedTopics().toString())
                }
            }
            "tag_subscribe" -> {
                input.prompt("Add Tag (key:value)", "Tag", "name:myName")
                        .subscribeBy {
                            if (it.isBlank() || it.split(":").size != 2) return@subscribeBy
                            val tagPair = it.split(":")
                            val tags = mapOf(tagPair[0] to tagPair[1])
                            Hengam.addTags(tags)
                        }
            }
            "tag_unsubscribe" -> {
                input.prompt("Remove Tag", "Tag", "name")
                        .subscribeBy { tag ->
                            tagManager.removeTags(listOf(tag))
                                    .subscribeBy(
                                            onComplete = { Plog.debug(T_DEBUG, "Tag $tag removed") },
                                            onError = { Plog.error(T_DEBUG, it) }
                                    )
                        }

            }
            "tag_list" -> {
                if (tagManager.subscribedTags.isEmpty()) {
                    Plog.info(T_DEBUG, "No tags have been added")
                } else {
                    val adapter = moshi.adapter(Any::class.java)
                    Plog.info(T_DEBUG, "Added Tags",
                            "Tags" to adapter.toJson(tagManager.subscribedTags))
                }
            }
            "tag_remove_all" -> {
                tagManager.removeTags(tagManager.subscribedTags.keys.toList())
                        .subscribeBy(
                                onComplete = { Plog.debug(T_DEBUG, "All tags removed") },
                                onError = { Plog.error(T_DEBUG, it) }
                        )
            }
            "is_last_available" -> {
                geoUtils.isLastLocationAvailable().subscribeBy { isAvailable ->
                    Plog.debug(T_DEBUG, "Is last location available? '$isAvailable'")
                }
            }
            "get_last_known" -> {
                geoUtils.getLastKnownLocation().justDo()
            }
            "request_location" -> {
                geoUtils.requestLocationUpdates(seconds(10))
            }
            "get_gaid" -> Plog.info(T_DEBUG, "Advertisement id: ${deviceIdHelper.advertisementId}")
            "get_aid" -> Plog.info(T_DEBUG, "Android id: ${deviceIdHelper.androidId}")
            "get_cid" -> Plog.info(T_DEBUG, "Custom id: ${Hengam.getCustomId()}")
            "set_cid" -> {
                input.prompt("Set Custom Id", "Custom Id", "")
                        .subscribeBy { userId ->
                            Plog.info(T_DEBUG, "Setting custom id to $userId")
                            Hengam.setCustomId(userId)
                        }
            }
            "get_email" -> Plog.info(T_DEBUG, "Email: ${Hengam.getUserEmail()}")
            "set_email" -> {
                input.prompt("Set User Email", "Email", "")
                        .subscribeBy { email ->
                            Plog.info(T_DEBUG, "Setting user email to $email")
                            Hengam.setUserEmail(email)
                        }
            }
            "get_phone" -> Plog.info(T_DEBUG, "Phone Number: ${Hengam.getUserPhoneNumber()}")
            "set_phone" -> {
                input.prompt("Set User Phone Number", "Phone Number", "")
                        .subscribeBy { phoneNumber ->
                            Plog.info(T_DEBUG, "Setting user phone number to $phoneNumber")
                            Hengam.setUserPhoneNumber(phoneNumber)
                        }
            }
            "workmanager_status" -> {
                val workStatuses = WorkManager.getInstance().getWorkInfosByTag(TaskScheduler.DEFAULT_WORK_TAG).get()
                val data = workStatuses?.map {
                    mapOf(
                            "Id" to it.id.toString(),
                            "State" to it.state,
                            "Tags" to it.tags.map { tag -> tag.replace("io.hengam.lib", "") }
                    )
                }
                Plog.debug(T_DEBUG, "Work Statuses", " Status" to data)
            }
            "cancel_tasks" -> {
                Plog.debug(T_DEBUG, "Cancelling all Hengam tasks")
                WorkManager.getInstance().cancelAllWorkByTag("hengam")
                WorkManager.getInstance().pruneWork()
            }
            "sched_retrying_task" -> {
                Plog.debug(T_DEBUG, "Scheduling 'RetryingTask' with maxAttempts = 3")
                taskScheduler.scheduleTask(RetryingTask.Options(maxAttempts = 3))
            }
            "app_details" -> {
                Plog.debug(T_DEBUG, "Application detail",
                        "Package name" to context.packageName,
                        "Signature" to applicationInfoHelper.getApplicationSignature(),
                        "Details" to ApplicationDetailJsonAdapter(moshi.moshi).toJson(applicationInfoHelper.getApplicationDetails())
                )
            }
            "log_storage" -> {
                val anyAdapter = moshi.adapter(Any::class.java)
                val storage =
                        context.getSharedPreferences(HengamStorage.SHARED_PREF_NAME, Context.MODE_PRIVATE)
                                .all
                                .mapValues {
                                    val stringValue = it.value.toString()
                                    if (stringValue.startsWith("{") || stringValue.startsWith("[")) {
                                        anyAdapter.fromJson(stringValue)
                                    } else {
                                        stringValue
                                    }
                                }

                val log = Plog.info.message("Storage Data").withTag(T_DEBUG)
                storage.forEach { item -> log.withData(item.key, item.value) }
                log.log()
            }
            else -> return false
        }
        return true
    }

    private class UpstreamMapMessage(val map: Map<String, String>, messageType: Int = 200) : SendableUpstreamMessage(messageType) {
        override fun onPrepare(): Completable = Completable.complete()

        override fun toJson(moshi: Moshi, writer: JsonWriter) {
            val anyAdapter = moshi.adapter(Any::class.java)
            anyAdapter.toJson(writer, map)
        }
    }
}


class RetryingTask : HengamTask() {
    override fun perform(inputData: Data): Single<ListenableWorker.Result> {
        Plog.debug(T_DEBUG, "Task failing with RETRY status")
        return Single.just(ListenableWorker.Result.retry())
    }

    class Options(private val maxAttempts: Int = -1) : OneTimeTaskOptions() {
        override fun networkType() = NetworkType.NOT_REQUIRED
        override fun task() = RetryingTask::class
        override fun existingWorkPolicy() = ExistingWorkPolicy.REPLACE
        override fun maxAttemptsCount(): Int = maxAttempts
    }
}