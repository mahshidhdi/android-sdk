package co.pushe.plus.utils

import co.pushe.plus.internal.PusheSchedulers

private inline fun assertionsEnabled() = BuildConfig.DEBUG

/**
 * Asserts than the current execution flow is happening in the CPU Thread
 */
fun assertCpuThread() {
    if (!assertionsEnabled()) {
        return
    }
    val thread = Thread.currentThread()
    val throwable = AssertionError("Expected code to be run in cpu thread but it wasn't")

    PusheSchedulers.cpuExecutor.execute {
        if (Thread.currentThread() != thread) {
            throw throwable
        }
    }
}

fun assertNotCpuThread() {
    if (!assertionsEnabled()) {
        return
    }
    val thread = Thread.currentThread()
    val throwable = AssertionError("Expected code to be run in cpu thread but it wasn't")

    PusheSchedulers.cpuExecutor.execute {
        if (Thread.currentThread() == thread) {
            throw throwable
        }
    }
}