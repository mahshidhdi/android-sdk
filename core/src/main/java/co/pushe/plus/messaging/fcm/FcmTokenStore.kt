package co.pushe.plus.messaging.fcm

import co.pushe.plus.LogTag.T_FCM
import co.pushe.plus.dagger.CoreScope
import co.pushe.plus.internal.cpuThread
import co.pushe.plus.utils.PusheStorage
import co.pushe.plus.utils.assertCpuThread
import co.pushe.plus.utils.log.LogLevel
import co.pushe.plus.utils.log.Plog
import co.pushe.plus.utils.rx.BehaviorRelay
import com.squareup.moshi.Json
import io.reactivex.Observable
import io.reactivex.Single
import java.io.IOException
import javax.inject.Inject

@CoreScope
class FcmTokenStore @Inject constructor(
        private val fcmServiceManager: FcmServiceManager,
        pusheStorage: PusheStorage
) {
    var token by pusheStorage.storedString("fcm_token", "")
        private set
    var tokenState by pusheStorage.storedObject("fcm_token_state", TokenState.NO_TOKEN, TokenState::class.java)
        private set
    private val tokenStateRelay = BehaviorRelay.create<TokenState>()
    private var storedInstanceId by pusheStorage.storedString("fcm_instance_id", "")

    val instanceId: String
        get() {
            val instanceId = storedInstanceId
            if (instanceId.isNotBlank()) {
                return instanceId
            }
            val newInstanceId = fcmServiceManager.firebaseInstanceId?.id
            if (!newInstanceId.isNullOrBlank()) {
                storedInstanceId = newInstanceId
            }
            return storedInstanceId
        }

    fun updateToken(tokenState: TokenState, token: String? = null) {
        if (token != null) {
            this.token = token
        }
        this.tokenState = tokenState
        tokenStateRelay.accept(tokenState)
    }

    /**
     * Checks whether the current stored Fcm token is valid and has not been changed
     *
     * @return A [Single] objects which resolves with the new [TokenState]. If obtaining the token
     * state failed because of a `SERVICE_NOT_AVAILABLE` error from FCM the previous [TokenState]
     * will be returned. If it fails with any other error the [Single] will emit the error.
     */
    fun revalidateTokenState(): Single<TokenState> {
        assertCpuThread()

        if (!fcmServiceManager.isFirebaseAvailable) {
            return Single.just(TokenState.UNAVAILABLE)
        }

        fcmServiceManager.firebaseApp
                ?: return Single.just(TokenState.UNAVAILABLE)

        return Single.create<TokenState> { emitter ->
            fcmServiceManager.firebaseInstanceId?.instanceId?.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    if (task.exception is IOException && task.exception?.message == "SERVICE_NOT_AVAILABLE") {
                        emitter.onSuccess(tokenState)
                    } else {
                        emitter.tryOnError(FcmTokenException("Request for Fcm InstanceId and Token failed", task.exception))
                    }
                } else {
                    cpuThread {
                        val result = task.result
                        if (result == null){
                            emitter.tryOnError(FcmTokenException("null token received from FCM"))
                            return@cpuThread
                        }
                        when {
                            token.isBlank() -> {
                                Plog.info.message("FCM token obtained")
                                        .withTag(T_FCM)
                                        .withData("Token", result.token)
                                        .useLogCatLevel(LogLevel.DEBUG)
                                        .log()

                                updateToken(TokenState.GENERATED, result.token)
                                emitter.onSuccess(TokenState.GENERATED)
                            }
                            result.token != token -> {
                                Plog.warn(T_FCM, "The saved FCM token has been invalidated, using new token",
                                    "Old Token" to token,
                                    "New Token" to result.token
                                )
                                updateToken(TokenState.GENERATED, result.token)
                                emitter.onSuccess(TokenState.GENERATED)
                            }
                            else -> emitter.onSuccess(tokenState)
                        }
                    }
                }
            }
        }
    }

    fun observeTokenState(): Observable<TokenState> {
        if (!tokenStateRelay.hasValue()) {
            tokenStateRelay.accept(TokenState.NO_TOKEN)
        }
        return tokenStateRelay
    }

    fun refreshFirebaseToken() {
        assertCpuThread()

        fcmServiceManager.firebaseInstanceId?.instanceId?.addOnCompleteListener { result ->
            cpuThread {
                if (result.exception != null) {
                    Plog.error(T_FCM, FcmTokenException("Error receiving FCM token", result.exception))
                    return@cpuThread
                }

                val fcmToken = result.result?.token

                if (fcmToken == null) {
                    Plog.error(T_FCM, "Null token received from FCM")
                } else if (token != fcmToken) {
                    Plog.info(T_FCM, "New FCM token received",
                            "Token" to fcmToken,
                            "Old Token" to token
                    )
                    updateToken(TokenState.GENERATED, fcmToken)
                }
            }
        }
    }

    fun invalidateToken() {
        updateToken(TokenState.NO_TOKEN, "")
        refreshFirebaseToken()
    }
}


enum class TokenState {
    @Json(name="UNAVAILABLE") UNAVAILABLE, // When Firebase is unavailable (not initialized)
    @Json(name="NO_TOKEN") NO_TOKEN,
    @Json(name="GENERATED") GENERATED,
    @Json(name="SYNCING") SYNCING,
    @Json(name="SYNCED") SYNCED
}

class FcmTokenException(message: String, cause: Throwable? = null) : Exception(message, cause)