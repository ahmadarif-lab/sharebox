package id.my.bontot.sharebox.server

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: InputStream,
    val contentLength: Long,
    val remote: String,
    val channel: java.nio.channels.SocketChannel,
) {
    fun q(name: String): String? = query[name]

    fun header(name: String): String? = headers[name]

    fun bodyBytes(limit: Int = 8 * 1024 * 1024): ByteArray {
        val n = if (contentLength in 0..limit.toLong()) contentLength.toInt() else limit
        val out = ByteArrayOutputStream(minOf(n, 64 * 1024))
        val buf = ByteArray(64 * 1024)
        var left = n
        while (left > 0) {
            val r = body.read(buf, 0, minOf(buf.size, left))
            if (r < 0) break
            out.write(buf, 0, r)
            left -= r
        }
        return out.toByteArray()
    }

    fun bodyString(): String = String(bodyBytes(), Charsets.UTF_8)
}

class HttpResult {
    var status = 200
    var statusText = "OK"
    var mime = "application/octet-stream"
    var length: Long = -1
    var stream: InputStream? = null
    var bytes: ByteArray? = null
    val headers = linkedMapOf<String, String>()

    /** Kalau diisi, body dikirim via sendfile (zero-copy) langsung dari file. */
    var file: java.io.File? = null
    var fileOffset: Long = 0
    var fileCount: Long = 0
    var onProgress: ((Long) -> Unit)? = null
    var onDone: (() -> Unit)? = null
    var onAbort: (() -> Unit)? = null

    /** Kalau > 0, koneksi didaftarkan supaya transfer bisa di-pause/cancel. */
    var trackId: Long = -1

    fun sendJson(obj: JSONObject) {
        val data = obj.toString().toByteArray(Charsets.UTF_8)
        mime = "application/json; charset=utf-8"
        bytes = data
        length = data.size.toLong()
    }

    fun fail(status: Int, statusText: String, msg: String) {
        this.status = status
        this.statusText = statusText
        stream = null
        bytes = null
        length = -1
        sendJson(JSONObject().put("error", msg))
    }
}
