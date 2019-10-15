package io.hengam.lib.notification

import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.notification.dagger.NotificationScope
import io.hengam.lib.notification.messages.downstream.NotificationMessage
import io.hengam.lib.utils.HengamStorage
import io.hengam.lib.utils.days
import com.squareup.moshi.*
import javax.inject.Inject

@NotificationScope
class NotificationErrorHandler @Inject constructor(
        private val hengamConfig: HengamConfig,
        hengamStorage: HengamStorage,
        moshi: HengamMoshi
) {

    private val exceptionStats = hengamStorage.createStoredMap(
            "notif_error_stats",
            NotificationErrorStat::class.java,
            NotificationErrorStat.Adapter(moshi.moshi),
            EXCEPTION_STATS_EXPIRATION_TIME
    )

    private fun getOrCreateStat(message: NotificationMessage): NotificationErrorStat =
            exceptionStats[message.messageId] ?: NotificationErrorStat()

    fun hasErrorLimitBeenReached(messageId: String, step: NotificationBuildStep): Boolean {
        val maxAttempts = hengamConfig.maxNotificationBuildStepAttempts(step)
        return exceptionStats[messageId]?.buildErrors?.get(step) ?: 0 >= maxAttempts
    }

    fun hasErrorLimitBeenReached(message: NotificationMessage, step: NotificationBuildStep): Boolean {
        return hasErrorLimitBeenReached(message.messageId, step)
    }

    fun onNotificationBuildFailed(message: NotificationMessage, reason: NotificationBuildStep) {
        val exceptionStat = getOrCreateStat(message)
        exceptionStat.buildErrors[reason] = (exceptionStat.buildErrors[reason] ?: 0) + 1
        exceptionStats[message.messageId] = exceptionStat
    }

    fun onNotificationValidationError(message: NotificationMessage, error: ValidationErrors) {
        val exceptionStat = getOrCreateStat(message)
        exceptionStat.validationErrors[error] = (exceptionStat.validationErrors[error] ?: 0) + 1
        exceptionStats[message.messageId] = exceptionStat
    }

    fun removeNotificationStats(messageId: String) {
        exceptionStats.remove(messageId)
    }

    fun getNotificationBuildErrorStats(messageId: String) = exceptionStats[messageId]?.buildErrors

    fun getNotificationValidationErrorStats(messageId: String) = exceptionStats[messageId]?.validationErrors

    fun getNotificationSkippedSteps(messageId: String): List<NotificationBuildStep> {
        return exceptionStats[messageId]?.buildErrors?.keys?.filter { hasErrorLimitBeenReached(messageId, it) }
                ?: emptyList()
    }

    companion object {
        val EXCEPTION_STATS_EXPIRATION_TIME = days(3)
    }
}


enum class ValidationErrors {
    @Json(name = "ic_not_exist") ICON_NOT_EXIST,
    @Json(name = "btn_ic_not_exist") BUTTON_ICON_NOT_EXIST,
    @Json(name = "led_format") LED_WRONG_FORMAT,
    @Json(name = "bad_action") BAD_ACTION,
    @Json(name = "bad_btn_action") BAD_BUTTON_ACTION
}

/**
 * Holds error stats for a notification build in progress.
 */
private class NotificationErrorStat (
        val buildErrors: MutableMap<NotificationBuildStep, Int> = mutableMapOf(),
        val validationErrors: MutableMap<ValidationErrors, Int> = mutableMapOf()
) {
    class Adapter(moshi: Moshi) : JsonAdapter<NotificationErrorStat>() {
        private val buildErrorsAdapter: JsonAdapter<Map<NotificationBuildStep, Int>> =
                moshi.adapter(Types.newParameterizedType(Map::class.java, NotificationBuildStep::class.java, Int::class.javaObjectType))
        private val validationErrorsAdapter: JsonAdapter<Map<ValidationErrors, Int>> =
                moshi.adapter(Types.newParameterizedType(Map::class.java, ValidationErrors::class.java, Int::class.javaObjectType))

        override fun fromJson(reader: JsonReader): NotificationErrorStat? {
            var buildErrors: MutableMap<NotificationBuildStep, Int>? = null
            var validationErrors: MutableMap<ValidationErrors, Int>? = null
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.selectName(JsonReader.Options.of("build_errs", "validation_errs"))) {
                    0 -> buildErrors = buildErrorsAdapter.fromJson(reader)?.toMutableMap()
                    1 -> validationErrors = validationErrorsAdapter.fromJson(reader)?.toMutableMap()
                }
            }
            reader.endObject()
            return NotificationErrorStat(buildErrors ?: mutableMapOf(), validationErrors
                    ?: mutableMapOf())
        }

        override fun toJson(writer: JsonWriter, value: NotificationErrorStat?) {
            writer.beginObject()
            writer.name("build_errs")
            buildErrorsAdapter.toJson(writer, value?.buildErrors)
            writer.name("validation_errs")
            validationErrorsAdapter.toJson(writer, value?.validationErrors)
            writer.endObject()
        }

    }
}
