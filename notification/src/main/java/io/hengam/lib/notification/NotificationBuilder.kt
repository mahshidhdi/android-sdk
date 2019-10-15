package io.hengam.lib.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.*
import android.provider.Settings
import android.support.annotation.RequiresApi
import android.util.DisplayMetrics
import android.util.Log
import android.widget.RemoteViews
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.internal.ioThread
import io.hengam.lib.notification.LogTag.T_NOTIF
import io.hengam.lib.notification.actions.Action
import io.hengam.lib.notification.actions.DialogAction
import io.hengam.lib.notification.actions.FallbackAction
import io.hengam.lib.notification.messages.downstream.NotificationMessage
import io.hengam.lib.notification.messages.downstream.NotificationMessageJsonAdapter
import io.hengam.lib.notification.utils.ImageDownloader
import io.hengam.lib.notification.utils.MaterialIconHelper
import io.hengam.lib.notification.utils.getNotificationButtonIds
import io.hengam.lib.utils.*
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.rx.safeSingleFromCallable
import com.squareup.moshi.Json
import io.reactivex.Observable
import io.reactivex.Single
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject


/**
 * Builds a [Notification] object based on parameters available in a [NotificationMessage]
 */
class NotificationBuilder constructor(
        private val message: NotificationMessage,
        private val context: Context,
        private val notificationSettings: NotificationSettings,
        private val errorHandler: NotificationErrorHandler,
        private val imageDownloader: ImageDownloader,
        private val hengamConfig: HengamConfig,
        moshi: HengamMoshi
) {
    private val messageAdapter = NotificationMessageJsonAdapter(moshi.moshi)
    private val actionAdapter = moshi.adapter(Action::class.java)
    private val skippedSteps = mutableListOf<NotificationBuildStep>()
    private val noError = Exception()

    /**
     * Builds a [Notification] object which the data provided in the [NotificationMessage] provided
     * in the class constructor. The [Notification] will be emitted by the [Single] returned by the
     * method.
     *
     * If building the notification fails a [NotificationBuildException] will be emitted which contains
     * the underlying exceptions which caused the process to fail.
     *
     * If the notification message contains a custom sound it will be played but only if all other
     * steps complete successfully.
     */
    fun build(): Single<Notification> {
        val builderGet = performStep(NotificationBuildStep.CREATE_BUILDER, Single.fromCallable{ createBuilder() })
                ?: Single.just(Notification.Builder(context))

        return builderGet.flatMap { builder ->
            val stepErrors = performStepsAndCollectErrors(
                    Step(NotificationBuildStep.ACTION_INTENT) { setNotificationContentAction(builder) },
                    Step(NotificationBuildStep.DISMISS_INTENT) { setNotificationDismissAction(builder) },
                    Step(NotificationBuildStep.BACKGROUND_IMAGE) { setNotificationBackgroundImage(builder) },
                    Step(NotificationBuildStep.CONTENT) { setNotificationContent(builder) },
                    Step(NotificationBuildStep.BIG_CONTENT) { setNotificationBigContent(builder) },
                    Step(NotificationBuildStep.IMAGE) { setNotificationImage(builder) },
                    Step(NotificationBuildStep.SMALL_ICON, { setBlankNotificationSmallIcon(builder) }) { setNotificationSmallIcon(builder) },
                    Step(NotificationBuildStep.ICON) { setNotificationIcon(builder) },
                    Step(NotificationBuildStep.DIALOG_ICON) { cacheDialogIcon() },
                    Step(NotificationBuildStep.BUTTONS) { setNotificationButtons(builder) },
                    Step(NotificationBuildStep.TICKER) { setNotificationTicker(builder) },
                    Step(NotificationBuildStep.AUTO_CANCEL) { setNotificationAutoCancel(builder) },
                    Step(NotificationBuildStep.ON_GOING) { setNotificationOngoing(builder) },
                    Step(NotificationBuildStep.LED) { setNotificationLed(builder) },
                    Step(NotificationBuildStep.SOUND) { setNotificationSound(builder) },
                    Step(NotificationBuildStep.BADGE) { setNotificationBadge(builder) },
                    Step(NotificationBuildStep.PRIORITY) { setNotificationPriority(builder) }
            )

            // Abort if any errors exit in previous steps
            stepErrors.toList()
                    .flatMap { buildErrors ->
                        if (buildErrors.isEmpty()) {
                            Single.just(builder)
                        } else {
                            Single.error(combineErrors(buildErrors))
                        }
                    }
        }.flatMap { builder ->
            // Create notification object
            performStep(NotificationBuildStep.FINALIZE, Single.fromCallable{ finalize(builder) })
                    ?.onErrorResumeNext { Single.error(combineErrors(listOf(it))) }
                    ?: Single.error(IllegalStateException("Cannot continue notification building due to failure in finalize step "))
        }.flatMap { notification ->
            // Play custom sound
            performStep(NotificationBuildStep.SOUND_DOWNLOAD, playCustomNotificationSound(notification))
                    ?.onErrorResumeNext { Single.error(combineErrors(listOf(it))) }?.map { notification }
                    ?: Single.just(notification)
        }.doOnSuccess {
            if (skippedSteps.size == 1) {
                Plog.warn(T_NOTIF, "Skipped notification build '${skippedSteps[0].name.toLowerCase()}' step due to too many failures",
                        "Message Id" to this@NotificationBuilder.message.messageId
                )
            } else if (skippedSteps.isNotEmpty()) {
                Plog.warn(T_NOTIF, "Skipped ${skippedSteps.size} notification build steps due to too many failures",
                        "Message Id" to this@NotificationBuilder.message.messageId,
                        "Skipped Steps" to skippedSteps.map { it.name.toLowerCase() }
                )
            }
        }
    }

    /**
     * Performs a list of notification build steps sequentially and collects any errors occur in
     * the build steps.
     * @return An [Observable] which emits the exceptions which occur during the build steps
     */
    private fun performStepsAndCollectErrors(vararg steps: Step): Observable<Exception> {
        // Note: intentionally using `concatMap` and not `flatMap` so that steps are performed sequentially
        return Observable.fromIterable(steps.toList())
                .concatMap { step ->
                    performStep(step.type, safeSingleFromCallable { step.func() }, step.onSkipFunc?.let { safeSingleFromCallable(it) })
                            ?.map { noError }
                            ?.onErrorReturn { it as Exception } // TODO
                            ?.toObservable()
                            ?: Observable.just(noError)
                }.filter { it != noError }

    }

    /**
     * Attempts running a notification build step defined by the `stepFunc` parameter and keeps
     * record of any errors that occur in the process with the [NotificationErrorHandler].
     *
     * If the error limit has been reached (i.e., the build step has already failed the maximum
     * allowed times in previous attempts) then `doIfLimitReached` will be executed instead of
     * `stepFunc`.
     *
     * If executing the step takes longer than the timeout defined by [HengamConfig.notificationBuildStepTimeout]
     * then the step will fail with a [NotificationStepTimeoutException].
     *
     * @param buildStep The [NotificationBuildStep] which is to be performed
     * @param stepFunc A [Single] which performs the step when subscribed to
     * @param doIfLimitReached An optional alternative step to perform if the attempt limit has been reached
     * @return A [Single] which emits the return value of the step function
     */
    private fun <T> performStep(buildStep: NotificationBuildStep, stepFunc: Single<T>, doIfLimitReached: Single<T>? = null): Single<T>? {
        return try {
            val limitReached = errorHandler.hasErrorLimitBeenReached(message, buildStep)
            val buildStepFunction = if (limitReached) {
                skippedSteps.add(buildStep)
                doIfLimitReached
            } else {
                stepFunc
            }

            val timeout = hengamConfig.notificationBuildStepTimeout(buildStep)
            buildStepFunction
                    ?.subscribeOn(ioThread())
                    ?.observeOn(cpuThread())
                    ?.timeout(timeout.toMillis(), TimeUnit.MILLISECONDS, cpuThread())
                    ?.onErrorResumeNext {
                        if (limitReached) {
                            Single.error(noError)
                        } else {
                            errorHandler.onNotificationBuildFailed(message, buildStep)
                            Single.error(
                                if (it is TimeoutException) {
                                    NotificationStepTimeoutException("Notification step '${buildStep.toString().toLowerCase()}' timed out after ${timeout.bestRepresentation()}")
                                } else {
                                    it
                                }
                            )
                        }
                    }
        } catch (ex: Exception) {
            errorHandler.onNotificationBuildFailed(message, buildStep)
            Single.error(ex)
        }
    }

    /**
     * Combines multiple errors into a single [NotificationBuildException].
     */
    private fun combineErrors(buildErrors: List<Throwable>): NotificationBuildException {
        return when {
            buildErrors.size == 1 -> NotificationBuildException("Notification build failed", buildErrors)
            buildErrors.isNotEmpty() -> NotificationBuildException("Notification build failed with ${buildErrors.size} errors", buildErrors)
            else -> throw IllegalStateException("Notification build exception can not be built with no exceptions")
        }
    }

    private fun createBuilder(): Notification.Builder {
        return when {
            SDK_INT >= O -> {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val ringerMode = am.ringerMode

                if (!message.notifChannelId.isNullOrBlank()) {
                    Notification.Builder(context, message.notifChannelId)
                } else if (message.soundUrl != null &&
                        isValidWebUrl(message.soundUrl) &&
                        notificationSettings.isCustomSoundEnabled &&
                        ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                    Notification.Builder(context, Constants.DEFAULT_SILENT_CHANNEL_ID)
                } else {
                    Notification.Builder(context, Constants.DEFAULT_CHANNEL_ID)
                }
            }
            else -> Notification.Builder(context)
        }
    }

    private fun finalize(builder: Notification.Builder): Notification {
        return when {
            SDK_INT >= JELLY_BEAN -> builder.build()
            else -> builder.notification
        }
    }

    private fun setNotificationPriority(builder: Notification.Builder) {
        if (SDK_INT >= JELLY_BEAN) {
            // TODO: Use Importance instead of priority
            when (message.priority) {
                Notification.PRIORITY_DEFAULT -> builder.setPriority(Notification.PRIORITY_DEFAULT)
                Notification.PRIORITY_HIGH -> builder.setPriority(Notification.PRIORITY_HIGH)
                Notification.PRIORITY_LOW -> builder.setPriority(Notification.PRIORITY_LOW)
                Notification.PRIORITY_MAX -> builder.setPriority(Notification.PRIORITY_MAX)
                Notification.PRIORITY_MIN -> builder.setPriority(Notification.PRIORITY_MIN)
                else -> builder.setPriority(Notification.PRIORITY_MAX)
            }
        }
    }

    private fun setNotificationContentAction(builder: Notification.Builder) {
        if (message.action is FallbackAction) {
            errorHandler.onNotificationValidationError(message, ValidationErrors.BAD_ACTION)
        }

        val actionPendingIntent = PendingIntent.getService(context, IdGenerator.generateIntegerId(),
                createActionIntent(message, message.action, null),
                PendingIntent.FLAG_UPDATE_CURRENT)

        builder.setContentIntent(actionPendingIntent)
    }

    private fun setNotificationDismissAction(builder: Notification.Builder) {
        val dismissPendingIntent = PendingIntent.getService(context, IdGenerator.generateIntegerId(),
                createDismissIntent(message), PendingIntent.FLAG_UPDATE_CURRENT)

        builder.setDeleteIntent(dismissPendingIntent)
    }

    private fun setNotificationTicker(builder: Notification.Builder) {
        message.ticker?.letIfNotBlank { builder.setTicker(it) }
    }

    private fun setNotificationAutoCancel(builder: Notification.Builder) {
        /*
         * Hadi: In the legacy Hengam SDK cancelling was done in the service run by the pending intent.
         *       Not sure if there was a reason behind this and why it wasn't done like below
         */
        // Set notification to auto cancel.
        builder.setAutoCancel(true)
    }

    private fun setNotificationOngoing(builder: Notification.Builder) {
        builder.setOngoing(message.permanentPush)
    }

    private fun setNotificationBackgroundImage(builder: Notification.Builder) {
        if (!message.justImgUrl.isNullOrBlank()) {
            val contentView = RemoteViews(context.packageName, R.layout.hengam_custom_notification)
            val backgroundImg = imageDownloader.getImage(message.justImgUrl)
            contentView.setImageViewBitmap(R.id.hengam_notif_bkgrnd_image, backgroundImg)
            when {
                SDK_INT >= N -> builder.setCustomContentView(contentView)
                else -> builder.setContent(contentView)
            }
        }
    }

    private fun setNotificationContent(builder: Notification.Builder) {
        if (message.justImgUrl.isNullOrBlank()) {
            builder.setContentTitle(
                    if (!message.title.isNullOrBlank()) message.title
                    else message.bigTitle
            )
            builder.setContentText(
                    if (!message.content.isNullOrBlank()) message.content
                    else message.bigContent
            )
        }
    }

    private fun setNotificationBigContent(builder: Notification.Builder) {
        when {
            SDK_INT >= JELLY_BEAN -> {
                if (!message.bigTitle.isNullOrBlank() || !message.bigContent.isNullOrBlank()) {
                    val style = Notification.BigTextStyle()

                    style.setBigContentTitle(
                            if (!message.bigTitle.isNullOrBlank()) message.bigTitle
                            else message.title
                    )

                    style.bigText(
                            if (!message.bigContent.isNullOrBlank()) message.bigContent
                            else message.content
                    )

                    message.summary?.letIfNotBlank { style.setSummaryText(it) }

                    builder.style = style
                }
            }
            else -> Plog.warn(T_NOTIF, "Hengam", "Notification style not supported below Android 4.1")
        }
    }

    private fun setNotificationImage(builder: Notification.Builder) {
        when {
            SDK_INT >= JELLY_BEAN -> {
                if (!message.imageUrl.isNullOrBlank()) {
                    val style = Notification.BigPictureStyle()

                    message.bigTitle?.letIfNotBlank { style.setBigContentTitle(it) }
                    message.summary?.letIfNotBlank { style.setSummaryText(it) }

                    message.bigIconUrl?.letIfNotBlank { imageUrl ->
                        style.bigLargeIcon(imageDownloader.getImage(imageUrl))
                    }

                    style.bigPicture(imageDownloader.getImage(message.imageUrl))

                    builder.style = style
                }
            }
            else -> Plog.warn(T_NOTIF, "Hengam", "Notification style not supported below Android 4.1")
        }
    }

    private fun setNotificationSmallIcon(builder: Notification.Builder) {
        if (message.useHengamIcon) {
            builder.setSmallIcon(io.hengam.lib.R.drawable.ic_hengam)
        } else {
            if (SDK_INT >= M && message.smallIconUrl != null && isValidWebUrl(message.smallIconUrl)) {
                val image = imageDownloader.getImage(message.smallIconUrl)
                builder.setSmallIcon(Icon.createWithBitmap(image))
            } else if (message.smallIcon.isNullOrBlank()){
                val useWhiteIcon =
                        SDK_INT >= LOLLIPOP
                val silhouetteId = context.resources.getIdentifier(
                        "ic_silhouette",
                        "drawable",
                        context.packageName
                )
                builder.setSmallIcon(
                        if (useWhiteIcon && silhouetteId > 0) silhouetteId
                        else context.applicationInfo.icon
                )
            } else {
                val resId = MaterialIconHelper.getIconResourceByMaterialName(context, message.smallIcon)
                if (resId > 0) { // if user specified an icon from drawable as its smallIcon
                    builder.setSmallIcon(resId)
                } else {
                    errorHandler.onNotificationValidationError(message, ValidationErrors.ICON_NOT_EXIST)
                    val emptyIconId = context.resources.getIdentifier("hengam_ic_empty", "drawable", context.packageName)
                    builder.setSmallIcon(
                            if ( emptyIconId > 0) emptyIconId
                            else context.applicationInfo.icon
                    )
                }
            }
        }
    }

    private fun setBlankNotificationSmallIcon(builder: Notification.Builder) {
        val emptyIconId = context.resources.getIdentifier("hengam_ic_empty", "drawable", context.packageName)
        builder.setSmallIcon(
                if ( emptyIconId > 0) emptyIconId
                else context.applicationInfo.icon
        )
    }

    private fun setNotificationIcon(builder: Notification.Builder) {
        if (message.iconUrl != null && !message.iconUrl.isBlank()) {
            val iconUrl = getIconForDevice(message.iconUrl)
            val image = imageDownloader.getImage(iconUrl)
            builder.setLargeIcon(image)
        }
    }

    private fun cacheDialogIcon() {
        val allActions =
                listOf(message.action, *message.buttons.map { it.action }.toTypedArray())

        allActions
                .filter { it is DialogAction }
                .forEach {
                    val dialogAction = it as DialogAction
                    if (dialogAction.iconUrl != null && isValidWebUrl(dialogAction.iconUrl)) {
                        imageDownloader.downloadImageAndCache(dialogAction.iconUrl)
                    }
                }
    }

    private fun setNotificationLed(builder: Notification.Builder) {
        if (message.ledColor != null) {
            if (message.ledColor.matches("-?\\d+\\.?\\d*".toRegex())) {
                builder.setLights(message.ledColor.toDouble().toInt(), message.ledOnTime, message.ledOffTime)
            } else {
                errorHandler.onNotificationValidationError(message, ValidationErrors.LED_WRONG_FORMAT)
            }
        }
    }

    private fun setNotificationButtons(builder: Notification.Builder) {
        when {
            SDK_INT >= JELLY_BEAN -> {
                if (message.buttons.isEmpty()) return
                val buttonIds = getNotificationButtonIds(message.buttons)

                message.buttons.forEachIndexed { index, button ->
                    val iconId = if (button.icon.isNullOrBlank()) {
                        context.resources.getIdentifier("hengam_ic_empty", "drawable", context.packageName)
                    } else {
                        val foundIconId = MaterialIconHelper.getIconResourceByMaterialName(context, button.icon)
                        if (foundIconId == 0) {
                            errorHandler.onNotificationValidationError(message, ValidationErrors.BUTTON_ICON_NOT_EXIST)
                            context.resources.getIdentifier("hengam_ic_empty", "drawable", context.packageName)
                        } else {
                            foundIconId
                        }
                    }

                    val buttonId = buttonIds[index]

                    if (button.action is FallbackAction) {
                        errorHandler.onNotificationValidationError(message, ValidationErrors.BAD_BUTTON_ACTION)
                    }

                    val pendingIntent = PendingIntent.getService(context, IdGenerator.generateIntegerId(),
                            createActionIntent(message, button.action, buttonId), 0)

                    when {
                        SDK_INT >= M -> builder.addAction(Notification.Action.Builder(Icon.createWithResource(context, iconId), button.text, pendingIntent).build())
                        SDK_INT >= KITKAT_WATCH -> builder.addAction(Notification.Action.Builder(iconId, button.text, pendingIntent).build())
                        else -> builder.addAction(iconId, button.text, pendingIntent)
                    }
                }
            }
            else -> Log.w("Hengam", "Notification buttons not supported below Android 4.1")
        }
    }

    private fun setNotificationBadge(builder: Notification.Builder) {
        message.badgeState?.let {
            if (it > 0) builder.setNumber(it)
            else Plog.warn(T_NOTIF, "Notification badge value should not be less than 1 otherwise it will be ignored")
        }
    }

    private fun setNotificationSound(builder: Notification.Builder) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val ringerMode = am.ringerMode

        if (!isValidWebUrl(message.soundUrl) || !notificationSettings.isCustomSoundEnabled || ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            // TODO: Use non-deprecated API for notification sound
            builder.setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
        }
    }

    private fun createActionIntent(notification: NotificationMessage, action: Action, btnId: String?): Intent {
        val messageJson = messageAdapter.toJson(notification)
        val actionJson = actionAdapter.toJson(action)
        val intent = Intent(context, NotificationActionService::class.java)
        intent.putExtra(NotificationActionService.INTENT_DATA_ACTION, actionJson)
        intent.putExtra(NotificationActionService.INTENT_DATA_NOTIFICATION, messageJson)
        intent.putExtra(NotificationActionService.INTENT_DATA_RESPONSE_ACTION, NotificationActionService.RESPONSE_ACTION_CLICK)
        btnId?.let { intent.putExtra(NotificationActionService.INTENT_DATA_BUTTON_ID, btnId) }
        return intent
    }

    private fun createDismissIntent(notification: NotificationMessage): Intent {
        val messageJson = messageAdapter.toJson(notification)
        val intent = Intent(context, NotificationActionService::class.java)
        intent.putExtra(NotificationActionService.INTENT_DATA_NOTIFICATION, messageJson)
        intent.putExtra(NotificationActionService.INTENT_DATA_RESPONSE_ACTION, NotificationActionService.RESPONSE_ACTION_DISMISS)
        return intent
    }

    /**
     * Migrated from Hengam v1.4.1 `NotificationController.changeIcon`
     */
    private fun getIconForDevice(iconUrl: String): String {
        val metrics = context.resources.displayMetrics
        var suffix = ""
        //below checks MUST be in this increasing order or it may faile
        if (metrics.densityDpi <= DisplayMetrics.DENSITY_MEDIUM)
            suffix = "-m"
        else if (metrics.densityDpi <= DisplayMetrics.DENSITY_HIGH)
            suffix = "-h"
        else if (metrics.densityDpi <= DisplayMetrics.DENSITY_XHIGH)
            suffix = "-xh"
        else if (metrics.densityDpi <= DisplayMetrics.DENSITY_XXHIGH || metrics.densityDpi > DisplayMetrics.DENSITY_XXHIGH)
            suffix = "-xxh"

        val pasvand = iconUrl.substring(iconUrl.lastIndexOf("."))
        val str = iconUrl.substring(0, iconUrl.lastIndexOf(".")) + suffix + pasvand

        Plog.trace(T_NOTIF, "Notification icon url for this device ",
            "Density" to metrics.densityDpi.toString(),
            "Icon url" to str
        )
        return str
    }

    private fun playCustomNotificationSound(notification: Notification): Single<Notification> {
        return Single.defer {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val ringerMode = am.ringerMode

            if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                notification.defaults = notification.defaults or Notification.DEFAULT_VIBRATE
            } else if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                if (message.soundUrl != null && isValidWebUrl(message.soundUrl) && notificationSettings.isCustomSoundEnabled) {
                    if (SDK_INT >= O) {
                        createSilentNotificationChannel()
                    }
                    notification.defaults = notification.defaults or Notification.DEFAULT_VIBRATE
                    return@defer NotificationSoundPlayer(message.soundUrl, hengamConfig.notificationMaxSoundDuration)
                            .play()
                            .toSingleDefault(notification)
                }
            }
            return@defer Single.just(notification)
        }
    }

    @RequiresApi(O)
    private fun createSilentNotificationChannel() {
        val notificationManager = context.getSystemService(
                Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel(Constants.DEFAULT_SILENT_CHANNEL_ID) == null) {
            Plog.debug(T_NOTIF, "Creating default silent notification channel")
            val channel = NotificationChannel(Constants.DEFAULT_SILENT_CHANNEL_ID,
                    Constants.DEFAULT_SILENT_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            channel.setSound(null, null)
            channel.enableLights(true)
            notificationManager.createNotificationChannel(channel)
        }
    }
}

