/* Aplicación GeovalladoScanner
MainActivity.kt
 * Solicita permisos de localización.
    https://developer.android.com/training/permissions/requesting?hl=es-419
 * Añade geovallados y receptor de transiciones de este.
    https://developer.android.com/training/location/geofencing?hl=es
 * Inicia WakeLock
*/

package com.example.geovalladoscanner

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
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

    companion object {
        val instance = MainActivity()
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest

    //Geofence
    private lateinit var geofencingClient: GeofencingClient//cliente de geovallado

    //Receptor de emisión para las transiciones de geovallado
    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(this, GeofenceBroadcastReceiver::class.java)
        intent.action = ".ACTION_RECEIVE_GEOFENCE"
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
        // addGeofences() and removeGeofences().
        PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    // Banderas
    private var addedGeofence = false
    private var createLocationRequestAndCheckSettingsBool = false
    private var locationUpdatesBool = false

    private val deviceIdleReceiver = object : BroadcastReceiver() {
        @SuppressLint("BatteryLife")
        @Suppress("NAME_SHADOWING")
        override fun onReceive(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm =
                    getSystemService(Context.POWER_SERVICE) as PowerManager
                if (pm.isDeviceIdleMode) {
                    val intent = Intent()
                    val packageName: String = packageName
                    intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                        intent.data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geofencingClient = LocationServices.getGeofencingClient(this)

        ignoreBatteryOptimization()
        createNotificationChannel(this) //Notification.kt

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

    @SuppressLint("BatteryLife")
    private fun ignoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent()
            val packageName: String = packageName
            val pm =
                getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        //Toast.makeText(this@MainActivity, "onStart", Toast.LENGTH_SHORT).show()
        if (!addedGeofence and checkPermissions())
            createLocationRequestAndCheckSettings()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intentFilter = IntentFilter()
            intentFilter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            registerReceiver(deviceIdleReceiver, intentFilter)
        }
    }

    private fun checkPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ),
                22
            )

            val permissionAccessFineLocationApproved = ActivityCompat
                .checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED

            val backgroundLocationPermissionApproved = ActivityCompat
                .checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED

            return  permissionAccessFineLocationApproved && backgroundLocationPermissionApproved

        }else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                22
            )

            return ActivityCompat
                .checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun createLocationRequestAndCheckSettings() {
        createLocationRequestAndCheckSettingsBool = true
        locationRequest = LocationRequest.create()?.apply {
            interval = 10 * 1000  //revisar
            fastestInterval = 5 * 1000
            maxWaitTime = 30 * 1000
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
                }catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                    //sendEx.printStackTrace()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun addGeofences() {
        geofencingClient.addGeofences(createGeofence(), geofencePendingIntent)?.run {
            addOnSuccessListener {
                // Geofences added
                Toast.makeText(this@MainActivity, "Geovallados agregados", Toast.LENGTH_SHORT).show()
                addedGeofence = true
            }
            addOnFailureListener {
                // Failed to add geofences
                Toast.makeText(this@MainActivity, "Geovallados NO agregados", Toast.LENGTH_SHORT).show()
                addedGeofence = false
            }
        }
    }

    //Crear georefencia
    private fun createGeofence(): GeofencingRequest? {
        val geofenceList: ArrayList<Geofence> = arrayListOf()
        for (station in GeofenceConstants.Station_TM) {
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
                    .setTransitionTypes(
                        Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL
                                or Geofence.GEOFENCE_TRANSITION_EXIT
                    )

                    // Create the geofence.
                    .build()
            )
        }
        return GeofencingRequest.Builder().apply {
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_DWELL)
            addGeofences(geofenceList)
        }.build()
    }

    // Siguientes ciclos de vida OnPause, OnResume y OnDestroy.
    private lateinit var wakeLock: PowerManager.WakeLock
    private var wakeLockOn = false
    @Suppress("DEPRECATION")
    override fun onPause() {
        super.onPause()
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag").apply {
                    acquire(10 * 60 * 1000L /*10 minutes*/)
                }
            }
        wakeLockOn = true

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            startLocationUpdates()
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "MyApp::MyScreenWakelockTag").apply {
                    acquire(30 * 60 * 1000L /*10 minutes*/)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        stopLocationUpdates()
        if (wakeLockOn) {
            if (wakeLock.isHeld) {
                wakeLock.release()
                wakeLockOn = false
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!locationUpdatesBool and !isServiceRunning(ScannerWifiService::class.java)) {
            val action = "start"
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                pendingIntentLocation(action)
            )
            locationUpdatesBool = true
        }
    }

    private fun stopLocationUpdates() {
        if (locationUpdatesBool) {
            val action = "stop"
            fusedLocationClient.removeLocationUpdates(pendingIntentLocation(action))
            locationUpdatesBool = false
        }
    }

    private fun pendingIntentLocation(action: String): PendingIntent? {
        val intent = Intent(this, LocationUpdatesService::class.java)
        intent.action = ".ACTION_PROCESS_UPDATES"
        intent.putExtra("Action",action)
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    @Suppress("DEPRECATION") //Appi level 26 Oreo < 8
    private fun <T> Context.isServiceRunning(service: Class<T>): Boolean {
        return (getSystemService(ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == service.name }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        removeGeofences()
    }

    private fun removeGeofences() {
        geofencingClient.removeGeofences(geofencePendingIntent)?.run {
            addOnSuccessListener {
                // Geofences removed
                Toast.makeText(this@MainActivity, "Geovallados eliminados", Toast.LENGTH_SHORT).show()
            }
            addOnFailureListener {
                // Failed to remove geofences
                Toast.makeText(this@MainActivity, "Geovallados NO eliminados", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Para los botones definidos en OnStart.
    // Inicia el servicio ScannerWifiService.kt, simulando activaciones de geovallado.
    private fun actionOnService(action: Actions) {
        Intent(this, ScannerWifiService::class.java).also {
            it.action = action.name
            it.putExtra("Station_name", "Prueba botones")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(it)
                return
            }
            startService(it)
        }
    }

}
