package com.example.geovalladoscanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        val geofenceEvent = GeofencingEvent.fromIntent(intent)
        if (geofenceEvent.hasError()) {
            //val errorMessage = GeofenceStatusCodes.getErrorString(geofencingEvent.errorCode)
            return
        }

        // Get the transition type.
        // Test that the reported transition was of interest.
        when (geofenceEvent.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {

                Intent(context, ScannerWifiService::class.java).also {
                    it.action = Actions.ENTER.name
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(it)
                        return
                    }
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
}


