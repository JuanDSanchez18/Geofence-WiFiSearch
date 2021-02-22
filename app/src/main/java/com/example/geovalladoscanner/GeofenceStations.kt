/* Aplicación GeovalladoScanner
GeofenceStations.kt
* Define latitud y longitud de cada geovalaldo (estación de TM), el radio para todos y el tiempo
 considerado como permanenecia para la trancisión.
*/
package com.example.geovalladoscanner

import com.google.android.gms.maps.model.LatLng
import java.util.concurrent.TimeUnit

data class StationDataObject(val key: String,  val latLong: LatLng)

internal object GeofenceConstants {

    val Station_TM = arrayOf(

        StationDataObject(
            "Casa",
            LatLng(4.7541223,
            -74.0985158)
        ),
        StationDataObject(
            "Estación1",
            LatLng(4.6272117,
            -74.0640529)
        ),

        StationDataObject(
            "Estación2",
            LatLng(5.7541223,
            -75.0985158)
        )
    )

    val GEOFENCE_EXPIRATION_IN_MILLISECONDS: Long = TimeUnit.HOURS.toMillis(1)
    const val GEOFENCE_DWELL_TIME = 5 * 60 * 1000 // 5 minutes
    const val GEOFENCE_RADIUS_IN_METERS = 60f // 60 meters
}