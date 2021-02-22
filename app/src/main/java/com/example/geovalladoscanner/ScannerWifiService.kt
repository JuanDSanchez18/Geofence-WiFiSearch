/* Aplicación GeovalladoScanner
ScannerWifiService.kt
 * Servicio iniciado con activación del geovallado.
    * Entrada.
        Manda notificación al usuario.
        Inicializa busqueda de Wifi. (Necesario)
        Inicia Busqueda de Wifi repetitiva. (30 s)
    * Permanencia
        Cambia la repetición a 1 minuto y 30 segundos.
    * Salida
        Cambia bandera de geovallado.
 * Sevicio muere cuando no se está dentro del geovallado y no se recibe algún SSID de la lista.

*/
package com.example.geovalladoscanner

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.*
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private lateinit var wifiManager: WifiManager
private lateinit var wakeLock: PowerManager.WakeLock

private lateinit var locationManager: LocationManager

private var isGeofence = false
private var isRepetitiveScan = false
private var numberOfSSID = 0

private const val defaultRepetitiveDelay = 30 * 1000
private const val changeRepetitiveDelay = 3 * 30 * 1000
private var repetitiveDelay = 0
private var isChangeRepetitivedelay = false

private val apSSIDlist = listOf("Esp32_EStacion1", "Esp32_EStacion2")
private var capApList: MutableList<String> = mutableListOf()
private var repetitiveAplist : MutableList<Int> = mutableListOf()
private var nearestAp: String = ""
private var outSSID = 0

private var textnotification: String = ""

class ScannerWifiService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            ignoreBatteryOptimization()
            when (intent.action) {
                Actions.ENTER.name -> {
                    textnotification = intent.extras?.getString("Station_name").toString()
                    sendNotification("Entrada: $textnotification")
                    isGeofence = true
                    repetitiveDelay = defaultRepetitiveDelay
                    isChangeRepetitivedelay = false
                    if (!isRepetitiveScan) {
                        geofenceEnter()
                    }
                }
                Actions.DWELL.name -> {
                    sendNotification("Permanencia: $textnotification")
                    if (!isChangeRepetitivedelay) {
                        repetitiveDelay = changeRepetitiveDelay
                        isChangeRepetitivedelay = true
                    }
                }
                Actions.EXIT.name -> {
                    if (isGeofence) {
                        isGeofence = false
                        if (isChangeRepetitivedelay) {
                            repetitiveDelay = defaultRepetitiveDelay
                            isChangeRepetitivedelay = false

                            isRepetitiveScan = false
                            repetitiveScanWifi()
                        }
                        //sendNotification("Salida: $textnotification")
                    }
                }
                Actions.ENDSERVICE.name -> {
                    stopService()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // Scanner Wifi code
    private val wifiScanReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.M)
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                scanSuccess()
            }
        }
    }

    private fun sendNotification(textnotification: String?) {
        val notificationId = 420
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notification = notificationForeground(this,textnotification)
            startForeground(notificationId, notification)
        }else {
            notification(this,textnotification)
        }
    }

    @SuppressLint("ShortAlarm")
    private fun geofenceEnter() {
        initializeWLocks()
        initializeScanWifi()
        repetitiveScanWifi()
    }

    @SuppressLint("BatteryLife")
    private fun ignoreBatteryOptimization() {
        //https://stackoverflow.com/questions/32316491/network-access-in-doze-mode
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

    private fun initializeWLocks() {
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag").apply {
                    acquire(10 * 60 * 1000L /*10 minutes*/)
                }
            }
    }

    private fun startWLock() {
        wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/)
    }

    private fun stopWLock() {
        if (wakeLock.isHeld) wakeLock.release()
    }

    private fun initializeScanWifi() {
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        registerReceiver(wifiScanReceiver, intentFilter)
    }

    private fun repetitiveScanWifi() {
        isRepetitiveScan = true
        GlobalScope.launch(Dispatchers.IO) {
            while (isRepetitiveScan) {
                launch(Dispatchers.IO) {
                    if (!wakeLock.isHeld)
                        startWLock()
                    startWifiScan()
                }
                delay(repetitiveDelay.toLong())
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun startWifiScan () {
        var ready = true
        val myContext: Context = this
        var messageScan = "Búsqueda activa de Wifi exitosa"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !locationManager.isLocationEnabled) {
            ready = false
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(myContext, "Es necesario tener activo el GPS", Toast.LENGTH_LONG).show()
            }
            stopService()
        }
        if (ready) {
            val success = wifiManager.startScan()
            if (!success) {
                messageScan = "Algo anda mal"
                //ignoreBatteryOptimization()
            }
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(myContext, messageScan, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun scanSuccess() {
        val results = wifiManager.scanResults

        var isSSID = false
        var numSSID = 0
        var inirssi = -200
        var apnearest = ""

        for (result in results) {
            val apssid = result.SSID
            if (apssid in apSSIDlist) {
                if (apssid in capApList) {
                    val index = capApList.indexOf(apssid)
                    val countSSID = repetitiveAplist[index] + 1
                    repetitiveAplist[index] = countSSID
                    /*if (countSSID >= 15 && !isChangeRepetitivedelay) { // ~= 465 s = 7,75 min
                        repetitiveDelay = changeRepetitiveDelay
                        isChangeRepetitivedelay = true
                    }*/
                }else {
                    capApList.add(apssid)
                    repetitiveAplist.add(0)
                }
                val rssi = result.level
                if (rssi > inirssi) {
                    inirssi = rssi
                    apnearest = apssid
                }
                numSSID += 1
            }
        }

        if (numSSID  >  0) {
            isSSID = true
            if (outSSID > 0)
                outSSID = 0

            if (numSSID != numberOfSSID)
                numberOfSSID = numSSID

            if (nearestAp != apnearest)
                nearestAp = apnearest
        }

        // Si no está en el geovallado y no tiene SSID de la lista
        if (!isGeofence and !isSSID) {
            outSSID++

            if (outSSID == 3)
                stopService()
        }
    }

    private fun stopService() {
        stopWLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true)
        }else {
            stopSelf()
            clearNotification(this)
        }
        isRepetitiveScan = false
    }

}

/*
Referencias
 * Servicio primer plano
    https://robertohuertas.com/2019/06/29/android_foreground_services/
*/
