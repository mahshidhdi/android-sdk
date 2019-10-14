package co.pushe.plus.messaging

import co.pushe.plus.LogTag.T_MESSAGE
import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.internal.cpuThread
import co.pushe.plus.utils.*
import co.pushe.plus.utils.log.Plog
import com.squareup.moshi.*
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * message.state.subscribe {
 *      if (it == UpstreamMessageState.BLABLA) do bla bal
 * }
 * Note: no guarantee that will only be called once per state (operations should be idempotent)
 *
 * No need to dispose manually, the subscription will automatically be disposed when
 * the state becomes SENT
 */
abstract class UpstreamMessage(
        @Transient val messageType: Int,
        @Transient val messageId: String,


        /**
         * All Upstream messages have timestamps
         *
         * This is intentionally a `var` instead of `val`. It's so that Moshi will
         * recognize it in code-gen (https://github.com/square/moshi/issues/601 https://github.com/square/moshi/issues/495)
         *
         * Moshi doesn't recognize @Json on superclass constructor params, so the property name for
         * the timestamp has to be the same as the json field (i.e, "time").
         * (https://github.com/square/moshi/issues/700)
         *
         * Note the @Millis annotation is not recognized here but works because millis is the default
         * If you want to change it to another time unit the time property should not be in the constructor
         */
        @Json(name="time") @Millis var time: Time = TimeUtils.now(),
        @Transient private val mixins: List<MessageMixin>? = null
)  {
    constructor(messageType: Int):
            this(messageType, IdGenerator.generateId(15))

    abstract fun toJson(moshi: Moshi, writer: JsonWriter)

    fun toJson(moshi: Moshi): String = Adapter(moshi).toJson(this)
    fun toJson(moshi: PusheMoshi): String = toJson(moshi.moshi)
    fun toJsonValue(moshi: Moshi): Any? = Adapter(moshi).toJsonValue(this)
    fun toJsonValue(moshi: PusheMoshi): Any? = toJsonValue(moshi.moshi)

    override fun toString(): String {
        val core = PusheInternals.getComponent(CoreComponent::class.java)
            ?: return super.toString()

        val moshi = core.moshi()
        return toJson(moshi)
    }

    class Adapter(val moshi: Moshi) : JsonAdapter<UpstreamMessage>() {
        override fun fromJson(reader: JsonReader?): UpstreamMessage? {
            throw NotImplementedError("UpstreamMessage deserializing not supported")
        }

        override fun toJson(writer: JsonWriter?, value: UpstreamMessage?) {
            if (writer != null) {
                value?.toJson(moshi, writer)
            }
        }
    }
}

class RecoveredUpstreamMessage(
        messageType: Int,
        messageId: String,
        time: Time,
        private val messageData: Any?
) : UpstreamMessage(messageType, messageId, time) {
    override fun toJson(moshi: Moshi, writer: JsonWriter) {
        return moshi.adapter(Any::class.java).toJson(writer, messageData)
    }
}

abstract class SendableUpstreamMessage(messageType: Int) : UpstreamMessage(messageType) {
    @Transient var isPrepared: Boolean = false
        private set
    abstract fun onPrepare(): Completable
    fun prepare(): Completable = onPrepare().doOnComplete { isPrepared = true }
}

abstract class MessageMixin {
    abstract fun collectMixinData(): Single<Map<String, Any?>>
}

abstract class TypedUpstreamMessage<T> (
        messageType: Int,
        @Transient val adapterProvider: (moshi: Moshi) -> JsonAdapter<T>,
        @Transient private val mixins: List<MessageMixin>? = null
) : SendableUpstreamMessage(messageType)  {
    @Transient protected var collectedMixinData: MutableMap<String, Any?>? = null

    override fun toJson(moshi: Moshi, writer: JsonWriter) {
        val jsonValues = adapterProvider(moshi).toJsonValue(this as T) as MutableMap<String, Any?>
        val anyAdapter = moshi.adapter(Any::class.java)
        collectedMixinData?.let { jsonValues.putAll(it) }
        return anyAdapter.toJson(writer, jsonValues)
    }

    override fun onPrepare(): Completable = applyMixins()

    private fun applyMixins(): Completable {
        if (mixins == null) {
            return Completable.complete()
        }
        collectedMixinData = mutableMapOf()

        val sources = mixins
                .map {
                    try {
                        it.collectMixinData()
                                .subscribeOn(cpuThread())
                                .toObservable()
                                .doOnError { err -> Plog.error(T_MESSAGE, err) }
                                .onErrorReturn { emptyMap() }
                    } catch (ex: Exception) {
                        Plog.error(T_MESSAGE, ex)
                        Observable.just(emptyMap<String, Any?>())
                    }
                }

        return Observable.merge(sources)
                .observeOn(cpuThread())
                .doOnNext { collectedMixinData?.putAll(it) }
                .ignoreElements()
    }
}

