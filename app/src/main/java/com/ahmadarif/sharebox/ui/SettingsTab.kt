package com.ahmadarif.sharebox.ui

import android.view.View
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
    private val etPort: EditText = root.findViewById(R.id.et_port)
    private val tvStoragePath: TextView = root.findViewById(R.id.tv_storage_path)
    private val tvStorageDesc: TextView = root.findViewById(R.id.tv_storage_desc)
    private val btnGrant: View = root.findViewById(R.id.btn_grant)
    private val btnGrantText: TextView = root.findViewById(R.id.btn_grant_text)
    private val tvAbout: TextView = root.findViewById(R.id.tv_about)
    private var lastRoot: String = ""

    init {
        root.findViewById<View>(R.id.btn_save).setOnClickListener { save() }
        btnGrant.setOnClickListener { grantAllFiles() }
        tvAbout.text = act.getString(R.string.set_about_text, BuildConfig.VERSION_NAME)
    }

    override fun onShow() = refresh()

    override fun onPermissionsResult(requestCode: Int, grantResults: IntArray) {
        if (requestCode == Req.STORAGE) refresh()
    }

    private fun refresh() {
        etName.setText(Prefs.displayName())
        etPort.setText(Prefs.port.toString())
        etPort.isEnabled = !ServerController.status().running

        val granted = Storage.hasAllFiles(act)
        tvStoragePath.text = runCatching { Storage.root(act).absolutePath }.getOrDefault("")
        tvStorageDesc.setText(
            if (granted) R.string.set_storage_all_desc else R.string.set_storage_need
        )
        btnGrant.visibility = if (granted) View.GONE else View.VISIBLE
        btnGrantText.setText(R.string.set_grant)

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

        val port = etPort.text.toString().toIntOrNull()
        when {
            port == null || port !in 1024..65535 ->
                Ui.toast(act, act.getString(R.string.err_port))
            ServerController.status().running && port != Prefs.port ->
                Ui.toast(act, "Stop the server first to change the port")
            else ->
                ServerController.setPort(port)
        }
        Ui.toast(act, act.getString(R.string.set_saved))
    }

    private fun grantAllFiles() {
        Ui.grantAllFiles(act)
    }
}
