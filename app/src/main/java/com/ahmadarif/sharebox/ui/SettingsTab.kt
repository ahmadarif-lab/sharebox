package com.ahmadarif.sharebox.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.Switch
import android.widget.EditText
import android.widget.TextView
import com.ahmadarif.sharebox.BuildConfig
import com.ahmadarif.sharebox.MainActivity
import com.ahmadarif.sharebox.R
import com.ahmadarif.sharebox.core.Prefs
import com.ahmadarif.sharebox.core.ServerController
import com.ahmadarif.sharebox.core.Storage

class SettingsTab(activity: MainActivity) : BaseTab(activity) {

    override fun layoutId(): Int = R.layout.tab_settings

    private val etName: EditText = root.findViewById(R.id.et_name)
    private val tvStorageDesc: TextView = root.findViewById(R.id.tv_storage_desc)
    private val swStorage: Switch = root.findViewById(R.id.sw_storage)
    private val themeChips: Map<String, TextView> = mapOf(
        "system" to root.findViewById(R.id.theme_system),
        "light" to root.findViewById(R.id.theme_light),
        "dark" to root.findViewById(R.id.theme_dark),
    )
    private val tvAbout: TextView = root.findViewById(R.id.tv_about)
    private var lastRoot: String = ""

    init {
        root.findViewById<View>(R.id.btn_save).setOnClickListener { save() }
        root.findViewById<View>(R.id.row_storage).setOnClickListener { toggleStorage() }
        themeChips.forEach { (mode, chip) -> chip.setOnClickListener { setTheme(mode) } }
        tvAbout.text = act.getString(R.string.set_about_text, BuildConfig.VERSION_NAME)
    }

    override fun onShow() = refresh()

    override fun onPermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode == Req.STORAGE) refresh()
    }

    private fun refresh() {
        etName.setText(Prefs.displayName())

        val granted = Storage.hasAllFiles(act)
        swStorage.isChecked = granted
        tvStorageDesc.setText(
            if (granted) R.string.set_storage_all_desc else R.string.set_storage_need
        )
        paintTheme()

        // Kalau izin baru diberikan (atau dicabut), samakan folder kerja Files tab + server.
        val rootNow = runCatching { Storage.root(act).absolutePath }.getOrDefault("")
        if (rootNow != lastRoot) {
            lastRoot = rootNow
            ServerController.refreshRoot()
            act.filesTab.openPath(Storage.root(act))
        }
    }

    private fun save() {
        val name = etName.text.toString().trim()
        if (name.isNotEmpty()) Prefs.deviceName = name
        Ui.toast(act, act.getString(R.string.set_saved))
    }

    /**
     * Izin "semua file" tidak bisa diubah dari dalam app: Android mewajibkan user melakukannya
     * di halaman pengaturan sistem. Toggle ini menampilkan statusnya dan membuka halaman itu;
     * status diperbarui lagi saat user kembali (onShow).
     */
    private fun toggleStorage() {
        if (Storage.hasAllFiles(act) && Build.VERSION.SDK_INT < 30) {
            // Android 10-: mencabut izin runtime hanya lewat halaman detail app.
            act.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + act.packageName))
            )
        } else {
            Ui.grantAllFiles(act)
        }
    }

    private fun setTheme(mode: String) {
        if (Prefs.theme == mode) return
        Prefs.theme = mode
        act.recreate()
    }

    private fun paintTheme() {
        themeChips.forEach { (mode, chip) ->
            val on = Prefs.theme == mode
            chip.setBackgroundResource(if (on) R.drawable.bg_chip_on else R.drawable.bg_chip)
            chip.setTextColor(act.getColor(if (on) R.color.on_accent else R.color.fg))
        }
    }
}
