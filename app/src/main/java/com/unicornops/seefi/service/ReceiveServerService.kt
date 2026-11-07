package com.unicornops.seefi.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.unicornops.seefi.R
import com.unicornops.seefi.SeefiApplication
import com.unicornops.seefi.ui.MainActivity
import com.unicornops.seefi.protocol.CredentialCalculator
import com.unicornops.seefi.protocol.EyeFiServer
import com.unicornops.seefi.protocol.SoapMessages
import com.unicornops.seefi.protocol.TarExtractor

/**
 * Foreground service hosting the EyeFi protocol server on port 59278.
 *
 * The server must stay alive while the card transfers photos, so the
 * service promotes itself to a foreground service with a dataSync type
 * and shows a notification with the number of photos received.
 */
class ReceiveServerService : Service() {

    companion object {
        const val SERVER_PORT = 59278
        const val CHANNEL_ID = "seefi_receive"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.unicornops.seefi.action.START"
        const val ACTION_STOP = "com.unicornops.seefi.action.STOP"

        @Volatile var isServerRunning: Boolean = false
            private set
    }

    private var server: EyeFiServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopServer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startServer()
        }
        return START_STICKY
    }

    private fun startServer() {
        if (server?.running == true) return

        val container = (application as SeefiApplication).container
        val snapshot = container.serverState.cardsSnapshot()
        val keysByMac = buildMap {
            snapshot.forEach { card -> put(card.macAddress.lowercase(), card.uploadKey) }
        }

        val newServer = EyeFiServer(
            port = SERVER_PORT,
            uploadKeyForMac = { mac ->
                keysByMac[mac.lowercase()] ?: CredentialCalculator.MOBI_KEY
            },
            onUpload = { tarData, soapEnvelope ->
                val filename = SoapMessages.extractXmlValue(soapEnvelope, "filename") ?: "photo"
                val entries = TarExtractor.extractImageEntries(tarData)
                var saved = 0
                for (entry in entries) {
                    if (container.photoStore.savePhoto(entry.name, entry.data) != null) {
                        saved++
                    }
                }
                if (saved > 0) {
                    container.serverState.onPhotosSaved(saved, filename)
                    updateNotification(container.serverState.photosReceived.value)
                }
                saved
            },
        )

        try {
            newServer.start()
            server = newServer
            isServerRunning = true
            container.serverState.onServerStarted(SERVER_PORT)
            startAsForeground()
        } catch (failure: Exception) {
            container.serverState.onServerError(failure.message ?: "Failed to start server")
            stopSelf()
        }
    }

    private fun startAsForeground() {
        val notification = buildNotification(
            (application as SeefiApplication).container.serverState.photosReceived.value,
        )
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun stopServer() {
        server?.stop()
        server = null
        isServerRunning = false
        (application as SeefiApplication).container.serverState.onServerStopped()
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Notification

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(photoCount: Int): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ReceiveServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_seefi)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(
                resources.getQuantityString(
                    R.plurals.notification_text,
                    photoCount,
                    photoCount,
                    SERVER_PORT,
                ),
            )
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_stat_seefi, getString(R.string.notification_stop), stopIntent)
            .build()
    }

    private fun updateNotification(photoCount: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(photoCount))
    }
}
