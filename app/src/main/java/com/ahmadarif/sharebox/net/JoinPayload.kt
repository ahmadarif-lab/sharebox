package com.ahmadarif.sharebox.net

import android.net.Uri

/**
 * Isi QR di layar penerima: `sharebox://join?...`. Membawa semua yang dibutuhkan pengirim —
 * hotspot (ssid + sandi) bila ada, alamat & port Direct, identitas, dan kunci sesi — sehingga
 * satu kali scan cukup untuk tersambung dan mengirim.
 */
data class JoinPayload(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val key: String,
    val ssid: String? = null,
    val pass: String? = null,
) {
    fun toUri(): String {
        val b = Uri.Builder().scheme("sharebox").authority("join")
            .appendQueryParameter("v", "1")
            .appendQueryParameter("id", id)
            .appendQueryParameter("n", name)
            .appendQueryParameter("h", host)
            .appendQueryParameter("p", port.toString())
            .appendQueryParameter("k", key)
        if (!ssid.isNullOrEmpty()) {
            b.appendQueryParameter("s", ssid)
            b.appendQueryParameter("w", pass.orEmpty())
        }
        return b.build().toString()
    }

    fun toTarget() = DirectClient.Target(id, name, host, port, key)

    companion object {
        fun parse(text: String): JoinPayload? = try {
            val u = Uri.parse(text.trim())
            if (u.scheme != "sharebox" || u.host != "join") null
            else {
                val host = u.getQueryParameter("h").orEmpty()
                val id = u.getQueryParameter("id").orEmpty()
                if (host.isEmpty() || id.isEmpty()) null
                else JoinPayload(
                    id = id,
                    name = u.getQueryParameter("n") ?: "Android",
                    host = host,
                    port = u.getQueryParameter("p")?.toIntOrNull() ?: Direct.PORT,
                    key = u.getQueryParameter("k").orEmpty(),
                    ssid = u.getQueryParameter("s")?.takeIf { it.isNotEmpty() },
                    pass = u.getQueryParameter("w"),
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
