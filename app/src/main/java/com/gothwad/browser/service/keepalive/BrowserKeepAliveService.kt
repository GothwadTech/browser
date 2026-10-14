package com.gothwad.browser.service.keepalive

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gothwad.browser.AppContext
import com.gothwad.browser.R
import com.gothwad.browser.activity.main.MainActivity

/**
 * Lightweight foreground service that elevates process priority when the app is backgrounded
 * (e.g. user presses Home on TV remote). This prevents the Android TV OS from prematurely
 * killing the browser process and discarding active tabs and web session state.
 */
class BrowserKeepAliveService : Service() {

    companion object {
        private const val TAG = "BrowserKeepAliveService"
        const val CHANNEL_ID_KEEPALIVE = "browser_keepalive_channel"
        const val NOTIFICATION_ID = 202401

        const val ACTION_START = "com.gothwad.browser.service.keepalive.ACTION_START"
        const val ACTION_STOP = "com.gothwad.browser.service.keepalive.ACTION_STOP"

        fun start(context: Context) {
            val config = AppContext.provideConfig()
            if (!config.keepAliveInBackground.value) {
                return
            }
            val intent = Intent(context, BrowserKeepAliveService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start keep-alive service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BrowserKeepAliveService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                try {
                    context.stopService(Intent(context, BrowserKeepAliveService::class.java))
                } catch (ignored: Exception) {}
            }
        }
    }

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): BrowserKeepAliveService = this@BrowserKeepAliveService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        val config = AppContext.provideConfig()
        if (!config.keepAliveInBackground.value) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundWithNotification()
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildKeepAliveNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground keep-alive service", e)
        }
    }

    private fun buildKeepAliveNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_KEEPALIVE)
            .setSmallIcon(R.drawable.ic_stat_network)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.background_keepalive_desc))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_KEEPALIVE,
                getString(R.string.background_keepalive_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.background_keepalive_channel_desc)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping foreground: ${e.message}")
        }
    }

    override fun onDestroy() {
        stopForegroundCompat()
        super.onDestroy()
    }
}
