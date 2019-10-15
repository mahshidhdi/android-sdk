package io.hengam.lib.datalytics.geofence

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.hengam.lib.HengamLifecycle
import io.hengam.lib.datalytics.LogTags.T_DATALYTICS
import io.hengam.lib.datalytics.LogTags.T_GEOFENCE
import io.hengam.lib.datalytics.messages.downstream.GeofenceMessage
import io.hengam.lib.datalytics.messages.downstream.GeofenceMessageJsonAdapter
import io.hengam.lib.datalytics.messages.downstream.RemoveGeofenceMessage
import io.hengam.lib.datalytics.tasks.GeofencePeriodicRegisterTask
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.internal.task.TaskScheduler
import io.hengam.lib.messaging.ParcelParseException
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.utils.*
import io.hengam.lib.utils.PermissionChecker.hasPermission
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.rx.justDo
import io.hengam.lib.utils.rx.subscribeBy
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.Geofence.*
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.GeofencingRequest.*
import com.google.android.gms.location.LocationServices
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import javax.inject.Inject


class GeofenceManager @Inject constructor(
        private val context: Context,
        private val postOffice: PostOffice,
        private val taskScheduler: TaskScheduler,
        hengamLifecycle: HengamLifecycle,
        hengamStorage: HengamStorage,
        hengamMoshi: HengamMoshi
) {
    /**
     * All added geofences are stored here. Geofences stored here may not have been successfully
     * registered with the [GeofencingClient] or may have been unregistered for a number of reasons,
     * so it is necessary to periodically re-register all geofences stored in this collection.
     *
     * Also if a geofence has an expiration time, the expiration time should be specified when storing
     * it to avoid holding on to expired geofence data.
     */
    val geofences: PersistedMap<GeofenceMessage> = hengamStorage.createStoredMap(
            "geofences",
            GeofenceMessage::class.java,
            GeofenceMessageJsonAdapter(hengamMoshi.moshi)
    )

    /**
     * This collection keeps track of the number of times a geofence has been triggered so that we
     * can enforce geofence trigger limits. Note, unlike the [geofences] collection, items stored
     * here should not have expiration times and should be stored for the lifetime of the installation.
     */
    val geofenceTriggerCounts: PersistedMap<Int> = hengamStorage.createStoredMap(
            "geofence_counts",
            Int::class.javaObjectType
    )

    /**
     * This collection keeps track of the last time a geofence has been triggered so that we
     * can enforce geofence trigger **rate** limits. Note, unlike the [geofences] collection, items stored
     * here should not have expiration times and should be stored for the lifetime of the installation.
     */
    val geofenceTriggerTimes: PersistedMap<Time> = hengamStorage.createStoredMap(
            "geofence_times",
            Time::class.java
    )

    private val geofencingClient: GeofencingClient by lazy { LocationServices.getGeofencingClient(context) }

    init {
        // We need to re-register all geofences on device boot
        // (https://developer.android.com/training/location/geofencing#re-register-geofences-only-when-required)
        hengamLifecycle.onBootCompleted.justDo(T_DATALYTICS, T_GEOFENCE) { this.ensureGeofencesAreRegistered().justDo(T_DATALYTICS, T_GEOFENCE) }
    }

    /**
     * Adds a geofence (defined by a [GeofenceMessage] object) and registers it with the [GeofencingClient].
     *
     * Note, registering the geofence may fail for a number of reasons. However, as long as a non-zero
     * number of geofences have been added, (i.e., the [geofences] collection is not empty) the
     * [GeofencePeriodicRegisterTask] task will be run periodically and will register all added geofences again.
     *
     * If user has not granted the [ACCESS_FINE_LOCATION] permission, the geofence will be stored,
     * however, it will not be registered with the [GeofencingClient]. If at a later time the user
     * grants this permission the [GeofencePeriodicRegisterTask] task will eventually register the
     * geofence.
     *
     * The geofence will not be added if it has an expiration date which has already passed.
     */
    fun addOrUpdateGeofence(geofence: GeofenceMessage) {
        val expirationDuration = geofence.expirationDate?.time?.let { it - TimeUtils.nowMillis() }
        if (expirationDuration ?: 0 < 0) {
            Plog.warn(T_DATALYTICS, T_GEOFENCE, "The expiration time for a received geofence request has " +
                    "already been reached, the geofence will not be registered")
            return
        }

        geofences.put(geofence.id, geofence, expirationDuration?.let { millis(it) })
        taskScheduler.schedulePeriodicTask(GeofencePeriodicRegisterTask.Options())
        attemptAddingGeofence(geofence).subscribeBy(
                onSuccess = { successful: Boolean ->
                    if (successful) {
                        Plog.info(T_DATALYTICS, T_GEOFENCE, "Geofence successfully registered",
                            "Lat/Long" to "${geofence.lat}/${geofence.long}",
                            "Radius" to geofence.radius,
                            "Id" to geofence.id,
                            "Trigger" to when (geofence.trigger) {
                                GeofenceMessage.GEOFENCE_TRIGGER_ENTER -> "enter"
                                GeofenceMessage.GEOFENCE_TRIGGER_EXIT -> "exit"
                                else -> "unknown (${geofence.trigger})"
                            },
                            "Trigger on Init" to geofence.triggerOnInit,
                            "Dwell Time" to geofence.dwellTime,
                            "Limit" to geofence.limit
                        )
                    }
                },
                onError = { ex: Throwable ->
                    if (ex is GeofenceException) Plog.warn(T_DATALYTICS, T_GEOFENCE, ex, "Geofence" to ex.geofenceMessage) else Plog.error(T_DATALYTICS, T_GEOFENCE, ex)
                }
        )


    }

    fun removeGeofence(geofence: RemoveGeofenceMessage) = removeGeofence(geofence.id)

    /**
     * Removes a geofence and unregisters it with the [GeofencingClient].
     *
     * Once this method is called the geofence data will be immediately removed from storage and the
     * geofence will essentially be inactive from the user's perspective. However, unregistering the
     * geofence with [GeofencingClient] may be unsuccessful the first time in which case the actual
     * geofence registration will remain active until it is either removed by [GeofencingClient]
     * itself or removed when the geofence triggers and we identify that it no longer exists.
     */
    fun removeGeofence(geofenceId: String) {
        geofences.remove(geofenceId)
        if (geofences.isEmpty()) {
            taskScheduler.cancelTask(GeofencePeriodicRegisterTask.Options())
        }
        attemptRemovingGeofence(geofenceId).subscribeBy(onError = { ex ->
            if (ex is GeofenceException) Plog.warn(T_DATALYTICS, T_GEOFENCE, ex) else Plog.error(T_DATALYTICS, T_GEOFENCE, ex)
        })
    }

    /**
     * Attempts to re-register all geofences which have been stored in the [geofences] collection.
     */
    fun ensureGeofencesAreRegistered(): Completable {
        if (geofences.isEmpty()) {
            return Completable.complete()
        }

        Plog.debug(T_DATALYTICS, T_GEOFENCE, "Re-registering ${geofences.size} geofences")
        val noError = Throwable()
        return Observable.fromIterable(geofences.values)
                .flatMapSingle { geofence -> attemptAddingGeofence(geofence).map { noError }.onErrorReturn { it } }
                .filter { it != noError }
                .toList()
                .doOnSuccess { errors ->
                    when {
                        errors.size > 1 -> Plog.warn(T_DATALYTICS, T_GEOFENCE, "Failed to reregister ${errors.size} geofences", errors[0])
                        errors.size == 1 -> Plog.warn(T_DATALYTICS, T_GEOFENCE, "Failed to reregister geofence", errors[0])
                        else -> Plog.trace(T_DATALYTICS, T_GEOFENCE, "Re-registering ${geofences.size} geofences successful")
                    }
                }
                .ignoreElement()
    }

    /**
     * @return A [Single] with type [Boolean]. If registering the geofence was not attempted due to lack
     * of permissions or because of the expiration date the [Single] will emit `false`. If registering
     * the geofence was successful then the [Single] will emit `true`. If registering the geofence
     * was attempted but failed the the [Single] will fail with an error.
     */
    @SuppressLint("MissingPermission")
    private fun attemptAddingGeofence(geofence: GeofenceMessage): Single<Boolean> {
        if (!hasPermission(context, ACCESS_FINE_LOCATION)) {
            Plog.warn(T_DATALYTICS, T_GEOFENCE, "Unable to add geofence due to missing location permissions")
            return Single.just(false)
        }

        try {
            val expirationDuration = geofence.expirationDate?.time?.let { it - TimeUtils.nowMillis() }
            if (expirationDuration ?: 0 < 0) {
                Plog.warn(T_DATALYTICS, T_GEOFENCE, "Attempted to register expired geofence, geofence will be removed")
                removeGeofence(geofence.id)
                return Single.just(false)
            }

            val transitionType = when {
                geofence.trigger == GeofenceMessage.GEOFENCE_TRIGGER_ENTER && (geofence.dwellTime == null || geofence.dwellTime.toMillis() <= 0)
                -> GEOFENCE_TRANSITION_ENTER
                geofence.trigger == GeofenceMessage.GEOFENCE_TRIGGER_ENTER && geofence.dwellTime != null
                -> GEOFENCE_TRANSITION_DWELL
                geofence.trigger == GeofenceMessage.GEOFENCE_TRIGGER_EXIT
                -> {
                    if (geofence.dwellTime != null && geofence.dwellTime.toMillis() > 0) {
                        Plog.warn(T_DATALYTICS, T_GEOFENCE, "Dwell times are not supported for geofences with `EXIT` triggers", "Geofence" to geofence)
                    }
                    GEOFENCE_TRANSITION_EXIT
                }
                else -> {
                    Plog.warn(T_DATALYTICS, T_GEOFENCE, "Invalid trigger value received for geofence: ${geofence.trigger}, using 'enter' trigger instead")
                    GEOFENCE_TRANSITION_ENTER
                }
            }

            val fence = Geofence.Builder().apply {
                setRequestId(geofence.id)
                setCircularRegion(geofence.lat, geofence.long, geofence.radius)
                setExpirationDuration(expirationDuration ?: NEVER_EXPIRE)
                setTransitionTypes(transitionType)

                if (geofence.responsiveness != null && geofence.responsiveness.toMillis() >= 0) {
                    setNotificationResponsiveness(geofence.responsiveness.toMillis().toInt())
                }

                if (transitionType == GEOFENCE_TRANSITION_DWELL) {
                    setLoiteringDelay(geofence.dwellTime?.toMillis()?.toInt() ?: 0)
                }
            }.build()


            val triggerOnInit = geofence.triggerOnInit ?: (geofence.trigger == GeofenceMessage.GEOFENCE_TRIGGER_ENTER)
            val request = GeofencingRequest.Builder().apply {
                if (triggerOnInit) {
                    setInitialTrigger(when (transitionType) {
                        GEOFENCE_TRANSITION_ENTER -> INITIAL_TRIGGER_ENTER
                        GEOFENCE_TRANSITION_EXIT -> INITIAL_TRIGGER_EXIT
                        GEOFENCE_TRANSITION_DWELL -> INITIAL_TRIGGER_DWELL
                        else -> GEOFENCE_TRANSITION_ENTER
                    })
                } else {
                    setInitialTrigger(0)
                }
                addGeofence(fence)
            }.build()


            /* Note: Geofence limits the application to only 5 active pending intents, so instead of creating
             * a new pending intent request code for each geofence we'll use the same pending intent. This
             * shouldn't cause a problem.
             */
            val intent = Intent(context, GeofenceService::class.java)
            val pendingIntent = PendingIntent.getService(context, 0xfedce, intent, PendingIntent.FLAG_UPDATE_CURRENT)

            return Single.create<Boolean> { emitter ->
                geofencingClient.addGeofences(request, pendingIntent)?.run {
                    addOnSuccessListener {
                        emitter.onSuccess(true)
                    }
                    addOnFailureListener { ex ->
                        emitter.tryOnError(GeofenceException("Adding or updating geofence failed", ex, geofence))
                    }
                }
            }.subscribeOn(cpuThread()).observeOn(cpuThread())

        } catch (ex: Exception) {
            return Single.fromCallable { throw ex }
        }
    }

    private fun attemptRemovingGeofence(geofenceId: String): Completable {
        return try {
            Completable.create { emitter ->
                geofencingClient.removeGeofences(listOf(geofenceId))?.run {
                    addOnSuccessListener {
                        Plog.info(T_DATALYTICS, T_GEOFENCE, "Geofence has been unregistered","Id" to geofenceId)
                        emitter.onComplete()
                    }
                    addOnFailureListener { ex ->
                        emitter.tryOnError(GeofenceException("Removing geofence failed", ex))
                    }
                }
            }.subscribeOn(cpuThread()).observeOn(cpuThread())
        } catch (ex: Exception) {
            Completable.error(GeofenceException("Error occurred while removing geofence", ex))
        }
    }

    /**
     * This method will be called once a geofence has been triggered.
     *
     * The message contents defined in the triggered geofence's [GeofenceMessage] will be given to the
     * [PostOffice] to be parsed and handled. If the contents contain a notification message for example,
     * then a notification will be shown to the user.
     *
     * The geofence's trigger count (stored in [geofenceTriggerCounts]) will also be incremented.
     *
     * Note, the geofence's message will not be given to the [PostOffice] if the geofence's trigger
     * limit has already been reached. If however the trigger limit is reached after incrementing the
     * count, the message will be given to the [PostOffice] and the message will be removed.
     *
     * If no geofence data is found for the given geofence id in the [geofences] collection, then no
     * actions will be performed other than attempting to unregister the geofence with the [GeofencingClient]
     * to prevent it from triggering again in the future.
     */
    fun onGeofenceTriggered(geofenceId: String) {
        val geofence = geofences[geofenceId]

        if (geofence == null) {
            // This probably means that unregistering the geofence was not successful before, we'll try again
            Plog.error(T_DATALYTICS, T_GEOFENCE, GeofenceException("Geofence triggered but geofence data is missing"), "Id" to geofenceId)
            removeGeofence(geofenceId)
            return
        }

        val now = TimeUtils.now()
        val previousTriggerTime = geofenceTriggerTimes[geofenceId]
        if (geofence.rateLimit != null && previousTriggerTime != null &&
                now - previousTriggerTime < geofence.rateLimit) {
            Plog.debug(T_DATALYTICS, T_GEOFENCE, "Geofence triggered but will be prevented due to rate limit.", "Id" to geofenceId)
            return
        }

        val triggerCount = geofenceTriggerCounts[geofenceId] ?: 0
        val triggerLimit = geofence.limit?.takeIf { it >= 0 }
        if (triggerLimit != null && triggerCount >= triggerLimit - 1) {
            removeGeofence(geofenceId)
            if (triggerCount >= triggerLimit) {
                Plog.debug(T_DATALYTICS, T_GEOFENCE, "Geofence triggered but it's trigger limit has been reached.", "Id" to geofenceId)
                return
            }
        }

        geofenceTriggerCounts[geofenceId] = triggerCount + 1
        geofenceTriggerTimes[geofenceId] = now

        Plog.info(T_DATALYTICS, T_GEOFENCE, "Geofence has been triggered",
            "Id" to geofenceId,
            "Count" to triggerCount + 1
        )

        try {
            postOffice.handleLocalParcel(geofence.message, geofence.messageId)
        } catch (ex: ParcelParseException) {
            Plog.warn(T_DATALYTICS, T_GEOFENCE, "Could not parse geofence content", ex)
        }
    }
}

class GeofenceException(message: String, cause: Throwable? = null, val geofenceMessage: GeofenceMessage? = null) : Exception(message, cause)