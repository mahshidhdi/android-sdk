package io.hengam.lib.datalytics

import io.hengam.lib.datalytics.messages.upstream.*
import io.hengam.lib.internal.HengamMoshi

/**
 * Function is designed to handle moshi adapters added to HengamMoshi.
 * @param moshi is the main class in sdk to handle moshi.
 * @see HengamMoshi
 * Every Json adapter generated by moshi should be added here (If not a message)
 */
fun extendMoshi(moshi: HengamMoshi) {
    moshi.enhance {
        it.add(CellArrayFactory.build())
        it.add { type, _, moshi ->
            val adapter = when (type) {
                // TODO: Do we need to add the message classes here?
                ApplicationDetailsMessage::class.java -> ApplicationDetailsMessageJsonAdapter(moshi)
                WifiInfoMessage::class.java -> WifiInfoMessageJsonAdapter(moshi)
                ConstantDataMessage::class.java -> ConstantDataMessageJsonAdapter(moshi)
                VariableDataMessage::class.java -> VariableDataMessageJsonAdapter(moshi)
                FloatingDataMessage::class.java -> FloatingDataMessageJsonAdapter(moshi)
                AppIsHiddenMessage::class.java -> AppIsHiddenMessageJsonAdapter(moshi)
                SSP::class.java -> SSPJsonAdapter(moshi)
                else -> null
            }
            adapter
        }

    }
}