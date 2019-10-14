package co.pushe.plus

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import co.pushe.plus.LogTag.T_DEBUG
import co.pushe.plus.internal.*
import co.pushe.plus.internal.task.OneTimeTaskOptions
import co.pushe.plus.internal.task.PusheTask
import co.pushe.plus.internal.task.TaskScheduler
import co.pushe.plus.internal.task.taskDataOf
import co.pushe.plus.messages.common.ApplicationDetailJsonAdapter
import co.pushe.plus.messages.downstream.RunDebugCommandMessage
import co.pushe.plus.messaging.*
import co.pushe.plus.messaging.fcm.FcmServiceManager
import co.pushe.plus.messaging.fcm.FcmTokenStore
import co.pushe.plus.messaging.fcm.TokenState
import co.pushe.plus.tasks.RegistrationTask
import co.pushe.plus.tasks.UpstreamFlushTask
import co.pushe.plus.tasks.UpstreamSenderTask
import co.pushe.plus.utils.ApplicationInfoHelper
import co.pushe.plus.utils.DeviceIDHelper
import co.pushe.plus.utils.PusheStorage
import co.pushe.plus.utils.log.Plog
import co.pushe.plus.utils.rx.justDo
import co.pushe.plus.utils.rx.subscribeBy
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
        private val pusheConfig: PusheConfig,
        private val messageStore: MessageStore,
        private val context: Context,
        private val moshi: PusheMoshi,
        private val topicManager: TopicManager,
        private val applicationInfoHelper: ApplicationInfoHelper,
        private val deviceIdHelper: DeviceIDHelper,
        private val fcmServiceManager: FcmServiceManager,
        private val pusheLifecycle: PusheLifecycle,
        private val fcmTokenStore: FcmTokenStore,
        private val tagManager: TagManager
) : DebugCommandProvider {
    private val loremIpsum: String = "Lorem ipsum dolor sit amet, consectetur adipiscing elit".repeat(4)

    override val commands: Map<String, Any> get() =
        mapOf(
                "Registration & FCM" to mapOf(
                        "restart Pushe" to "restart_pushe",
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
                        "Unsubscribe from Topic" to "topic_unsubscribe",
                        "List Subscribed Topics" to "topic_list"
                ),
                "Tag" to mapOf(
                    "Add Tag" to "tag_subscribe",
                    "Remove Tag" to "tag_unsubscribe",
                    "List Added Tags" to "tag_list"
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
            "restart_pushe" -> {
                Plog.debug(T_DEBUG, "Clearing Pushe data...")
                val messageStore = context.getSharedPreferences(MessageStore.MESSAGE_STORE_NAME, Context.MODE_PRIVATE)
                val pusheStorage = context.getSharedPreferences(PusheStorage.SHARED_PREF_NAME, Context.MODE_PRIVATE)
                val configStorage = context.getSharedPreferences(PusheConfig.PUSHE_CONFIG_STORE, Context.MODE_PRIVATE)
                messageStore.edit().clear().apply()
                pusheStorage.edit().clear().apply()
                configStorage.edit().clear().apply()
                fcmServiceManager.clearFirebase()
                Plog.debug(T_DEBUG, "Cancelling all Pushe tasks...")
                WorkManager.getInstance().cancelAllWorkByTag("pushe")
                WorkManager.getInstance().pruneWork()
                Plog.debug(T_DEBUG, "Initializing SDK...")
                pusheLifecycle.forgetRegistration()
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
                pusheLifecycle.waitForRegistration()
                    .justDo("datalytics") {
                        PusheDebug().handleCommand(RunDebugCommandMessage("reschedule_collections"))
                    }
            }
            "register" -> {
                Plog.debug(T_DEBUG, "Triggering registration")
                registrationManager.performRegistration("admin")
            }
            "is_registered" -> {
                if (Pushe.isRegistered()) {
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
                    val message = UpstreamMapMessage(map, (Math.random()*3).toInt() + 200)
                    postOffice.sendMessage(message, sendPriority = SendPriority.SOON)
                }
            }
            "upstream_send" -> {
                Plog.debug(T_DEBUG, "Scheduling upstream sender")
                taskScheduler.scheduleTask(UpstreamSenderTask.Options)
            }
            "parcel_size_limit" -> {
                input.promptNumber("Enter parcel size limit", "Limit (bytes)", pusheConfig.upstreamMaxParcelSize.toLong())
                        .subscribeBy { limit ->
                            pusheConfig.updateConfig("upstream_max_parcel_size", limit)
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
            "topic_list" -> {
                if (topicManager.subscribedTopics.isEmpty()) {
                    Plog.info(T_DEBUG, "No topics have been subscribed")
                } else {
                    val adapter = moshi.adapter(Any::class.java)
                    Plog.info(T_DEBUG, "Subscribed Topics",
                            "Topics" to adapter.toJson(topicManager.subscribedTopics.toTypedArray()))
                }
            }
            "tag_subscribe" -> {
                input.prompt("Add Tag", "Tag", "mytag")
                    .subscribeBy { tag ->
                        tagManager.addTags(listOf(tag))
                            .subscribeBy(
                                onComplete = { Plog.debug(T_DEBUG, "Tag $tag added") },
                                onError = { Plog.error(T_DEBUG, it) }
                            )
                    }
            }
            "tag_unsubscribe" -> {
                input.prompt("Remove Tag", "Tag", "mytag")
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
                        "Tags" to adapter.toJson(tagManager.subscribedTags.toTypedArray()))
                }
            }
            "get_gaid" -> Plog.info(T_DEBUG, "Advertisement id: ${deviceIdHelper.advertisementId}")
            "get_aid" -> Plog.info(T_DEBUG, "Android id: ${deviceIdHelper.androidId}")
            "get_cid" -> Plog.info(T_DEBUG, "Custom id: ${Pushe.getCustomId()}")
            "set_cid" -> {
                input.prompt("Set Custom Id", "Custom Id", "")
                        .subscribeBy { userId ->
                            Plog.info(T_DEBUG, "Setting custom id to $userId")
                            Pushe.setCustomId(userId)
                        }
            }
            "get_email" -> Plog.info(T_DEBUG, "Email: ${Pushe.getUserEmail()}")
            "set_email" -> {
                input.prompt("Set User Email", "Email", "")
                        .subscribeBy { email ->
                            Plog.info(T_DEBUG, "Setting user email to $email")
                            Pushe.setUserEmail(email)
                        }
            }
            "get_phone" -> Plog.info(T_DEBUG, "Phone Number: ${Pushe.getUserPhoneNumber()}")
            "set_phone" -> {
                input.prompt("Set User Phone Number", "Phone Number", "")
                        .subscribeBy { phoneNumber ->
                            Plog.info(T_DEBUG, "Setting user phone number to $phoneNumber")
                            Pushe.setUserPhoneNumber(phoneNumber)
                        }
            }
            "workmanager_status" -> {
                val workStatuses = WorkManager.getInstance().getWorkInfosByTag(TaskScheduler.DEFAULT_WORK_TAG).get()
                val data = workStatuses?.map {
                    mapOf(
                            "Id" to it.id.toString(),
                            "State" to it.state,
                            "Tags" to it.tags.map { tag -> tag.replace("co.pushe.plus", "") }
                    )
                }
                Plog.debug(T_DEBUG, "Work Statuses", " Status" to data)
            }
            "cancel_tasks" -> {
                Plog.debug(T_DEBUG, "Cancelling all Pushe tasks")
                WorkManager.getInstance().cancelAllWorkByTag("pushe")
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
                        context.getSharedPreferences(PusheStorage.SHARED_PREF_NAME, Context.MODE_PRIVATE)
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

    private class RetryingTask(context: Context, workerParameters: WorkerParameters)
        : PusheTask("retrying_task", context, workerParameters) {
        override fun perform(): Single<Result> {
            Plog.debug(T_DEBUG, "Task failing with RETRY status")
            return Single.just(Result.retry())
        }

        companion object

        class Options(private val maxAttempts: Int = -1) : OneTimeTaskOptions() {
            override fun networkType() = NetworkType.NOT_REQUIRED
            override fun task() = RetryingTask::class
            override fun existingWorkPolicy() = ExistingWorkPolicy.REPLACE
            override fun maxAttemptsCount(): Int = maxAttempts
        }
    }
}


