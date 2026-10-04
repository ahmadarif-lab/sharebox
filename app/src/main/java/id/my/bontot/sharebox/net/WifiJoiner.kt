package id.my.bontot.sharebox.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Menyambungkan HP pengirim ke hotspot penerima setelah scan QR, lalu mengikat proses ke
 * jaringan itu supaya socket Direct lewat sana. Android 10+: WifiNetworkSpecifier (sistem
 * menampilkan dialog konfirmasi sekali). Android 8–9: konfigurasi Wi-Fi lama.
 */
object WifiJoiner {

    private val main = Handler(Looper.getMainLooper())
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var legacyNetId = -1

    fun join(ctx: Context, ssid: String, pass: String?, onResult: (ok: Boolean, error: String?) -> Unit) {
        val app = ctx.applicationContext
        leave(app)
        if (Build.VERSION.SDK_INT >= 29) joinModern(app, ssid, pass, onResult) else joinLegacy(app, ssid, pass, onResult)
    }

    fun leave(ctx: Context) {
        val app = ctx.applicationContext
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            cm.bindProcessToNetwork(null)
        } catch (_: Exception) {
        }
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
        if (legacyNetId >= 0) {
            @Suppress("DEPRECATION")
            runCatching {
                val wm = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wm.removeNetwork(legacyNetId)
                wm.reconnect()
            }
            legacyNetId = -1
        }
    }

    private fun joinModern(ctx: Context, ssid: String, pass: String?, onResult: (Boolean, String?) -> Unit) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val spec = WifiNetworkSpecifier.Builder().setSsid(ssid).apply {
            if (!pass.isNullOrEmpty()) setWpa2Passphrase(pass)
        }.build()
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(spec)
            .build()
        var answered = false
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                cm.bindProcessToNetwork(network)
                if (!answered) {
                    answered = true
                    main.post { onResult(true, null) }
                }
            }

            override fun onUnavailable() {
                if (!answered) {
                    answered = true
                    main.post { onResult(false, "unavailable") }
                }
            }
        }
        callback = cb
        try {
            cm.requestNetwork(req, cb, 30_000)
        } catch (e: Exception) {
            callback = null
            onResult(false, e.message ?: "failed")
        }
    }

    @Suppress("DEPRECATION")
    private fun joinLegacy(ctx: Context, ssid: String, pass: String?, onResult: (Boolean, String?) -> Unit) {
        val wm = ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cfg = WifiConfiguration().apply {
            SSID = "\"$ssid\""
            if (pass.isNullOrEmpty()) {
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            } else {
                preSharedKey = "\"$pass\""
            }
        }
        val id = wm.addNetwork(cfg)
        if (id < 0) {
            onResult(false, "addNetwork failed")
            return
        }
        legacyNetId = id
        wm.disconnect()
        wm.enableNetwork(id, true)
        wm.reconnect()
        val deadline = System.currentTimeMillis() + 25_000
        val poll = object : Runnable {
            override fun run() {
                val info = wm.connectionInfo
                if (info != null && info.ssid == "\"$ssid\"" && info.ipAddress != 0) {
                    val net = cm.allNetworks.firstOrNull {
                        cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                    }
                    if (net != null) cm.bindProcessToNetwork(net)
                    onResult(true, null)
                } else if (System.currentTimeMillis() > deadline) {
                    onResult(false, "timeout")
                } else {
                    main.postDelayed(this, 500)
                }
            }
        }
        main.postDelayed(poll, 500)
    }
}
