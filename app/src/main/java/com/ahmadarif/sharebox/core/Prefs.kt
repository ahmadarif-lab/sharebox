package com.ahmadarif.sharebox.core

import android.content.Context
import android.content.SharedPreferences
import android.os.Build

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (!::sp.isInitialized) {
            sp = ctx.applicationContext.getSharedPreferences("sharebox", Context.MODE_PRIVATE)
        }
    }

    var port: Int
        get() = sp.getInt("port", 2999)
        set(v) = sp.edit().putInt("port", v).apply()

    var deviceName: String
        get() = sp.getString("device", "").orEmpty()
        set(v) = sp.edit().putString("device", v).apply()

    var useAllFiles: Boolean
        get() = sp.getBoolean("all_files", false)
        set(v) = sp.edit().putBoolean("all_files", v).apply()

    fun displayName(): String = deviceName.ifBlank { Build.MODEL ?: "Android" }
}
