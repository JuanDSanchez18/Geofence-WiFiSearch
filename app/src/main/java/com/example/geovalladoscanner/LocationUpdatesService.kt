package com.example.geovalladoscanner

import android.annotation.SuppressLint
import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.android.gms.location.LocationResult

private const val ACTION_PROCESS_UPDATES = ".ACTION_PROCESS_UPDATES"
private  const val START_ACTION = "start"

@Suppress("DEPRECATION") //Appi level 26 Oreo < 8
class LocationUpdatesService(name: String = "LocationUpdatesService" ): IntentService(name) {

    @SuppressLint("BatteryLife")
    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            //val action = intent.extras?.getString("Action")
            if (intent.action == ACTION_PROCESS_UPDATES) {
                val result = LocationResult.extractResult(intent)
                if (result != null) {
                    val myContext: Context = this
                    //val location = result.locations
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(myContext, "Location request", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
