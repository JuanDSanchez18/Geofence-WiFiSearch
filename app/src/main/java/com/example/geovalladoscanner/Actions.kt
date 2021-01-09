/* Aplicación GeovalladoScanner
Actions.kt
 * Define las acciones posibles, se pasan desde GeofenceBroadcastReceiver.kt
 al servicio ScannerWifiService.
*/
package com.example.geovalladoscanner

enum class Actions {
    ENTER,
    DWELL,
    EXIT,
    ENDSERVICE
}