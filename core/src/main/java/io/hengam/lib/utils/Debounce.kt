package io.hengam.lib.utils

import io.hengam.lib.internal.cpuThread
import io.hengam.lib.utils.rx.justDo
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit

private val debouncers: MutableMap<String, PublishSubject<Boolean>> = mutableMapOf()
private val debounceData: MutableMap<String, MutableList<Any?>> = mutableMapOf()

fun <T> debounce(key: String, value: T, time: Long, timeUnit: TimeUnit, func: (List<T>) -> Unit) {
    cpuThread {
        if (key !in debouncers) {
            val debouncer = PublishSubject.create<Boolean>()
            debouncers[key] = debouncer
            debounceData[key] = mutableListOf()
            debouncer
                    .debounce(time, timeUnit, cpuThread())
                    .justDo {
                        func((debounceData[key] ?: mutableListOf<T>()) as List<T>)
                        debouncers.remove(key)
                        debounceData.remove(key)
                        debouncer.onComplete()
                    }
        }
        debouncers[key]?.onNext(true)
        debounceData[key]?.add(value)
    }
}

fun <T> debounce(key: String, value: T, time: Time, func: (List<T>) -> Unit) {
    debounce(key, value, time.toMillis(), TimeUnit.MILLISECONDS, func)
}

fun debounce(key: String, time: Long, timeUnit: TimeUnit, func: () -> Unit) {
    cpuThread {
        if (key !in debouncers) {
            val debouncer = PublishSubject.create<Boolean>()
            debouncers[key] = debouncer
            debouncer
                    .debounce(time, timeUnit, cpuThread())
                    .justDo {
                        func()
                        debouncers.remove(key)
                        debouncer.onComplete()
                    }
        }
        debouncers[key]?.onNext(true)
    }
}

fun debounce(key: String, time: Time, func: () -> Unit) {
    debounce(key, time.toMillis(), TimeUnit.MILLISECONDS, func)
}
