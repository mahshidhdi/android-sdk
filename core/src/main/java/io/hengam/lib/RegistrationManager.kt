package io.hengam.lib

import android.content.Context
import io.hengam.lib.Constants.BROADCAST_TOPIC
import io.hengam.lib.LogTag.T_FCM
import io.hengam.lib.LogTag.T_REGISTER
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.internal.task.TaskScheduler
import io.hengam.lib.internal.task.taskDataOf
import io.hengam.lib.messages.upstream.RegistrationMessage
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.messaging.ResponseMessage
import io.hengam.lib.messaging.SendPriority
import io.hengam.lib.messaging.fcm.FcmTokenStore
import io.hengam.lib.messaging.fcm.TokenState
import io.hengam.lib.tasks.RegistrationTask
import io.hengam.lib.utils.ApplicationInfoHelper
import io.hengam.lib.utils.DeviceIDHelper
import io.hengam.lib.utils.DeviceInfoHelper
import io.hengam.lib.utils.HengamStorage
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.rx.justDo
import io.hengam.lib.utils.rx.keepDoing
import javax.inject.Inject

/**
 * Contains methods for checking if registrations is required, performing registration if needed
 * and handling registration responses from the server
 */
class RegistrationManager @Inject constructor(
        private val context: Context,
        private val postOffice: PostOffice,
        private val fcmTokenStore: FcmTokenStore,
        private val deviceInfo: DeviceInfoHelper,
        private val deviceId: DeviceIDHelper,
        private val hengamLifecycle: HengamLifecycle,
        private val topicManager: TopicManager,
        private val taskScheduler: TaskScheduler,
        private val applicationInfoHelper: ApplicationInfoHelper,
        hengamStorage: HengamStorage
) {

    /**
     * Determines whether the client is registered or not.
     *
     * A client is registered if it has ever successfully sent a a `RegistrationMessage` and
     * received a successful response.
     *
     * Note that the client may need to re-register even after a successful registration
     * (e.g., if the FcmToken may becomes invalid), but `isRegistered` will always return `true` after
     * the first registration has successfully been performed
     *
     * @return true if the client is registered and false if it is not
     */
    var isRegistered: Boolean by hengamStorage.storedBoolean("client_registered", false)


    /**
     * Check if registration is required and send registration message if it is.
     *
     * Also, listen for changes in the [TokenState] to re-register if token is invalidated or new
     * token is received
     */
    fun checkRegistration() {
        fcmTokenStore.revalidateTokenState()
                .justDo(T_FCM, T_REGISTER) {
                    Plog.debug(T_REGISTER, "Token state is $it")

                    if (it == TokenState.SYNCING) {
                        Plog.info(T_REGISTER, "Previous registration was not completed, performing registration")
                        taskScheduler.scheduleTask(RegistrationTask.Options(), taskDataOf(RegistrationTask.DATA_REGISTRATION_CAUSE to "init"))
                    }
                }

        fcmTokenStore.observeTokenState()
                .observeOn(cpuThread())
                .filter { it == TokenState.GENERATED }
                .keepDoing(T_FCM) {
                    Plog.info(T_REGISTER, "Registration is required, performing registration")
                    taskScheduler.scheduleTask(RegistrationTask.Options(), taskDataOf(RegistrationTask.DATA_REGISTRATION_CAUSE to "init"))

                    if (ApplicationInfoHelper(context).isAppHidden()) {
                        Plog.warn(T_REGISTER, "App is hidden, will not subscribe to broadcast topic")
                    } else {
                        topicManager.subscribe(BROADCAST_TOPIC)
                    }
                }

        if (isRegistered) {
            hengamLifecycle.registrationComplete()
        }
    }

    fun performRegistration(registrationCause: String) {
        Plog.debug(T_REGISTER, "Performing registration")

        val fcmToken = fcmTokenStore.token

        if (fcmToken.isBlank()) {
            Plog.warn(T_REGISTER, T_FCM, "The stored FCM token is blank")
            fcmTokenStore.invalidateToken()
            return
        }

        val registrationMessage = RegistrationMessage(
                deviceId = deviceId.hengamId,
                deviceBrand = deviceInfo.getDeviceBrand(),
                deviceModel = deviceInfo.getDeviceModel(),
                osVersion = deviceInfo.getOSVersion(),
                appVersion = applicationInfoHelper.getApplicationVersion() ?: "",
                appVersionCode = applicationInfoHelper.getApplicationVersionCode() ?: 0,
                fcmToken = fcmToken,
                hengamVersion = BuildConfig.VERSION_NAME,
                hengamVersionCode = BuildConfig.VERSION_CODE,
                registerCause = registrationCause,
                appSignature = applicationInfoHelper.getApplicationSignature(),
                installer = applicationInfoHelper.getInstallerPackageName(),
                firstInstallTime = applicationInfoHelper.getApplicationDetails()?.installationTime,
                lastUpdateTime = applicationInfoHelper.getApplicationDetails()?.lastUpdateTime,
                isFreshInstall = applicationInfoHelper.isFreshInstall()
        )

        postOffice.sendMessage(
                registrationMessage,
                sendPriority = SendPriority.IMMEDIATE,
                persistAcrossRuns = false,
                requiresRegistration = false
        )

        fcmTokenStore.updateToken(TokenState.SYNCING)
    }

    /**
     * Should be called with if a response of type `REGISTRATION` is received.
     *
     * If registration was successful, updates [TokenState] to [TokenState.SYNCED]
     */
    fun handleRegistrationResponseMessage(response: ResponseMessage) {
        when (response.status) {
            ResponseMessage.Status.SUCCESS -> onSuccessfulRegistration()
            ResponseMessage.Status.FAIL -> onFailedRegistration(response.error)
            else -> {
            }
        }
    }

    private fun onSuccessfulRegistration() {
        isRegistered = true
        hengamLifecycle.registrationComplete()

        when (fcmTokenStore.tokenState) {
            TokenState.SYNCING -> {
                Plog.info(T_REGISTER, "Registration successful")
                fcmTokenStore.updateToken(TokenState.SYNCED)
            }
            TokenState.NO_TOKEN ->
                Plog.warn(T_REGISTER, "Registration was successful but no FCM token exists. The token " +
                        "was probably invalidated")
            TokenState.GENERATED ->
                Plog.warn(T_REGISTER, "Registration was successful but new FCM token has been " +
                        "generated. Will need to register again")
            TokenState.SYNCED ->
                Plog.warn(T_REGISTER, "Registration was successful but registration has already " +
                        "been performed")
            TokenState.UNAVAILABLE -> Plog.warn(T_REGISTER, "Registration successful message received but " +
                    "FCM is unavailable.") // Shouldn't happen
        }
    }

    private fun onFailedRegistration(error: String) {
        Plog.warn(T_REGISTER, "Registration failed response received", "Error" to error)
    }
}