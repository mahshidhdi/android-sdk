package io.hengam.lib.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

/***
 * A helper class to for checking permission that has been granted or not
 */
object PermissionChecker {
    val ACCESS_NETWORK_STATE = Manifest.permission.ACCESS_NETWORK_STATE
    val READ_PHONE_STATE = Manifest.permission.READ_PHONE_STATE
    val ACCESS_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION
    val ACCESS_FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION
    val ACCESS_WIFI_STATE = Manifest.permission.ACCESS_WIFI_STATE
    val GET_ACCOUNTS = Manifest.permission.GET_ACCOUNTS
    val ACTIVITY_RECOGNITION = "com.google.android.gms.permission.ACTIVITY_RECOGNITION"


    /***
     * check permission has granted or not
     *
     * @param context    the app context
     * @param permission desired permission
     * @return true if permission has granted
     */
    fun hasPermission(context: Context, permission: String): Boolean {
        val res = context.checkCallingOrSelfPermission(permission)
        return res == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Same as [hasPermission] but for more than one permission.
     */
    fun hasPermissions(context: Context, vararg permissions: String): Boolean {
        var allGranted = true
        permissions.forEach {
            if (!hasPermission(context, it)) {
                allGranted = false
            }
        }
        return allGranted
    }


    /***
     * Do the task only if the permission is granted.
     *
     * @param context    the app context
     * @param permission desired permission
     * @param toDo`
     * will be invoked if the [hasPermission] returned true.
     */
    fun ifHasPermission(context: Context, permission: String, toDo: () -> Unit) {
        if (hasPermission(context, permission)) toDo()
    }
}

/**
 * Check the permission and do when allowed/not allowed.
 * @param context is needed for [PermissionChecker.hasPermission]
 * @param permission is the Permission that is analyzed.
 * @see PermissionChecker
 * @param toDo
 * is the function that will be invoked when permission is granted.
 * @param notToDo is the function that will be invoked when permission is denied.
 */
fun ifHasPermission(context: Context, permission: String, toDo: () -> Unit, notToDo: (String) -> Unit) {
    if (PermissionChecker.hasPermission(context, permission)) toDo()
    else notToDo(permission)
}
