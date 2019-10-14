package co.pushe.plus.internal

import co.pushe.plus.utils.TimeAdapterFactory
import com.squareup.moshi.*
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.util.*

class PusheMoshi constructor(var moshi: Moshi) {
    constructor(): this(
            Moshi.Builder()
            .add(NumberAdapterFactory())
            .add(TimeAdapterFactory)
            .add(DateAdapter())
            .build()
    )

    /**
     * Add new adapters to the current [PusheMoshi] instance
     */
    fun enhance(enhancer: (moshiBuilder: Moshi.Builder) -> Unit) {
        val builder = moshi.newBuilder()
        enhancer(builder)
        moshi = builder.build()
    }

    /**
     * Create a new [PusheMoshi] from the current one and dd new adapter the
     * new instance
     */
    fun extend(enhancer: (moshiBuilder: Moshi.Builder) -> Unit): PusheMoshi {
        val builder = moshi.newBuilder()
        enhancer(builder)
        return PusheMoshi(builder.build())
    }

    fun <T> adapter(type: Class<T>): JsonAdapter<T> = moshi.adapter(type)
    fun <T> adapter(type: Type): JsonAdapter<T>  = moshi.adapter(type)
}

/**
 * A Json Adapter that prevents numbers getting mapped to Double by default.
 *
 * If you have a json string which contains a number value, And use a Moshi adapter to deserialize
 * it into an `Any` object like below, the default behaviour for Moshi is
 * to convert the number into a Double.
 *
 * ```kotlin
 * val json = """
 * {
 *    "key": 123
 * }
 * """
 *
 * val adapter = moshi.adapter(Any::class.java)
 * val result = adapter.fromJson(json)
 *
 * val map = result as Map<String, Any>
 * map["key"] is Double
 * ```
 *
 * By adding this JsonAdapter Factory to Moshi, if the number in the json
 * string contains a decimal point it will be converted to a Double, otherwise it
 * will be converted to a Long.
 */
class NumberAdapterFactory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (type != Double::class && type != java.lang.Double::class.java) {
            return null
        }

        val delegate = moshi.nextAdapter<Any>(this, type, annotations)

        return object : JsonAdapter<Any>() {
            override fun fromJson(reader: JsonReader): Any? {
                return if (reader.peek() != JsonReader.Token.NUMBER) {
                    delegate.fromJson(reader)
                } else {
                    val next = reader.nextString()
                    return if ("." in next) {
                        next.toDouble()
                    } else {
                        next.toLong()
                    }
                }
            }

            override fun toJson(writer: JsonWriter, value: Any?) {
                delegate.toJson(writer, value)
            }
        }
    }
}

class DateAdapter {
    @ToJson
    fun toJson(date: Date): String {
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
        return simpleDateFormat.format(date)
    }

    @FromJson
    fun fromJson(json: String): Date {
        return json.toLongOrNull()?.let { Date(it) } ?: SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse(json)
    }
}