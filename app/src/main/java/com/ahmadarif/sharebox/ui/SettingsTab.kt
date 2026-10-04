package com.ahmadarif.sharebox.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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

    init {
        root.findViewById<View>(R.id.btn_save).setOnClickListener { save() }
        btnGrant.setOnClickListener { grantAllFiles() }
        root.findViewById<View>(R.id.btn_use_app).setOnClickListener { useApp() }
        root.findViewById<View>(R.id.btn_use_all).setOnClickListener { useAll() }
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
            if (granted && Prefs.useAllFiles) R.string.set_storage_all_desc
            else R.string.set_storage_app_desc
        )
        btnGrant.visibility = if (granted) View.GONE else View.VISIBLE
        btnGrantText.setText(R.string.set_grant)
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
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                act.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + act.packageName)
                    )
                )
            } catch (e: Exception) {
                act.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            act.requestPermissions(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                Req.STORAGE
            )
        }
    }

    private fun useApp() {
        Prefs.useAllFiles = false
        ServerController.refreshRoot()
        refresh()
    }

    private fun useAll() {
        if (!Storage.hasAllFiles(act)) {
            Ui.toast(act, act.getString(R.string.set_grant))
            grantAllFiles()
            return
        }
        Prefs.useAllFiles = true
        ServerController.refreshRoot()
        refresh()
    }
}
