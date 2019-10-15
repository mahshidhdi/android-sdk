package io.hengam.lib.internal

import android.content.Context

abstract class HengamComponentInitializer {
    /**
     * Called before anything else on app startup
     *
     * Will be called on Main Thread
     */
    abstract fun preInitialize(context: Context)

    /**
     * Called after `preInitialize` has been called on all components
     *
     * Will be called on [cpuThread]
     */
    abstract fun postInitialize(context: Context)
}