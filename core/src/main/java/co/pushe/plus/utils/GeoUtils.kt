package co.pushe.plus.utils


import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import co.pushe.plus.LogTag.T_LOCATION
import co.pushe.plus.dagger.CoreScope
import co.pushe.plus.internal.cpuThread
import co.pushe.plus.utils.PermissionChecker.ACCESS_COARSE_LOCATION
import co.pushe.plus.utils.PermissionChecker.ACCESS_FINE_LOCATION
import co.pushe.plus.utils.PermissionChecker.hasPermission
import co.pushe.plus.utils.log.Plog
import co.pushe.plus.utils.rx.PublishRelay
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import io.reactivex.Maybe
import io.reactivex.Single
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Provides methods for obtaining and requesting the device location
 *
 * All methods in this class operates on the [cpuThread]
 */
@CoreScope
class GeoUtils @Inject constructor(
        private val context: Context,
        private val fusedLocationProviderClient: FusedLocationProviderClient
) : LocationCallback() {

    private var locationResponseRelay = PublishRelay.create<Location>()

    /**
     * Retrieves and returns the device location using [FusedLocationProviderClient] or returns
     * nothing if the location is not available
     *
     * If the [ACCESS_COARSE_LOCATION] and [ACCESS_FINE_LOCATION] permissions are both unavailable
     * then will return nothing.
     *
     * If a "good" last-known-location then it will be returned and a new location request will not
     * be made. Otherwise, the function will request a new location using the [FusedLocationProviderClient]
     * and wait for the response.
     *
     * If a location is obtained within the time specified by the `timeout` parameter, then it will
     * be returned. If not, nothing will be returned.
     *
     * This method operates on the [cpuThread]
     *
     * @param timeout The amount of time to wait for a location to be obtained. If a location is not
     * available by then the location request will be cancelled and nothing will be returned. If
     * this parameter is not provided the timeout will default to 10 seconds.
     *
     * @return A [Maybe] object which will resolve with a location or will complete without a value
     * if the location could not be obtained.
     */
    @SuppressLint("MissingPermission")
    fun getLocation(timeout: Time = seconds(10)): Maybe<Location> {
        if (!hasLocationPermissions()) {
            return Maybe.empty()
        }

        return isLastLocationAvailable()
                .subscribeOn(cpuThread())
                .observeOn(cpuThread())
                .flatMapMaybe { if (it) getLastKnownLocation() else Maybe.empty() }
                .switchIfEmpty(Maybe.defer {
                    requestLocationUpdates(timeout)
                    locationResponseRelay.firstElement()
                })
                .timeout(timeout.toMillis(), TimeUnit.MILLISECONDS, cpuThread(), Maybe.empty())
                .doOnError { Plog.error(T_LOCATION, it) }
                .onErrorComplete()
    }

    /**
     * Retrieves the last known location from the [FusedLocationProviderClient]
     *
     * If the [ACCESS_COARSE_LOCATION] and [ACCESS_FINE_LOCATION] permissions are both unavailable
     * then will return nothing.
     *
     * If the [FusedLocationProviderClient] does not provide a last known location then will
     * complete without returning a value.
     *
     * @return A [Maybe] that will resolve with the last known location or will complete without a
     * value if it does not exist
     */
    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(): Maybe<Location> {
        if (!hasLocationPermissions()) {
            return Maybe.empty()
        }

        return Maybe.create { emitter ->
            fusedLocationProviderClient.lastLocation.addOnSuccessListener { location: Location? ->
                Plog.trace(T_LOCATION, "Last known location retrieved",
                    "Location" to if (location == null) null else "${location.latitude} ${location.longitude}",
                    "Time" to location?.time
                )

                if (location == null) emitter.onComplete()
                else emitter.onSuccess(location)
            }.addOnFailureListener { exception -> emitter.tryOnError(exception) }
        }
    }

    @SuppressLint("MissingPermission")
    fun isLastLocationAvailable(): Single<Boolean> {
        if (!hasLocationPermissions()) {
            return Single.just(false)
        }

        return Single.create { emitter ->
            fusedLocationProviderClient.locationAvailability.addOnSuccessListener { locationAvailability ->
                emitter.onSuccess(locationAvailability.isLocationAvailable)
            }.addOnFailureListener { exception -> emitter.tryOnError(exception) }
        }
    }

    private fun hasLocationPermissions(): Boolean {
        return hasPermission(context, ACCESS_COARSE_LOCATION) || hasPermission(context, ACCESS_FINE_LOCATION)
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates(timeout: Time) {
        Plog.trace(T_LOCATION, "Requesting location update")
        val locationRequest = LocationRequest.create().apply {
            interval = LOCATION_UPDATE_INTERVAL
            fastestInterval = LOCATION_UPDATE_FASTEST_INTERVAL
            priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
            numUpdates = 1
        }
        locationRequest.setExpirationDuration(timeout.toMillis())
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, this, Looper.getMainLooper())   // TODO this shouldn't be main looper
    }

    override fun onLocationResult(locationResult: LocationResult?) {
        cpuThread {
            locationResult?.let {
                // it.lastLocation could be null even though kotlin doesn't recognize it
                val nullableLocation: Location? = it.lastLocation
                nullableLocation?.let { location ->
                    Plog.trace(T_LOCATION, "New location received ${System.currentTimeMillis()}",
                        "Lat" to location.latitude,
                        "Long" to location.longitude
                    )

                    locationResponseRelay.accept(location)
                }
            }
        }
    }

    companion object {
        private const val LOCATION_UPDATE_INTERVAL = 10000L
        private const val LOCATION_UPDATE_FASTEST_INTERVAL = 2000L
    }
}
