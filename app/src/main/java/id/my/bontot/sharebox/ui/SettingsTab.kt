package id.my.bontot.sharebox.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.Switch
import android.widget.EditText
import android.widget.TextView
import id.my.bontot.sharebox.BuildConfig
import id.my.bontot.sharebox.MainActivity
import id.my.bontot.sharebox.R
import id.my.bontot.sharebox.core.Prefs
import id.my.bontot.sharebox.core.ServerController
import id.my.bontot.sharebox.core.Storage
import id.my.bontot.sharebox.core.Updater

class SettingsTab(activity: MainActivity) : BaseTab(activity) {

    override fun layoutId(): Int = R.layout.tab_settings

    private val etName: EditText = root.findViewById(R.id.et_name)
    private val tvStorageDesc: TextView = root.findViewById(R.id.tv_storage_desc)
    private val swStorage: Switch = root.findViewById(R.id.sw_storage)
    private val swUpdates: Switch = root.findViewById(R.id.sw_updates)
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
        root.findViewById<View>(R.id.row_updates).setOnClickListener {
            Prefs.updateCheck = !Prefs.updateCheck
            swUpdates.isChecked = Prefs.updateCheck
        }
        root.findViewById<View>(R.id.btn_check_now).setOnClickListener { checkNow() }
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
        swUpdates.isChecked = Prefs.updateCheck
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

    /** Cek manual: selalu jalan (abaikan batas sehari dan sakelar), dan memberi tahu hasilnya. */
    private fun checkNow() {
        Updater.check { result ->
            when (result) {
                is Updater.Result.Available -> {
                    Prefs.updateDismissed = ""
                    Ui.toast(act, act.getString(R.string.update_available_sub, result.info.version))
                    act.select(0)
                }
                Updater.Result.UpToDate ->
                    Ui.toast(act, act.getString(R.string.update_up_to_date, BuildConfig.VERSION_NAME))
                Updater.Result.Failed -> Ui.toast(act, act.getString(R.string.update_failed))
            }
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
