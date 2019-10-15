package io.hengam.lib.messaging

import io.hengam.lib.*
import io.hengam.lib.LogTag.T_MESSAGE
import io.hengam.lib.dagger.CoreScope
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.internal.ioThread
import io.hengam.lib.internal.task.TaskScheduler
import io.hengam.lib.tasks.UpstreamSenderTask
import io.hengam.lib.utils.*
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.rx.*
import com.squareup.moshi.JsonDataException
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.ceil

@CoreScope
class PostOffice @Inject constructor(
        private val taskScheduler: TaskScheduler,
        private val messageStore: MessageStore,
        private val parcelStamper: ParcelStamper,
        private val moshi: HengamMoshi,
        private val hengamConfig: HengamConfig,
        hengamLifecycle: HengamLifecycle
) {
    private val incomingMessages: PublishRelay<RawDownstreamMessage> = PublishRelay.create()
    private val upstreamThrottler: Relay<UpstreamMessageSignal> = PublishRelay.create<UpstreamMessageSignal>()
    private var allowsPostRegistrationMessages = false
    private var shouldScheduleSendOnRegistrationComplete = false

    init {
        initializeThrottlers()

        /* Allow sending post-registration upstream messages after registration */
        hengamLifecycle.waitForRegistration()
                .justDo {
                    allowsPostRegistrationMessages = true
                    if (shouldScheduleSendOnRegistrationComplete) scheduleUpstreamMessageSender()
                }

        /* Restore messages after initialization */
        hengamLifecycle.waitForPreInit()
                .justDo {
                    messageStore.restoreMessages()
                            .subscribeOn(cpuThread())
                            .subscribeBy(
                                    onSuccess = { upstreamThrottler.accept(UpstreamMessageSignal(it, true)) },
                                    onError = {
                                        Plog.error(T_MESSAGE, MessageRestoreException("Restoring upstream messages failed", it))
                                    }
                            )
                }
    }

    /**
     * Used for signalling that an UpstreamMessage is available for sending.
     *
     * The message throttlers will use the parameters to determine whether an [UpstreamSenderTask]
     * should be scheduled or not.
     */
    private class UpstreamMessageSignal(
            val sendPriority: SendPriority,
            val requiresRegistration: Boolean
    )

    /**
     * Schedule upstream message to be sent.
     *
     * The message will be stored on the message store and depending on the message's
     * `sendPriority` value, an [UpstreamSenderTask] may be scheduled to run.
     *
     * Both operations will be scheduled to be run on the [cpuThread]. The method will return
     * immediately.
     *
     * @param message An object of [UpstreamMessage] to be sent
     * @param sendPriority The priority of the message which determines when the message will
     *                     be sent
     * @param persistAcrossRuns If this parameter is true and the message sending does not
     *                          successfully happen before the application closes (it fails or
     *                          because of SendPriority) then the message will be stored in the
     *                          message store and will be attempted again sent on the application's
     *                          next run
     * @param requiresRegistration If this parameter is true the message will only be sent if Hengam
     *                             registration has successfully been performed. If the SDK has not
     *                             been registered yet, the message will be queued and sent when
     *                             registration is successful.
     * @param parcelGroupKey If this parameter is give, then the parcel which this message is sent
     *                       in will only contain other messages which have the same value for
     *                       `parcelGroupKey`.
     * @param expireAfter Specifies the maximum amount of time which this message should be kept in
     *                    the message store and attempted to be sent before being discarded. Note,
     *                    this time is calculated from the moment the message was created, not when
     *                    the `sendMessage` function was called. If not specified, then a default
     *                    value obtained from the config will be used.
     *
     * @see UpstreamMessage
     * @see SendPriority
     */
    fun sendMessage(message: SendableUpstreamMessage,
                    sendPriority: SendPriority = SendPriority.SOON,
                    persistAcrossRuns: Boolean = true,
                    requiresRegistration: Boolean = true,
                    parcelGroupKey: String? = null,
                    expireAfter: Time? = null
    ) {
        if (persistAcrossRuns && !requiresRegistration) {
            // Persisting messages can only happen if message requires registration
            // This was decided just for code simplicity, this way we don't have to check if
            // messages require registration when restoring them.
            Plog.warn.message("Persisting upstream messages is not supported for messages that to not require registration")
                    .withTag(T_MESSAGE)
                    .withData("Message Type", message.messageType)
                    .withData("Message Id", message.messageId)
                    .log()
        }

        message.prepare()
                .subscribeOn(cpuThread())
                .observeOn(cpuThread())
                .justDo {
                    val storedMessage = messageStore.storeMessage(message, sendPriority,
                            persistAcrossRuns && requiresRegistration,
                            requiresRegistration, parcelGroupKey, expireAfter)
                    if (storedMessage != null) {
                        upstreamThrottler.accept(UpstreamMessageSignal(sendPriority, requiresRegistration))
                    }
                }
    }

    /**
     * Simple overloaded function to be used in Java
     */
    fun sendMessage(message: SendableUpstreamMessage, sendPriority: SendPriority) {
        sendMessage(message, sendPriority, true)
    }

    private fun initializeThrottlers() {
        upstreamThrottler
                .filter { it.sendPriority == SendPriority.IMMEDIATE }
                .keepDoing { scheduleUpstreamMessageSender(it) }

        upstreamThrottler
                .filter { it.sendPriority == SendPriority.SOON }
                .debounce(BUFFER_TIME_SOON, TimeUnit.MILLISECONDS, ioThread())
                .observeOn(cpuThread())
                .keepDoing { scheduleUpstreamMessageSender(it) }

        upstreamThrottler
            .filter { it.sendPriority == SendPriority.LATE }
            .debounce(BUFFER_TIME_LATE, TimeUnit.MILLISECONDS, ioThread())
            .observeOn(cpuThread())
            .keepDoing { scheduleUpstreamMessageSender(it) }

        upstreamThrottler
                .filter { it.sendPriority == SendPriority.BUFFER || it.sendPriority == SendPriority.WHENEVER }
                .throttleLatest(500, TimeUnit.MILLISECONDS, ioThread(), false)
                .observeOn(cpuThread())
                .flatMapSingle { isFullParcelReady() }
                .filter { it }
                .doOnNext { Plog.trace(T_MESSAGE, "Full parcel available for sending, triggering upstream send task") }
                .keepDoing { scheduleUpstreamMessageSender() }
    }

    /**
     * Checks these two conditions:
     * - Whether enough upstream messages are stored and available to create a parcel with the
     * maximum size (specified by [maxParcelMessageSize])
     * - Whether an upstream message is stored that has a [SendPriority.BUFFER] priority
     *
     * If both conditions are valid then returns `true`. Note, for both conditions only messages are
     * considered which have their registration requirement satisfied. This means that either the
     * message does not require registration or that registration has been performed.
     *
     * @return A [Single] which emits `true` if both conditions are satisfied
     */
    private fun isFullParcelReady(): Single<Boolean> {
        class Result (var totalSize: Int = 0, var messageAvailable: Boolean = false)
        val maxParcelSize = hengamConfig.upstreamMaxParcelSize
        return messageStore.readMessages()
                .filter { it.messageState is UpstreamMessageState.Stored }
                .collect({Result()}) { result, storedMessage ->
                    if (storedMessage.requiresRegistration || allowsPostRegistrationMessages) {
                        if (storedMessage.sendPriority == SendPriority.BUFFER) {
                            result.messageAvailable = true
                        }
                        result.totalSize += storedMessage.messageSize
                    }
                }
                .map { it.messageAvailable && it.totalSize >= maxParcelSize }
    }

    /**
     * Schedules an [UpstreamSenderTask] to stored messages
     *
     * If the [UpstreamMessageSignal] which triggered this event has a true value for
     * `requiresRegistration` then the task will not be scheduled if registration has not completed.
     * Instead, a flag will be set to schedule the task once registration is complete.
     * If no [UpstreamMessageSignal] is provided then the task will be scheduled regardless of
     * whether the client is registered or not.
     *
     * @param messageSignal The [UpstreamMessageSignal] that triggered this event
     */
    private fun scheduleUpstreamMessageSender(messageSignal: UpstreamMessageSignal? = null, delay: Time? = null) {
        if (messageSignal == null || !messageSignal.requiresRegistration || allowsPostRegistrationMessages) {
            taskScheduler.scheduleTask(UpstreamSenderTask.Options, null, delay)
        } else {
            shouldScheduleSendOnRegistrationComplete = true
        }
    }

    /**
     * Collect stored outbound messages which are available for sending from the [MessageStore].
     * Only messages with the [UpstreamMessageState.Stored] state will be collected.
     *
     * Messages will be grouped and returned as [UpstreamParcel] instances. The parcels will be
     * stamped using the [ParcelStamper].
     *
     * The sum of all message sizes in a parcel will not exceed [HengamConfig.upstreamMaxParcelSize],
     * however the total parcel size will includes other information (e.g., the stamp data, message
     * types) and may go higher than this value.
     *
     * There is no specific rule on which messages will be grouped together in a parcel except that
     * messages which have a `parcelGroupKey` will not be grouped with messages which have a
     * different key or don't have a key.
     *
     * @return An [Observable] which will emit one or multiple [UpstreamParcel] instances
     */
    fun collectParcelsForSending(): Observable<out UpstreamParcel> {
        val maxParcelSize = hengamConfig.upstreamMaxParcelSize
        return messageStore.readMessages()
                .filter { allowsPostRegistrationMessages || !it.requiresRegistration }
                .filter { it.messageState is UpstreamMessageState.Stored }
                .groupBy { "${it.parcelGroupKey ?: ""}#$#${(it.messageState as? UpstreamMessageState.Stored)?.parcelSubGroupKey}" }
                .flatMap { group ->
                    group
                            .bufferWithValue(maxParcelSize) { it.messageSize }
                            .map { storedMessages -> storedMessages.map { it.message } }
                            .map { UpstreamParcel(UpstreamParcel.generateParcelId(it), it)}
                            .flatMapSingle { parcelStamper.stampParcel(it) }
                            .filter { it.messages.isNotEmpty() }
                }
    }

    /**
     * Checks whether any in-flight messages exist.
     *
     * An in-flight message is a message which has been sent with a courier but no ACK or ERROR
     * has been received.
     *
     * @return A [Single] which emits `true` if any messages in the message store have a
     *         [UpstreamMessageState.InFlight] state.
     */
    fun areMessagesInFlight(): Single<Boolean> {
        return messageStore.readMessages()
                .filter { it.messageState is UpstreamMessageState.InFlight }
                .any { true }
    }

    /**
     * Should be called when an [UpstreamParcel] has been sent with a courier and is waiting for
     * an ACK.
     *
     * After calling this function all the messages in the parcel will have an
     * [UpstreamMessageState.InFlight] state.
     */
    fun onParcelInFlight(parcel: UpstreamParcel, courierId: String) {
        val newState = UpstreamMessageState.InFlight(TimeUtils.now(), courierId, parcel.parcelId)
        val parcelMessageIds = parcel.messages.map { it.messageId }.toSet()
        messageStore.allMessages
                .filter { it.message.messageId in parcelMessageIds }
                .forEach { it.updateState(newState) }
    }

    /**
     * Should be called by an outbound courier once it has received an ACK for an in-flight parcel.
     *
     * After calling this function all the messages in the parcel will have an
     * [UpstreamMessageState.Sent] state.
     */
    fun onParcelAck(parcelId: String, courierId: String) {
        val sentMessages = messageStore.allMessages
                .filter { (it.messageState as? UpstreamMessageState.InFlight)?.parcelId == parcelId }

        Plog.debug.message("Parcel successfully sent")
                .withTag(T_MESSAGE)
                .withData("Id", parcelId)
                .withData("Message Count", sentMessages.size)
                .aggregate("parcel-ack", 1, TimeUnit.SECONDS) {
                    message("${logs.size} Parcels successfully sent")
                    withData("Parcel Ids", logs.map { it.logData["Id"] })
                    withData("Total Messages", logs.sumBy { it.logData["Message Count"] as? Int ?: 0})
                    withData("Total Messages", logs.sumBy { it.logData["Message Count"] as? Int ?: 0})
                }
                .log()

        val newState = UpstreamMessageState.Sent(parcelId, courierId)
        sentMessages
                .forEach {
                    it.updateState(newState, persistChange = false)
                    it.disposeMessage()
                }
    }

    /**
     * Should be called by an outbound courier once it has received an ACK for an in-flight parcel.
     *
     * The failed send attempt will be recorded for each of the parcel's messages in the message
     * store. After calling this function all the messages in the parcel will be given a
     * [UpstreamMessageState.Stored] state and will be available for sending again.
     */
    fun onParcelError(parcelId: String, courierId: String, cause: Exception) {
        val parcelMessages = messageStore.allMessages
                .filter { it.messageState is UpstreamMessageState.InFlight }
                .filter { (it.messageState as? UpstreamMessageState.InFlight)?.parcelId == parcelId }

        val originalMessageCount = UpstreamParcel.getParcelMessageCountFromId(parcelId)

        if (cause is ParcelTooBigException) {
            // Checking whether the parcel contained only a single message or not should be done with
            // the "original" message count` extracted from the parcel id rather than the size of the
            // `parcelMessages` list. This is because some of the messages in the parcel may have been
            // expired and removed and this may cause us to mistake that the parcel had only a single
            // "too-big" message when it actually had more.

            if (parcelMessages.isEmpty()) {
                Plog.error(T_MESSAGE, ParcelSendingException("Parcel is too big error received for parcel that does not exist", cause),
                        "Original Message Count" to originalMessageCount)
            } else if (originalMessageCount.takeIf { it > 0 } ?: parcelMessages.size == 1) {
                Plog.error.withError(ParcelSendingException("Parcel is too big for courier $courierId but cannot be split any further", cause))
                        .withTag(T_MESSAGE)
                        .withData("Courier", courierId)
                        .withData("Parcel Id", parcelId)
                        .withData("Original Message Count", originalMessageCount)
                        .withData("Message Type", parcelMessages[0].message.messageType)
                        .withData("Message Size", parcelMessages[0].messageSize)
                        .log()
                /*
                 * For now we are disposing messages which are too big for a courier
                 * We might want to change this in the future though, it might be big for one courier
                 * but ok for another.
                 *
                 * Fix Needed: If there are two couriers and the upstream sender sends with the first and
                 *        fails because of this, the message will be disposed. Now even if the next
                 *        courier manages to send it immediately the message will be disposed and the changes will
                 *        not be available in the store.
                 */
                // parcelMessages.forEach { it.recordFailedSendAttempt(courierId) }
                parcelMessages.forEach { it.disposeMessage() }
                return
            }

            val parcelGroup = IdGenerator.generateId(5)
            val firstState = UpstreamMessageState.Stored("$parcelGroup-1")
            val secondState = UpstreamMessageState.Stored("$parcelGroup-2")

            val firstHalfSize = ceil(parcelMessages.size / 2.0)
            for (i in 0 until parcelMessages.size) {
                parcelMessages[i].updateState(if (i < firstHalfSize) firstState else secondState)
            }

            Plog.debug.message("Splitting large parcel in to two smaller parcels")
                    .withTag(T_MESSAGE)
                    .withData("Original Parcel Id", parcelId)
                    .withData("Message Count", parcelMessages.size)
                    .withData("Original Message Count", originalMessageCount)
                    .aggregate("parcel-split", millis(500)) {
                        message("Splitting ${logs.size} large parcels in to smaller parcels")
                        withData("Original Parcel Ids", logs.map { it.logData["Original Parcel Id"] })
                    }
                    .log()

            debounce("parcel-too-big-retry", seconds(1)) { scheduleUpstreamMessageSender() }
        } else {
            Plog.warn.message("Parcel sending failed with $courierId")
                    .withTag(T_MESSAGE)
                    .withError(cause)
                    .withData("Id", parcelId)
                    .withData("Message Count", parcelMessages.size)
                    .withData("Original Message Count", originalMessageCount)
                    .aggregate("send-fail-$courierId-${cause.message?.hashCode()}", millis(500)) {
                        message("Parcel sending failed for ${logs.size} parcels with $courierId")
                        logs[0].throwable?.let { withError(it) }
                        withData("Parcel Ids", logs.map { it.logData["Id"] })
                        withData("Total Messages", logs.sumBy { it.logData["Message Count"] as? Int ?: 0 })
                    }
                    .log()

            val newState = UpstreamMessageState.Stored()
            parcelMessages.forEach {
                it.recordFailedSendAttempt(courierId)
                it.updateState(newState)
            }

            val minBackoff = parcelMessages
                    .map { Math.pow(2.0, ((it.sendAttempts[courierId]?.toDouble() ?: 0.0) + 2)).toLong() }
                    .min() ?: 4L

            debounce("parcel-fail-retry", minBackoff, seconds(1)) { backOffs ->
                val globalMinBackOff = backOffs.min()
                Plog.debug(T_MESSAGE, "Scheduling upstream sender to send failed messages in $globalMinBackOff seconds")
                scheduleUpstreamMessageSender(delay = seconds(minBackoff))
            }
        }
    }

    /**
     * Checks if any in-flight messages have timed out and need to be sent again.
     *
     * An in-flight message is considered to be timed out if it has been sent for longer than
     * the time specified by [HengamConfig.upstreamMessageTimeout] and we have not received an ACK or error
     * for it.
     *
     * If a message has been timed out it will be give an [UpstreamMessageState.Stored] state and
     * will be available for sending again.
     *
     * @return A [Completable] that will complete once the check has been made for all messages
     */
    fun checkInFlightMessageTimeouts(): Completable {
        val now = TimeUtils.now()
        val messageTimeout = hengamConfig.upstreamMessageTimeout
        val newState = UpstreamMessageState.Stored()
        return messageStore.readMessages()
                .filter { it.messageState is UpstreamMessageState.InFlight }
                .filter { getMessageInFlightTime(now, it) >= messageTimeout }
                .doOnNext {
                    it.recordFailedSendAttempt((it.messageState as? UpstreamMessageState.InFlight)?.courier ?: "unknown")
                    it.updateState(newState)
                }
                .map { mapOf(
                    "Id" to it.messageId,
                    "Type" to it.message.messageType,
                    "In-flight Time" to "${getMessageInFlightTime(now, it).toHours()} hours"
                ) }
                .toList()
                .doOnSuccess {
                    if (it.size > 0) {
                        Plog.warn(T_MESSAGE, "${it.size} in-flight messages have timed out and will be sent again", "Messages" to it)
                    }
                }
                .ignoreElement()
    }

    private fun getMessageInFlightTime(now: Time, storedMessage: StoredUpstreamMessage): Time {
        return (now - ((storedMessage.messageState as? UpstreamMessageState.InFlight)?.timestamp ?: now)).abs()
    }

    /**
     * Checks if any messages with an [UpstreamMessageState.Stored] state have been expired and
     * disposes messages which have been.
     *
     * The message expiration time is specified when calling [sendMessage] or by the
     * [HengamConfig.upstreamMessageExpirationTime] value. Once this amount of time has passed since
     * the creation of the message, the message will be considered expired.
     *
     * @return A [Completable] that will complete once all expired messages have been disposed
     */
    fun checkMessageExpirations(): Completable {
        val now = TimeUtils.now()
        val defaultExpirationTime = hengamConfig.upstreamMessageExpirationTime
        return messageStore.readMessages()
                .filter { it.messageState is UpstreamMessageState.Stored }
                .filter { (now - it.message.time) >= (it.expireAfter ?: defaultExpirationTime) }
                // Need to call toList() here to complete the [readMessages] observable in order to be able to call dispose on messages
                .toList()
                .doOnSuccess { messages ->
                    messages.map {
                        Plog.trace(T_MESSAGE, "Upstream message has expired, disposing message",
                                "Id" to it.messageId, "Type" to it.message.messageType, "Time In Store" to TimeUtils.now() - it.message.time)
                        it.disposeMessage()
                    }
                }
                .doOnSuccess { if (it.size > 0) Plog.warn(T_MESSAGE, "${it.size} messages have been expired") }
                .ignoreElement()
    }

    /**
     * Should be called by [InboundCourier] instances whenever a new parcel has been received
     *
     * @param parcel The received downstream parcel
     */
    fun onInboundParcelReceived(parcel: DownstreamParcel) {
        parcel.messages.forEach { incomingMessages.accept(it) }
    }

    fun handleLocalParcel(parcel: DownstreamParcel) {
        parcel.messages.forEach { incomingMessages.accept(it) }
    }

    @Throws(ParcelParseException::class)
    fun handleLocalParcel(parcelData: Map<String, Any>, defaultMessageId: String? = null) {
        val validParcelData = if (MessageFields.MESSAGE_ID !in parcelData) {
            val updatedData = parcelData.toMutableMap()
            updatedData[MessageFields.MESSAGE_ID] = defaultMessageId ?: IdGenerator.generateId()
            updatedData
        } else {
            parcelData
        }

        try {
            DownstreamParcel.Adapter(moshi.moshi).fromJsonValue(validParcelData)?.let(this::handleLocalParcel)
        } catch (ex: Exception) {
            when(ex) {
                is IOException, is JsonDataException, is ParcelParseException ->
                    throw ParcelParseException("Invalid parcel data received in local parcel handler", ex)
                else -> throw ex
            }
        }
    }

    /**
     * Receive downstream messages of all types.
     * As long as you keep a subscription to the returned [Observable]
     * you will receive any new received messages in the subscribers `onNext()`method.
     * Messages are given as [RawDownstreamMessage] objects.
     *
     * ```
     * postOffice.receiveMessages()
     *      .subscribe {
     *          // it: RawDownstreamMessage
     *          println("Received new message $it")
     *      }
     * ```
     *
     * @return An [Observable] of [RawDownstreamMessage]
     */
    fun receiveMessages(): Observable<RawDownstreamMessage> {
        return incomingMessages
    }

    /**
     * Receive downstream messages with the given Message Type.
     * As long as you keep a subscription to the returned [Observable]
     * you will receive new messages with the type `messageType` in the
     * subscribers `onNext()`method. Messages are given as
     * [RawDownstreamMessage] objects.
     *
     * ```
     * postOffice.receiveMessages(10)
     *      .subscribe {
     *          // it: RawDownstreamMessage
     *          println("Received new message $it of type 10")
     *      }
     * ```
     *
     * @param messageType The Message Type to filter messages with
     * @return An [Observable] of [RawDownstreamMessage]
     */
    fun receiveMessages(messageType: Int): Observable<RawDownstreamMessage> {
        return incomingMessages
                .observeOn(cpuThread())
                .filter { it.messageType == messageType }
    }

    /**
     * Receive downstream messages, filtered and parsed with the given
     * [DownstreamMessageParser].
     *
     * As long as you keep a subscription to the returned [Observable]
     * you will receive new messages in the subscribers `onNext()`method.
     *
     * The type of the message objects returned should be provided as a
     * generic type for the method. The message can have any type and does not
     * need to enhance any particular class. However, a subclass of
     * [DownstreamMessageParser] capable of converting [RawDownstreamMessage] objects
     * to the desired message object should be provided.
     *
     * ```
     * class MyMessageParser<MyMessage> {...}
     *
     * postOffice.receiveMessages(MyMessageParser())
     *      .subscribe {
     *          // it: MyMessage
     *          println("Received new message $it")
     *      }
     * ```
     *
     * @param messageParser An instance of a [DownstreamMessageParser] capable of parsing messages
     * of type `T`
     * @return An [Observable] of instances of type `T`
     * @see DownstreamMessageParser
     */
    fun <T> receiveMessages(messageParser: DownstreamMessageParser<T>, parseErrorHandler: ((Map<String, Any?>) -> Unit)? = null): Observable<T> {
        return incomingMessages
                .observeOn(cpuThread())
                .filter { it.messageType == messageParser.messageType}
                .flatMap {
                    try {
                        Observable.just(messageParser.parseMessage(moshi, it))
                    } catch (ex: Exception) {
                        when (ex) {
                            is JsonDataException, is IOException ->
                                Plog.error(T_MESSAGE, MessageHandlingException("Could not parse downstream message", ex),
                                        "Message Type" to messageParser.messageType, "Message" to moshi.adapter(Any::class.java).toJson(it.rawData))
                            else ->
                                Plog.wtf(T_MESSAGE, MessageHandlingException("Unexpected error occurred on downstream message parsing", ex),
                                        "Message Type" to messageParser.messageType, "Message" to moshi.adapter(Any::class.java).toJson(it.rawData))
                        }

                        try {
                            parseErrorHandler?.invoke(it.rawData as Map<String, Any?>)
                        } catch (ex: Exception) {
                            Plog.error(T_MESSAGE, ex)
                        }

                        Observable.empty<T>()
                    }
                }
    }

    /**
     * Register a handler to be called when any new messages are received.
     *
     * This method will internally call the `receiveMessages()` method, subscribe
     * to the returned observable and call the handler on the subscriber's `onNext` call.
     */
    fun mailBox(handler: (RawDownstreamMessage) -> Unit) {
        receiveMessages().keepDoing(T_MESSAGE, onHandlerError = {
            Plog.error(T_MESSAGE, MessageHandlingException("Unhandled error occurred while handling message", it))
        }, onNext = handler)
    }

    /**
     * Register a handler to be called when messages of the given message type are received.
     *
     * This method will internally call the `receiveMessages(messageType)` method, subscribe
     * to the returned observable and call the handler on the subscriber's `onNext` call.
     */
    fun mailBox(messageType: Int, handler: (RawDownstreamMessage) -> Unit) {
        receiveMessages(messageType).keepDoing(T_MESSAGE, onHandlerError = {
            Plog.error(T_MESSAGE, MessageHandlingException("Unhandled error occurred while handling message t$messageType", it))
        }, onNext = handler)
    }

    /**
     * Register a handler to be called when messages of the given parser's type are received.
     *
     * This method will internally call the `receiveMessages(messageParser)` method, subscribe
     * to the returned observable and call the handler on the subscriber's `onNext` call.
     *
     */
    fun <T: Any> mailBox(messageParser: DownstreamMessageParser<T>, handler: (T) -> Unit) {
        receiveMessages(messageParser).keepDoing(T_MESSAGE, onHandlerError = {
            Plog.error(T_MESSAGE, MessageHandlingException("Unhandled error occurred while handling message t${messageParser.messageType}", it))
        }, onNext = handler)
    }

    /**
     * Register a handler to be called when messages of the given parser's type are received.
     *
     * This method will internally call the `receiveMessages(messageParser)` method, subscribe
     * to the returned observable and call the handler on the subscriber's `onNext` call.
     *
     * TODO add docs for parseErrorHandler
     */
    fun <T: Any> mailBox(messageParser: DownstreamMessageParser<T>, handler: (T) -> Unit, parseErrorHandler: ((Map<String, Any?>) -> Unit)) {
        receiveMessages(messageParser, parseErrorHandler).keepDoing(T_MESSAGE, onHandlerError = {
            Plog.error(T_MESSAGE, MessageHandlingException("Unhandled error occurred while handling message t${messageParser.messageType}", it))
        }, onNext = handler)
    }

    companion object {
        const val BUFFER_TIME_SOON = 2000L
        const val BUFFER_TIME_LATE = 3 * 60 * 1000L
    }
}

private class MessageRestoreException(message: String, cause: Throwable? = null) : Exception(message, cause)
class MessageHandlingException(message: String, cause: Throwable? = null) : Exception(message, cause)
class ParcelSendingException(message: String, cause: Throwable? = null) : Exception(message, cause)
class ParcelTooBigException(message: String, cause: Throwable? = null) : Exception(message, cause)