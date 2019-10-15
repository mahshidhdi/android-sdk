package io.hengam.lib.notification

import android.app.DownloadManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.support.v4.app.NotificationCompat
import androidx.work.workDataOf
import io.hengam.lib.internal.HengamInternals
import io.hengam.lib.internal.cpuThread
import io.hengam.lib.internal.task.TaskScheduler
import io.hengam.lib.notification.Constants.DEFAULT_CHANNEL_ID
import io.hengam.lib.notification.LogTag.T_NOTIF
import io.hengam.lib.notification.LogTag.T_NOTIF_ACTION
import io.hengam.lib.notification.dagger.NotificationComponent
import io.hengam.lib.notification.messages.downstream.NotificationMessage
import io.hengam.lib.notification.tasks.InstallationCheckTask
import io.hengam.lib.notification.utils.FileHelper
import io.hengam.lib.utils.*
import io.hengam.lib.utils.log.Plog
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.ToJson
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Downloads and installs an application on the user's device.
 * Use the [downloadAndInstallApp] method to start the installation process for an application.
 *
 * The following events will be reported to the [NotificationInteractionReporter]:
 * - Download complete ([NotificationInteractionReporter.onApkDownloadSuccess])
 * - Download failed ([NotificationInteractionReporter.onApkDownloadFailed])
 * - APK installed ([NotificationInteractionReporter.onApkInstalled])
 * - APK not installed ([NotificationInteractionReporter.onApkNotInstalled])
 */
