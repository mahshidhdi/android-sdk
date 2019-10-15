package io.hengam.lib.analytics.utils

import android.os.Build
import android.view.View
import io.hengam.lib.utils.log.Plog
import java.lang.reflect.Field

/**
 * Returns the current View.OnClickListener for the given View
 * @param view the View whose click listener to retrieve
 * @return the View.OnClickListener attached to the view; null if it could not be retrieved
 */
fun getOnClickListener(view: View): View.OnClickListener? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
        getOnClickListenerV14(view)
    } else {
        getOnClickListenerV(view)
    }
}

/**
 * Returns the current View.OnClickListener for the given View
 * Used for APIs lower than ICS (API 14)
 */
private fun getOnClickListenerV(view: View): View.OnClickListener? {
    var retrievedListener: View.OnClickListener? = null
    val viewStr = "android.view.View"
    val field: Field

    try {
        field = Class.forName(viewStr).getDeclaredField("mOnClickListener")
        retrievedListener = field.get(view) as View.OnClickListener
    } catch (ex: NoSuchFieldException) {
        Plog.error("Reflection", "No Such Field.")
    } catch (ex: IllegalAccessException) {
        Plog.error("Reflection", "Illegal Access.")
    } catch (ex: ClassNotFoundException) {
        Plog.error("Reflection", "Class Not Found.")
    }

    return retrievedListener
}

/**
 * Returns the current View.OnClickListener for the given View
 * Used for new ListenerInfo class structure used beginning with API 14 (ICS)
 */
private fun getOnClickListenerV14(view: View): View.OnClickListener? {
    var retrievedListener: View.OnClickListener? = null
    val viewStr = "android.view.View"
    val lInfoStr = "android.view.View\$ListenerInfo"

    try {
        val listenerField = Class.forName(viewStr).getDeclaredField("mListenerInfo")
        var listenerInfo: Any? = null

        if (listenerField != null) {
            listenerField.isAccessible = true
            listenerInfo = listenerField.get(view)
        }

        val clickListenerField = Class.forName(lInfoStr).getDeclaredField("mOnClickListener")

        if (clickListenerField != null && listenerInfo != null) {
            retrievedListener = clickListenerField.get(listenerInfo) as View.OnClickListener
        }
    } catch (ex: NoSuchFieldException) {
        Plog.error("Reflection", "No Such Field.")
    } catch (ex: IllegalAccessException) {
        Plog.error("Reflection", "Illegal Access.")
    } catch (ex: ClassNotFoundException) {
        Plog.error("Reflection", "Class Not Found.")
    }

    return retrievedListener
}
