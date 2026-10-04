package com.ahmadarif.sharebox.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.ahmadarif.sharebox.MainActivity
import com.ahmadarif.sharebox.R
import com.ahmadarif.sharebox.core.Prefs
import com.ahmadarif.sharebox.core.ServerController
import com.ahmadarif.sharebox.core.Storage
import com.ahmadarif.sharebox.hotspot.HotspotManager
import com.ahmadarif.sharebox.net.PeerDiscovery
import com.ahmadarif.sharebox.net.Qr
import com.ahmadarif.sharebox.service.ServerService

class HomeTab(activity: MainActivity) : BaseTab(activity) {

    override fun layoutId(): Int = R.layout.tab_home

    private val tvDot: TextView = root.findViewById(R.id.tv_dot)
    private val tvStatus: TextView = root.findViewById(R.id.tv_status)
    private val tvDevname: TextView = root.findViewById(R.id.tv_devname)
    private val tvUrl: TextView = root.findViewById(R.id.tv_url)
    private val tvHint: TextView = root.findViewById(R.id.tv_hint)
    private val imgWifiQr: ImageView = root.findViewById(R.id.img_wifi_qr)
    private val tvWifiQrHint: TextView = root.findViewById(R.id.tv_wifi_qr_hint)
    private val btnShowPass: TextView = root.findViewById(R.id.btn_show_pass)
    private val btnServer: View = root.findViewById(R.id.btn_server)
    private val btnServerIcon: ImageView = root.findViewById(R.id.btn_server_icon)
    private val btnServerText: TextView = root.findViewById(R.id.btn_server_text)
    private val btnCopy: View = root.findViewById(R.id.btn_copy)
    private val tvHotspot: TextView = root.findViewById(R.id.tv_hotspot)
    private val btnHotspot: View = root.findViewById(R.id.btn_hotspot)
    private val btnHotspotText: TextView = root.findViewById(R.id.btn_hotspot_text)
    private val btnRefreshPeers: View = root.findViewById(R.id.btn_refresh_peers)
    private val tvPeersEmpty: TextView = root.findViewById(R.id.tv_peers_empty)
    private val peersContainer: LinearLayout = root.findViewById(R.id.peers_container)
    private val btnInbox: View = root.findViewById(R.id.btn_inbox)
    private val btnHowto: View = root.findViewById(R.id.btn_howto)

