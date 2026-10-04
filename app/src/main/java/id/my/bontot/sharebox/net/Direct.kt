package id.my.bontot.sharebox.net

import android.content.Context
import id.my.bontot.sharebox.core.Fs
import id.my.bontot.sharebox.core.Notifier
import id.my.bontot.sharebox.core.Prefs
import id.my.bontot.sharebox.core.Storage
import id.my.bontot.sharebox.core.TransferDir
import id.my.bontot.sharebox.core.TransferTracker
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.Executors

/**
 * ShareBox Direct (SBX1): protokol transfer HP ke HP yang BERBEDA dari web server HTTP.
 *
 *  - Port sendiri ([PORT]) dan hanya aktif selama mode Receive; web server (HTTP, port 2999)
 *    tidak ikut terlibat dan tidak menerima kiriman dari HP lain.
 *  - TCP polos berframe: `int MAGIC`, lalu frame JSON (`writeUTF`) dan byte file mentah.
 *
 *      C → S  MAGIC, {"id","name","key"?,"token"?}
 *      S → C  {"ok":true,"token","name"} | {"ok":false,"error":"declined"|"timeout"}
 *      C → S  {"name","size"} + <size byte>     (diulang per file)
 *      S → C  {"ok":true,"name":<nama tersimpan>}
 *      C → S  {"end":true}
 *
 * Otorisasi: pengirim yang memindai QR membawa `key` sesi (hanya terlihat di layar penerima)
 * dan langsung diterima; pengirim lain (daftar Nearby) harus disetujui lewat notifikasi.
 */
object Direct {
    const val PORT = 47778
    const val MAGIC = 0x53425831 // "SBX1"
    const val BUF = 256 * 1024
}

object DirectServer {

    @Volatile var running = false
        private set

    /** Kunci sesi yang ikut di QR. Berganti tiap server dinyalakan. */
    @Volatile var sessionKey: String = ""
        private set

    private var socket: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool { r ->
        Thread(r, "sharebox-direct").apply { isDaemon = true }
    }

    @Synchronized
    fun start(ctx: Context): Boolean {
        if (running) return true
        return try {
            val ss = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(Direct.PORT))
            }
            socket = ss
            Pairing.clear()
            sessionKey = randomKey()
            running = true
            val app = ctx.applicationContext
            pool.execute {
                while (running) {
                    val client = try {
                        ss.accept()
                    } catch (_: Exception) {
                        break
                    }
                    pool.execute { runCatching { handle(app, client) } }
                }
            }
            true
        } catch (e: Exception) {
            running = false
            false
        }
    }

    @Synchronized
    fun stop() {
        running = false
        sessionKey = ""
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
    }

    private fun randomKey(): String {
        val chars = "abcdefghijkmnpqrstuvwxyz23456789"
        val rnd = SecureRandom()
        return (1..10).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
    }

    private fun handle(ctx: Context, sock: Socket) {
        sock.use {
            sock.tcpNoDelay = true
            sock.soTimeout = 60_000
            val input = DataInputStream(BufferedInputStream(sock.getInputStream(), Direct.BUF))
            val out = DataOutputStream(BufferedOutputStream(sock.getOutputStream()))
            if (input.readInt() != Direct.MAGIC) return
            val hello = JSONObject(input.readUTF())
            val peerId = hello.optString("id")
            if (peerId.isEmpty()) return
            val peerName = hello.optString("name", "Android").take(40)
            val ip = sock.inetAddress?.hostAddress

            fun reply(obj: JSONObject) {
                out.writeUTF(obj.toString())
                out.flush()
            }

            if (Pairing.isDeclined(ip)) {
                reply(JSONObject().put("ok", false).put("error", "declined"))
                return
            }
            val key = hello.optString("key")
            val token = hello.optString("token")
            val granted: String? = when {
                key.isNotEmpty() && key == sessionKey -> Pairing.grant(peerId)
                token.isNotEmpty() && token == Pairing.tokenFor(peerId) -> token
                else -> {
                    // Menunggu keputusan user bisa sampai 30 detik; jangan putus karena timeout baca.
                    val (t, error) = Pairing.requestApproval(ctx, peerId, peerName, ip)
                    if (t == null) {
                        reply(JSONObject().put("ok", false).put("error", error ?: "declined"))
                        return
                    }
                    t
                }
            }
            reply(JSONObject().put("ok", true).put("token", granted).put("name", Prefs.displayName()))

            while (true) {
                val h = JSONObject(input.readUTF())
                if (h.optBoolean("end")) break
                val name = Fs.sanitizeName(h.optString("name", "file"))
                val size = h.optLong("size", -1)
                if (size < 0) throw IllegalArgumentException("bad size")
                val saved = receiveFile(ctx, input, name, size)
                reply(JSONObject().put("ok", true).put("name", saved))
            }
        }
    }

    private fun receiveFile(ctx: Context, input: DataInputStream, name: String, size: Long): String {
        val dir = Storage.inbox(ctx)
        val target = Fs.unique(dir, name)
        val tmp = File(dir, ".sharebox-part-${System.currentTimeMillis()}-${java.util.concurrent.ThreadLocalRandom.current().nextInt(10_000)}")
        val trackId = TransferTracker.start(name, TransferDir.IN, size, "Inbox", target.absolutePath)
        try {
            FileOutputStream(tmp).use { fo ->
                val buf = ByteArray(Direct.BUF)
                var left = size
                var done = 0L
                var lastReport = 0L
                while (left > 0) {
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n < 0) throw EOFException("connection closed")
                    fo.write(buf, 0, n)
                    left -= n
                    done += n
                    if (done - lastReport >= 512 * 1024) {
                        lastReport = done
                        TransferTracker.progress(trackId, done)
                    }
                }
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = false)
                tmp.delete()
            }
            TransferTracker.finish(trackId)
            Notifier.received(ctx, target.name)
            return target.name
        } catch (t: Throwable) {
            tmp.delete()
            TransferTracker.fail(trackId, t.message ?: "failed")
            throw t
        }
    }
}

