package co.pushe.plus

import co.pushe.plus.messages.MessageType
import co.pushe.plus.messaging.UpstreamMessage
import co.pushe.plus.utils.IdGenerator
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi

/**
 * This singleton class provides helper functions for converting/processing legacy API calls
 * to make them compliant with the desired API behaviour. The API calls should be fixed in the
 * future removing the need for these functions.
 *
 * Keep all patches for legacy API calls here in order to have them documented in a single class
 *
 * When adding an API patch function, keep in mind that the current code should also work correctly
 * if the API is fixed from the server's side in the future without also requiring an SDK update
 */
object ApiPatch {

    /**
     * In the current (08/25/18) API if the upstream message type is REGISTRATION (t10) then
     * it shouldn't be a list and should only be a single json object. (This is only the case for
     * REGISTRATION message, all other messages should be a list)
     *
     * ```
     *  {
     *    t28: [{...}, {...}],
     *    t10: {...}  // Not a list!
     *  }
     * ```
     *
     * The API should be changed in the future to one of these:
     * - All message types should be presented as a list even if they could only have a single object
     * - Every message type could be presented either as a single object or a list. If only one
     *   instance of the message is being sent then a single object will be used otherwise a list
     *   will be used
     * (The second proposal is preferred. The first proposal will break the current code and
     * will require SDK update)
     *
     * This function will check if the message type is [MessageType.Upstream.REGISTRATION] (t10)
     * and will change the list format to single object format. If for some reason more than one
     * instances of the registration message exist, it will use the latest one based on the message
     * timestamp.
     *
     * @return true If the message was a registration message and it was changed, false if no
     *         operations were performed
     */
    fun convertRegistrationMessageToSingleMessage(moshi: Moshi, writer: JsonWriter,
                                                  messageKey: String,
                                                  messageValue: List<UpstreamMessage>): Boolean {
        val singleMessageAdapter = moshi.adapter(UpstreamMessage::class.java)
        if (messageKey == "t${MessageType.Upstream.REGISTRATION}" && messageValue.isNotEmpty()) {
            singleMessageAdapter.toJson(writer, messageValue.maxBy { it.time.toMillis() })
            return true
        }
        return false
    }

    /**
     *
     * Currently (08/25/18) the messages given in downstream parcels are json strings
     * which need to be parsed into json objects.
     * E.g,
     * ```
     * {
     *  "message_id": "some message id",
     *  "t10": "{\"status\": 0}"   // value is a string not a json object
     * }
     * ```
     *
     * This should be changed in the future to use json objects instead.
     *
     * This function will check all data in the fcm message to see if the key matches that of a
     * message type (tXX) and if the value is a string. If so, the value will be parsed into a Map
     * and be replaced.
     *
     * @return A new [Map] which will have replaced values if needed
     */
    fun parseDownstreamParcelObjectStrings(message: Map<String, String>,
                                           anyAdapter: JsonAdapter<Any>): Map<String, Any> {
        val messageTypeRegex = "t[0-9]+".toRegex()
        return message.mapValues {
            if (messageTypeRegex.matches(it.key) && it.value is String) {
                anyAdapter.fromJson(it.value) ?: it.value
            } else {
                it.value
            }
        }
    }


    /**
     * There are two formats for downstream parcels
     *
     * Legacy Format: Single message in root. The key `type` determines what message it is
     * ```
     *  {
     *      "message_id": "<message_id>",
     *      "type": 1,
     *      "title": "Notification Title",
     *      "content": "Notification Content"
     *  }
     * ```
     *
     * Standard Format: No messages in root, each message type is in a separate object with the key
     * `t{messageType}`
     * ```
     *  {
     *      "message_id": "<message_id>",
     *      "t1": {
     *          "title": "Notification Title",
     *          "content" "Notification Content"
     *      }
     *  }
     * ```
     *
     * The legacy format is deprecated and will stop being supported from some time in the future.
     * However, since some older versions of the SDK only support the legacy format, messages are
     * currently (08/25/18) being sent in legacy format.
     *
     * This function checks the parcel data to see if it contains a legacy format message. If it
     * does, it converts it into a Standard Format one and adds it to the message.
     *
     * @return If no `type` key exists in the data, returns the same data given in the params. If
     *         `type does exist, creates new data map in Standard Format and returns it.
     */
    fun extractLegacyStyleMessageFromRoot(data: Map<String, Any>,
                                          anyAdapter: JsonAdapter<Any>): Map<String, Any> {
        if ("type" !in data.keys) {
            return data
        }

        val messageTypeRegex = "t[0-9]+".toRegex()
        val extractedMessage = HashMap<String, Any>()
        val newParcel = HashMap<String, Any>()

        for ((key, value) in data) {
            if (!messageTypeRegex.matches(key) &&
                    key != "message_id" && key != "type") {
                if (value is String) {
                    extractedMessage[key] = try {
                        anyAdapter.fromJson(value) ?: throw NullPointerException()
                    } catch (ex: Exception) {
                        value
                    }
                } else {
                    extractedMessage[key] = value
                }
            } else if (key != "type") {
                newParcel[key] = value
            }
        }
        newParcel["t${data["type"]}"] = extractedMessage

        return newParcel
    }

    /**
     * Gcm used to require that all topic names be prefixed with `/topics/` when subscribing.
     * This requirement is deprecated in Fcm and topic names should avoid using this prefix.
     *
     * Currently (09/03/18) the server gives topic names with the mentioned prefix. This should
     * be changed in the future.
     *
     * This function takes a topic name and returns a prefix-less topic name. If the
     * given topic name does not contain a prefix, then it is returned without any
     * changes.
     */
    fun removeTopicNamePrefix(topicName: String): String {
        return if (topicName.startsWith("/topics/")) {
            topicName.substring(8)
        } else {
            topicName
        }
    }


    /**
     * Previously (before 09/29/18) all downstream parcels had a `message_id` field in their root,
     * this parcel id was then given to all messages in the parcel when the parcel was being parsed.
     *
     * This has now changed and downstream parcels don't necessarily contain a parcel id. To ensure
     * that messages still have an identifier this function will check if the parcel has a
     * `message_id` field and if it doesn't it will return a new map with a `message_id` set, the
     * parcel id will then be passed on to messages in the parcel.
     *
     * @param parcel The downstream parcel to inject a `message_id` in
     * @param messageId The message id to set as the parcel's `message_id` if it doesn't already
     *                  have one. If this is not provided, a random message id will be generated.
     */
    fun injectMessageIdInParcel(parcel: Map<String, Any>, messageId: String? = null): Map<String, Any> {
        if (MessageFields.MESSAGE_ID !in parcel) {
            val newParcel = HashMap(parcel)
            val nonNullId = messageId ?: IdGenerator.generateId(12)
            newParcel[MessageFields.MESSAGE_ID] = nonNullId
            return newParcel
        }
        return parcel
    }
}