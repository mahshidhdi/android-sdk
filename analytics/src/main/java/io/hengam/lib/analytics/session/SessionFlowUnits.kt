package io.hengam.lib.analytics.session

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

abstract class SessionFragmentParent {
    abstract val fragmentFlows: MutableMap<String, MutableList<SessionFragment>>
}

/**
 * Since there can be multiple fragment layouts inside an activity (or fragment), the fragmentFlows
 * are stored using a map with keys being fragment IDs and values being sessionFragment lists.
 *
 * Each sessionFragment itself has such a map named fragmentFlows to support nested fragments in the session
 */

@JsonClass(generateAdapter = true)
class SessionActivity(
    @Json(name = "name") val name: String,
    @Json(name = "start_time") var startTime: Long,
    @Json(name = "original_start_time") var originalStartTime: Long,
    @Json(name = "duration") var duration: Long,
    @Json(name = "fragment_flows") override var fragmentFlows: MutableMap<String, MutableList<SessionFragment>> = mutableMapOf(),
    @Json(name = "src_notif") var sourceNotifMessageId: String? = null
): SessionFragmentParent() {
    override fun toString(): String {
        return "SessionActivity(name='$name', originalStartTime='$originalStartTime', duration=$duration, fragmentFlows=$fragmentFlows)"
    }
}

@JsonClass(generateAdapter = true)
class SessionFragment(
    @Json(name = "name") val name: String,
    @Json(name = "start_time") var startTime: Long,
    @Json(name = "original_start_time") var originalStartTime: Long,
    @Json(name = "duration") var duration: Long,
    @Json(name = "fragment_flows") override var fragmentFlows: MutableMap<String, MutableList<SessionFragment>> = mutableMapOf()
): SessionFragmentParent() {
    override fun toString(): String {
        return "SessionFragment(name='$name', originalStartTime='$originalStartTime', duration=$duration, fragmentFlows=$fragmentFlows)"
    }
}