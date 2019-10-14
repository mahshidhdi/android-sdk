package co.pushe.plus.utils

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import co.pushe.plus.LogTag.T_UTILS
import co.pushe.plus.messages.common.ApplicationDetail
import co.pushe.plus.utils.log.Plog
import java.security.MessageDigest
import javax.inject.Inject


class ApplicationInfoHelper @Inject constructor(
        private val context: Context
) {
    /**
     * Get the version of an installed application.
     *
     * @param packageName The package name of the application to get the version for. If not
     *                    specified the package name of the running application will be used.
     * @return The application version string
     */
    fun getApplicationVersion(packageName: String? = null): String? {
        val pm = context.packageManager
        return try {
            pm?.getPackageInfo(packageName ?: context.packageName, 0)?.versionName
        } catch (ex: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Get the version code of an installed application.
     *
     * @param packageName The package name of the application to get the version for. If not
     *                    specified the package name of the running application will be used.
     * @return The application version code
     */
    @Suppress("DEPRECATION")
    @SuppressLint("NewApi")
    fun getApplicationVersionCode(packageName: String? = null): Long? {
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    pm?.getPackageInfo(packageName ?: context.packageName, 0)?.longVersionCode
                } catch (ex: NoSuchMethodError) {
                    pm?.getPackageInfo(packageName ?: context.packageName, 0)?.versionCode?.toLong()
                }
            } else {
                pm?.getPackageInfo(packageName ?: context.packageName, 0)?.versionCode?.toLong()
            }
        } catch (ex: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Get details of an installed application
     *
     * @param packageName The package name of the application to get details for. If not
     *                    specified the package name of the running application will be used.
     * @return An [ApplicationDetail] instance containing the details of the specified
     *         application or null of the application is not installed
     */
    fun getApplicationDetails(packageName: String = context.packageName): ApplicationDetail? {
        val pm = context.packageManager

        return try {
            packageInfoToApplicationDetail(pm.getPackageInfo(packageName, 0))
        } catch (ex: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Get a list of packages installed on the user's device.
     *
     * @return A [List] containing an [ApplicationDetail] object for each installed package
     */
    fun getInstalledApplications(): List<ApplicationDetail> {
        return context.packageManager.getInstalledPackages(0)
                .map { packageInfoToApplicationDetail(it) }
    }

    private fun packageInfoToApplicationDetail(info: PackageInfo): ApplicationDetail {
        return ApplicationDetail(
                packageName = info.packageName,
                appVersion = info.versionName,
                installer = getInstallerPackageName(info.packageName),
                installationTime = info.firstInstallTime,
                lastUpdateTime = info.lastUpdateTime,
                name = context.packageManager.getApplicationLabel(info.applicationInfo).toString(),
                sign = getApplicationSignature(info.packageName),
                isHidden = isAppHidden(info.packageName)
        )
    }

    /**
     * Get the installer for an installed application
     *
     * @param packageName The package name of the application to get the installer for. If not
     *                    specified the package name of the running application will be used.
     * @return A [String] containing the installer package name
     */
    fun getInstallerPackageName(packageName: String = context.packageName): String {
        return try {
            context.packageManager.getInstallerPackageName(packageName) ?: INSTALLER_DIRECT
        } catch (e: IllegalArgumentException) {
            Plog.warn(T_UTILS, "Error getting installer source. Setting installer to $INSTALLER_DIRECT")
            INSTALLER_DIRECT
        }
    }

    /**
     * Checks whether an installed application is a hidden application
     *
     * @param packageName The package name of the application to check. If not specified the
     *                    package name of the running application will be used.
     * @return true if the application is installed and is hidden and false if the application
     *         is not installed or is not hidden.
     */
     fun isAppHidden(packageName: String = context.packageName): Boolean {
        val pm = context.packageManager
        val i = Intent()
        i.addCategory("android.intent.category.LAUNCHER")
        i.setPackage(packageName)

        val lst: List<ResolveInfo>
        try {
            lst = pm.queryIntentActivities(i, PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_ALL)
        } catch (ex: NullPointerException) {
            return false
        }

        if (lst == null || lst.isEmpty()) {
            return true
        }

        for (resolveInfo in lst) {
            if (resolveInfo.activityInfo.packageName == context.packageName) {
                val mPackage = resolveInfo.activityInfo.packageName
                val mClass = resolveInfo.activityInfo.name
                val status = context.packageManager.getComponentEnabledSetting(ComponentName(mPackage, mClass))

                if (PackageManager.COMPONENT_ENABLED_STATE_DEFAULT == status) {
                    return !resolveInfo.activityInfo.isEnabled
                } else if (PackageManager.COMPONENT_ENABLED_STATE_DISABLED == status) {
                    return true
                } else if (PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED == status) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Get the signature hash from package name provided.
     * @param packageName is the package name of that app.
     * @return a text that is the hash of that file.
     */
    fun getApplicationSignature(packageName: String = context.packageName): List<String> {
        val signatureList: List<String>
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val sig = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
                signatureList = if (sig.hasMultipleSigners()) {
                    // Send all with apkContentsSigners
                    sig.apkContentsSigners.map {
                        val digest = MessageDigest.getInstance("SHA")
                        digest.update(it.toByteArray())
                        bytesToHex(digest.digest()).toUpperCase()
                    }
                } else {
                    // Send one with signingCertificateHistory
                    sig.signingCertificateHistory.map {
                        val digest = MessageDigest.getInstance("SHA")
                        digest.update(it.toByteArray())
                        bytesToHex(digest.digest()).toUpperCase()
                    }
                }
            } else {
                val sig = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
                signatureList = sig.map {
                    val digest = MessageDigest.getInstance("SHA")
                    digest.update(it.toByteArray())
                    bytesToHex(digest.digest()).toUpperCase()
                }
            }

            return signatureList
        } catch (e: Exception) {
            Plog.warn("Failed to get App signature of $packageName. ", e)
        }

        return emptyList()
    }

    fun isFreshInstall(): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val firstInstallTime = packageInfo.firstInstallTime
            val lastUpdateTime = packageInfo.lastUpdateTime
            firstInstallTime == lastUpdateTime
        } catch (e: PackageManager.NameNotFoundException) {
            true
        }

    }

    companion object {
        const val INSTALLER_DIRECT = "direct"
    }


    fun getHostingApplicationVersionCode() : Int {
        return context.packageManager.getPackageInfo(
            context.packageName, 0).versionCode
    }
}