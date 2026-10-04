package com.ahmadarif.sharebox.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.ahmadarif.sharebox.MainActivity
import com.ahmadarif.sharebox.R
import com.ahmadarif.sharebox.core.Prefs
import com.ahmadarif.sharebox.core.ServerController
import com.ahmadarif.sharebox.core.Storage
import com.ahmadarif.sharebox.core.Updater
import com.ahmadarif.sharebox.core.TransferDir
import com.ahmadarif.sharebox.core.TransferTracker
import com.ahmadarif.sharebox.files.FileRepo
import com.ahmadarif.sharebox.hotspot.HotspotManager
import com.ahmadarif.sharebox.net.Direct
import com.ahmadarif.sharebox.net.DirectServer
import com.ahmadarif.sharebox.net.JoinPayload
import com.ahmadarif.sharebox.net.NetInfo
import com.ahmadarif.sharebox.net.Pairing
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
    private val btnRoleReceive: View = root.findViewById(R.id.btn_role_receive)
    private val btnRoleSend: View = root.findViewById(R.id.btn_role_send)
    private val btnWeb: View = root.findViewById(R.id.btn_web)
    private val tvWebState: TextView = root.findViewById(R.id.tv_web_state)
    private val headerBrand: View = root.findViewById(R.id.header_brand)
    private val barPage: View = root.findViewById(R.id.bar_page)
    private val btnBack: View = root.findViewById(R.id.btn_back)
    private val tvPageTitle: TextView = root.findViewById(R.id.tv_page_title)
    private val stampPage: View = root.findViewById(R.id.stamp_page)
    private val tvDotPage: TextView = root.findViewById(R.id.tv_dot_page)
    private val tvStatusPage: TextView = root.findViewById(R.id.tv_status_page)
    private val pageHome: View = root.findViewById(R.id.page_home)
    private val panelReceive: View = root.findViewById(R.id.panel_receive)
    private val panelSend: View = root.findViewById(R.id.panel_send)
    private val panelWeb: View = root.findViewById(R.id.panel_web)
    private val cardHotspot: View = root.findViewById(R.id.card_hotspot)
    private val tvRecvTitle: TextView = root.findViewById(R.id.tv_recv_title)
    private val tvRecvSub: TextView = root.findViewById(R.id.tv_recv_sub)
    private val radarRecv: RadarView = root.findViewById(R.id.radar_recv)
    private val radarSend: RadarView = root.findViewById(R.id.radar_send)
    private val radarWeb: RadarView = root.findViewById(R.id.radar_web)
    private val tvWebTitle: TextView = root.findViewById(R.id.tv_web_title)
    private val tvWebSub: TextView = root.findViewById(R.id.tv_web_sub)
    private val btnWebServer: View = root.findViewById(R.id.btn_web_server)
    private val btnWebServerIcon: ImageView = root.findViewById(R.id.btn_web_server_icon)
    private val btnWebServerText: TextView = root.findViewById(R.id.btn_web_server_text)
    private val stamp: View = root.findViewById(R.id.stamp)
    private val imgRefresh: View = root.findViewById(R.id.img_refresh)
    private val recvActivity: LinearLayout = root.findViewById(R.id.recv_activity)
    private val tvRecvEmpty: TextView = root.findViewById(R.id.tv_recv_empty)
    private val sendActivity: LinearLayout = root.findViewById(R.id.send_activity)
    private val tvSendEmpty: TextView = root.findViewById(R.id.tv_send_empty)
    private val cardRecvQr: View = root.findViewById(R.id.card_recv_qr)
    private val imgRecvQr: ImageView = root.findViewById(R.id.img_recv_qr)
    private val tvRecvQrHint: TextView = root.findViewById(R.id.tv_recv_qr_hint)
    private val cardUpdate: View = root.findViewById(R.id.card_update)
    private val tvUpdateSub: TextView = root.findViewById(R.id.tv_update_sub)
    private val catGrid: LinearLayout = root.findViewById(R.id.cat_grid)
    private val cardRecent: View = root.findViewById(R.id.card_recent)
    private val recentActivity: LinearLayout = root.findViewById(R.id.recent_activity)

    /** Halaman di dalam tab Home: landing dengan tiga pintu masuk + satu halaman per mode. */
    private enum class Page { HOME, SEND, RECEIVE, WEB }

    private var page = Page.HOME

    private val handler = Handler(Looper.getMainLooper())
    private var visible = false
    private var lastWifiQrKey: String? = null
    private var passShown = false
    private var lastPeersKey: String = ""
    private var scanning = false
    private var pendingMode: String = ServerService.MODE_WEB
    private var lastRecvQrKey: String? = null
    private var spin: ObjectAnimator? = null

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            if (visible) handler.postDelayed(this, 1500L)
        }
    }

    init {
        btnServer.setOnClickListener { toggleMode(ServerService.MODE_DIRECT) }
        btnCopy.setOnClickListener {
            val url = ServerController.urls().firstOrNull()?.url
            if (url != null) Ui.copy(act, url) else Ui.toast(act, act.getString(R.string.no_url))
        }
        btnHotspot.setOnClickListener { toggleHotspot() }
        btnShowPass.setOnClickListener {
            passShown = !passShown
            refresh()
        }
        btnRefreshPeers.setOnClickListener { startScan() }
        btnRoleReceive.setOnClickListener { openPage(Page.RECEIVE) }
        btnRoleSend.setOnClickListener { openPage(Page.SEND) }
        btnWeb.setOnClickListener { openPage(Page.WEB) }
        btnBack.setOnClickListener { openPage(Page.HOME) }
        buildCategoryGrid()
        root.findViewById<View>(R.id.btn_update_later).setOnClickListener {
            Updater.pending()?.let { Updater.dismiss(it) }
            renderUpdate()
        }
        root.findViewById<View>(R.id.btn_update_download).setOnClickListener {
            Updater.pending()?.let {
                try {
                    act.startActivity(Intent(Intent.ACTION_VIEW, it.link))
                } catch (_: Exception) {
                    Ui.toast(act, act.getString(R.string.no_app_open))
                }
            }
        }
        btnWebServer.setOnClickListener { toggleMode(ServerService.MODE_WEB) }
    }

    /**
     * Navigasi antar halaman. Receive & Web server memakai server yang sama, jadi masuk ke
     * salah satunya langsung menyalakan server (dengan izin notifikasi dulu di Android 13+).
     * Send memulai scan device sekitar.
     */
    private fun openPage(next: Page) {
        page = next
        when (next) {
            Page.RECEIVE -> {
                startMode(ServerService.MODE_DIRECT)
                startHotspot(auto = true)
            }
            Page.WEB -> startMode(ServerService.MODE_WEB)
            Page.SEND -> startScan()
            Page.HOME -> stopScanSpin()
        }
        paintPage()
        refresh()
        // Tunggu layout halaman baru selesai, baru kembali ke atas.
        root.post { (root as android.widget.ScrollView).scrollTo(0, 0) }
    }

    private class CatTile(val labelRes: Int, val iconRes: Int, val bg: Int, val ink: Int, val cat: FileRepo.Cat?)

    /** Kisi 3×2: Apps, Photos, Videos, Music, Documents, Files — tiap tile membuka Files dalam mode pilih. */
    private fun buildCategoryGrid() {
        val tiles = listOf(
            CatTile(R.string.cat_apps, R.drawable.ic_apk, R.drawable.bg_icon_card, R.color.accent, FileRepo.Cat.APPS),
            CatTile(R.string.cat_images, R.drawable.ic_image, R.drawable.bg_icon_card, R.color.accent, FileRepo.Cat.IMAGES),
            CatTile(R.string.cat_videos, R.drawable.ic_video, R.drawable.bg_icon_card, R.color.accent, FileRepo.Cat.VIDEOS),
            CatTile(R.string.cat_audio, R.drawable.ic_music, R.drawable.bg_icon_card, R.color.accent, FileRepo.Cat.AUDIO),
            CatTile(R.string.cat_docs, R.drawable.ic_doc, R.drawable.bg_icon_card, R.color.accent, FileRepo.Cat.DOCS),
            CatTile(R.string.cat_files, R.drawable.ic_folder, R.drawable.bg_icon_card, R.color.accent, null),
        )
        val inflater = LayoutInflater.from(act)
        tiles.chunked(3).forEach { rowTiles ->
            val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
            rowTiles.forEach { t ->
                val v = inflater.inflate(R.layout.row_cat, row, false)
                v.findViewById<ImageView>(R.id.img_cat).apply {
                    setBackgroundResource(t.bg)
                    setImageResource(t.iconRes)
                    imageTintList = android.content.res.ColorStateList.valueOf(act.getColor(t.ink))
                }
                v.findViewById<TextView>(R.id.tv_cat).setText(t.labelRes)
                v.setOnClickListener {
                    act.filesTab.openForSend(t.cat)
                    act.select(1)
                }
                row.addView(v)
            }
            catGrid.addView(row)
        }
    }

    override fun onBack(): Boolean {
        if (page == Page.HOME) return false
        openPage(Page.HOME)
        return true
    }

    private fun paintPage() {
        val home = page == Page.HOME
        headerBrand.visibility = if (home) View.VISIBLE else View.GONE
        barPage.visibility = if (home) View.GONE else View.VISIBLE
        pageHome.visibility = if (home) View.VISIBLE else View.GONE
        panelReceive.visibility = if (page == Page.RECEIVE) View.VISIBLE else View.GONE
        panelSend.visibility = if (page == Page.SEND) View.VISIBLE else View.GONE
        panelWeb.visibility = if (page == Page.WEB) View.VISIBLE else View.GONE
        cardHotspot.visibility = if (page == Page.WEB) View.VISIBLE else View.GONE
        tvPageTitle.setText(
            when (page) {
                Page.SEND -> R.string.role_send
                Page.RECEIVE -> R.string.role_receive
                Page.WEB -> R.string.home_web_title
                Page.HOME -> R.string.app_name
            }
        )
    }

    private fun isRunning(mode: String): Boolean =
        if (mode == ServerService.MODE_DIRECT) DirectServer.running else ServerController.status().running

    /** Nyalakan salah satu mode server (dengan izin notifikasi dulu di Android 13+). */
    private fun startMode(mode: String) {
        if (isRunning(mode)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            act.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingMode = mode
            act.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), Req.NOTIF)
        } else {
            startModeNow(mode)
        }
    }

    private fun startModeNow(mode: String) {
        val intent = Intent(act, ServerService::class.java)
            .setAction(ServerService.ACTION_START)
            .putExtra(ServerService.EXTRA_MODE, mode)
        act.startForegroundService(intent)
        handler.postDelayed({ refresh() }, 600L)
    }

    private fun stopMode(mode: String) {
        act.startService(
            Intent(act, ServerService::class.java)
                .setAction(ServerService.ACTION_STOP)
                .putExtra(ServerService.EXTRA_MODE, mode)
        )
        handler.postDelayed({ refresh() }, 400L)
    }

    private fun toggleMode(mode: String) {
        if (isRunning(mode)) stopMode(mode) else startMode(mode)
    }

    override fun onShow() {
        visible = true
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        maybeAskStorage()
        paintPage()
        // Cek versi baru di latar (maks. sekali sehari); hasilnya muncul sebagai banner di Home.
        Updater.checkIfDue { renderUpdate() }
        renderUpdate()
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
        stopScanSpin()
    }

    override fun onPermissionsResult(requestCode: Int, grantResults: IntArray) {
        when (requestCode) {
            Req.NOTIF -> startModeNow(pendingMode)
            Req.HOTSPOT, Req.HOTSPOT_AUTO -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    startHotspotNow(auto = requestCode == Req.HOTSPOT_AUTO)
                } else {
                    Ui.toast(act, act.getString(R.string.hotspot_failed, "izin ditolak"))
                }
            }
        }
    }

    // ---------- hotspot ----------

    private fun toggleHotspot() {
        if (HotspotManager.running) {
            HotspotManager.stop()
            refresh()
            return
        }
        startHotspot(auto = false)
    }

    /**
     * [auto] = dinyalakan oleh mode Receive (ikut mati saat Receive dihentikan); selain itu
     * manual dari kartu Hotspot di halaman Web server.
     */
    private fun startHotspot(auto: Boolean) {
        if (HotspotManager.running) return
        val perm = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (act.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            act.requestPermissions(arrayOf(perm), if (auto) Req.HOTSPOT_AUTO else Req.HOTSPOT)
        } else {
            startHotspotNow(auto)
        }
    }

    private fun startHotspotNow(auto: Boolean) {
        HotspotManager.start(act) { info, err ->
            if (info != null) {
                HotspotManager.autoStarted = auto
                if (!auto) {
                    Ui.toast(act, "Hotspot active")
                    startMode(ServerService.MODE_WEB)
                }
            } else {
                Ui.toast(act, act.getString(R.string.hotspot_failed, err ?: "?"))
            }
            handler.postDelayed({ refresh() }, 1500L)
        }
    }

    // ---------- render ----------

    private fun refresh() {
        val st = ServerController.status()
        val direct = DirectServer.running
        btnServerText.setText(if (direct) R.string.btn_stop else R.string.btn_start)
        btnServerIcon.setImageResource(if (direct) R.drawable.ic_close else R.drawable.ic_download)
        tvDevname.text = Prefs.displayName()

        // Stempel status (header Home + bar sub-halaman) dan penanda di pintu "Web server".
        val anyOn = st.running || direct
        val stampBg = if (anyOn) R.drawable.bg_badge_recv else R.drawable.bg_badge_muted
        val stampInk = act.getColor(if (anyOn) R.color.ok else R.color.muted)
        val stampText = if (anyOn) R.string.stamp_on else R.string.stamp_off
        for ((box, dot, label) in listOf(Triple(stamp, tvDot, tvStatus), Triple(stampPage, tvDotPage, tvStatusPage))) {
            box.setBackgroundResource(stampBg)
            label.setText(stampText)
            label.setTextColor(stampInk)
            dot.setTextColor(stampInk)
        }
        val webText = if (st.running) R.string.stamp_on else R.string.stamp_off
        tvWebState.setText(webText)
        tvWebState.setBackgroundResource(if (st.running) R.drawable.bg_badge_recv else R.drawable.bg_badge_muted)
        tvWebState.setTextColor(act.getColor(if (st.running) R.color.ok else R.color.muted))

        // Receive
        tvRecvTitle.setText(if (direct) R.string.recv_title_on else R.string.recv_title_off)
        tvRecvSub.text =
            if (direct) act.getString(R.string.recv_sub_on, Prefs.displayName())
            else act.getString(R.string.recv_sub_off)
        radarRecv.ringColor = act.getColor(R.color.on_recv)
        radarRecv.active = direct && page == Page.RECEIVE

        // Web server
        tvWebTitle.setText(if (st.running) R.string.web_title_on else R.string.web_title_off)
        tvWebSub.setText(if (st.running) R.string.web_sub_on else R.string.web_sub_off)
        btnWebServerText.setText(if (st.running) R.string.btn_web_stop else R.string.btn_web_start)
        btnWebServerIcon.setImageResource(if (st.running) R.drawable.ic_close else R.drawable.ic_pc)
        radarWeb.ringColor = act.getColor(R.color.accent)
        radarWeb.active = st.running && page == Page.WEB

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

        renderReceiveQr()
        renderUpdate()
        renderPeers()
        renderActivity(recvActivity, tvRecvEmpty, TransferDir.IN)
        renderActivity(sendActivity, tvSendEmpty, TransferDir.OUT)
        renderActivity(recentActivity, null, null, 3)
        cardRecent.visibility = if (recentActivity.childCount == 0) View.GONE else View.VISIBLE
        radarSend.ringColor = act.getColor(R.color.on_accent)
        radarSend.active = page == Page.SEND && (scanning || PeerDiscovery.list().isEmpty())
    }

    /** Transfer terakhir per arah (atau semua arah kalau dir null), supaya tiap peran melihat aktivitasnya sendiri. */
    private fun renderActivity(container: LinearLayout, empty: View?, dir: TransferDir?, max: Int = 3) {
        val items = TransferTracker.items().filter { dir == null || it.dir == dir }.take(max)
        empty?.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        if (container.childCount != items.size) {
            container.removeAllViews()
            val inflater = LayoutInflater.from(act)
            items.forEach { container.addView(inflater.inflate(R.layout.row_transfer, container, false)) }
        }
        items.forEachIndexed { i, item ->
            val row = container.getChildAt(i)
            Ui.bindTransfer(act, row, item)
            // Media → viewer bawaan; selain itu buka tab Transfers.
            row.setOnClickListener {
                val path = Ui.viewablePath(item)
                if (path != null) {
                    val media = TransferTracker.items().mapNotNull { Ui.viewablePath(it) }.distinct()
                    ViewerActivity.open(act, media, media.indexOf(path).coerceAtLeast(0))
                } else if (item.path != null && Ui.isApk(item.name) && item.state == com.ahmadarif.sharebox.core.TransferState.DONE) {
                    Ui.openFile(act, java.io.File(item.path))
                } else {
                    act.select(2)
                }
            }
        }
    }

    /**
     * QR di layar penerima: hotspot (ssid + sandi) bila menyala, alamat & port Direct, dan kunci
     * sesi. Pengirim cukup scan sekali untuk tersambung dan langsung diterima.
     */
    private fun joinPayload(): JoinPayload? {
        if (!DirectServer.running) return null
        val hot = HotspotManager.currentInfo()
        val host = (if (hot != null) NetInfo.hotspotIp() else null) ?: NetInfo.lanIp() ?: NetInfo.hotspotIp() ?: return null
        return JoinPayload(
            id = Prefs.deviceId,
            name = Prefs.displayName(),
            host = host,
            port = Direct.PORT,
            key = DirectServer.sessionKey,
            ssid = hot?.ssid,
            pass = hot?.pass,
        )
    }

    /** Banner "Update available": hanya di halaman Home, dan hanya untuk versi yang belum ditutup user. */
    private fun renderUpdate() {
        val info = if (page == Page.HOME) Updater.pending() else null
        cardUpdate.visibility = if (info != null) View.VISIBLE else View.GONE
        if (info == null) return
        val base = act.getString(R.string.update_available_sub, info.version)
        tvUpdateSub.text = if (info.note != null) base + "\n" + info.note else base
    }

    private fun renderReceiveQr() {
        val payload = joinPayload()
        val text = payload?.toUri()
        cardRecvQr.visibility = if (page == Page.RECEIVE && text != null) View.VISIBLE else View.GONE
        tvRecvQrHint.setText(
            if (payload?.ssid != null) R.string.recv_qr_hotspot else R.string.recv_qr_lan
        )
        if (text == null) {
            lastRecvQrKey = null
            return
        }
        if (text == lastRecvQrKey) return
        lastRecvQrKey = text
        Thread({
            val bmp = runCatching { Qr.bitmap(text, 640) }.getOrNull()
            if (bmp != null) handler.post { if (lastRecvQrKey == text) imgRecvQr.setImageBitmap(bmp) }
        }, "sharebox-recvqr").apply { isDaemon = true }.start()
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

    /**
     * Tombol refresh = scan sungguhan: kirim announcement discovery sekarang (tanpa
     * menunggu interval 2 detik), putar ikon selama ~1,6 detik, lalu berhenti.
     */
    private fun startScan() {
        if (scanning) return
        scanning = true
        PeerDiscovery.announceNow()
        spin = ObjectAnimator.ofFloat(imgRefresh, View.ROTATION, 0f, 360f).apply {
            duration = 700L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        refresh()
        handler.postDelayed({
            scanning = false
            stopScanSpin()
            refresh()
        }, 1600L)
    }

    private fun stopScanSpin() {
        spin?.cancel()
        spin = null
        imgRefresh.rotation = 0f
    }

    /** Baris kedua kartu device: status saja (alamat IP tidak penting bagi user dan membuat teks terpotong). */
    private fun peerSubtitle(peer: PeerDiscovery.Peer): CharSequence {
        val (res, color) = when {
            !peer.ready -> R.string.peer_not_receiving to R.color.muted
            Pairing.tokenFor(peer.id) != null -> R.string.peer_paired to R.color.accent
            else -> R.string.peer_ready to R.color.ok
        }
        return android.text.SpannableString(act.getString(res)).apply {
            setSpan(android.text.style.ForegroundColorSpan(act.getColor(color)), 0, length, 0)
            setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, length, 0)
        }
    }

    private fun renderPeers() {
        val peers = PeerDiscovery.list()
        // Status pairing / scan / kesiapan server ikut masuk key supaya labelnya langsung berubah.
        val key = (if (scanning) "s|" else "n|") + peers.joinToString("|") {
            val paired = if (Pairing.tokenFor(it.id) != null) "p" else "n"
            it.id + ":" + paired + if (it.ready) "r" else "x"
        }
        tvPeersEmpty.setText(if (scanning) R.string.peers_scanning else R.string.peers_empty)
        tvPeersEmpty.visibility = if (peers.isEmpty()) View.VISIBLE else View.GONE
        if (key == lastPeersKey) return
        lastPeersKey = key
        peersContainer.removeAllViews()
        val inflater = LayoutInflater.from(act)
        for (peer in peers) {
            val row = inflater.inflate(R.layout.row_peer, peersContainer, false)
            row.findViewById<TextView>(R.id.tv_name).text = peer.name
            row.findViewById<TextView>(R.id.tv_sub).text = peerSubtitle(peer)
            row.findViewById<View>(R.id.btn_send).setOnClickListener { act.sendTo(peer) }
            peersContainer.addView(row)
        }
    }
}