private class Step(
        val type: NotificationBuildStep,
        val onSkipFunc: (() -> Unit)? = null,
        val func: () -> Unit
)

enum class NotificationBuildStep {
    @Json(name = "create_builder") CREATE_BUILDER,
    @Json(name = "dl_sound") SOUND_DOWNLOAD,
    @Json(name = "action_intent") ACTION_INTENT,
    @Json(name = "dismiss_intent") DISMISS_INTENT,
    @Json(name = "sm_icon") SMALL_ICON,
    @Json(name = "icon") ICON,
    @Json(name = "image") IMAGE,
    @Json(name = "led") LED,
    @Json(name = "sound") SOUND,
    @Json(name = "bg_img") BACKGROUND_IMAGE,
    @Json(name = "ticker") TICKER,
    @Json(name = "auto_cancel") AUTO_CANCEL,
    @Json(name = "ongoing") ON_GOING,
    @Json(name = "dg_icon") DIALOG_ICON,
    @Json(name = "buttons") BUTTONS,
    @Json(name = "style") STYLE,
    @Json(name = "priority") PRIORITY,
    @Json(name = "content") CONTENT,
    @Json(name = "big_content")  BIG_CONTENT,
    @Json(name = "badge") BADGE,
    @Json(name = "finalize") FINALIZE,
    @Json(name = "unknown") UNKNOWN
}

class NotificationBuildException(
        message: String,
        causes: Iterable<Throwable>? = null
) : CompositeException(message, causes ?: listOf(IllegalStateException(message))) {
    constructor(message: String, cause: Throwable) : this(message, listOf(cause))
}

class NotificationStepTimeoutException(
        message: String,
        cause: Throwable? = null
) : Exception(message, cause)


class NotificationBuilderFactory @Inject constructor (
        private val context: Context,
        private val notificationSettings: NotificationSettings,
        private val errorHandler: NotificationErrorHandler,
        private val imageDownloader: ImageDownloader,
        private val hengamConfig: HengamConfig,
        private val moshi: HengamMoshi
) {
    fun createNotificationBuilder(message: NotificationMessage): NotificationBuilder {
        return NotificationBuilder(message, context, notificationSettings, errorHandler, imageDownloader, hengamConfig, moshi)
    }
}