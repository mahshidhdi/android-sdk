package co.pushe.plus

import co.pushe.plus.dagger.CoreComponent
import co.pushe.plus.dagger.CoreScope
import co.pushe.plus.internal.PusheInternals
import co.pushe.plus.internal.cpuThread
import co.pushe.plus.internal.ioThread
import co.pushe.plus.messages.upstream.UserIdUpdateMessage
import co.pushe.plus.messaging.SendPriority
import co.pushe.plus.utils.PusheStorage
import co.pushe.plus.utils.rx.PublishRelay
import co.pushe.plus.utils.rx.keepDoing

import java.util.concurrent.TimeUnit
import javax.inject.Inject

@CoreScope
class UserCredentials @Inject constructor(
        pusheStorage: PusheStorage
) {
    private var storedCustomId by pusheStorage.storedString("custom_id", "")
    private var storedEmail by pusheStorage.storedString("user_email", "")
    private var storedPhoneNumber by pusheStorage.storedString("user_phone", "")

    private val updateThrottler = PublishRelay.create<Boolean>()

    init {
        updateThrottler
                .throttleLatest(10, TimeUnit.SECONDS, ioThread(), true)
                .observeOn(cpuThread())
                .keepDoing {
                    // Can't specify postOffice as dagger dependency due to cycle
                    val core = PusheInternals.getComponent(CoreComponent::class.java)
                    val updateMessage = UserIdUpdateMessage(
                            customId = customId,
                            email = email,
                            phoneNumber = phoneNumber
                    )
                    core?.postOffice()?.sendMessage(updateMessage, SendPriority.SOON)
                }
    }

    /**
     * Custom User ID set by the Pushe hosting app
     * The Custom ID will be sent with all upstream parcels
     */
    var customId: String
        get() = storedCustomId
        set(value) {
            storedCustomId = value
            updateThrottler.accept(true)
        }

    var email: String
        get() = storedEmail
        set(value) {
            storedEmail = value
            updateThrottler.accept(true)
        }

    var phoneNumber: String
        get() = storedPhoneNumber
        set(value) {
            storedPhoneNumber = value
            updateThrottler.accept(true)
        }
}