object DirectClient {

    /** Tujuan kirim. [key] ada kalau berasal dari QR (otomatis disetujui). */
    class Target(
        val id: String,
        val name: String,
        val host: String,
        val port: Int,
        val key: String? = null,
    )

    class Result(val ok: Int, val failed: Int, val error: String?)

    /** Blokir sampai selesai; panggil dari thread latar. */
    fun send(target: Target, items: List<PeerSender.Item>, folder: String?): Result {
        val sock = Socket()
        try {
            sock.connect(InetSocketAddress(target.host, target.port), 6000)
        } catch (e: ConnectException) {
            return failAll(items, folder, "server off")
        } catch (e: Exception) {
            return failAll(items, folder, "no response")
        }
        var ok = 0
        var failed = 0
        sock.use {
            try {
                sock.tcpNoDelay = true
                sock.soTimeout = 45_000 // penerima bisa menunggu user menekan Approve
                val out = DataOutputStream(BufferedOutputStream(sock.getOutputStream(), Direct.BUF))
                val input = DataInputStream(BufferedInputStream(sock.getInputStream()))
                out.writeInt(Direct.MAGIC)
                val hello = JSONObject()
                    .put("id", PeerDiscovery.myId())
                    .put("name", Prefs.displayName())
                target.key?.let { hello.put("key", it) }
                Pairing.tokenFor(target.id)?.let { hello.put("token", it) }
                out.writeUTF(hello.toString())
                out.flush()
                val auth = JSONObject(input.readUTF())
                if (!auth.optBoolean("ok")) {
                    val err = if (auth.optString("error") == "declined") "declined" else "no response"
                    return failAll(items, folder, err)
                }
                Pairing.storeToken(target.id, auth.optString("token"))
                sock.soTimeout = 60_000

                for ((index, item) in items.withIndex()) {
                    val size = item.file.length()
                    val trackId = TransferTracker.start(item.name, TransferDir.OUT, size, folder, item.file.absolutePath)
                    try {
                        out.writeUTF(JSONObject().put("name", item.name).put("size", size).toString())
                        var sent = 0L
                        var lastReport = 0L
                        item.file.inputStream().use { fi ->
                            val buf = ByteArray(Direct.BUF)
                            while (true) {
                                val n = fi.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                sent += n
                                if (sent - lastReport >= 512 * 1024) {
                                    lastReport = sent
                                    TransferTracker.progress(trackId, sent)
                                }
                            }
                        }
                        out.flush()
                        val ack = JSONObject(input.readUTF())
                        if (!ack.optBoolean("ok")) throw IllegalStateException("rejected")
                        TransferTracker.finish(trackId)
                        ok++
                    } catch (e: Exception) {
                        TransferTracker.fail(trackId, e.message ?: "failed")
                        failed++
                        // Koneksi sudah tidak sinkron: sisa file ditandai gagal, bukan dicoba lagi.
                        for (rest in items.drop(index + 1)) {
                            val id = TransferTracker.start(rest.name, TransferDir.OUT, rest.file.length(), folder, rest.file.absolutePath)
                            TransferTracker.fail(id, "connection lost")
                            failed++
                        }
                        return Result(ok, failed, null)
                    }
                }
                out.writeUTF(JSONObject().put("end", true).toString())
                out.flush()
            } catch (e: Exception) {
                return failAll(items.drop(ok + failed), folder, "no response", Result(ok, failed, null))
            }
        }
        return Result(ok, failed, null)
    }

    private fun failAll(
        items: List<PeerSender.Item>,
        folder: String?,
        error: String,
        base: Result? = null,
    ): Result {
        var failed = base?.failed ?: 0
        for (item in items) {
            val id = TransferTracker.start(item.name, TransferDir.OUT, item.file.length(), folder, item.file.absolutePath)
            TransferTracker.fail(id, error)
            failed++
        }
        return Result(base?.ok ?: 0, failed, error)
    }
}
