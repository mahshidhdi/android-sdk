package io.hengam.lib.analytics.session

import io.hengam.lib.analytics.dagger.AnalyticsScope
import io.hengam.lib.utils.IdGenerator
import io.hengam.lib.utils.HengamStorage
import javax.inject.Inject

@AnalyticsScope
class SessionIdProvider @Inject constructor(
    storage: HengamStorage
) {

    var sessionId by storage.storedString("user_session_id", IdGenerator.generateId(SESSION_ID_LENGTH))
        private set

    fun renewSessionId(){
        sessionId = IdGenerator.generateId(SESSION_ID_LENGTH)
    }

    companion object {
        const val SESSION_ID_LENGTH = 16
    }
}