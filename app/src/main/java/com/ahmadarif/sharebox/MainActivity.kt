package com.ahmadarif.sharebox

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.ahmadarif.sharebox.net.PeerDiscovery
import com.ahmadarif.sharebox.ui.BaseTab
import com.ahmadarif.sharebox.ui.FilesTab
import com.ahmadarif.sharebox.ui.HomeTab
import com.ahmadarif.sharebox.ui.SettingsTab
import com.ahmadarif.sharebox.ui.TransfersTab

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
    private val navViews = ArrayList<Triple<View, ImageView, TextView>>()

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

        select(0)
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

    fun sendTo(peer: PeerDiscovery.Peer) {
        filesTab.setTarget(peer)
        select(1)
    }

    private fun paintNav() {
        val active = getColor(R.color.accent)
        val idle = getColor(R.color.muted)
        navViews.forEachIndexed { i, (_, icon, text) ->
            val color = if (i == current) active else idle
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
        if (current >= 0 && tabs[current].onBack()) return
        super.onBackPressed()
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
}