class NotificationAppInstaller @Inject constructor(
        private val context: Context,
        private val notificationInteractionReporter: NotificationInteractionReporter,
        private val taskScheduler: TaskScheduler,
        private val applicationInfoHelper: ApplicationInfoHelper,
        hengamStorage: HengamStorage
){

    private val pendingInstallations = hengamStorage.createStoredMap(
            "notification_pending_downloads",
            PendingInstall::class.java,
            PendingInstall.Adapter(),
            PENDING_INSTALL_EXPIRATION_TIME
    )

    /**
     * Download APK file using the [DownloadManager] and install the application on the users device
     * Once the file is downloaded it will either be opened immediately or when the user clicks on
     * the download-complete notification.
     *
     * The following events will be reported to the [NotificationInteractionReporter]:
     * - When the file successfully downloads
     * - When the file download fails
     * - When the file has been opened
     *
     * @param messageId The message id of the [NotificationMessage] which triggered the download.
     *                  This id will be passed to the [NotificationInteractionReporter] when reporting events.
     * @param packageName The package name of the application which is going to be installed
     * @param downloadUrl The URL which the file should be downloaded from
     * @param openImmediate If true the file will open immediately after the download is finished.
     *                      If false, a notification will be shown when the download finished and
     *                      the file will open once the notification is clicked.
     * @param notifTitle The title of the notification to show.
     * @param timeToInstall The maximum time after the file has been opened which we should expect
     *                      the application to be installed by. After opening the APK a task will
     *                      be scheduled to be run with a delay of `timeToInstall` to check if the
     *                      application is installed.

     */
    @Throws(AppInstallException::class)
    fun downloadAndInstallApp(messageId: String, packageName: String, downloadUrl: String,
                              openImmediate : Boolean, notifTitle: String?, timeToInstall: Time?) {
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
        request.setTitle(notifTitle ?: "downloading")
        request.setMimeType("application/vnd.android.package-archive")

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                ?: throw AppInstallException("Could not obtain DownloadManager instance")

        val requestId = manager.enqueue(request)

        pendingInstallations[requestId.toString()] = PendingInstall(
                messageId = messageId,
                packageName = packageName,
                timeToInstall = timeToInstall,
                notifTitle = notifTitle,
                openImmediate = openImmediate
        )
    }

    /**
     * Is called when an APK download has been completed.
     *
     * 1. Will inform the [NotificationInteractionReporter] of the successful download
     * 2. If the message had an `open_immediate=true` value, will open the file immediately by
     *    calling [openApkFile]. Otherwise, will call [showNotification] to show a "download
     *    completed" notification.
     */
    private fun onDownloadComplete(downloadId: Long, downloadedPackageUriString: String) {
        val pendingInstall = pendingInstallations[downloadId.toString()]
                ?: throw AppInstallException("Attempting to get pending install which does not exist")

        Plog.debug(T_NOTIF, T_NOTIF_ACTION, "Download completed in notification app installer",
            "Package Name" to pendingInstall.packageName,
            "Message Id" to pendingInstall.messageId,
            "URI" to downloadedPackageUriString
        )

        notificationInteractionReporter.onApkDownloadSuccess(pendingInstall.messageId, pendingInstall.packageName)

        if (pendingInstall.openImmediate) {
            openApkFile(downloadId, downloadedPackageUriString, pendingInstall)
        } else {
            showNotification(context, downloadId, downloadedPackageUriString, pendingInstall)
        }
    }

    /**
     * Is called when an APK download fails.
     * Will inform the [NotificationInteractionReporter] of the failed download
     */
    private fun onDownloadFailed(downloadId: Long, reason: Int) {
        val pendingInstall = pendingInstallations[downloadId.toString()]
                ?: throw AppInstallException("Attempting to get pending install which does not exist")

        Plog.warn(T_NOTIF, T_NOTIF_ACTION, "Downloading file failed",
            "Download Id" to downloadId,
            "Package Name" to pendingInstall.packageName,
            "Message Id" to pendingInstall.messageId,
            "Reason" to reason
        )

        notificationInteractionReporter.onApkDownloadFailed(pendingInstall.messageId, pendingInstall.packageName)
        pendingInstallations.remove(downloadId.toString())
    }


    /**
     * Is called when a "download complete" notification (shown using the [showNotification] function),
     * has been clicked.
     * Will call [openApkFile] to open the downloaded APK.
     */
    private fun onDownloadCompleteNotificationClicked(downloadId: Long, downloadedPackageUriString: String) {
        val pendingInstall = pendingInstallations[downloadId.toString()]
                ?: throw AppInstallException("Attempting to get pending install which does not exist")

        openApkFile(downloadId, downloadedPackageUriString, pendingInstall)
    }

    /**
     * Shows a "download complete" notification to the user.
     */
    private fun showNotification(context: Context, downloadId: Long, downloadedPackageUriString : String, pendingInstall: PendingInstall) {
        val notificationIntent = Intent(context, DownloadCompleteNotificationClickReceiver::class.java)
        notificationIntent.putExtra(DATA_DOWNLOAD_ID, downloadId)
        notificationIntent.putExtra(DATA_FILE_LOCAL_URI, downloadedPackageUriString)

        notificationIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP

        val pendingIntent = PendingIntent.getBroadcast(context, IdGenerator.generateIntegerId(),
                notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        val notificationBuilder = NotificationCompat.Builder(context, DEFAULT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(pendingInstall.notifTitle ?: "فایل")
                .setContentText("دانلود تمام شد")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: throw AppInstallException("Could not obtain NotificationManager")

        notificationManager.notify(IdGenerator.generateIntegerId(), notificationBuilder.build())
    }

    /**
     * Opens the downloaded APK file to prompt the user to install the package.
     *
     * Will call [scheduleInstallationChecker] to schedule an installation checker to be run.
     */
    private fun openApkFile(downloadId: Long, downloadedPackageUriString : String, pendingInstall: PendingInstall){
        scheduleInstallationChecker(downloadId, pendingInstall)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE)
            installIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            installIntent.setData(Uri.parse(downloadedPackageUriString))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(installIntent)
        } else {
            val installIntent = Intent(Intent.ACTION_VIEW)
            installIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            installIntent.setDataAndType(
                    Uri.fromFile(File(FileHelper.getPath(context, Uri.parse(downloadedPackageUriString)))),
                    "application/vnd.android.package-archive"
            )
            context.startActivity(installIntent)
        }
    }

    /**
     * Schedules a [InstallationCheckTask] to be run with a delay. The delay is specified by  the
     * [PendingInstall.timeToInstall] parameter or by [DEFAULT_TIME_TO_INSTALL] if it is not specified.
     *
     * Once the task is run, it will call the [checkIsAppInstalled] method to check whether the
     * user has installed the application within this time.
     *
     * It will also check to see if the application is already installed and if it is it will save
     * the current app version in the [PendingInstall] object. This will be used to check whether the
     * application has been newly installed (or updated) or not whether it already existed.
     */
    private fun scheduleInstallationChecker(downloadId: Long, pendingInstall: PendingInstall) {
        val existingApp = applicationInfoHelper.getApplicationDetails(pendingInstall.packageName)

        pendingInstallations[downloadId.toString()] = pendingInstall.copy(
            existingVersion = existingApp?.appVersion,
            lastUpdateTime = existingApp?.lastUpdateTime
        )

        taskScheduler.scheduleTask(
                InstallationCheckTask.Options(),
                workDataOf(InstallationCheckTask.DOWNLOAD_ID to downloadId),
                pendingInstall.timeToInstall ?: DEFAULT_TIME_TO_INSTALL
        )
    }

    /**
     * Is called by the [InstallationCheckTask].
     *
     * Checks whether the application is installed or not notifies the [NotificationInteractionReporter]
     */
    fun checkIsAppInstalled(downloadId: Long) {
        val pendingInstall = pendingInstallations[downloadId.toString()]
                ?: throw AppInstallException("Attempting to get pending install which does not exist")

        val installedApp = applicationInfoHelper.getApplicationDetails(pendingInstall.packageName)

        if (installedApp?.lastUpdateTime != null && (pendingInstall.lastUpdateTime == null || (installedApp.lastUpdateTime!! > pendingInstall.lastUpdateTime))) {
            notificationInteractionReporter.onApkInstalled(
                pendingInstall.messageId,
                installedApp,
                pendingInstall.existingVersion
            )
        } else {
            notificationInteractionReporter.onApkNotInstalled(
                pendingInstall.messageId,
                pendingInstall.existingVersion
            )
        }

        pendingInstallations.remove(downloadId.toString())
    }

    /**
     * Broadcast receiver which listens for the [DownloadManager] events.
     *
     * Notifies the [NotificationAppInstaller] when an APK download has successfully finished or
     * it when it has failed
     */
    class DownloadCompleteReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            cpuThread {
                tryAndCatch(T_NOTIF, T_NOTIF_ACTION) {
                    handleDownloadEvent(context, intent)
                }
            }
        }

        private fun handleDownloadEvent(context: Context, intent: Intent) {
            val notifComponent = HengamInternals.getComponent(NotificationComponent::class.java)
                    ?: throw AppInstallException("Failed to obtain notification component")

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                    ?: throw AppInstallException("Could not obtain DownloadManager instance")

            val extras = intent.extras ?: return
            val downloadId = extras.getLong(DownloadManager.EXTRA_DOWNLOAD_ID)

            val query = DownloadManager.Query()
            query.setFilterById(downloadId)
            val cursor = downloadManager.query(query)

            if (cursor != null && cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val downloadedPackageUriString = cursor.getString(
                            cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                    notifComponent.notificationAppInstaller()
                            .onDownloadComplete(downloadId, downloadedPackageUriString)
                } else if (status == DownloadManager.STATUS_FAILED) {
                    notifComponent.notificationAppInstaller()
                            .onDownloadFailed(downloadId, cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON)))
                }
            }
        }
    }

    /**
     * Broadcast receiver which is triggered when a download-complete notification is clicked by
     * the user.
     *
     * Simply notifies the [NotificationAppInstaller] that the notification has been clicked
     */
    class DownloadCompleteNotificationClickReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            cpuThread {
                tryAndCatch(T_NOTIF, T_NOTIF_ACTION) {
                    val notifComponent = HengamInternals.getComponent(NotificationComponent::class.java)
                            ?: throw AppInstallException("Failed to obtain notification component")
                    val downloadId = intent.getLongExtra(DATA_DOWNLOAD_ID, 0)
                    val downloadedPackageUriString = intent.getStringExtra(DATA_FILE_LOCAL_URI)
                    notifComponent.notificationAppInstaller()
                            .onDownloadCompleteNotificationClicked(downloadId, downloadedPackageUriString)
                }
            }
        }
    }

    companion object {
        const val DATA_FILE_LOCAL_URI = "file_local_uri"
        const val DATA_DOWNLOAD_ID = "download_id"

        /**
         * The default `timeToInstall` parameter to use for [PendingInstall] instances which don't
         * specify a value.
         */
        val DEFAULT_TIME_TO_INSTALL = minutes(15)

        /**
         * The expiration time for items stored in [pendingInstallations].
         *
         * Items stored in [pendingInstallations] should be removed once the whole process has
         * been completed. But in order to ensure that errored items do not linger in the store,
         * all items will be removed after an expiration time.
         */
        val PENDING_INSTALL_EXPIRATION_TIME = days(7)
    }
}

