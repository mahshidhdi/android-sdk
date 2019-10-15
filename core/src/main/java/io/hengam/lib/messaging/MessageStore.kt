package io.hengam.lib.messaging

import android.content.Context
import io.hengam.lib.LogTag.T_MESSAGE
import io.hengam.lib.dagger.CoreScope
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.maxPendingUpstreamMessagesForType
import io.hengam.lib.utils.Time
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.millis
import io.hengam.lib.utils.rx.PublishRelay
import io.hengam.lib.utils.rx.keepDoing
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import io.reactivex.Maybe
import io.reactivex.Observable
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * This class stores upstream messages which are scheduled to be sent. All references to _message_
 * in this class and it's documentations implicitly refer to **upstream** messages.
 *
 * This class is not thread safe. Reading messages and adding/removing messages must be performed on
 * the same thread.
 *
 * For the purpose of this class, _storing_ a message and _persisting_ a message are two distinct
 * operations:
 * - A **stored** message is simply a message which is held in memory and can be collected
 *   at any time. A message can be stored using the `storeMessage()` method and later collected using
 *   the `readMessages()` method.
 *
 * - Any message may also be **persisted** but it does not necessarily have to be. If a message is
 *   persisted, it will be written to device storage (SharedPreferences). If the application closes,
 *   any stored messages which have not been persisted will be lost. By calling the `restoreMessages()`
 *   method when the application re-opens, any persisted messages will be recovered and _stored_ again.
 *   To specify that a message should be persisted, pass a `true` value for the `persistAcrossRuns`
 *   parameter when calling `storeMessage()`.
 *
 * TODO: Add docs explaining when the message will be removed from the store
 *
 * @since 2.0.0
 */
