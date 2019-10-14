package co.pushe.plus

import androidx.work.BackoffPolicy
import co.pushe.plus.internal.PusheMoshi
import co.pushe.plus.messages.common.ApplicationDetail
import co.pushe.plus.messages.common.ApplicationDetailJsonAdapter
import co.pushe.plus.messaging.*
import co.pushe.plus.utils.NetworkType
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson

fun extendMoshi(moshi: PusheMoshi) {
    moshi.enhance {
        it.add { type, _, moshi ->
            val adapter = when (type) {
                UpstreamMessage::class.java -> UpstreamMessage.Adapter(moshi)
                UpstreamParcel::class.java -> UpstreamParcel.Adapter(moshi)
                DownstreamParcel::class.java -> DownstreamParcel.Adapter(moshi)

                // Should be null-safe, ApplicationDetail may be null when used in messages
                ApplicationDetail::class.java -> ApplicationDetailJsonAdapter(moshi).nullSafe()

                else -> null
            }
            adapter
        }

        it.add(ResponseMessage.Status.Adapter())
        it.add(UpstreamMessageState.Adapter())
        it.add(NetworkType.Adapter())
        it.add(BackoffPolicyAdapter())

    }
}

class BackoffPolicyAdapter {
    @ToJson
    fun toJson(backoffPolicy: BackoffPolicy): String = when (backoffPolicy) {
        BackoffPolicy.EXPONENTIAL -> "exponential"
        BackoffPolicy.LINEAR -> "linear"
        else -> ""
    }

    @FromJson
    fun fromJson(json: String) = when (json) {
        "exponential" -> BackoffPolicy.EXPONENTIAL
        "linear" -> BackoffPolicy.LINEAR
        else -> null
    }
}
