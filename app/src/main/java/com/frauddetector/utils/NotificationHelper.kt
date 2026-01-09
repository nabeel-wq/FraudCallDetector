package com.frauddetector.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.frauddetector.R

/**
 * Helper for creating and managing notifications.
 */
class NotificationHelper(private val context: Context) {
    
    companion object {
        private const val CHANNEL_ID_SCAM_ALERT = "scam_alert"
        private const val CHANNEL_ID_CALL_STATUS = "call_status"
        private const val NOTIFICATION_ID_SCAM = 1001
        private const val NOTIFICATION_ID_CALL = 1002
    }
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    init {
        createNotificationChannels()
    }
    
    /**
     * Create notification channels (required for Android O+).
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Scam alert channel
            val scamChannel = NotificationChannel(
                CHANNEL_ID_SCAM_ALERT,
                "Scam Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for detected scam calls"
                enableVibration(true)
            }
            
            // Call status channel
            val callChannel = NotificationChannel(
                CHANNEL_ID_CALL_STATUS,
                "Call Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing call analysis status"
            }
            
            notificationManager.createNotificationChannel(scamChannel)
            notificationManager.createNotificationChannel(callChannel)
        }
    }
    
    /**
     * Show notification for detected scam call.
     */
    fun showScamAlert(callerNumber: String, confidence: Float, category: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_SCAM_ALERT)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle("Scam Call Blocked")
            .setContentText("$category detected from $callerNumber (${(confidence * 100).toInt()}% confidence)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_SCAM, notification)
    }
    
    /**
     * Show notification for legitimate call.
     */
    fun showLegitimateCallAlert(callerNumber: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CALL_STATUS)
            .setSmallIcon(R.drawable.ic_phone)
            .setContentTitle("Incoming Call")
            .setContentText("Call from $callerNumber appears legitimate")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_CALL, notification)
    }
    
    /**
     * Show ongoing call analysis notification.
     */
    fun showAnalysisNotification(): android.app.Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID_CALL_STATUS)
            .setSmallIcon(R.drawable.ic_phone)
            .setContentTitle("Analyzing Call")
            .setContentText("Checking for scam indicators...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
