package id.my.bontot.sharebox

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import id.my.bontot.sharebox.core.Prefs
import id.my.bontot.sharebox.net.PeerDiscovery
import id.my.bontot.sharebox.ui.BaseTab
import id.my.bontot.sharebox.ui.FilesTab
import id.my.bontot.sharebox.ui.HomeTab
import id.my.bontot.sharebox.ui.ScanActivity
import id.my.bontot.sharebox.ui.SettingsTab
import id.my.bontot.sharebox.ui.TransfersTab

class MainActivity : Activity() {

    lateinit var homeTab: HomeTab
        private set
    lateinit var filesTab: FilesTab
        private set

    private lateinit var transfersTab: TransfersTab
    private lateinit var settingsTab: SettingsTab
    private lateinit var tabs: List<BaseTab>
    private lateinit var container: FrameLayout
    private var current = -1
    private var started = false
    private var exitDialog: AlertDialog? = null
    private val navViews = ArrayList<Triple<View, ImageView, TextView>>()

    /**
     * Pilihan tema di Settings: paksa terang/gelap dengan menimpa flag night di konfigurasi
     * activity. "System" tidak menimpa apa pun, jadi tetap mengikuti HP.
     */
    override fun attachBaseContext(base: Context) {
        val night = when (Prefs.theme) {
            "dark" -> Configuration.UI_MODE_NIGHT_YES
            "light" -> Configuration.UI_MODE_NIGHT_NO
            else -> null
        }
        if (night == null) {
            super.attachBaseContext(base)
            return
        }
        val cfg = Configuration(base.resources.configuration)
        cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        super.attachBaseContext(base.createConfigurationContext(cfg))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        container = findViewById(R.id.tab_container)

        homeTab = HomeTab(this)
        filesTab = FilesTab(this)
        transfersTab = TransfersTab(this)
        settingsTab = SettingsTab(this)
        tabs = listOf(homeTab, filesTab, transfersTab, settingsTab)

        navViews += Triple(
            findViewById(R.id.nav_home),
            findViewById(R.id.nav_home_icon),
            findViewById(R.id.nav_home_text)
        )
        navViews += Triple(
            findViewById(R.id.nav_files),
            findViewById(R.id.nav_files_icon),
            findViewById(R.id.nav_files_text)
        )
        navViews += Triple(
            findViewById(R.id.nav_transfers),
            findViewById(R.id.nav_transfers_icon),
            findViewById(R.id.nav_transfers_text)
        )
        navViews += Triple(
            findViewById(R.id.nav_settings),
            findViewById(R.id.nav_settings_icon),
            findViewById(R.id.nav_settings_text)
        )
        navViews.forEachIndexed { i, (view, _, _) -> view.setOnClickListener { select(i) } }

        // Android 13+ dengan predictive back (default untuk targetSdk 36) tidak lagi memanggil
        // onBackPressed, jadi daftarkan callback supaya back tetap lewat logika handleBack().
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                OnBackInvokedCallback { handleBack() }
            )
        }

        select(savedInstanceState?.getInt(STATE_TAB) ?: 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TAB, current)
    }

    fun select(index: Int) {
        if (index == current) return
        if (current >= 0) tabs[current].onHide()
        container.removeAllViews()
        container.addView(
            tabs[index].root,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        current = index
        if (started) tabs[index].onShow()
        paintNav()
    }

    /** Pengirim: buka kamera untuk scan QR penerima. Hasilnya kembali lewat [onActivityResult]. */
    fun startScan() {
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(this, ScanActivity::class.java), REQ_SCAN)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SCAN) {
            filesTab.onScanResult(
                if (resultCode == RESULT_OK) data?.getStringExtra(ScanActivity.EXTRA_TEXT) else null
            )
        }
    }

    fun sendTo(peer: PeerDiscovery.Peer) {
        filesTab.setTarget(peer)
        select(1)
    }

    private fun paintNav() {
        val active = getColor(R.color.accent)
        val idle = getColor(R.color.muted)
        navViews.forEachIndexed { i, (view, icon, text) ->
            val on = i == current
            val color = if (on) active else idle
            // Tab aktif: pil lembut di belakang ikon+label.
            view.setBackgroundResource(if (on) R.drawable.bg_nav_on else 0)
            icon.setColorFilter(color)
            text.setTextColor(color)
        }
    }

    override fun onStart() {
        super.onStart()
        started = true
        PeerDiscovery.acquire(this)
        if (current >= 0) tabs[current].onShow()
    }

    override fun onStop() {
        if (current >= 0) tabs[current].onHide()
        PeerDiscovery.release()
        started = false
        super.onStop()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        handleBack()
    }

    /**
     * Back sistem: sub-halaman / riwayat tab dulu; kalau sudah di akar, minta konfirmasi
     * supaya aplikasi tidak tertutup tanpa sengaja.
     */
    private fun handleBack() {
        if (current >= 0 && tabs[current].onBack()) return
        confirmExit()
    }

    private fun confirmExit() {
        if (exitDialog?.isShowing == true) return
        exitDialog = AlertDialog.Builder(this)
            .setTitle(R.string.exit_title)
            .setMessage(R.string.exit_msg)
            .setPositiveButton(R.string.exit_confirm) { _, _ -> finish() }
            .setNegativeButton(R.string.exit_cancel, null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        tabs.forEach { it.onPermissionsResult(requestCode, grantResults) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private companion object {
        const val STATE_TAB = "tab"
        const val REQ_SCAN = 201
    }
}
