package id.my.bontot.sharebox.core

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import id.my.bontot.sharebox.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Cek versi baru lewat `latest.json` di situs rilis, sama seperti app lain di apps.bontot.my.id.
 *
 * Permintaannya tidak membawa apa pun tentang user atau versi terpasang (perbandingan terjadi di
 * HP), maksimal sekali sehari, dan bisa dimatikan di Settings. Isi manifest dianggap tidak
 * tepercaya: hanya link https ke host di [ALLOWED_HOSTS] yang diterima.
 */
object Updater {

    const val MANIFEST_URL = "https://apps.bontot.my.id/sharebox/latest.json"
    private val ALLOWED_HOSTS = setOf("apps.bontot.my.id", "github.com")
    private const val MIN_INTERVAL_MS = 24L * 60 * 60 * 1000
    private const val TIMEOUT_MS = 8000
    private val VERSION = Regex("^\\d+(\\.\\d+){0,3}$")

    class Info(val version: String, val note: String?, val link: Uri)

    sealed class Result {
        object UpToDate : Result()
        class Available(val info: Info) : Result()
        object Failed : Result()
    }

    private val main = Handler(Looper.getMainLooper())

    /** "0.10.0" > "0.9.0"; bagian yang hilang dianggap 0. Versi tak valid tidak pernah dianggap lebih baru. */
    fun isNewer(candidate: String, installed: String): Boolean {
        if (!VERSION.matches(candidate) || !VERSION.matches(installed)) return false
        val a = candidate.split('.').map { it.toInt() }
        val b = installed.split('.').map { it.toInt() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Validasi manifest; null untuk apa pun yang janggal atau menunjuk host di luar daftar. */
    fun parse(text: String): Info? = try {
        val o = JSONObject(text)
        val version = o.optString("version")
        if (!VERSION.matches(version)) {
            null
        } else {
            val downloads = o.optJSONObject("downloads")
            val raw = downloads?.optString("universal").orEmpty().ifEmpty { o.optString("page") }
            val link = Uri.parse(raw)
            if (link.scheme != "https" || link.host !in ALLOWED_HOSTS) {
                null
            } else {
                val notes = o.optJSONObject("notes")
                val lang = Locale.getDefault().language.let { if (it == "in") "id" else it }
                val list = notes?.optJSONArray(lang) ?: notes?.optJSONArray("en")
                Info(version, list?.optString(0)?.takeIf { it.isNotBlank() }, link)
            }
        }
    } catch (_: Exception) {
        null
    }

    /** Update yang sudah diketahui dari cek terakhir dan belum ditutup user; null kalau tidak ada. */
    fun pending(): Info? {
        if (!Prefs.updateCheck) return null
        val info = parse(Prefs.updateManifest) ?: return null
        if (!isNewer(info.version, BuildConfig.VERSION_NAME)) return null
        return info.takeIf { it.version != Prefs.updateDismissed }
    }

    fun dismiss(info: Info) {
        Prefs.updateDismissed = info.version
    }

    /** Cek otomatis: hanya kalau diizinkan dan sudah lewat 24 jam sejak cek terakhir. */
    fun checkIfDue(onDone: (Result) -> Unit = {}) {
        if (!Prefs.updateCheck) return
        if (System.currentTimeMillis() - Prefs.updateLastCheck < MIN_INTERVAL_MS) return
        check(onDone)
    }

    /** Cek sekarang (tombol "Check now"). Hasil dikirim di main thread. */
    fun check(onDone: (Result) -> Unit) {
        Thread({
            val result = try {
                val conn = URL(MANIFEST_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.setRequestProperty("Accept", "application/json")
                try {
                    if (conn.responseCode != 200) {
                        Result.Failed
                    } else {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val info = parse(body)
                        if (info == null) {
                            Result.Failed
                        } else {
                            Prefs.updateManifest = body
                            Prefs.updateLastCheck = System.currentTimeMillis()
                            if (isNewer(info.version, BuildConfig.VERSION_NAME)) Result.Available(info)
                            else Result.UpToDate
                        }
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                Result.Failed
            }
            main.post { onDone(result) }
        }, "sharebox-update").apply { isDaemon = true }.start()
    }
}
