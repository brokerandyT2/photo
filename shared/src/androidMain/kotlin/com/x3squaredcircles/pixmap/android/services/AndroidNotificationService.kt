//shared/src/androidMain/kotlin/com/x3squaredcircles/pixmap/android/services/AndroidNotificationService.kt
package com.x3squaredcircles.pixmap.android.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.x3squaredcircles.pixmap.shared.services.INotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android implementation of notification service
 */
class AndroidNotificationService(
    private val context: Context
) : INotificationService {

    companion object {
        private const val CHANNEL_ID = "pixmap_notifications"
        private const val CHANNEL_NAME = "PixMap Notifications"
        private const val CHANNEL_DESCRIPTION = "Notifications for PixMap app"
    }

    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannel()
    }

    override suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    override suspend fun showNotification(title: String, message: String, id: Int) {
        if (!hasPermission()) {
            return
        }

        withContext(Dispatchers.IO) {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_info) // Using system icon instead of R.drawable
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(id, notification)
        }
    }

    override suspend fun cancelNotification(id: Int) {
        withContext(Dispatchers.IO) {
            notificationManager.cancel(id)
        }
    }

    override suspend fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    override suspend fun requestPermission(): Boolean {
        return hasPermission()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}