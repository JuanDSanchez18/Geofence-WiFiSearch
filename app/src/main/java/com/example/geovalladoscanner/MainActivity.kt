package com.example.geovalladoscanner

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import java.util.*

//https://github.com/Damian9696/Geofences/blob/master/app/src/main/java/com/example/android/treasureHunt/HuntMainActivity.kt
class MainActivity : AppCompatActivity() {

    private val tag = "MainActivity"

    private val runningQOrLater =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest

    //Geofence
    private lateinit var geofencingClient: GeofencingClient//cliente de geovallado

    //Receptor de emisión para las transiciones de geovallado
    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(this, GeofenceBroadcastReceiver::class.java)
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // addGeofences() and removeGeofences().
        PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geofencingClient = LocationServices.getGeofencingClient(this)

        createNotificationChannel(this)


    }

    private var addedGeofence = false
    override fun onStart() {
        super.onStart()

        if (!addedGeofence and checkPermissions()) {
            createLocationRequestAndcheckSettings()
        }

        //Buttons
        findViewById<Button>(R.id.enterGeofence).let {
            it.setOnClickListener {
                actionOnService(Actions.ENTER)
            }
        }
        findViewById<Button>(R.id.exitGeofence).let {
            it.setOnClickListener {
                actionOnService(Actions.EXIT)
            }
        }
        findViewById<Button>(R.id.stopButton).let {
            it.setOnClickListener {
                actionOnService(Actions.ENDSERVICE)
            }
        }
    }

    //request permission Q
    private fun checkPermissions(): Boolean {

        if (runningQOrLater) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ),
                21
            )
            val permissionAccessFineLocationApproved = ActivityCompat
                .checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            val backgroundLocationPermissionApproved = ActivityCompat
                .checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED

            return permissionAccessFineLocationApproved && backgroundLocationPermissionApproved
        }else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                22
            )

            return ActivityCompat
                .checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED

        }
    }

    private fun createLocationRequestAndcheckSettings() {

        locationRequest = LocationRequest.create()?.apply {
            interval = 20 * 1000  //revisar
            fastestInterval = 15 * 1000
            maxWaitTime = 40 * 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }!!

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener { // locationSettingsResponse ->
            // All location settings are satisfied. The client can initialize
            // location requests here.
            // ...
            Log.i(tag, "Success check settings")

            addGeofences()

        }

        task.addOnFailureListener { exception ->

            if (exception is ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    exception.startResolutionForResult(
                        this@MainActivity,
                        29
                    )//REQUEST_CHECK_SETTINGS


                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                    //sendEx.printStackTrace()
                    Log.e(tag, "Error getting location settings resolution: " + sendEx.message)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun addGeofences() {

        geofencingClient.addGeofences(createGeofence(), geofencePendingIntent)?.run {
            addOnSuccessListener {
                // Geofences added
                addedGeofence = true
                Toast.makeText(this@MainActivity, "Added geofences", Toast.LENGTH_SHORT).show()
                Log.i(tag, "Adding geofences")
            }
            addOnFailureListener {
                // Failed to add geofences
                Log.e(tag, "Fail adding geofences")
            }
        }
    }

    //Crear georefencia
    private fun createGeofence(): GeofencingRequest? {

        val geofenceList: ArrayList<Geofence> = arrayListOf()

        for (station in GeofenceConstants.Station_TM) {//posible error

            //val constants = GeofencingConstants.LANDMARK_DATA[i]
            Log.i(tag, "Add geofences: ${station.key}")

            geofenceList.add(
                Geofence.Builder()
                    // Set the request ID of the geofence. This is a string to identify this
                    // geofence.
                    .setRequestId(station.key)

                    // Set the circular region of this geofence.
                    .setCircularRegion(
                        station.latLong.latitude,
                        station.latLong.longitude,
                        GeofenceConstants.GEOFENCE_RADIUS_IN_METERS
                    )

                    // Set the expiration duration of the geofence. This geofence gets automatically
                    // removed after this period of time.
                    .setExpirationDuration(GeofenceConstants.GEOFENCE_EXPIRATION_IN_MILLISECONDS)

                    .setLoiteringDelay(GeofenceConstants.GEOFENCE_DWELL_TIME)

                    // Set the transition types of interest. Alerts are only generated for these
                    // transition. We track dwell and exit transitions in this sample.
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL
                            or Geofence.GEOFENCE_TRANSITION_EXIT)

                    // Create the geofence.
                    .build()
            )
        }
        return GeofencingRequest.Builder().apply {
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_DWELL)
            addGeofences(geofenceList)
        }.build()
    }

    private fun removeGeofences() {
        geofencingClient.removeGeofences(geofencePendingIntent)?.run {
            addOnSuccessListener {
                // Geofences removed
                Log.i(tag, "Removing geofences")
            }
            addOnFailureListener {
                // Failed to remove geofences
                Log.e(tag, "Fail removing geofences")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeGeofences()
    }

    private fun actionOnService(action: Actions) {
        Intent(this, ScannerWifiService::class.java).also {
            it.action = action.name
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(it)
                return
            }
            startService(it)
        }
    }

    private lateinit var wakeLock: PowerManager.WakeLock
    private var wakeLockOn = false
    override fun onPause() {
        super.onPause()
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag").apply {
                    acquire(10*60*1000L /*10 minutes*/)
                }
            }
        wakeLockOn = true
    }

    override fun onResume() {
        super.onResume()
        if (wakeLockOn)
            if (wakeLock.isHeld) wakeLock.release()

   }
}


