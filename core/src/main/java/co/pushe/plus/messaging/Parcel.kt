package co.pushe.plus.messaging

import co.pushe.plus.ApiPatch
import co.pushe.plus.LogTag.T_MESSAGE
import co.pushe.plus.MessageFields
import co.pushe.plus.utils.IdGenerator
import co.pushe.plus.utils.log.Plog
import com.squareup.moshi.*
import java.io.IOException


open class UpstreamParcel constructor(
        val parcelId: String,
        val messages: Collection<UpstreamMessage>
)  {
    val messageCount: Int get() = messages.size

    private fun groupMessages(): Map<String, List<UpstreamMessage>> {
        return messages
                .groupBy { it.messageType }
                .mapKeys { "t${it.key}" }
    }

    protected open fun toJson(moshi: Moshi, writer: JsonWriter) {
        val messageListType = Types.newParameterizedType(List::class.java, UpstreamMessage::class.java)
        val messageListAdapter: JsonAdapter<List<UpstreamMessage>> = moshi.adapter(messageListType)

        val groupedMessages = groupMessages()
        for ((key, value) in groupedMessages) {
            writer.name(key)

            if (ApiPatch.convertRegistrationMessageToSingleMessage(moshi, writer, key, value)) {
                continue
            }

            messageListAdapter.toJson(writer, value)
        }

        /* Add `types: []` key to json */
        val listAdapter: JsonAdapter<List<String>> = moshi.adapter(
                Types.newParameterizedType(List::class.java, String::class.java))
        writer.name("types")
        listAdapter.toJson(writer, groupedMessages.keys.toList())
    }

    open class Adapter (val moshi: Moshi) : JsonAdapter<UpstreamParcel>() {
        override fun fromJson(reader: JsonReader?): UpstreamParcel? {
            throw NotImplementedError("UpstreamParcel Json parsing is not supported")
        }

        override fun toJson(writer: JsonWriter?, value: UpstreamParcel?) {
            writer?.let {
                writer.beginObject()
                value?.toJson(moshi, writer)
                writer.endObject()
            }
        }
    }

    companion object {
        private const val PARCEL_ID_LENGTH = 16

        /**
         * Create a parcel id for a parcel with the given messages.
         *
         * The parcel id is in the format `{randomString}#{messageCount}` where `{messageCount}` is
         * the number of messages contained in the parcel in hexadecimal and `{randomString}` is a
         * randomly generated string. The overall size of the id is defined by [PARCEL_ID_LENGTH].
         *
         * The reason for including the message count in the parcel id is for cases where a ACK or
         * error is received for the parcel and we need to know how many messages the parcel contained.
         * In such cases some of the parcel messages might have been deleted from the [MessageStore]
         * (e.g., been expired) and so we won't be able to get the real message count by searching
         * the store.
         */
        fun generateParcelId(messages: List<UpstreamMessage>): String {
            val messageCount = messages.size.toString(16)
            return "${IdGenerator.generateId(length = PARCEL_ID_LENGTH - messageCount.length)}#$messageCount"
        }

        /**
         * Extract how many messages in the parcel from the parcel id.
         *
         * @see [generateParcelId] to see why this may be useful
         *
         * @return The number of messages contained in the parcel with the given id or -1 if it is
         * unable to extract this information for any reason
         */
        fun getParcelMessageCountFromId(parcelId: String): Int {
            val hexCount = parcelId.split("#").takeIf { it.size >= 2 }?.get(1) ?: return -1
            return hexCount.toIntOrNull(16) ?: -1
        }
    }
}

class UpstreamStampedParcel(
        parcel: UpstreamParcel,
        private val stamp: Map<String, Any>
) : UpstreamParcel(parcel.parcelId, parcel.messages) {
    override fun toJson(moshi: Moshi, writer: JsonWriter) {
        super.toJson(moshi, writer)
        for ((key, value) in stamp) {
            writer.name(key)
            when (value) {
                is String -> writer.value(value)
                is Int -> writer.value(value)
                is Boolean -> writer.value(value)
                is Long -> writer.value(value)
                is Double -> writer.value(value)
            }
        }
    }
}

