package co.pushe.plus.utils

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

typealias Pack = Map<String, Any>
typealias ListPack = List<Any>

fun Pack.toJsonObject(): JSONObject {
    val json = JSONObject()
    for ((key, value) in this) {
        when (value) {
            is String -> json.put(key, value)
            is Int -> json.put(key, value)
            is Double -> json.put(key, value)
            is Boolean -> json.put(key, value)
            is Packable -> json.put(key, value.toPack().toJsonObject())
            is Map<*, *> -> json.put(key, (value as Map<String, Any>).toJsonObject())
            is List<*> -> json.put(key, (value as List<String>).toJsonArray())
        }
    }
    return json
}

fun Pack.toJson(): String = this.toJsonObject().toString()
fun Pack.toJson(indent: Int): String = this.toJsonObject().toString(indent)

fun jsonToPack(json: JSONObject): Pack {
    val map = HashMap<String, Any>()
    for (key in json.keys()) {
        val value = json.get(key)
        when (value) {
            is JSONObject -> map[key] = jsonToPack(value)
            is JSONArray -> map[key] = jsonToListPack(value)
            else -> map[key] = value
        }
    }
    return map
}

fun jsonToPack(json: String) = try {
    jsonToPack(JSONObject(json))
} catch (ex: JSONException) {
    throw PackParseException("Invalid json received when parsing json to list pack", ex)
}

fun ListPack.toJsonArray(): JSONArray {
    val json = JSONArray()
    for (value in this) {
        when (value) {
            is String -> json.put(value)
            is Int -> json.put(value)
            is Double -> json.put(value)
            is Boolean -> json.put(value)
            is Packable -> json.put(value.toPack().toJsonObject())
            is Map<*, *> -> json.put((value as Map<String, Any>).toJsonObject())
            is List<*> -> json.put((value as List<String>).toJsonArray())
        }
    }
    return json
}

fun ListPack.toJson(): String = this.toJsonArray().toString()
fun ListPack.toJson(indent: Int): String = this.toJsonArray().toString(indent)

fun jsonToListPack(json: JSONArray): ListPack {
    val list = ArrayList<Any>()
    for (ind in 0 until json.length()) {
        val value = json.get(ind)
        when (value) {
            is JSONObject -> list.add(jsonToPack(value))
            is JSONArray -> list.add(jsonToListPack(value))
            else -> list.add(value)
        }
    }
    return list
}

fun jsonToListPack(json: String) = try {
    jsonToListPack(JSONArray(json))
} catch (ex: JSONException) {
    throw PackParseException("Invalid json received when parsing json to list pack", ex)
}

fun packOf (vararg args: Pair<String, Any>): Pack = mapOf(*args)

fun lpackOf (vararg args: Any): ListPack = listOf(*args)

interface Packable {
    fun toPack(): Pack
    fun toJson() = toPack().toString()
    fun toJson(indent: Int) = toPack().toJson(indent)
}

open class PackException(message: String) : Exception(message)

class PackParseException(message: String, t: Throwable) : Exception(message, t)

class InvalidPackItemTypeException(message: String) : PackException(message) {
    constructor(key: String, value: Any) :
            this("Attempted to cast Pack Item $key to invalid type $value")
}

class NoSuchPackItemException(key: String) :
        PackException("No item exists in the pack with key $key")
