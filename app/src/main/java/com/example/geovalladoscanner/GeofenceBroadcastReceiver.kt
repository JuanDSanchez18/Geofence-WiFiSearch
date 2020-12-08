package com.example.geovalladoscanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.TextUtils
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    private val tag = "GeofenceReceiver"

    override fun onReceive(context: Context, intent: Intent) {

        val geofenceEvent = GeofencingEvent.fromIntent(intent)
        if (geofenceEvent.hasError()) {
            //val errorMessage = GeofenceStatusCodes.getErrorString(geofencingEvent.errorCode)
            Log.e(tag, "errorMessage")
            return
        }

        // Get the transition type.
        // Test that the reported transition was of interest.
        when (geofenceEvent.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                val triggeringGeofences = geofenceEvent.triggeringGeofences

                // Get the transition details as a String.
                val geofenceTransitionDetails = getGeofenceTransitionDetails(
                    triggeringGeofences
                )

                Intent(context, ScannerWifiService::class.java).also {
                    it.action = Actions.ENTER.name
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(it)
                        return
                    }
                    sendNotification(context, geofenceTransitionDetails)
                    context.startService(it)

                }
            }
            Geofence.GEOFENCE_TRANSITION_DWELL -> {
                Intent(context, ScannerWifiService::class.java).also {
                    it.action = Actions.DWELL.name
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        context.startForegroundService(it)
                    else
                        context.startService(it)
                }
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Intent(context, ScannerWifiService::class.java).also {
                    it.action = Actions.EXIT.name
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        context.startForegroundService(it)
                    else
                        context.startService(it)

                }
            }
        }
    }

    private fun getGeofenceTransitionDetails(
        triggeringGeofences: MutableList<Geofence>
    ): String {

        // Get the Ids of each geofence that was triggered.
        val triggeringGeofencesIdsList: ArrayList<String> = arrayListOf()
        for (geofence in triggeringGeofences) {
            triggeringGeofencesIdsList.add(geofence.requestId)
        }
        val triggeringGeofencesIdsString = TextUtils.join(", ", triggeringGeofencesIdsList)

        return "Geovallado: $triggeringGeofencesIdsString \n Empieza Scan Wifi."
    }
}

