package com.ahmadarif.sharebox.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.ahmadarif.sharebox.App
import com.ahmadarif.sharebox.MainActivity
import com.ahmadarif.sharebox.R
import com.ahmadarif.sharebox.core.ServerController
import com.ahmadarif.sharebox.hotspot.HotspotManager
import com.ahmadarif.sharebox.net.PeerDiscovery

class ServerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var wifiLock: WifiManager.WifiLock? = null

    private val ticker = object : Runnable {
        override fun run() {
            updateNotification()
            handler.postDelayed(this, 15_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        PeerDiscovery.acquire(this)
        acquireWifiLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        ServerController.start()
        startForeground(App.NOTIF_SERVER, buildNotification())
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 15_000L)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        ServerController.stop()
        HotspotManager.stop()
        PeerDiscovery.release()
        releaseWifiLock()
        super.onDestroy()
    }

    private fun acquireWifiLock() {
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "sharebox:wifi").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseWifiLock() {
        try {
            wifiLock?.release()
        } catch (_: Exception) {
        }
        wifiLock = null
    }

    private fun updateNotification() {
        try {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            nm.notify(App.NOTIF_SERVER, buildNotification())
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, ServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val url = ServerController.urls().firstOrNull()?.url ?: "http://…:${ServerController.status().port}/"
        return Notification.Builder(this, App.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_share)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(url)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_stat_share),
                    getString(R.string.notif_stop),
                    stopIntent
                ).build()
            )
            .build()
    }

    companion object {
        const val ACTION_START = "com.ahmadarif.sharebox.START"
        const val ACTION_STOP = "com.ahmadarif.sharebox.STOP"
    }
}
