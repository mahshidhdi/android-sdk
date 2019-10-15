package io.hengam.lib.notification.utils

import android.content.Context

object MaterialIconHelper {

    /**
     * Get an icon resource id by its material design icon name
     *
     * @param context A valid context for the application
     * @param name    The name of the material design icon. Valid name formats are: "shopping cart",
     * "shopping-cart" and "shopping_cart"
     * @return The resource id of the icon
     */
    fun getIconResourceByMaterialName(context: Context, name: String?): Int {
        var resId=0
        if (name == null || name.isEmpty() || name.isBlank())
            return resId //to prevent null pointer exception

        resId=context.resources.getIdentifier(name, "drawable", context.packageName)
        if(resId>0) return resId

        val mName = "hengam_ic_" + name.replace("[- ]".toRegex(), "_")
        resId=context.resources.getIdentifier(mName, "drawable", context.packageName)
        return resId
    }
}
