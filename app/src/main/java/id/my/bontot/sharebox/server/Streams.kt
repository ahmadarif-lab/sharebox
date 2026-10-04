package id.my.bontot.sharebox.server

import java.io.IOException
import java.io.InputStream

/** Limit body reads to Content-Length; close() must not close the underlying socket. */
class LimitedInputStream(private val src: InputStream, private var left: Long) : InputStream() {

    override fun read(): Int {
        if (left <= 0) return -1
        val b = src.read()
        if (b >= 0) left--
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (left <= 0) return -1
        val n = src.read(b, off, minOf(len.toLong(), left).toInt())
        if (n > 0) left -= n
        return n
    }

    override fun close() {
        // keep the socket open; the server owns it
    }
}

/** Decoder transfer-encoding: chunked untuk request body. */
class ChunkedInputStream(private val src: InputStream) : InputStream() {

    private var remaining = 0L
    private var done = false
    private var eof = false

    private fun readLine(): String {
        val sb = StringBuilder()
        while (true) {
            val b = src.read()
            if (b < 0) {
                eof = true
                break
            }
            if (b == 10) break
            if (b != 13) sb.append(b.toChar())
            if (sb.length > 8192) throw IOException("chunk line too long")
        }
        return sb.toString()
    }

    private fun nextSize(): Boolean {
        if (eof) {
            done = true
            return false
        }
        val line = readLine().trim()
        if (line.isEmpty()) return nextSize()
        val size = line.substringBefore(';').trim().toLongOrNull(16)
            ?: throw IOException("invalid chunk size: $line")
        if (size == 0L) {
            while (true) {
                val l = readLine()
                if (l.isEmpty()) break
            }
            done = true
            return false
        }
        remaining = size
        return true
    }

    override fun read(): Int {
        val b = ByteArray(1)
        val r = read(b, 0, 1)
        return if (r <= 0) -1 else b[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (done) return -1
        if (remaining == 0L) {
            if (!nextSize()) return -1
        }
        val n = src.read(b, off, minOf(len.toLong(), remaining).toInt())
        if (n < 0) throw IOException("unexpected end of body")
        remaining -= n
        if (remaining == 0L) readLine() // buang CRLF penutup chunk
        return n
    }

    override fun close() {
        // keep the socket open
    }
}
