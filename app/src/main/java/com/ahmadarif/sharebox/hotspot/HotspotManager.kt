package com.ahmadarif.sharebox.hotspot

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper

object HotspotManager {

    data class Info(val ssid: String, val pass: String)

    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    val running: Boolean
        get() = reservation != null

    fun currentInfo(): Info? {
        val res = reservation ?: return null
        return if (Build.VERSION.SDK_INT >= 30) {
            val cfg = res.softApConfiguration
            Info(cfg.ssid ?: "ShareBox", cfg.passphrase ?: "")
        } else {
            @Suppress("DEPRECATION")
            val cfg = res.wifiConfiguration
            @Suppress("DEPRECATION")
            Info(cfg?.SSID ?: "ShareBox", cfg?.preSharedKey ?: "")
        }
    }

    fun start(ctx: Context, onResult: (Info?, String?) -> Unit) {
        if (reservation != null) {
            onResult(currentInfo(), null)
            return
        }
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        try {
            wm.startLocalOnlyHotspot(
                object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(res: WifiManager.LocalOnlyHotspotReservation) {
                        reservation = res
                        Handler(Looper.getMainLooper()).post { onResult(currentInfo(), null) }
                    }

                    override fun onFailed(reason: Int) {
                        Handler(Looper.getMainLooper()).post { onResult(null, "code $reason") }
                    }

                    override fun onStopped() {
                        reservation = null
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (e: Exception) {
            onResult(null, e.message ?: "not supported")
        }
    }

    fun stop() {
        try {
            reservation?.close()
        } catch (_: Exception) {
        }
        reservation = null
    }
}
