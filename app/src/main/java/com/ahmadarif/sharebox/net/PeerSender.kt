package com.ahmadarif.sharebox.net

import android.os.Handler
import android.os.Looper
import com.ahmadarif.sharebox.core.Prefs
import com.ahmadarif.sharebox.core.TransferDir
import com.ahmadarif.sharebox.core.TransferTracker
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object PeerSender {

    /** Kirim file ke peer lewat HTTP PUT, setelah minta izin (pairing) ke pemilik HP peer. */
    fun send(
        files: List<File>,
        peer: PeerDiscovery.Peer,
        folder: String?,
        onDone: (ok: Int, failed: Int, error: String?) -> Unit,
    ) {
        Thread({
            var ok = 0
            var failed = 0
            var error: String? = null

            var token = Pairing.tokenFor(peer.id)
            if (token == null) {
                val (newToken, pairError) = requestPair(peer)
                if (newToken == null) {
                    error = pairError ?: "no response"
                    for (file in files) {
                        val id = TransferTracker.start(file.name, TransferDir.OUT, file.length(), folder)
                        TransferTracker.fail(id, if (error == "declined") "declined" else "no response")
                        failed++
                    }
                    Handler(Looper.getMainLooper()).post { onDone(ok, failed, error) }
                    return@Thread
                }
                token = newToken
            }

            for (file in files) {
                val trackId = TransferTracker.start(file.name, TransferDir.OUT, file.length(), folder)
                try {
                    putFile(file, peer, token, trackId)
                    TransferTracker.finish(trackId)
                    ok++
                } catch (e: Exception) {
                    TransferTracker.fail(trackId, e.message ?: "failed")
                    failed++
                }
            }
            Handler(Looper.getMainLooper()).post { onDone(ok, failed, null) }
        }, "sharebox-send").apply { isDaemon = true }.start()
    }

    /** POST /api/pair/request ke peer; blokir sampai user di sana menekan Approve (maks 45s). */
    private fun requestPair(peer: PeerDiscovery.Peer): Pair<String?, String?> {
        return try {
            val url = URL("http://${peer.host}:${peer.port}/api/pair/request")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 6000
            conn.readTimeout = 45_000
            conn.setRequestProperty("Content-Type", "application/json")
            val payload = JSONObject()
                .put("id", PeerDiscovery.myId())
                .put("name", Prefs.displayName())
                .toString()
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) return null to "no response"
            val obj = JSONObject(conn.inputStream.bufferedReader().readText())
            if (!obj.optBoolean("ok")) {
                return null to if (obj.optString("error") == "timeout") "no response" else "declined"
            }
            val token = obj.optString("token")
            if (token.isEmpty()) null to "no response"
            else {
                Pairing.storeToken(peer.id, token)
                token to null
            }
        } catch (e: Exception) {
            null to "no response"
        }
    }

    private fun putFile(file: File, peer: PeerDiscovery.Peer, token: String, trackId: Long) {
        val encodedName = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
        val url = URL("http://${peer.host}:${peer.port}/api/upload?path=Inbox&name=$encodedName&token=$token")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.connectTimeout = 6000
            conn.readTimeout = 60_000
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.setFixedLengthStreamingMode(file.length())
            var sent = 0L
            var lastReport = 0L
            conn.outputStream.use { out ->
                file.inputStream().use { input ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        sent += n
                        if (sent - lastReport >= 512 * 1024) {
                            lastReport = sent
                            TransferTracker.progress(trackId, sent)
                        }
                    }
                }
            }
            TransferTracker.progress(trackId, sent)
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
        } finally {
            conn.disconnect()
        }
    }
}
