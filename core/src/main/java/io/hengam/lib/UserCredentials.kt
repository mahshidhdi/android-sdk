package io.hengam.lib

import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.dagger.CoreScope
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.internal.ioThread
import io.hengam.lib.messages.upstream.UserIdUpdateMessage
import io.hengam.lib.messaging.SendPriority
import io.hengam.lib.utils.HengamStorage
import io.hengam.lib.utils.rx.PublishRelay
import io.hengam.lib.utils.rx.keepDoing

import java.util.concurrent.TimeUnit
import javax.inject.Inject

@CoreScope
class UserCredentials @Inject constructor(
        hengamStorage: HengamStorage
) {
    private var storedCustomId by hengamStorage.storedString("custom_id", "")
    private var storedEmail by hengamStorage.storedString("user_email", "")
    private var storedPhoneNumber by hengamStorage.storedString("user_phone", "")

    private val updateThrottler = PublishRelay.create<Boolean>()

    init {
        updateThrottler
                .throttleLatest(10, TimeUnit.SECONDS, ioThread(), true)
                .observeOn(cpuThread())
                .keepDoing {
                    // Can't specify postOffice as dagger dependency due to cycle
                    val core = HengamInternals.getComponent(CoreComponent::class.java)
                    val updateMessage = UserIdUpdateMessage(
                            customId = customId,
                            email = email,
                            phoneNumber = phoneNumber
                    )
                    core?.postOffice()?.sendMessage(updateMessage, SendPriority.SOON)
                }
    }

    /**
     * Custom User ID set by the Hengam hosting app
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