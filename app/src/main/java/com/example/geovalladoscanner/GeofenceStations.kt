package com.example.geovalladoscanner

import com.google.android.gms.maps.model.LatLng
import java.util.concurrent.TimeUnit

data class StationDataObject(val key: String,  val latLong: LatLng)

internal object GeofenceConstants {

    val Station_TM = arrayOf(

        /*StationDataObject(
            "Calle45",
            LatLng(5.7541223, -75.0985158)
        ),

        StationDataObject(
            "Marly",
            LatLng(5.7541223, -75.0985158)
        ),

        StationDataObject(
            "Calle 22",
            LatLng(4.8541223,
            -76.0985158)
        ),

        StationDataObject(
            "casa2",
            LatLng(4.7341223,
            -73.0985158)
        ),

        */
        StationDataObject(
            "Casa",
            LatLng(4.7541223,
            -74.0985158)
        )

    )

    val GEOFENCE_EXPIRATION_IN_MILLISECONDS: Long = TimeUnit.HOURS.toMillis(1)
    const val GEOFENCE_DWELL_TIME = 10 * 60 * 1000 // 8 minutes
    const val GEOFENCE_RADIUS_IN_METERS = 50f // 50 meters
}