    private val handler = Handler(Looper.getMainLooper())
    private var visible = false
    private var lastWifiQrKey: String? = null
    private var passShown = false
    private var lastPeersKey: String = ""

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            if (visible) handler.postDelayed(this, 1500L)
        }
    }

    init {
        btnServer.setOnClickListener { toggleServer() }
        btnCopy.setOnClickListener {
            val url = ServerController.urls().firstOrNull()?.url
            if (url != null) Ui.copy(act, url) else Ui.toast(act, act.getString(R.string.no_url))
        }
        btnHotspot.setOnClickListener { toggleHotspot() }
        btnShowPass.setOnClickListener {
            passShown = !passShown
            refresh()
        }
        btnRefreshPeers.setOnClickListener {
            refresh()
            Ui.toast(act, act.getString(R.string.refresh))
        }
        btnInbox.setOnClickListener {
            act.filesTab.openPath(Storage.inbox(act))
            act.select(1)
        }
        btnHowto.setOnClickListener {
            Ui.alert(act, act.getString(R.string.howto_title), act.getString(R.string.howto_text))
        }
    }

    override fun onShow() {
        visible = true
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        maybeAskStorage()
    }

    /** Tanya sekali saja: app ini perlu akses semua file untuk menampilkan & mengirim file. */
    private fun maybeAskStorage() {
        if (Storage.hasAllFiles(act) || Prefs.storageAsked) return
        Prefs.storageAsked = true
        AlertDialog.Builder(act)
            .setTitle(R.string.storage_prompt_title)
            .setMessage(R.string.storage_prompt_msg)
            .setPositiveButton(R.string.storage_prompt_grant) { _, _ -> Ui.grantAllFiles(act) }
            .setNegativeButton(R.string.storage_prompt_later, null)
            .show()
    }

    override fun onHide() {
        visible = false
        handler.removeCallbacks(ticker)
    }

    override fun onPermissionsResult(requestCode: Int, grantResults: IntArray) {
        when (requestCode) {
            Req.NOTIF -> startServerNow()
            Req.HOTSPOT -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startHotspotNow()
                else Ui.toast(act, act.getString(R.string.hotspot_failed, "izin ditolak"))
            }
        }
    }

    // ---------- server ----------

    private fun toggleServer() {
        if (ServerController.status().running) {
            act.startService(Intent(act, ServerService::class.java).setAction(ServerService.ACTION_STOP))
        } else if (Build.VERSION.SDK_INT >= 33 &&
            act.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            act.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), Req.NOTIF)
        } else {
            startServerNow()
        }
        handler.postDelayed({ refresh() }, 400L)
    }

    private fun startServerNow() {
        val intent = Intent(act, ServerService::class.java).setAction(ServerService.ACTION_START)
        act.startForegroundService(intent)
        handler.postDelayed({ refresh() }, 600L)
    }

    // ---------- hotspot ----------

    private fun toggleHotspot() {
        if (HotspotManager.running) {
            HotspotManager.stop()
            refresh()
            return
        }
        val perm = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (act.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            act.requestPermissions(arrayOf(perm), Req.HOTSPOT)
        } else {
            startHotspotNow()
        }
    }

    private fun startHotspotNow() {
        Ui.toast(act, "Starting hotspot…")
        HotspotManager.start(act) { info, err ->
            if (info != null) {
                Ui.toast(act, "Hotspot active — ${info.ssid} / ${info.pass}")
            } else {
                Ui.toast(act, act.getString(R.string.hotspot_failed, err ?: "?"))
            }
            handler.postDelayed({ refresh() }, 1500L)
        }
    }

    // ---------- render ----------

    private fun refresh() {
        val st = ServerController.status()
        tvStatus.setText(if (st.running) R.string.server_on else R.string.server_off)
        tvDot.setTextColor(act.getColor(if (st.running) R.color.ok else R.color.muted))
        btnServerText.setText(if (st.running) R.string.btn_stop else R.string.btn_start)
        btnServerIcon.setImageResource(if (st.running) R.drawable.ic_close else R.drawable.ic_send)
        tvDevname.text = Prefs.displayName()

        val urls = if (st.running) ServerController.urls() else emptyList()
        // Kalau hotspot sedang jalan, utamakan alamat interface AP-nya: device yang baru
        // menyambung ke hotspot tidak bisa memakai IP Wi-Fi rumah.
        val primary = if (HotspotManager.running) {
            urls.firstOrNull { it.label.startsWith("ap") || it.label.startsWith("swlan") || it.label.startsWith("wlan1") }?.url
                ?: urls.firstOrNull()?.url
        } else {
            urls.firstOrNull()?.url
        }
        tvUrl.text = primary ?: "—"
        tvHint.setText(if (primary != null) R.string.scan_qr else R.string.no_url)

        if (primary != null) {
            tvHint.setText(R.string.scan_qr)
        } else {
            tvHint.setText(R.string.no_url)
        }

        val hot = HotspotManager.currentInfo()
        if (HotspotManager.running && hot != null) {
            // Nama & sandi tidak ditulis di layar — QR sudah cukup untuk menyambung.
            // Toggle kecil disediakan buat device tanpa kamera (PC) yang perlu manual.
            tvHotspot.text = "SSID: ${hot.ssid}   •   Password: ${hot.pass.ifBlank { "-" }}"
            tvHotspot.visibility = if (passShown) View.VISIBLE else View.GONE
            btnHotspotText.setText(R.string.hotspot_stop)
            imgWifiQr.visibility = View.VISIBLE
            tvWifiQrHint.visibility = View.VISIBLE
            btnShowPass.visibility = View.VISIBLE
            btnShowPass.setText(if (passShown) R.string.hotspot_hide_pass else R.string.hotspot_show_pass)
            val key = hot.ssid + "\u0000" + hot.pass
            if (key != lastWifiQrKey) {
                lastWifiQrKey = key
                generateWifiQr(hot.ssid, hot.pass)
            }
        } else {
            tvHotspot.setText(R.string.hotspot_desc)
            tvHotspot.visibility = View.VISIBLE
            btnHotspotText.setText(R.string.hotspot_start)
            imgWifiQr.visibility = View.GONE
            tvWifiQrHint.visibility = View.GONE
            btnShowPass.visibility = View.GONE
            lastWifiQrKey = null
            passShown = false
        }

        renderPeers()
    }

    private fun generateWifiQr(ssid: String, pass: String) {
        Thread({
            val bmp = runCatching { Qr.wifiBitmap(ssid, pass, 512) }.getOrNull()
            if (bmp != null) {
                handler.post {
                    if (lastWifiQrKey != null) imgWifiQr.setImageBitmap(bmp)
                }
            }
        }, "sharebox-wifiqr").apply { isDaemon = true }.start()
    }

    private fun renderPeers() {
        val peers = PeerDiscovery.list()
        val key = peers.joinToString("|") { it.id }
        tvPeersEmpty.visibility = if (peers.isEmpty()) View.VISIBLE else View.GONE
        if (key == lastPeersKey) return
        lastPeersKey = key
        peersContainer.removeAllViews()
        val inflater = LayoutInflater.from(act)
        for (peer in peers) {
            val row = inflater.inflate(R.layout.row_peer, peersContainer, false)
            row.findViewById<TextView>(R.id.tv_name).text = peer.name
            row.findViewById<TextView>(R.id.tv_sub).text = peer.host
            row.findViewById<View>(R.id.btn_send).setOnClickListener { act.sendTo(peer) }
            peersContainer.addView(row)
        }
    }
}
