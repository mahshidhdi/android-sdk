package io.hengam.lib.analytics

import io.hengam.lib.analytics.goal.GoalFactory
import io.hengam.lib.internal.HengamMoshi

fun extendMoshi(moshi: HengamMoshi) {
    moshi.enhance {
        it.add(GoalFactory.build())
    }
}

