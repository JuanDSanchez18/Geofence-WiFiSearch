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
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


private val runningOOrLater =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

private lateinit var aContext: Context

private lateinit var wifiManager: WifiManager
private lateinit var wakeLock: PowerManager.WakeLock
private lateinit var wifiLock: WifiManager.WifiLock

private lateinit var locationManager: LocationManager

private var isGeofence = false
private var isRepetitiveScan = false
private var isSSID = false
private var numberOfSSID = 0

private var repetitiveDelay = 40 * 1000
private var isChangeRepetitivedelay = false

private val apSSIDlist = listOf("Esp32_Calle45","Esp32_Marly" ,"Network 25","2","3")
private val capApList: MutableList<String> = mutableListOf()
private val repetitiveAplist : MutableList<Int> = mutableListOf()
private var countSSID = 0
private var outSSID = 0

class ScannerWifiService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {

            when (intent.action) {
                Actions.ENTER.name -> {
                    isGeofence = true
                    repetitiveDelay = 40 * 1000
                    if (!isRepetitiveScan){
                        geofenceEnter()
                    }else if (isChangeRepetitivedelay){
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

    override fun onCreate() {
        super.onCreate()
        val notificationId = 420
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notification = notificationForeground(this)
            startForeground(notificationId, notification)
        }

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

    @SuppressLint("ShortAlarm")
    private fun geofenceEnter() {
        ignoreBatteryOptimization()
        initializeWLocks()
        startWLock()
        initializeScanWifi()
        repetitiveScanWifi()
    }
    @SuppressLint("BatteryLife")
    private fun ignoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent()
            val packageName: String = packageName
            val pm =
                getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
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
        if (wifiManager.isWifiEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (!locationManager.isLocationEnabled) {
                    ready = false
                    //Toast.makeText(applicationContext, "Required GPS", Toast.LENGTH_SHORT).show()
                    stopService()
                    //not work, may be applicationContext
                    //Toast.makeText(applicationContext, "I'm a toast!", Toast.LENGTH_SHORT).show()
                }
            }
            if (ready) {
                val success = wifiManager.startScan()
                if (!success) {
                    // scan failure handling
                    Toast.makeText(applicationContext, "Something wrong", Toast.LENGTH_SHORT).show()
                    //not work
                }
            }
        }
    }

    private fun scanSuccess() {
        isSSID = false
        var numSSID = 0
        val results = wifiManager.scanResults
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
                numSSID += 1
            }
        }

        if (numSSID  >  0) {
            isSSID = true
            outSSID = 0
            if (numSSID != numberOfSSID) {
                numberOfSSID = numSSID
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