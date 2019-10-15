package io.hengam.lib.datalytics

import android.Manifest
import android.content.Context
import io.hengam.lib.LogTag.T_DEBUG
import io.hengam.lib.datalytics.LogTags.T_DATALYTICS
import io.hengam.lib.datalytics.LogTags.T_GEOFENCE
import io.hengam.lib.datalytics.collectors.*
import io.hengam.lib.datalytics.geofence.GeofenceManager
import io.hengam.lib.datalytics.messages.downstream.GeofenceMessage
import io.hengam.lib.datalytics.messages.upstream.AppInstallMessage
import io.hengam.lib.datalytics.messages.upstream.AppInstallMessageBuilder
import io.hengam.lib.datalytics.tasks.InstallDetectorTask
import io.hengam.lib.datalytics.tasks.scheduleLocationCollection
import io.hengam.lib.internal.DebugCommandProvider
import io.hengam.lib.internal.DebugInput
import io.hengam.lib.internal.HengamConfig
import io.hengam.lib.internal.HengamMoshi
import io.hengam.lib.messaging.PostOffice
import io.hengam.lib.messaging.PostOffice_Factory
import io.hengam.lib.messaging.SendPriority
import io.hengam.lib.utils.*
import io.hengam.lib.utils.log.Plog
import io.hengam.lib.utils.rx.justDo
import io.hengam.lib.utils.rx.subscribeBy
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class DebugCommands @Inject constructor(
        private val context: Context,
        private val hengamStorage: HengamStorage,
        private val hengamConfig: HengamConfig,
        private val collectorExecutor: CollectorExecutor,
        private val moshi: HengamMoshi,
        private val appListCollector: AppListCollector,
        private val cellularInfoCollector: CellularInfoCollector,
        private val constantDataCollector: ConstantDataCollector,
        private val variableDataCollector: VariableDataCollector,
        private val floatingDataCollector: FloatingDataCollector,
        private val wifiListCollector: WifiListCollector,
        private val geofenceManager: GeofenceManager,
        private val collectorScheduler: CollectorScheduler,
        private val applicationInfoHelper: ApplicationInfoHelper,
        private val postOffice: PostOffice
) : DebugCommandProvider {

    override val commands: Map<String, Any>
        get() =
            mapOf(
                    "Datalytics" to mapOf(
                            "Ensure Permissions" to "data_permissions",
                            "Log Collection Times" to "data_collection_times",
                            "Log Collectable Settings" to "data_collectable_settings",
                            "ReSchedule Collections" to "reschedule_collections",
                            "Send Data" to mapOf(
                                    "App list" to "data_send_app",
                                    "Cell info" to "data_send_cell",
                                    "Wifi list" to "data_send_wifi",
                                    "Constant Data" to "data_send_const",
                                    "Variable Data" to "data_send_var",
                                    "Floating Data" to "data_send_float",
                                    "App install detection" to "data_send_app_install"
                            ),
                            "Collect Data" to mapOf(
                                    "App list" to "data_get_app",
                                    "Cell info" to "data_get_cell",
                                    "Wifi list" to "data_get_wifi",
                                    "Constant Data" to "data_get_const",
                                    "Variable Data" to "data_get_var",
                                    "Floating Data" to "data_get_float"
                            ),
                            "Geofence" to mapOf(
                                    "Print Registered Geofences" to "data_geofences_print",
                                    "Re-register Geofences" to "data_geofences_reregister",
                                    "Register New Geofence" to "data_new_geofence_register",
                                    "Remove Geofence" to "remove_geofence",
                                    "Schedule Location Collection" to "collect_locations",
                                    "Stop Location Collection" to "stop_location_collection"
                            )
                    )
            )

    override fun handleCommand(commandId: String, input: DebugInput): Boolean {
        when (commandId) {
            "data_permissions" -> {
                val permissions = arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.ACCESS_WIFI_STATE,
                        Manifest.permission.ACCESS_NETWORK_STATE
                )

                if (!PermissionChecker.hasPermissions(context, *permissions)) {
                    input.requestPermissions(*permissions)
                } else {
                    Plog.debug(T_DATALYTICS, T_DEBUG, "Permissions are already granted")
                }
            }
            "data_collection_times" -> {
                val collectionTimes = hengamStorage.createStoredMap("collection_last_run_times", Long::class.javaObjectType)
                Plog.info(T_DEBUG, "Data Collection Times", "Collected At" to collectionTimes)
            }
            "data_collectable_settings" -> {
                val data = Collectable.allCollectables
                        .map {
                            val settings = hengamConfig.getCollectableSettings(it)
                            Pair(it.id, mapOf("repeat_interval" to settings.repeatInterval, "send_priority" to settings.sendPriority))
                        }
                        .toMap()
                Plog.debug(T_DATALYTICS, T_DEBUG, "Collectable Settings", "Settings" to data)
            }
            "reschedule_collections" -> {
                collectorScheduler.cancelAllCollectables()
                collectorScheduler.scheduleAllCollectablesWithInitialValue()
            }
            "data_send_app" -> collectorExecutor.collectAndSend(Collectable.AppList, SendPriority.IMMEDIATE).justDo(T_DATALYTICS, T_DEBUG)
            "data_send_cell" -> collectorExecutor.collectAndSend(Collectable.CellInfo, SendPriority.IMMEDIATE).justDo(T_DATALYTICS, T_DEBUG)
            "data_send_wifi" -> collectorExecutor.collectAndSend(Collectable.WifiList, SendPriority.IMMEDIATE).justDo(T_DATALYTICS, T_DEBUG)
            "data_send_const" -> collectorExecutor.collectAndSend(Collectable.ConstantData, SendPriority.IMMEDIATE).justDo(T_DATALYTICS, T_DEBUG)
            "data_send_var" -> collectorExecutor.collectAndSend(Collectable.VariableData, SendPriority.IMMEDIATE).justDo(T_DATALYTICS, T_DEBUG)
            "data_send_float" -> collectorExecutor.collectAndSend(Collectable.FloatingData, SendPriority.IMMEDIATE).justDo(T_DATALYTICS, T_DEBUG)
            "data_send_app_install" -> {
                val lastCollectedAt = hengamStorage.getLong("install_detector_task_last_run_time", TimeUtils.nowMillis())
                val apps = applicationInfoHelper.getInstalledApplications().filter { app -> app.installationTime != null && app.installationTime!! > lastCollectedAt }
                apps.map { app ->
                    Plog.info(T_DATALYTICS, app.packageName + " installed in last " + Time(TimeUtils.nowMillis() - lastCollectedAt, TimeUnit.MILLISECONDS).bestRepresentation())
                    postOffice.sendMessage(AppInstallMessageBuilder.build(app))
                }
                if (apps.isEmpty()) Plog.info(T_DATALYTICS, "no app installed in last" + Time(TimeUtils.nowMillis() - lastCollectedAt, TimeUnit.MILLISECONDS).bestRepresentation())
                hengamStorage.putLong("install_detector_task_last_run_time", TimeUtils.nowMillis())
            }
            "data_get_app" -> {
//                appListCollector.installedApplications
//                        .toList()
//                        .subscribe { data ->
//                            Plog[T_DATALYTICS, T_DEBUG].debug("App list") {
//                                "Size" to data.size
//                                "Package names" to data.map { it.packageName }.toString()
//                            }
//                        }
            }
            "data_get_cell" -> {
                cellularInfoCollector
                        .collect()
                        .map { it.toJson(moshi) }
                        .subscribe {
                            Plog.debug(T_DATALYTICS, T_DEBUG, "Cellular info", "Cellular Info" to it)
                        }
            }
            "data_get_const" -> {
                Plog.debug(T_DATALYTICS, T_DEBUG, "Constant data", "Constant Data" to constantDataCollector.getConstantData().toJson(moshi))
            }
            "data_get_wifi" -> {
                wifiListCollector.getWifiList()
                        .subscribe {
                            Plog.debug(T_DATALYTICS, T_DEBUG, "Wifi list", "Wifi" to it)
                        }
            }
            "data_get_var" -> {
                variableDataCollector.collect()
                        .doOnNext {
                            Plog.debug(T_DATALYTICS, T_DEBUG, "Variable data", "Variable data" to it.toJson(moshi))
                        }
                        .subscribe()
            }
            "data_get_float" -> {
                floatingDataCollector.collect()
                        .subscribe {
                            Plog.debug(T_DATALYTICS, T_DEBUG, "Floating data", "Data" to it.toJson(moshi))
                        }
            }
            "data_geofences_print" -> {
                val data = geofenceManager.geofences.values.map {
                    mapOf(
                            "id" to it.id,
                            "location" to "${it.lat},${it.long}",
                            "radius" to it.radius,
                            "trigger" to when (it.trigger) {
                                GeofenceMessage.GEOFENCE_TRIGGER_ENTER -> "on enter"
                                GeofenceMessage.GEOFENCE_TRIGGER_EXIT -> "on exit"
                                else -> "none"
                            },
                            "dwell_time" to it.dwellTime,
                            "trigger_on_init" to it.triggerOnInit,
                            "expiration" to it.expirationDate,
                            "limit" to it.limit,
                            "message" to it.message,
                            "trigger_count" to (geofenceManager.geofenceTriggerCounts[it.id] ?: 0),
                            "trigger_time" to (geofenceManager.geofenceTriggerTimes[it.id])
                    )
                }
                Plog.debug.message("Geofences")
                        .withTag(T_DATALYTICS, T_GEOFENCE, T_DEBUG)
                        .withData("Geofence Count", data.size)
                        .withData("Geofence Data", data)
                        .log()
            }
            "data_geofences_reregister" -> {
                geofenceManager.ensureGeofencesAreRegistered().justDo(T_DATALYTICS, T_GEOFENCE, T_DEBUG)
            }
            "data_new_geofence_register" -> {
                input.prompt("Add New Geofence", "Geofence", "hengamGeo,35.7050026,51.35218868,500,enter")
                    .subscribeBy { geo ->
                        val config = geo.split(",")
                        geofenceManager.addOrUpdateGeofence(GeofenceMessage(
                            id = config[0],
                            lat = config[1].toDouble(),
                            long = config[2].toDouble(),
                            radius = config[3].toFloat(),
                            messageId = IdGenerator.generateId(),
                            message = mapOf("t1" to mapOf(
                                "title" to "GeofenceTest",
                                "content" to "${config[4]}ing the Geofence ${config[0]} with location {Lat: ${config[1]} | Long: ${config[2]}}",
                                "allow_multi_publish" to true
                            )),
                            trigger = when(config[4]){
                                "exit" -> 2
                                else -> 1
                            }
                        ))
                    }
            }
            "remove_geofence" -> {
                input.prompt("Remove Geofence", "Geofence Id", "hengamGeo")
                    .subscribeBy { geo ->
                        geofenceManager.removeGeofence(geo)
                    }
            }
            "collect_locations" -> {
                input.prompt("Schedule Location Updates", "Interval(in_Minutes)", "60")
                    .subscribeBy { interval ->
                        hengamConfig.updateConfig("location_collection_enabled", true)
                        hengamConfig.updateConfig("location_collection_interval", interval.toLong())
                        scheduleLocationCollection()
                    }
            }
            "stop_location_collection" -> {
                hengamConfig.updateConfig("location_collection_enabled", false)
                scheduleLocationCollection()
            }
            else -> return false
        }
        return true
    }
}