@CoreScope
class MessageStore @Inject constructor(
        private val moshi: HengamMoshi,
        private val hengamConfig: HengamConfig,
        context: Context
) {
    private val sharedPrefs = context.getSharedPreferences(MESSAGE_STORE_NAME, Context.MODE_PRIVATE)
    private val persistedMessageAdapter by lazy { PersistedUpstreamMessageWrapperJsonAdapter(moshi.moshi) }
    private val messageAdapter = moshi.adapter(UpstreamMessage::class.java)
    private val persistor = PublishRelay.create<PersistAction>()
    private val messageCountPerType = mutableMapOf<Int, Int>()

    /**
     * Messages contained in the Message Store.
     *
     * New messages which are added to the Message Store will be added to [newMessages] before
     * being added to [storedMessages]. This is to prevent [ConcurrentModificationException]s if someone
     * is already iterating over this list. The [storedMessages] and [newMessages] will be combined
     * every time the messages are accessed with [allMessages].
     */
    private var storedMessages = listOf<StoredUpstreamMessage>()

    /**
     * Messages which have recently been added to the Message Store but have not been appended to
     * [storedMessages] yet.
     */
    private var newMessages = mutableListOf<StoredUpstreamMessage>()

    /**
     * A set containing the message ids of messages which should be removed from the Message Store.
     * The removing of these messages will be applied every time [allMessages] is accessed.
     */
    private var removedMessages = mutableSetOf<String>()

    /**
     * A set of the message id's of all messages currently stored in the Message Store. This includes
     * messages in the [storedMessages] and [newMessages] lists excluding those in the [removedMessages]
     * set.
     */
    private val existingMessageIds = mutableSetOf<String>()

    val size: Int get() = existingMessageIds.size

    init {
        initializeMessagePersisting()
    }

    /**
     * A collection containing all messages stored in the Message Store.
     */
    val allMessages: List<StoredUpstreamMessage>
        get() {
            var allMessages = storedMessages

            if (newMessages.isNotEmpty()){
                allMessages = allMessages + newMessages
                newMessages = mutableListOf()
            }

            if (removedMessages.isNotEmpty()) {
                allMessages = allMessages.filter { it.messageId !in removedMessages }
                removedMessages = mutableSetOf()
            }

            storedMessages = allMessages
            return allMessages
        }

    /**
     * Load any stored messages from the shared preferences and add them to the
     * message store for sending.
     *
     * This should be called on initialization
     *
     * @return The highest priority of the restored messages or null if no messages
     *         were restored
     */
    fun restoreMessages(): Maybe<SendPriority> {
        return Maybe.fromCallable {
            val keys = sharedPrefs.all.keys
            val erroredKeys = mutableListOf<String>()

            val recoveredMessages = mutableListOf<StoredUpstreamMessage>()

            if (keys.size > 0) {
                var highestPriority = SendPriority.WHENEVER

                for (key in keys) {
                    val json = sharedPrefs.getString(key, "")

                    if (json == null || json.isBlank()) {
                        continue
                    }

                    val persistedMessage = try {
                        persistedMessageAdapter.fromJson(json)
                    } catch (ex: Exception) {
                        when(ex) {
                            is IOException, is JsonDataException -> {
                                Plog.warn(T_MESSAGE, "Unable to recover persisted upstream message", ex,"Message Data" to json)
                                erroredKeys.add(key)
                                null
                            }
                            else -> throw ex
                        }
                    }

                    persistedMessage?.let {
                        val message = RecoveredUpstreamMessage(
                                messageType = it.messageType,
                                messageId = it.messageId,
                                time = it.messageTimestamp,
                                messageData = it.messageData
                        )

                        recoveredMessages.add(StoredUpstreamMessage(
                                this,
                                messageId = message.messageId,
                                message = message,
                                sendPriority = it.sendPriority,
                                requiresRegistration = true,
                                messageSize = it.messageSize,
                                parcelGroupKey = it.parcelGroupKey,
                                expireAfter = it.expireAfter,
                                initialMessageState = it.messageState,
                                initialSendAttempts = it.sendAttempts
                        ))

                        increaseMessageCount(message.messageType)

                        if (it.sendPriority > highestPriority) {
                            highestPriority = it.sendPriority
                        }
                    }
                }

                newMessages.addAll(recoveredMessages)
                existingMessageIds.addAll(recoveredMessages.map { it.messageId })

                Plog.debug(T_MESSAGE, "Restored ${keys.size} pending outbound message, will schedule with priority $highestPriority",
                    "Message Types" to recoveredMessages.groupBy { it.message.messageType }
                            .map { Pair(it.key, it.value.size) }.toMap()
                )

                erroredKeys.forEach { persistor.accept(PersistAction.Remove(it)) }

                if (erroredKeys.size == keys.size) {
                    return@fromCallable null
                }

                return@fromCallable highestPriority
            }

            return@fromCallable null
        }
    }

    /**
     * Read all messages stored in the message storage (regardless of their state or any other
     * properties) as a [Observable].
     *
     * @return An [Observable] which will emit [StoredUpstreamMessage] instances
     */
    fun readMessages(): Observable<StoredUpstreamMessage> {
        return Observable.fromIterable(allMessages)
    }

    /**
     * Add an upstream message to the message store. Once a message is added
     * to the message store, it will be available for sending with the [UpstreamSender].
     *
     * The maximum number of allowed pending messages with the same message type is defined by
     * [HengamConfig.maxPendingUpstreamMessagesPerType]. A pending message is any message which
     * exists in the message regardless of it's state. If too many pending messages already exist
     * with the same message type as the given message the message will be ignored.
     *
     * Messages stored in the [MessageStore] should have unique message ids. If the message being
     * stored has an id which already exists in the [MessageStore] it will be ignored.
     *
     * @param message The [UpstreamMessage] instance which is available for sending
     * @param sendPriority The [SendPriority] used for sending the message
     * @param persist If true the message will be persisted and will be available on application
     *                restart
     * @param requiresRegistration If true, the message will not be sent until the SDK has
     *                             registered with the server
     * @param parcelGroupKey The parcelGroupKey used for sending this message
     *
     * @return A [StoredUpstreamMessage] instance which is stored in the message store or null if
     *         the message is ignored. The [StoredUpstreamMessage] will include the upstream message
     *         and the options given while calling this function.
     */
    fun storeMessage(message: UpstreamMessage, sendPriority: SendPriority,
                     persist: Boolean, requiresRegistration: Boolean,
                     parcelGroupKey: String?, expireAfter: Time?): StoredUpstreamMessage? {
        if (message.messageId in existingMessageIds) {
            Plog.error(T_MESSAGE, "Attempted to store upstream message with duplicate message id", "Message" to messageAdapter.toJson(message))
            return null
        }

        if ((messageCountPerType[message.messageType] ?: 0) >= hengamConfig.maxPendingUpstreamMessagesForType(message.messageType)) {
            Plog.warn.message("Ignoring upstream message with type ${message.messageType}, too many messages of this type are already pending")
                    .withTag(T_MESSAGE)
                    .withData("Pending Count", messageCountPerType[message.messageType])
                    .aggregate("upstream_message_type_limit", millis(500)) {
                        message("Ignoring ${logs.size} upstream messages with type ${message.messageType}, " +
                                "too many messages of this type are already pending")
                        withData("Pending Count", messageCountPerType[message.messageType])
                    }
                    .log()
            return null
        }

        val messageSize = messageAdapter.toJson(message).length
        val storedMessage = StoredUpstreamMessage(
                this,
                message.messageId,
                message,
                sendPriority,
                requiresRegistration,
                messageSize,
                parcelGroupKey,
                expireAfter,
                UpstreamMessageState.Stored()
        )
        newMessages.add(storedMessage)
        existingMessageIds.add(storedMessage.messageId)

        if (persist) {
            persistMessage(storedMessage)
        }

        increaseMessageCount(message.messageType)

        return storedMessage
    }

    /**
     * Persist a [StoredUpstreamMessage] instance or update it if it is already persisted
     *
     * Note, persisting messages are rate limited. This means that by calling this function the
     * stored message will *not* necessarily be immediately be persisted to disk. It maximum amount
     * of time it may take for the message to be persisted is defined by [STORE_WRITE_RATE_LIMIT].
     *
     * @param storedMessage The [StoredUpstreamMessage] to persist
     * @param insertIfNotExist If true, the message will be persisted even if it doesn't already
     *                         exist in the store.
     */
    fun persistMessage(storedMessage: StoredUpstreamMessage,
                       insertIfNotExist: Boolean = true): Boolean {
        if (!insertIfNotExist && storedMessage.messageId !in existingMessageIds) {
            return false
        }
        persistor.accept(PersistAction.Save(storedMessage))
        return true
    }

    /**
     * Remove message from store. If the message is persisted, it will also me removed from
     * the disk storage.
     *
     * @param storedMessage The [StoredUpstreamMessage] instance to remove from storage
     */
    fun removeMessage(storedMessage: StoredUpstreamMessage) {
        removedMessages.add(storedMessage.messageId)
        existingMessageIds.remove(storedMessage.messageId)
        persistor.accept(PersistAction.Remove(storedMessage))
        decreaseMessageCount(storedMessage.message.messageType)
    }

    operator fun contains(messageId: String): Boolean {
        return existingMessageIds.contains(messageId)
    }

    private fun increaseMessageCount(messageType: Int) {
        messageCountPerType[messageType] = (messageCountPerType[messageType] ?: 0) + 1
    }

    private fun decreaseMessageCount(messageType: Int) {
        messageCountPerType[messageType] = (messageCountPerType[messageType] ?: 1) - 1
    }

    private fun initializeMessagePersisting() {
        val changes = mutableListOf<PersistAction>()

        persistor
                .observeOn(cpuThread())
                .doOnNext { changes.add(it) }
                .debounce(STORE_WRITE_RATE_LIMIT, TimeUnit.MILLISECONDS, cpuThread())
                .keepDoing {
                    if (changes.isEmpty()) return@keepDoing
                    
                    Plog.trace(T_MESSAGE, "Persisting ${changes.size} changes in message store")

                    val editor = sharedPrefs.edit()
                    changes.forEach { persistAction ->
                        when (persistAction) {
                            is PersistAction.Save -> {
                                val storedMessage = persistAction.storedMessage
                                val messageData = messageAdapter.toJsonValue(storedMessage.message) ?: emptyMap<String, Any>()
                                val persistedMessage = PersistedUpstreamMessageWrapper(
                                        messageType = storedMessage.message.messageType,
                                        messageId = storedMessage.message.messageId,
                                        sendPriority = storedMessage.sendPriority,
                                        messageData = messageData,
                                        messageSize = storedMessage.messageSize,
                                        parcelGroupKey = storedMessage.parcelGroupKey,
                                        expireAfter = storedMessage.expireAfter,
                                        messageState = storedMessage.messageState,
                                        sendAttempts = storedMessage.sendAttempts,
                                        messageTimestamp = storedMessage.message.time
                                )
                                val json = persistedMessageAdapter.toJson(persistedMessage)
                                editor.putString(storedMessage.messageId, json).apply()
                            }
                            is PersistAction.Remove -> { editor.remove(persistAction.messageId) }
                        }
                    }
                    editor.apply()
                    changes.clear()
                }
    }



    companion object {
        const val MESSAGE_STORE_NAME = "hengam_message_store"
        const val STORE_WRITE_RATE_LIMIT = 1000L
    }
}

