package io.hengam.lib.utils

import android.content.Context
import android.graphics.Point
import android.os.Build
import io.hengam.lib.dagger.CoreScope
import javax.inject.Inject

@CoreScope
class DeviceInfoHelper @Inject constructor(private val context: Context) {
    /**
     * @return The device model name (error.g. Galaxy S2)
     */
    fun getDeviceModel(): String {
        return Build.MODEL
    }

    /**
     * @return The device OS version (error.g. 4.4.2)
     */
    fun getOSVersion(): String {
        return Build.VERSION.RELEASE
    }

    /**
     * @return The device screen size
     */
    fun getScreenSize(): Point {
        val point = Point()
        val display = context.resources.displayMetrics
        point.set(display.widthPixels, display.heightPixels)
        return point
    }

    /**
     * @return The device brand name (error.g. Samsung)
     */
    fun getDeviceBrand(): String {
        return Build.MANUFACTURER
    }
}