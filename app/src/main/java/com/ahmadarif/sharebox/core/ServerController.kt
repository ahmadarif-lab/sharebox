package com.ahmadarif.sharebox.core

import android.content.Context
import android.util.Log
import com.ahmadarif.sharebox.net.NetInfo
import com.ahmadarif.sharebox.net.Pairing
import com.ahmadarif.sharebox.server.ApiHandler
import com.ahmadarif.sharebox.server.HttpServer

object ServerController {

    data class Status(
        val running: Boolean = false,
        val port: Int = 2999,
        val rootPath: String = "",
        val error: String? = null,
    )

    @Volatile
    private var status = Status()
    private var server: HttpServer? = null
    private var app: Context? = null

    fun init(ctx: Context) {
        app = ctx.applicationContext
        status = status.copy(
            port = Prefs.port,
            rootPath = runCatching { Storage.root(ctx).absolutePath }.getOrDefault(""),
        )
    }

    fun status(): Status = status

    @Synchronized
    fun start(): Boolean {
        val ctx = app ?: return false
        if (status.running) return true
        // Sesi server baru = state pairing baru (blokir device ditolak tidak ikut terbawa).
        Pairing.clear()
        return try {
            val port = Prefs.port
            val handler = ApiHandler(ctx)
            val srv = HttpServer(port) { req, res -> handler.handle(req, res) }
            srv.start()
            server = srv
            status = Status(true, port, Storage.root(ctx).absolutePath, null)
            true
        } catch (e: Exception) {
            Log.w("ShareBox", "failed to start server", e)
            status = status.copy(running = false, error = e.message ?: "failed")
            false
        }
    }

    @Synchronized
    fun stop() {
        try {
            server?.stop()
        } catch (_: Exception) {
        }
        server = null
        status = status.copy(running = false)
    }

    fun refreshRoot() {
        app?.let {
            status = status.copy(rootPath = runCatching { Storage.root(it).absolutePath }.getOrDefault(""))
        }
    }

    fun setPort(p: Int) {
        Prefs.port = p
        status = status.copy(port = p)
    }

    fun urls(): List<NetInfo.UrlInfo> = runCatching { NetInfo.urls(status.port) }.getOrDefault(emptyList())
}
