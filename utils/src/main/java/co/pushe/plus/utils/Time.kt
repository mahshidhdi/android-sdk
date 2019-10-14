package co.pushe.plus.utils

import com.squareup.moshi.*
import java.lang.IllegalArgumentException
import java.util.concurrent.TimeUnit
import java.lang.reflect.Type
import kotlin.math.absoluteValue


open class Time(val time: Long, val timeUnit: TimeUnit) {
    constructor(time: Int, timeUnit: TimeUnit) : this(time.toLong(), timeUnit)

    fun toMillis() = timeUnit.toMillis(time)
    fun toSeconds() = timeUnit.toSeconds(time)
    fun toMinutes() = timeUnit.toMinutes(time)
    fun toHours() = timeUnit.toHours(time)
    fun toDays() = timeUnit.toDays(time)

    override fun toString(): String = toMillis().toString()
    override fun equals(other: Any?): Boolean = other is Time && toMillis() == other.toMillis()
    override fun hashCode(): Int = toMillis().hashCode()

    operator fun plus(other: Time): Time = Time(toMillis() + other.toMillis(), TimeUnit.MILLISECONDS)
    operator fun minus(other: Time): Time = Time(toMillis() - other.toMillis(), TimeUnit.MILLISECONDS)
    operator fun compareTo(other: Time): Int = toMillis().compareTo(other.toMillis())
    fun abs(): Time = Time(time.absoluteValue, timeUnit)

    fun bestRepresentation(): String {
        val second = 1000
        val minute = second * 60
        val hour = minute * 60
        val day = hour * 24
        var result = ""
        var time = toMillis()

        if (time == 0L) {
            return "0"
        }

        (time / day).let { if (it > 0) result = "$it days" }
        time %= day
        (time / hour).let { if (it > 0) result = "$result $it hours" }
        time %= hour
        (time / minute).let { if (it > 0) result = "$result $it minutes" }
        time %= minute
        (time / second).let { if (it > 0) result = "$result $it seconds" }
        time %= second
        if (time > 0) result = "$result $time milliseconds"
        return result
    }
}

fun millis(millis: Long) = Time(millis, TimeUnit.MILLISECONDS)
fun seconds(seconds: Long) = Time(seconds, TimeUnit.SECONDS)
fun minutes(minutes: Long) = Time(minutes, TimeUnit.MINUTES)
fun hours(hours: Long) = Time(hours, TimeUnit.HOURS)
fun days(days: Long) = Time(days, TimeUnit.DAYS)

@Retention(AnnotationRetention.RUNTIME)
@JsonQualifier
annotation class Millis

@Retention(AnnotationRetention.RUNTIME)
@JsonQualifier
annotation class Seconds

@Retention(AnnotationRetention.RUNTIME)
@JsonQualifier
annotation class Minutes

@Retention(AnnotationRetention.RUNTIME)
@JsonQualifier
annotation class Hours

@Retention(AnnotationRetention.RUNTIME)
@JsonQualifier
annotation class Days

object TimeAdapterFactory : JsonAdapter.Factory {
    private val allTimeUnits = setOf(Millis::class.java, Seconds::class.java, Minutes::class.java, Hours::class.java, Days::class.java)

    override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (type != Time::class.java) {
            return null
        }

        for (annotation in annotations) {
            for (timeUnit in allTimeUnits) {
                if (annotation.annotationClass.java == timeUnit) {
                    return TimeAdapter(timeUnit)
                }
            }
        }

        return TimeAdapter(Millis::class.java)
    }

    class TimeAdapter(private val timeUnit: Any) : JsonAdapter<Time> () {
        override fun fromJson(reader: JsonReader): Time? {
            return Time(reader.nextLong(), when(timeUnit) {
                Millis::class.java -> TimeUnit.MILLISECONDS
                Seconds::class.java -> TimeUnit.SECONDS
                Minutes::class.java -> TimeUnit.MINUTES
                Hours::class.java -> TimeUnit.HOURS
                Days::class.java -> TimeUnit.DAYS
                else -> throw IllegalArgumentException("Invalid time unit annotation $timeUnit")
            })
        }

        override fun toJson(writer: JsonWriter, value: Time?) {
            writer.value(when(timeUnit) {
                Millis::class.java -> value?.toMillis()
                Seconds::class.java -> value?.toSeconds()
                Minutes::class.java -> value?.toMinutes()
                Hours::class.java -> value?.toHours()
                Days::class.java -> value?.toDays()
                else -> throw IllegalArgumentException("Invalid time unit annotation $timeUnit")
            })
        }

    }
}