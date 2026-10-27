package id.ahmadarif.sharebox.server

import id.ahmadarif.sharebox.core.Prefs
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Login web UI: satu sandi ([Prefs.webPassword]) ditukar dengan cookie sesi acak.
 * Sesi hanya hidup di memori, jadi hilang saat server dimatikan atau sandi diganti.
 * Percobaan gagal dibatasi per IP supaya sandi pendek tidak bisa ditebak beruntun.
 */
object WebAuth {
    const val COOKIE = "sb_session"

    private const val FREE_ATTEMPTS = 5
    private const val LOCK_MS = 30_000L

    private val rnd = SecureRandom()
    private val sessions: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** IP -> (jumlah gagal beruntun, terkunci sampai). */
    private val failures = ConcurrentHashMap<String, Pair<Int, Long>>()

    fun isAuthed(req: HttpRequest): Boolean {
        val token = cookie(req, COOKIE) ?: return false
        return token in sessions
    }

    sealed class Result {
        class Ok(val token: String) : Result()
        object Wrong : Result()
        class Locked(val retryAfterSec: Long) : Result()
    }

    fun login(remote: String, password: String): Result {
        val now = System.currentTimeMillis()
        val (count, until) = failures[remote] ?: (0 to 0L)
        if (until > now) return Result.Locked((until - now + 999) / 1000)

        val expected = Prefs.webPassword.toByteArray(Charsets.UTF_8)
        val given = password.trim().toByteArray(Charsets.UTF_8)
        if (!MessageDigest.isEqual(expected, given)) {
            val n = count + 1
            failures[remote] = n to (if (n >= FREE_ATTEMPTS) now + LOCK_MS else 0L)
            return Result.Wrong
        }
        failures.remove(remote)
        val bytes = ByteArray(24).also { rnd.nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        sessions.add(token)
        return Result.Ok(token)
    }

    fun logout(req: HttpRequest) {
        cookie(req, COOKIE)?.let { sessions.remove(it) }
    }

    /** Putuskan semua browser yang sudah login (server mati / sandi diganti). */
    fun reset() {
        sessions.clear()
        failures.clear()
    }

    private fun cookie(req: HttpRequest, name: String): String? {
        val raw = req.header("cookie") ?: return null
        for (part in raw.split(';')) {
            val eq = part.indexOf('=')
            if (eq > 0 && part.substring(0, eq).trim() == name) return part.substring(eq + 1).trim()
        }
        return null
    }
}
