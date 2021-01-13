package com.example.geovalladoscanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.google.android.gms.location.LocationResult

private const val ACTION_PROCESS_UPDATES = ".ACTION_PROCESS_UPDATES"

class LocationUpdatesBroadcastReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent != null) {
            if (intent.action == ACTION_PROCESS_UPDATES) {
                val result = LocationResult.extractResult(intent)
                //val location = result.locations

                Toast.makeText(context, "Location request", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