class StoredUpstreamMessage(
        private val messageStore: MessageStore,
        val messageId: String,
        val message: UpstreamMessage,
        val sendPriority: SendPriority,
        val requiresRegistration: Boolean,
        val messageSize: Int,
        val parcelGroupKey: String?,
        val expireAfter: Time?,
        initialMessageState: UpstreamMessageState,
        initialSendAttempts: Map<String, Int>? = null
) {
    private val sendAttemptsField: MutableMap<String, Int> = initialSendAttempts?.toMutableMap() ?: mutableMapOf()

    val sendAttempts: Map<String, Int>
        get() = sendAttemptsField

    val totalSendAttempts: Int
        get() = sendAttemptsField.map { it.value }.sum()

    var messageState: UpstreamMessageState = initialMessageState
        private set


    fun updateState(state: UpstreamMessageState, persistChange: Boolean = true) {
        this.messageState = state
        if (persistChange) {
            save(false)
        }
    }

    fun recordFailedSendAttempt(courierId: String) {
        this.sendAttemptsField[courierId] = (this.sendAttemptsField[courierId] ?: 0) + 1
        save(false)
    }

    /**
     * Note: do not dispose message while iterating over message with [MessageStore.readMessages].
     * This will raise a concurrent modification exceptions
     */
    fun disposeMessage() {
        messageStore.removeMessage(this)
    }

    fun save(insertIfNotExist: Boolean) {
        messageStore.persistMessage(this, insertIfNotExist)
    }
}

@JsonClass(generateAdapter = true)
class PersistedUpstreamMessageWrapper(
        @Json(name = "type") val messageType: Int,
        @Json(name = "id") val messageId: String,
        @Json(name = "priority") val sendPriority: SendPriority,
        @Json(name = "data") val messageData: Any,
        @Json(name = "size") val messageSize: Int,
        @Json(name = "group") val parcelGroupKey: String?,
        @Json(name = "expire") val expireAfter: Time?,
        @Json(name = "state") val messageState: UpstreamMessageState,
        @Json(name = "attempts") val sendAttempts: Map<String, Int>,
        @Json(name = "time") val messageTimestamp: Time
)

private sealed class PersistAction {
    class Save(val storedMessage: StoredUpstreamMessage) : PersistAction()
    class Remove(val messageId: String) : PersistAction() {
        constructor(storedMessage: StoredUpstreamMessage) : this(storedMessage.messageId)
    }
}
