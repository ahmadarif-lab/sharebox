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

    /** Sekali saja: sudah pernah menawarkan izin "semua file"? */
    var storageAsked: Boolean
        get() = sp.getBoolean("storage_asked", false)
        set(v) = sp.edit().putBoolean("storage_asked", v).apply()

    fun displayName(): String = deviceName.ifBlank { Build.MODEL ?: "Android" }
}
