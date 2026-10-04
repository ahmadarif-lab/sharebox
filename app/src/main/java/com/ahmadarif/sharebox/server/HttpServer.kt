package com.ahmadarif.sharebox.server

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.channels.FileChannel
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.Executors

/**
 * Minimal HTTP/1.1 server: GET/HEAD/PUT/POST, Connection: close, two-way body streaming.
 * File downloads use kernel sendfile (FileChannel.transferTo -> SocketChannel) for
 * zero-copy throughput; everything else is streamed with large buffers.
 */
class HttpServer(
    private val port: Int,
    private val handler: (HttpRequest, HttpResult) -> Unit,
) {
    private var serverChannel: ServerSocketChannel? = null
    private val pool = Executors.newCachedThreadPool { r ->
        Thread(r, "sharebox-http").apply { isDaemon = true }
    }
    @Volatile private var running = false

    fun start() {
        val sc = ServerSocketChannel.open()
        sc.bind(InetSocketAddress(port), 64)
        serverChannel = sc
        running = true
        pool.execute { acceptLoop(sc) }
        Log.i(TAG, "HTTP listening :$port")
    }

    fun stop() {
        running = false
        try {
            serverChannel?.close()
        } catch (_: Exception) {
        }
        serverChannel = null
    }

    private fun acceptLoop(sc: ServerSocketChannel) {
        while (running) {
            val ch = try {
                sc.accept()
            } catch (e: Exception) {
                if (running) Log.w(TAG, "accept: ${e.message}")
                break
            } ?: continue
            pool.execute { handle(ch) }
        }
    }

    private fun handle(ch: SocketChannel) {
        try {
            val socket = ch.socket()
            socket.tcpNoDelay = true
            socket.soTimeout = 120_000
            try {
                socket.sendBufferSize = 1 shl 20
            } catch (_: Exception) {
            }
            try {
                socket.receiveBufferSize = 1 shl 20
            } catch (_: Exception) {
            }
            val input = BufferedInputStream(socket.getInputStream(), 64 * 1024)
            val output = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)
            val req = parseRequest(input, socket, ch) ?: return
            if ((req.header("expect") ?: "").contains("100-continue", ignoreCase = true)) {
                output.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                output.flush()
            }
            val res = HttpResult()
            try {
                handler(req, res)
            } catch (t: Throwable) {
                Log.w(TAG, "handler error", t)
                res.fail(500, "Internal Server Error", t.message ?: "internal error")
            }
            writeResponse(output, res, req.method == "HEAD", ch)
        } catch (e: Exception) {
            Log.d(TAG, "conn: ${e.message}")
        } finally {
            try {
                ch.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun parseRequest(
        input: BufferedInputStream,
        socket: java.net.Socket,
        ch: SocketChannel,
    ): HttpRequest? {
        val requestLine = readLine(input) ?: return null
        if (requestLine.isBlank()) return null
        val parts = requestLine.split(" ")
        if (parts.size < 3) return null
        val method = parts[0].uppercase()
        var target = parts[1]

        val headers = LinkedHashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx > 0) {
                val key = line.substring(0, idx).trim().lowercase()
                if (!headers.containsKey(key)) headers[key] = line.substring(idx + 1).trim()
            }
            if (headers.size > 200) break
        }

        if (target.startsWith("http://") || target.startsWith("https://")) {
            target = "/" + target.substringAfter("://").substringAfter('/', "")
        }
        val qi = target.indexOf('?')
        val rawPath = if (qi >= 0) target.substring(0, qi) else target
        val queryString = if (qi >= 0) target.substring(qi + 1) else ""
        val path = decode(rawPath)
        val query = LinkedHashMap<String, String>()
        for (kv in queryString.split('&')) {
            if (kv.isEmpty()) continue
            val eq = kv.indexOf('=')
            if (eq >= 0) {
                query[decode(kv.substring(0, eq))] = decode(kv.substring(eq + 1))
            } else {
                query[decode(kv)] = ""
            }
        }

        val te = (headers["transfer-encoding"] ?: "").lowercase()
        val contentLength = headers["content-length"]?.toLongOrNull() ?: -1L
        val body: InputStream = when {
            te.contains("chunked") -> ChunkedInputStream(input)
            contentLength >= 0 -> LimitedInputStream(input, contentLength)
            else -> input
        }
        return HttpRequest(
            method = method,
            path = path,
            query = query,
            headers = headers,
            body = body,
            contentLength = contentLength,
            remote = socket.inetAddress?.hostAddress ?: "",
            channel = ch,
        )
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder(128)
        var any = false
        while (true) {
            val b = input.read()
            if (b < 0) return if (any) sb.toString() else null
            any = true
            if (b == 10) return sb.toString()
            if (b != 13) sb.append(b.toChar())
            if (sb.length > 16384) throw IOException("header line too long")
        }
    }

    private fun decode(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (_: Exception) {
        s
    }

    private fun writeResponse(
        out: BufferedOutputStream,
        res: HttpResult,
        headOnly: Boolean,
        ch: SocketChannel,
    ) {
        if (res.trackId > 0) TransferChannels.register(res.trackId, ch)
        try {
            val sb = StringBuilder(256)
            sb.append("HTTP/1.1 ").append(res.status).append(' ').append(res.statusText).append("\r\n")
            sb.append("Content-Type: ").append(res.mime).append("\r\n")
            for ((k, v) in res.headers) sb.append(k).append(": ").append(v).append("\r\n")
            val bytes = res.bytes
            val stream = res.stream
            val len = when {
                bytes != null -> bytes.size.toLong()
                res.length >= 0 -> res.length
                else -> -1L
            }
            if (len >= 0) sb.append("Content-Length: ").append(len).append("\r\n")
            sb.append("Connection: close\r\n\r\n")
            out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))

            if (headOnly) {
                out.flush()
                return
            }

            if (res.file != null && trySendFile(out, res, ch)) {
                out.flush()
                return
            }

            if (bytes != null) {
                out.write(bytes)
            } else if (stream != null) {
                val buf = ByteArray(256 * 1024)
                while (true) {
                    while (TransferChannels.isPaused(res.trackId)) {
                        if (!ch.isOpen) throw IOException("cancelled")
                        Thread.sleep(150)
                    }
                    val r = stream.read(buf)
                    if (r < 0) break
                    out.write(buf, 0, r)
                }
            }
            out.flush()
        } finally {
            if (res.trackId > 0) TransferChannels.unregister(res.trackId)
            try {
                res.stream?.close()
            } catch (_: Exception) {
            }
        }
    }

    /** Zero-copy body via sendfile; returns true kalau body sudah ditangani. */
    private fun trySendFile(out: BufferedOutputStream, res: HttpResult, ch: SocketChannel): Boolean {
        val file = res.file ?: return false
        out.flush()
        var done = 0L
        try {
            FileChannel.open(file.toPath(), StandardOpenOption.READ).use { fc ->
                var pos = res.fileOffset
                var remaining = res.fileCount
                var lastReport = 0L
                while (remaining > 0) {
                    while (TransferChannels.isPaused(res.trackId)) {
                        if (!ch.isOpen) throw IOException("cancelled")
                        Thread.sleep(150)
                    }
                    val slice = minOf(remaining, 1L shl 20)
                    val n = fc.transferTo(pos, slice, ch)
                    if (n <= 0) throw IOException("sendfile stalled")
                    pos += n
                    remaining -= n
                    done += n
                    if (done - lastReport >= 1L shl 20) {
                        lastReport = done
                        res.onProgress?.invoke(done)
                    }
                }
            }
            res.onDone?.invoke()
            return true
        } catch (e: Exception) {
            Log.d(TAG, "sendfile: ${e.message}")
            // Kalau sudah terkirim sebagian, jangan fallback (akan korup) — putuskan saja.
            if (done > 0L) res.onAbort?.invoke()
            return done > 0L
        }
    }

    companion object {
        private const val TAG = "ShareBoxSrv"
    }
}