class DownstreamParcel(
        val parcelId: String,
        val messages: Collection<RawDownstreamMessage>
) {

    class Adapter (val moshi: Moshi): JsonAdapter<DownstreamParcel>() {
        private val stringAdapter: JsonAdapter<String> = moshi.adapter(String::class.java).nonNull()
        private val messageTypeRegex = "t[0-9]+".toRegex()
        private val otherKnownKeys = listOf("courier")
        override fun fromJson(reader: JsonReader): DownstreamParcel? {
            val messages = ArrayList<RawDownstreamMessage>()
            val unknownKeys by lazy { HashSet<String>() }
            var hasUnknownKeys = false

            try {
                reader.beginObject()

                var parcelId: String? = null
                val messagePairs = ArrayList<Pair<Int, Any>>()

                while (reader.hasNext()) {
                    val key = reader.nextName()

                    if (key == MessageFields.MESSAGE_ID) {
                        parcelId = stringAdapter.fromJson(reader)
                        continue
                    }

                    val value = reader.readJsonValue() ?: emptyMap<String, Any?>()

                    if (!messageTypeRegex.matches(key)) {
                        if (key !in otherKnownKeys) {
                            unknownKeys.add(key)
                            hasUnknownKeys = true
                        }
                        continue
                    }

                    val type = try {
                        key.substring(1).toInt()
                    } catch (ex: NumberFormatException) {
                        throw ParcelParseException("Invalid message type $key")
                    }

                    when (value) {
                        is List<*> -> value.forEach { message -> message?.let{ messagePairs.add(Pair(type, it)) } }
                        is Map<*, *> -> messagePairs.add(Pair(type, value))
                        else -> Plog.error(T_MESSAGE, "Invalid message type received in downstream message, it was neither List or Map",
                                "Message Data Class" to value::class.java.simpleName,
                                "Message Type" to type
                            )
                    }
                }

                if (parcelId == null || parcelId.isBlank()) {
                    throw ParcelParseException("Missing `${MessageFields.MESSAGE_ID}` on downstream parcel")
                }

                reader.endObject()

                /* Add the parcelId as a `message_id` field to all messages in the parcel */
                messagePairs.forEach {
                    try {
                        if (it.second is MutableMap<*, *>) {
                            @Suppress("UNCHECKED_CAST")
                            val mutableValue = it.second as MutableMap<String, Any>

                            if (MessageFields.MESSAGE_ID in mutableValue) {
                                Plog.warn(T_MESSAGE, "Downstream message in parcel contains a `${MessageFields.MESSAGE_ID}` field." +
                                    "Messages should not contain a `${MessageFields.MESSAGE_ID}`, it will be replaced with the parcel's id")
                            }

                            mutableValue[MessageFields.MESSAGE_ID] = parcelId
                        } else {
                            Plog.error(T_MESSAGE, "Downstream message data was not a mutable map when parsing parcel",
                                "Message Data Class" to it.second::class.java.simpleName,
                                "Message Type" to it.first
                            )
                        }
                    } catch (ex: Exception) {
                        Plog.error(T_MESSAGE, "Exception occurred when adding `${MessageFields.MESSAGE_ID}` to downstream message",
                            ex,
                            "Parcel Id" to parcelId,
                            "Message Type" to it.first
                        )
                    }
                }

                messages.addAll(messagePairs.map { RawDownstreamMessage(parcelId, it.first, it.second) })

                if (hasUnknownKeys) {
                    Plog.warn(T_MESSAGE, "Unidentified keys found in downstream parcel, they will be ignored",
                        "Unknown Keys" to unknownKeys.toList().toString()
                    )
                }

                return DownstreamParcel(parcelId, messages)
            } catch (ex: IOException) {
                throw ParcelParseException("Error parsing downstream parcel", ex)
            }
        }

        override fun toJson(writer: JsonWriter?, value: DownstreamParcel?) {
            throw NotImplementedError("DownstreamParcel toJson is not supported")
        }
    }
}

class ParcelParseException(message: String, t: Throwable?) : Exception(message, t) {
    constructor(message: String) : this(message, null)
}
