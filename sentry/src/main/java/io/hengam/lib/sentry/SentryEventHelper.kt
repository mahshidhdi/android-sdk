package io.hengam.lib.sentry

import io.hengam.lib.dagger.CoreComponent
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.utils.environment
import io.sentry.event.EventBuilder
import io.sentry.event.helper.EventBuilderHelper
import io.sentry.event.interfaces.UserInterface

class SentryEventHelper(
        private val applicationPackageName: String
) : EventBuilderHelper {

    override fun helpBuildingEvent(eventBuilder: EventBuilder?) {
        eventBuilder?.withRelease(Settings.HENGAM_VERSION)
        eventBuilder?.withEnvironment(environment().toString())
        eventBuilder?.withTag("app", applicationPackageName)


        // User details
        val core = HengamInternals.getComponent(CoreComponent::class.java)
        if (core != null) {
            val deviceIDHelper = core.deviceIdHelper()
            val userCredentials = core.userCredentials()
            val fcmTokenStore = core.fcmTokenStore()

            eventBuilder?.withSentryInterface(UserInterface(
                    deviceIDHelper.androidId,
                    null,
                    null,
                    userCredentials.email,
                    mapOf(
                            "Advertisement Id" to deviceIDHelper.advertisementId,
                            "Android Id" to deviceIDHelper.androidId,
                            "Custom Id" to userCredentials.customId,
                            "Instance Id" to fcmTokenStore.instanceId
                    ))
            )
        }
    }
}