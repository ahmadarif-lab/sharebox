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

    /** Port server tetap: tidak ada pengaturan, supaya device lain selalu tahu ke mana terhubung. */
    const val port: Int = 2999

    var deviceName: String
        get() = sp.getString("device", "").orEmpty()
        set(v) = sp.edit().putString("device", v).apply()

    /** Sekali saja: sudah pernah menawarkan izin "semua file"? */
    var storageAsked: Boolean
        get() = sp.getBoolean("storage_asked", false)
        set(v) = sp.edit().putBoolean("storage_asked", v).apply()

    /** Tema app: "system" (ikut HP), "light", atau "dark". */
    var theme: String
        get() = sp.getString("theme", "system").orEmpty().ifEmpty { "system" }
        set(v) = sp.edit().putString("theme", v).apply()

    /** Peran device: "receive" (siap menerima, server nyala) atau "send". */
    var role: String
        get() = sp.getString("role", "receive").orEmpty().ifEmpty { "receive" }
        set(v) = sp.edit().putString("role", v).apply()

    /**
     * Identitas device yang stabil antar-restart (dipakai discovery + pairing), dibuat
     * sekali lalu disimpan. Dengan id yang tetap, pasangan yang sudah di-approve tidak
     * "hilang" hanya karena app-nya ditutup.
     */
    val deviceId: String
        get() {
            val cur = sp.getString("device_id", "").orEmpty()
            if (cur.isNotEmpty()) return cur
            val fresh = java.util.UUID.randomUUID().toString()
            sp.edit().putString("device_id", fresh).apply()
            return fresh
        }

    fun displayName(): String = deviceName.ifBlank { Build.MODEL ?: "Android" }
}