sealed class UpstreamMessageState {
    object Created : UpstreamMessageState() // Deprecated
    class Stored(val parcelSubGroupKey: String? = null) : UpstreamMessageState()
    class InFlight(val timestamp: Time, val courier: String, val parcelId: String) : UpstreamMessageState()
    class Sent(val parcelId: String, val courier: String) : UpstreamMessageState() // Deprecated

    class Adapter {
        @ToJson
        fun toJson(state: UpstreamMessageState): Map<String, String?> {
            return when(state) {
                is Created -> mapOf("state" to "created")
                is Stored -> mapOf("state" to "stored", "parcel_subgroup" to state.parcelSubGroupKey)
                is InFlight -> mapOf("state" to "in-flight", "time" to state.timestamp.toString(), "courier" to state.courier, "parcel" to state.parcelId)
                is Sent -> mapOf("state" to "sent", "parcel_id" to state.parcelId, "courier" to state.courier)
            }
        }

        @FromJson
        fun fromJson(json: Map<String, String?>): UpstreamMessageState {
            val stateType = json["state"] ?: throw JsonDataException("Missing 'state' field")
            return when (stateType) {
                "created" -> Created
                "stored" -> Stored(json["parcel_subgroup"])
                "in-flight" -> InFlight(
                        Time(json["time"]?.toLongOrNull() ?: throw JsonDataException("Missing 'time' field"), TimeUnit.MILLISECONDS),
                        json["courier"] ?: throw JsonDataException("Missing 'courier' field"),
                        json["parcel"] ?: throw JsonDataException("Missing 'parcel' field")
                )
                "sent" -> Sent(json["parcel_id"] ?: throw JsonDataException("Missing 'parcel_id' field"),
                            json["courier"] ?: throw JsonDataException("Missing 'courier' field"))
                else -> throw JsonDataException("Invalid value for field 'state': $stateType")
            }
        }
    }
}

/**
 * Order is important, from lowest priority to highest priority
 * `SendPriority.IMMEDIATE > SendPriority.WHENEVER`
 */
enum class SendPriority {
    @Json(name="whenever") WHENEVER,
    @Json(name="buffer") BUFFER,
    @Json(name="late") LATE,
    @Json(name="soon") SOON,
    @Json(name="immediate") IMMEDIATE;
}

open class RawDownstreamMessage(val messageId: String, val messageType: Int, val rawData: Any) {
    override fun toString(): String = "RawDownstreamMessage[Id=$messageId Type=$messageType]"
}

abstract class DownstreamMessageParser<T>(
        val messageType: Int,
        private val adapterProvider: (Moshi) -> JsonAdapter<T>
) {

    @Throws(IOException::class)
    fun parseMessage(pusheMoshi: PusheMoshi, rawMessage: RawDownstreamMessage): T? {
        return adapterProvider(pusheMoshi.moshi).fromJsonValue(rawMessage.rawData)
    }

    @Throws(IOException::class)
    fun parseMessage(pusheMoshi: PusheMoshi, jsonData: String): T? {
        return adapterProvider(pusheMoshi.moshi).fromJson(jsonData)
    }
}

@JsonClass(generateAdapter = true)
open class ResponseMessage(
        @Json(name="status") val status: Status,
        @Json(name="error") val error: String = ""
) {
    class Parser(messageType: Int) : DownstreamMessageParser<ResponseMessage>(
            messageType,
            { ResponseMessageJsonAdapter(it) }
    )

    enum class Status(private val value: Int) {
        SUCCESS(0),
        FAIL(1),
        NONE(-1);

        class Adapter  {
            @ToJson fun toJson(responseStatus: Status): Int = responseStatus.value
            @FromJson fun fromJson(value: Int): Status =
                    (Status.values().find { it.value == value}) ?: Status.NONE
        }
    }
}
