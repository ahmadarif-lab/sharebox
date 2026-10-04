package com.ahmadarif.sharebox.net

import android.os.Handler
import android.os.Looper
import com.ahmadarif.sharebox.core.TransferDir
import com.ahmadarif.sharebox.core.TransferTracker
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object PeerSender {

    /** Kirim file ke peer lewat HTTP PUT ke endpoint /api/upload milik peer. */
    fun send(
        files: List<File>,
        peer: PeerDiscovery.Peer,
        folder: String?,
        onDone: (ok: Int, failed: Int) -> Unit,
    ) {
        Thread({
            var ok = 0
            var failed = 0
            for (file in files) {
                val trackId = TransferTracker.start(file.name, TransferDir.OUT, file.length(), folder)
                try {
                    putFile(file, peer, trackId)
                    TransferTracker.finish(trackId)
                    ok++
                } catch (e: Exception) {
                    TransferTracker.fail(trackId, e.message ?: "failed")
                    failed++
                }
            }
            Handler(Looper.getMainLooper()).post { onDone(ok, failed) }
        }, "sharebox-send").apply { isDaemon = true }.start()
    }

    private fun putFile(file: File, peer: PeerDiscovery.Peer, trackId: Long) {
        val encodedName = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
        val url = URL("http://${peer.host}:${peer.port}/api/upload?path=Inbox&name=$encodedName")
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
