package co.pushe.plus.messaging.fcm

import android.content.Context
import android.util.Log
import co.pushe.plus.AppManifest
import co.pushe.plus.LogTag.T_FCM
import co.pushe.plus.LogTag.T_INIT
import co.pushe.plus.dagger.CoreScope
import co.pushe.plus.utils.log.Plog
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessaging
import java.lang.reflect.InvocationTargetException
import javax.inject.Inject

/**
 * Must be singleton since it holds global state
 */
@CoreScope
class FcmServiceManager @Inject constructor(
        private val context: Context,
        private val appManifest: AppManifest
){
    private var restartedByDebugCommand: Boolean = false

    var isFirebaseAvailable: Boolean = false
    var firebaseApp: FirebaseApp? = null
        private set

    private var isDefaultFirebaseApp: Boolean? = null

    private var firebaseMessagingInstance: FirebaseMessaging? = null
    val firebaseMessaging: FirebaseMessaging
        get() {
            firebaseMessagingInstance?.let { return it }
            val firebaseApp = this.firebaseApp ?: throw FirebaseNotInitializedException("Cannot initialize Firebase Messaging with null Firebase App")
            if (isDefaultFirebaseApp == true && !restartedByDebugCommand) {
                val messaging = FirebaseMessaging.getInstance()
                firebaseMessagingInstance = messaging
                return messaging
            }

            try {
                val firebaseInstanceId = FirebaseInstanceId.getInstance(firebaseApp)
                val constructor = FirebaseMessaging::class.java.getDeclaredConstructor(FirebaseInstanceId::class.java)
                constructor.isAccessible = true
                val messaging = constructor.newInstance(firebaseInstanceId)
                firebaseMessagingInstance = messaging
                return messaging
            } catch (e: NoSuchMethodException) {
                throw FirebaseNotInitializedException("Getting FirebaseMessaging failed", e)
            } catch (e: IllegalAccessException) {
                throw FirebaseNotInitializedException("Getting FirebaseMessaging failed", e)
            } catch (e: InstantiationException) {
                throw FirebaseNotInitializedException("Getting FirebaseMessaging failed", e)
            } catch (e: InvocationTargetException) {
                throw FirebaseNotInitializedException("Getting FirebaseMessaging failed", e)
            }
        }

    val firebaseInstanceId: FirebaseInstanceId?
        get() = firebaseApp?.let { FirebaseInstanceId.getInstance(it) }

    fun initializeFirebase() {
        if (appManifest.fcmSenderId == null) {
            Log.w("Pushe", "Firebase cannot initialize due to missing Sender Id")
            Plog.warn(T_FCM, T_INIT, "Firebase cannot initialize due to missing Sender id," +
                    " FCM services will be disabled")
            return
        }


        val builder = FirebaseOptions.Builder()
                .setGcmSenderId(appManifest.fcmSenderId)
                .setApplicationId(appManifest.appId)
        try {
            firebaseApp = try {
                FirebaseApp.getInstance()
                Plog.debug(T_FCM, "Default firebase app already exists, using non-default app")
                isDefaultFirebaseApp = false
                FirebaseApp.initializeApp(context, builder.build(), "Pushe")
            } catch (ex: IllegalStateException) {
                isDefaultFirebaseApp = true
                FirebaseApp.initializeApp(context, builder.build())
            }

            if (firebaseApp == null) {
                Plog.warn(T_FCM, T_INIT, "Initializing FCM unsuccessful")
            } else {
                Plog.info(T_FCM, T_INIT, "FCM is ready, you may ignore the following Firebase errors")
                isFirebaseAvailable = true
            }
        } catch (ex: Exception) {
            Plog.error(T_FCM, T_INIT, FirebaseNotInitializedException("Initializing Firebase failed", ex))
        }
    }

    fun clearFirebase() {
        restartedByDebugCommand = true
        firebaseApp?.delete()
        firebaseApp = null
        firebaseMessagingInstance = null
        isFirebaseAvailable = false
        isDefaultFirebaseApp = null
    }
}

class FirebaseNotInitializedException(message: String, cause: Throwable? = null) : Exception(message, cause)