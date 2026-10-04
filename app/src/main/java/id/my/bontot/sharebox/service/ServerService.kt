package id.my.bontot.sharebox.service

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
import id.my.bontot.sharebox.App
import id.my.bontot.sharebox.MainActivity
import id.my.bontot.sharebox.R
import id.my.bontot.sharebox.core.ServerController
import id.my.bontot.sharebox.hotspot.HotspotManager
import id.my.bontot.sharebox.net.DirectServer
import id.my.bontot.sharebox.net.PeerDiscovery

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

    /**
     * Dua mode independen di service yang sama: web server HTTP (PC/browser) dan penerima
     * Direct (HP ke HP). Masing-masing dinyalakan/dimatikan sendiri; service berhenti kalau
     * keduanya mati.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mode = intent?.getStringExtra(EXTRA_MODE)
        if (intent?.action == ACTION_STOP) {
            if (mode != MODE_DIRECT) ServerController.stop()
            if (mode != MODE_WEB) stopDirect()
            if (!ServerController.status().running && !DirectServer.running) {
                stopSelf()
                return START_NOT_STICKY
            }
        } else {
            if (mode == MODE_DIRECT) DirectServer.start(this) else ServerController.start()
        }
        startForeground(App.NOTIF_SERVER, buildNotification())
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, 15_000L)
        return START_STICKY
    }

    private fun stopDirect() {
        DirectServer.stop()
        // Hotspot yang dinyalakan otomatis oleh Receive ikut mati; hotspot manual dibiarkan.
        if (HotspotManager.autoStarted) HotspotManager.stop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        ServerController.stop()
        DirectServer.stop()
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
        val web = ServerController.status().running
        val text = when {
            web && DirectServer.running -> getString(R.string.notif_both)
            DirectServer.running -> getString(R.string.notif_receiving)
            else -> ServerController.urls().firstOrNull()?.url ?: "http://…:${ServerController.status().port}/"
        }
        return Notification.Builder(this, App.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_share)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
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
        const val ACTION_START = "id.my.bontot.sharebox.START"
        const val ACTION_STOP = "id.my.bontot.sharebox.STOP"
        const val EXTRA_MODE = "mode"
        const val MODE_WEB = "web"
        const val MODE_DIRECT = "direct"
    }
}
