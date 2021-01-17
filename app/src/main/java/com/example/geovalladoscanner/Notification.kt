/* Aplicación GeovalladoScanner
Notification.kt
 * Crea canal de notificaiones, desde MainActivity.kt
 * Eastilo de notificación.
 * Envía notificaciones según version de android desde ScannerWifiService.kt
*/
package com.example.geovalladoscanner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat


fun createNotificationChannel(context: Context) {
    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is new and not in the support library
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channelId = "GeofenceChannel"
        val name = "STUTM"
        val descriptionText = "Channel description"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, name, importance).let {
            it.description = descriptionText
            it.enableLights(true)
            it.lightColor = Color.RED
            it.enableVibration(true)
            it.vibrationPattern = longArrayOf(400, 200, 300, 200, 300)
            it
        }

        // Register the channel with the system
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}

fun notification(context: Context, textnotification: String?) {

    // Create an explicit intent for an Activity in your app
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, 0)

    val builder = NotificationCompat.Builder(context, channelId)

        //https://walkiriaapps.com/blog/android/iconos-notificaciones-android-studio/
        .setSmallIcon(R.drawable.geofence_icon)
        .setContentTitle("Activación geovallado")
        .setContentText(textnotification)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setVibrate(longArrayOf(400, 200, 300, 200, 300, 200, 300))

        // Set the intent that will fire when the user taps the notification
        .setContentIntent(pendingIntent)

    with(NotificationManagerCompat.from(context)) {
        // notificationId is a unique int for each notification that you must define
        notify(notificationId, builder.build())
    }
}

fun clearNotification(context: Context) {
    NotificationManagerCompat.from(context).cancel(notificationId)
}

@RequiresApi(Build.VERSION_CODES.O)
fun notificationForeground(context: Context, textnotification: String?): Notification {

    val pendingIntent: PendingIntent =
        Intent(context, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(context, 0, notificationIntent, 0)
        }

    return Notification.Builder(context, channelId)
        .setSmallIcon(R.drawable.geofence_icon)
        .setColor(Color.YELLOW)
        .setShowWhen(true)
        .setContentTitle("Activación geovallado")
        .setContentText(textnotification)
        .setContentIntent(pendingIntent)
        .setTicker("Ticket Text")
        .build()
}

private const val channelId  = "GeofenceChannel"
private const val notificationId = 420
