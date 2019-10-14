package co.pushe.plus.analytics

import co.pushe.plus.analytics.goal.GoalFactory
import co.pushe.plus.internal.PusheMoshi

fun extendMoshi(moshi: PusheMoshi) {
    moshi.enhance {
        it.add(GoalFactory.build())
    }
}