/**
 * Holds information about an application install in progress.
 *
 * @param messageId The message id for the [NotificationMessage] which triggered the install process
 * @param packageName The package name of the application which is being installed
 * @param timeToInstall Specifies the amount of time to wait after opening the apk file before
 *                      checking whether the application has been installed
 * @param notifTitle Specifies the title to use for notifications used during the install process
 * @param openImmediate Specifies whether the apk file should be opened immediately after download
 *                      or whether a "download complete" notification should be shown first.
 * @param existingVersion The version of an installed application with the given package name if one
 *                        exists before installing this package, or null if it doesn't
 * @param lastUpdateTime Last Update Time of an installed application with the given package name if one
 *                        exists before installing this package, or null if it doesn't
 */
private data class PendingInstall(
        val messageId: String,
        val packageName: String,
        val timeToInstall: Time?,
        val notifTitle: String?,
        val openImmediate: Boolean,
        val existingVersion: String? = null,
        val lastUpdateTime: Long? = null
) {
    /**
     * The @JvmSuppressWildcards is needed, see here: https://github.com/square/moshi/issues/573
     */
    class Adapter {
        @ToJson fun toJson(pendingInstall: PendingInstall): Map<String, @JvmSuppressWildcards Any?> = mapOf(
                "message_id" to pendingInstall.messageId,
                "package_name" to pendingInstall.packageName,
                "time_to_install" to pendingInstall.timeToInstall?.toSeconds(),
                "notif_title" to pendingInstall.notifTitle,
                "open_immediate" to pendingInstall.openImmediate,
                "existing_version" to pendingInstall.existingVersion,
                "last_update_time" to pendingInstall.lastUpdateTime
        )

        @FromJson fun fromJson(json: Map<String, @JvmSuppressWildcards Any?>): PendingInstall = PendingInstall(
                messageId = json["message_id"] as? String ?: throw JsonDataException("Missing 'message_id' field"),
                packageName = json["package_name"] as? String ?: throw JsonDataException("Missing 'package_name' field"),
                timeToInstall = (json["time_to_install"] as Long?)?.let { Time(it, TimeUnit.SECONDS) },
                notifTitle = json["notif_title"] as String?,
                openImmediate = json["open_immediate"] as? Boolean ?: throw JsonDataException("Missing 'open_immediate' field"),
                existingVersion = json["existing_version"] as String?,
                lastUpdateTime = json["last_update_time"] as Long?
        )
    }
}

class AppInstallException(message: String, cause: Throwable? = null) : Exception(message, cause)