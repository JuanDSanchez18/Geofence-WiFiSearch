package com.example.geovalladoscanner

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.*
import android.widget.Toast
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


private lateinit var aContext: Context

private lateinit var wifiManager: WifiManager
private lateinit var wakeLock: PowerManager.WakeLock
private lateinit var wifiLock: WifiManager.WifiLock

private lateinit var locationManager: LocationManager

private var isGeofence = false
private var isRepetitiveScan = false
private var isSSID = false
private var numberOfSSID = 0

private var repetitiveDelay = 32 * 1000
private var isChangeRepetitivedelay = false

private val apSSIDlist = listOf("Esp32_Calle45", "Esp32_Marly")
private val capApList: MutableList<String> = mutableListOf()
private val repetitiveAplist : MutableList<Int> = mutableListOf()
private var nearestAp: String = ""
private var countSSID = 0
private var outSSID = 0

class ScannerWifiService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                Actions.ENTER.name -> {
                    isGeofence = true
                    repetitiveDelay = 32 * 1000
                    if (!isRepetitiveScan) {
                        sendForegroundNotification()
                        geofenceEnter()
                    } else if (isChangeRepetitivedelay) {
                        repetitiveScanWifi()
                    }
                    isChangeRepetitivedelay = false
                }
                Actions.DWELL.name -> {
                    if (!isChangeRepetitivedelay) {
                        repetitiveDelay = 2 * 60 * 1000
                        isChangeRepetitivedelay = true
                    }
                }
                Actions.EXIT.name -> {
                    if (isGeofence) {
                        isGeofence = false
                        countSSID = 0
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
        TODO("Not yet implemented")
        return null
    }

    // Scanner Wifi code

    private val wifiScanReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.M)
        override fun onReceive(context: Context, intent: Intent) {
            aContext = context
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                Toast.makeText(context, "Success Scan", Toast.LENGTH_SHORT).show()
                scanSuccess()
            }
            else Toast.makeText(context, "Fail Scan", Toast.LENGTH_SHORT).show()
        }
    }
    private fun sendForegroundNotification(){
        val notificationId = 420
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notification = notificationForeground(this)
            startForeground(notificationId, notification)
        }
    }

    @SuppressLint("ShortAlarm")
    private fun geofenceEnter() {
        initializeWLocks()
        startWLock()
        initializeScanWifi()
        repetitiveScanWifi()
    }

    private fun initializeWLocks() {
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag")
            }

        wifiLock =
            (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).run {
                createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "MyApp::MyWifilockTag")
            }
    }

    private fun startWLock() {
        wakeLock.acquire()
        wifiLock.acquire()
    }

    private fun stopWLock() {
        if (wakeLock.isHeld) wakeLock.release()
        if (wifiLock.isHeld) wifiLock.release()
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
                    if (!wakeLock.isHeld or !wifiLock.isHeld)
                        startWLock()
                    startWifiScan()
                }
                delay(repetitiveDelay.toLong())
            }
        }
    }

    private fun startWifiScan () {
        var ready = true
        val myContext: Context = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!locationManager.isLocationEnabled) {
                ready = false
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(myContext, "Required GPS", Toast.LENGTH_LONG).show()
                }
                stopService()
            }
        }
        if (ready) {
            val success = wifiManager.startScan()
            if (!success) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(myContext, "Something wrong", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun scanSuccess() {
        isSSID = false
        var numSSID = 0
        val results = wifiManager.scanResults
        var inirssi = -200
        var apnearest = ""
        for (result in results) {
            if (result.SSID in apSSIDlist) {
                if (result.SSID in capApList) {
                    val index = capApList.indexOf(result.SSID)
                    countSSID = repetitiveAplist[index] + 1
                    repetitiveAplist[index] = countSSID
                    if (countSSID == 15) { // ~= 360 s = 6 min
                        repetitiveDelay = 2 * 60 * 1000
                        isChangeRepetitivedelay = true
                    }
                }else {
                    capApList.add(result.SSID)
                    repetitiveAplist.add(0)
                }
                val rssi = result.level
                if (rssi > inirssi) {
                    inirssi = rssi
                    apnearest = result.SSID
                }
                numSSID += 1
            }
        }

        if (numSSID  >  0) {
            isSSID = true
            outSSID = 0
            if (numSSID != numberOfSSID) {
                numberOfSSID = numSSID
            }
            if (nearestAp != apnearest) {
                nearestAp = apnearest
            }
        }

        if (!isSSID and !isGeofence) {
            outSSID++
            if (isChangeRepetitivedelay) {
                repetitiveDelay = 30 * 1000
                isChangeRepetitivedelay = false
                repetitiveScanWifi()
            }
            if (outSSID == 3)  
                stopService()
        }
    }

    private fun stopService() {
        isRepetitiveScan = false
        stopWLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true)
        }else stopSelf()
    }

}

/*
References
https://robertohuertas.com/2019/06/29/android_foreground_services/

*/

//probar sin wlock