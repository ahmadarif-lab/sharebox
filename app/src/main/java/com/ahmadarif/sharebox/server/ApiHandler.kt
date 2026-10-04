package com.ahmadarif.sharebox.server

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.ahmadarif.sharebox.App
import com.ahmadarif.sharebox.BuildConfig
import com.ahmadarif.sharebox.MainActivity
import com.ahmadarif.sharebox.R
import com.ahmadarif.sharebox.core.Fs
import com.ahmadarif.sharebox.core.Prefs
import com.ahmadarif.sharebox.core.Storage
import com.ahmadarif.sharebox.core.TransferDir
import com.ahmadarif.sharebox.core.TransferTracker
import com.ahmadarif.sharebox.net.NetInfo
import com.ahmadarif.sharebox.net.Pairing
import com.ahmadarif.sharebox.net.PeerDiscovery
import com.ahmadarif.sharebox.net.Qr
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.RandomAccessFile
import java.net.URLEncoder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ApiHandler(private val ctx: Context) {

    fun handle(req: HttpRequest, res: HttpResult) {
        try {
            when (req.method) {
                "GET", "HEAD" -> when (req.path) {
                    "/", "/index.html" -> asset(res, "index.html")
                    "/style.css" -> asset(res, "style.css")
                    "/app.js" -> asset(res, "app.js")
                    "/icon.png" -> asset(res, "icon.png")
                    "/favicon.ico" -> {
                        res.status = 204
                        res.statusText = "No Content"
                        res.bytes = ByteArray(0)
                        res.length = 0
                    }
                    "/api/list" -> list(req, res)
                    "/api/download" -> download(req, res)
                    "/api/zip" -> zip(req, res)
                    "/api/info" -> info(res)
                    "/api/browse" -> browse(res)
                    "/api/progress" -> progress(res)
                    "/api/qr" -> qr(req, res)
                    "/api/find" -> find(req, res)
                    "/api/peers" -> peers(res)
                    else -> notFound(res)
                }
                "PUT" -> if (req.path == "/api/upload") upload(req, res) else notFound(res)
                "POST" -> when (req.path) {
                    "/api/mkdir" -> mkdir(req, res)
                    "/api/rename" -> rename(req, res)
                    "/api/delete" -> delete(req, res)
                    "/api/pause" -> pause(req, res)
                    "/api/resume" -> resume(req, res)
                    "/api/cancel" -> cancel(req, res)
                    "/api/pair/request" -> pairRequest(req, res)
                    else -> notFound(res)
                }
                else -> {
                    res.status = 405
                    res.statusText = "Method Not Allowed"
                    res.sendJson(JSONObject().put("error", "method not supported"))
                }
            }
        } catch (e: SecurityException) {
            res.fail(403, "Forbidden", e.message ?: "forbidden")
        } catch (e: FileNotFoundException) {
            res.fail(404, "Not Found", "not found")
        } catch (t: Throwable) {
            res.fail(500, "Internal Server Error", t.message ?: "error")
        }
    }

    private fun root(): File = Storage.root(ctx)

    // ---------- static ----------

    private fun asset(res: HttpResult, name: String) {
        val data = ctx.assets.open("web/$name").use { it.readBytes() }
        res.mime = when (name.substringAfterLast('.', "")) {
            "html" -> "text/html; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "js" -> "application/javascript; charset=utf-8"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "application/octet-stream"
        }
        res.bytes = data
        res.length = data.size.toLong()
        res.headers["Cache-Control"] = "no-cache"
    }

    private fun notFound(res: HttpResult) = res.fail(404, "Not Found", "not found")

    // ---------- API ----------

    private fun list(req: HttpRequest, res: HttpResult) {
        val r = root()
        val dir = Fs.resolve(r, req.q("path") ?: "")
        if (!dir.isDirectory) throw FileNotFoundException()
        val arr = JSONArray()
        dir.listFiles()
            ?.asSequence()
            ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            ?.take(5000)
            ?.forEach { f ->
                arr.put(
                    JSONObject()
                        .put("name", f.name)
                        .put("dir", f.isDirectory)
                        .put("size", if (f.isFile) f.length() else 0L)
                        .put("mtime", f.lastModified())
                )
            }
        val relPath = Fs.rel(r, dir)
        val obj = JSONObject().put("path", relPath).put("entries", arr)
        obj.put("parent", if (relPath.isEmpty()) JSONObject.NULL else Fs.rel(r, dir.parentFile ?: r))
        res.sendJson(obj)
    }

    private fun download(req: HttpRequest, res: HttpResult) {
        val file = Fs.resolve(root(), req.q("path") ?: "")
        if (!file.isFile) throw FileNotFoundException()
        val mime = mimeOf(file.name)
        val inline = req.q("inline") == "1"

        // Browser tidak bisa decode HEIC/HEIF — konversi ke JPEG untuk preview.
        val ext = file.name.substringAfterLast('.', "").lowercase()
        if (inline && (ext == "heic" || ext == "heif") && heicPreview(file, res)) return

        val cd = if (inline) "inline" else "attachment"
        res.headers["Content-Disposition"] =
            "$cd; filename*=UTF-8''" + URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
        res.headers["Accept-Ranges"] = "bytes"
        res.headers["Cache-Control"] = "no-store"
        res.mime = mime

        val total = file.length()
        var start = 0L
        var end = total - 1
        var partial = false
        val range = req.header("range")
        if (!range.isNullOrBlank() && range.startsWith("bytes=")) {
            val spec = range.removePrefix("bytes=").split(',').first().trim()
            val dash = spec.indexOf('-')
            if (dash >= 0) {
                val s = spec.substring(0, dash).trim()
                val e = spec.substring(dash + 1).trim()
                try {
                    if (s.isEmpty()) {
                        val suffix = e.toLong()
                        start = (total - suffix).coerceAtLeast(0)
                        end = total - 1
                    } else {
                        start = s.toLong()
                        end = if (e.isEmpty()) total - 1 else minOf(e.toLong(), total - 1)
                    }
                    partial = true
                } catch (_: NumberFormatException) {
                    partial = false
                }
            }
            if (partial && (start > end || start >= total)) {
                res.status = 416
                res.statusText = "Range Not Satisfiable"
                res.headers["Content-Range"] = "bytes */$total"
                res.bytes = ByteArray(0)
                res.length = 0
                return
            }
        }

        // Preview/streaming (inline) tidak dihitung sebagai transfer — hanya download asli yang di-track.
        val trackId = if (inline) -1L else TransferTracker.start(
            file.name, TransferDir.OUT, end - start + 1,
            Fs.rel(root(), file.parentFile ?: root())
        )
        val raf = RandomAccessFile(file, "r")
        raf.seek(start)
        val count = end - start + 1
        var reported = 0L
        var finished = false
        var started = false
        val body = object : InputStream() {
            private var left = count

            override fun read(): Int {
                val b = ByteArray(1)
                val r = read(b, 0, 1)
                return if (r <= 0) -1 else b[0].toInt() and 0xFF
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (left <= 0) {
                    if (!finished) {
                        finished = true
                        if (trackId > 0) TransferTracker.finish(trackId)
                    }
                    return -1
                }
                started = true
                val r = raf.read(b, off, minOf(len.toLong(), left).toInt())
                if (r > 0) {
                    left -= r
                    val done = count - left
                    if (done - reported >= 512 * 1024) {
                        reported = done
                        if (trackId > 0) TransferTracker.progress(trackId, done)
                    }
                }
                return r
            }

            override fun close() {
                try {
                    raf.close()
                } catch (_: Exception) {
                }
                if (!finished) {
                    finished = true
                    if (!started) return // body dikirim lewat sendfile; bukan urusan stream ini
                    if (trackId > 0) {
                        if (left > 0) TransferTracker.fail(trackId, "cancelled") else TransferTracker.finish(trackId)
                    }
                }
            }
        }
        res.length = count
        res.stream = body
        res.trackId = trackId
        res.file = file
        res.fileOffset = start
        res.fileCount = count
        res.onProgress = { done -> if (trackId > 0) TransferTracker.progress(trackId, done) }
        res.onDone = { if (trackId > 0) TransferTracker.finish(trackId) }
        res.onAbort = { if (trackId > 0) TransferTracker.fail(trackId, "cancelled") }
        if (partial) {
            res.status = 206
            res.statusText = "Partial Content"
            res.headers["Content-Range"] = "bytes $start-$end/$total"
        }
    }

    private fun zip(req: HttpRequest, res: HttpResult) {
        val r = root()
        val dir = Fs.resolve(r, req.q("path") ?: "")
        if (!dir.isDirectory) throw FileNotFoundException()
        val zipName = (if (dir == r) "sharebox" else dir.name) + ".zip"

        val pipedOut = PipedOutputStream()
        val pipedIn = PipedInputStream(pipedOut, 1 shl 20)
        Thread({
            try {
                ZipOutputStream(BufferedOutputStream(pipedOut, 1 shl 16)).use { zos ->
                    dir.walkTopDown().forEach { f ->
                        if (f == dir) return@forEach
                        val rel = f.relativeTo(dir).path.replace(File.separatorChar, '/')
                        if (f.isDirectory) {
                            if (f.listFiles().isNullOrEmpty()) {
                                zos.putNextEntry(ZipEntry("$rel/"))
                                zos.closeEntry()
                            }
                        } else {
                            zos.putNextEntry(ZipEntry(rel))
                            f.inputStream().use { it.copyTo(zos, 1 shl 16) }
                            zos.closeEntry()
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    pipedOut.close()
                } catch (_: Exception) {
                }
            }
        }, "sharebox-zip").apply { isDaemon = true }.start()

        res.mime = "application/zip"
        res.length = -1
        res.stream = pipedIn
        res.headers["Content-Disposition"] =
            "attachment; filename*=UTF-8''" + URLEncoder.encode(zipName, "UTF-8").replace("+", "%20")
    }

    private fun info(res: HttpResult) {
        val r = root()
        val (total, free) = Storage.freeTotal(r)
        res.sendJson(
            JSONObject()
                .put("device", Prefs.displayName())
                .put("model", NetInfo.deviceName())
                .put("app", BuildConfig.VERSION_NAME)
                .put("android", Build.VERSION.RELEASE)
                .put("root", r.absolutePath)
                .put("port", Prefs.port)
                .put("allFiles", Storage.hasAllFiles(ctx))
                .put("totalBytes", total)
                .put("freeBytes", free)
        )
    }

    private fun browse(res: HttpResult) {
        val r = root()
        val arr = JSONArray()
        val candidates = listOf(
            "Download", "DCIM", "Pictures", "Movies", "Music", "Documents", "Inbox",
        )
        for (rel in candidates) {
            if (File(r, rel).isDirectory) {
                arr.put(JSONObject().put("label", rel).put("path", rel))
            }
        }
        res.sendJson(JSONObject().put("root", r.absolutePath).put("shortcuts", arr))
    }

    private fun progress(res: HttpResult) {
        val arr = JSONArray()
        for (item in TransferTracker.items()) {
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("dir", if (item.dir == TransferDir.IN) "in" else "out")
                    .put("state", item.state.name.lowercase())
                    .put("paused", item.paused)
                    .put("done", item.done)
                    .put("total", item.total)
                    .put("speed", item.speed)
                    .put("started", item.startedAt)
                    .put("folder", item.folder ?: JSONObject.NULL)
                    .put("error", item.error ?: JSONObject.NULL)
            )
        }
        res.sendJson(JSONObject().put("transfers", arr))
    }

    private fun pause(req: HttpRequest, res: HttpResult) {
        val id = JSONObject(req.bodyString()).optLong("id", -1)
        if (id <= 0) throw IllegalArgumentException("invalid id")
        TransferChannels.pause(id)
        TransferTracker.setPaused(id, true)
        res.sendJson(JSONObject().put("ok", true))
    }

    private fun resume(req: HttpRequest, res: HttpResult) {
        val id = JSONObject(req.bodyString()).optLong("id", -1)
        if (id <= 0) throw IllegalArgumentException("invalid id")
        TransferChannels.resume(id)
        TransferTracker.setPaused(id, false)
        res.sendJson(JSONObject().put("ok", true))
    }

    private fun cancel(req: HttpRequest, res: HttpResult) {
        val id = JSONObject(req.bodyString()).optLong("id", -1)
        if (id <= 0) throw IllegalArgumentException("invalid id")
        val aborted = TransferChannels.cancel(id)
        TransferTracker.fail(id, "cancelled")
        res.sendJson(JSONObject().put("ok", true).put("aborted", aborted))
    }

    // ---------- QR / find / peers ----------

    /** Decode HEIC/HEIF jadi JPEG (max 2048px) buat preview di browser. */
    private fun heicPreview(file: File, res: HttpResult): Boolean {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            var sample = 1
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxDim / sample > 2048) sample *= 2
            val bmp = BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return false
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            bmp.recycle()
            val data = out.toByteArray()
            res.mime = "image/jpeg"
            res.bytes = data
            res.length = data.size.toLong()
            res.headers["Cache-Control"] = "max-age=300"
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun qr(req: HttpRequest, res: HttpResult) {
        val text = req.q("text") ?: throw IllegalArgumentException("text required")
        if (text.length > 512) throw IllegalArgumentException("text too long")
        val size = (req.q("size")?.toIntOrNull() ?: 320).coerceIn(64, 1024)
        val bmp = Qr.bitmap(text, size)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        val data = out.toByteArray()
        res.mime = "image/png"
        res.bytes = data
        res.length = data.size.toLong()
        res.headers["Cache-Control"] = "max-age=600"
    }

    @Volatile
    private var scanCache: Triple<Long, Long, List<File>>? = null

    /**
     * Scan filesystem sebagai pelengkap MediaStore (menangkap file yang tidak ter-index,
     * mis. mp3 hasil copy manual). Tiap folder top-level dapat jatah sendiri supaya folder
     * besar seperti DCIM tidak menghabiskan kuota dan folder Music tetap tercakup.
     */
    private fun scanAll(): List<File> {
        val cached = scanCache
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.first < cached.second) return cached.third

        val out = ArrayList<File>(4096)
        val perDirBudget = 5000
        val totalCap = 40000
        val deadline = now + 10_000
        var truncated = false

        val rootDir = root()
        rootDir.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") && it.name != "Android" }
            ?.forEach { top ->
                val before = out.size
                fun walk(dir: File, depth: Int) {
                    if (out.size - before >= perDirBudget || out.size >= totalCap) {
                        truncated = true
                        return
                    }
                    if (System.currentTimeMillis() > deadline) {
                        truncated = true
                        return
                    }
                    val files = dir.listFiles() ?: return
                    for (f in files) {
                        if (f.name.startsWith(".")) continue
                        if (f.isDirectory) {
                            if (f.name == ".thumbnails") continue
                            if (depth < 5) walk(f, depth + 1)
                        } else {
                            out.add(f)
                        }
                        if (out.size - before >= perDirBudget || out.size >= totalCap) {
                            truncated = true
                            return
                        }
                    }
                }
                walk(top, 0)
            }

        val ttl = when {
            out.isEmpty() -> 0L          // scan gagal/kosong: jangan di-cache
            !truncated -> 60_000L        // scan lengkap
            else -> 10_000L              // terpotong tapi ada isinya
        }
        if (ttl > 0) scanCache = Triple(now, ttl, out)
        return out
    }

    /**
     * Kategori & search ambil dari MediaStore (index media Android) — instan dan lengkap,
     * tidak bergantung pada scan filesystem yang bisa terpotong di storage besar.
     */
    private fun storeEntries(cat: String, q: String, rootDir: File): List<File> {
        val uris = when (cat) {
            "images" -> listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            "videos" -> listOf(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            "music" -> listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
            "documents", "archives", "others" -> listOf(MediaStore.Files.getContentUri("external"))
            // recent / search lintas kategori: MediaStore.Files tidak memuat file media,
            // jadi semua koleksi harus di-query.
            else -> listOf(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Files.getContentUri("external"),
            )
        }
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
        val rootPrefix = rootDir.absolutePath
        val seen = HashSet<String>(1024)
        val out = ArrayList<File>(1024)
        for (uri in uris) {
            var rows = 0
            try {
                ctx.contentResolver.query(
                    uri, projection, null, null,
                    MediaStore.MediaColumns.DATE_MODIFIED + " DESC"
                )?.use { c ->
                    val iData = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (iData < 0) return@use
                    while (c.moveToNext() && rows < 4000) {
                        val path = c.getString(iData) ?: continue
                        rows++
                        // filter nama dari path asli: _display_name di koleksi audio bukan nama file
                        if (q.isNotEmpty() && !path.substringAfterLast('/').lowercase().contains(q)) continue
                        if (path != rootPrefix && !path.startsWith(rootPrefix + File.separator)) continue
                        val f = File(path)
                        if (!f.isFile) continue
                        if (seen.add(path)) out.add(f)
                    }
                }
            } catch (_: Exception) {
                // izin/media store tidak tersedia -> caller akan fallback ke scan
            }
        }
        return out
    }

    private fun find(req: HttpRequest, res: HttpResult) {
        val cat = (req.q("cat") ?: "recent").lowercase()
        val q = (req.q("q") ?: "").trim().lowercase()
        val limit = (req.q("limit")?.toIntOrNull() ?: 300).coerceIn(1, 1000)
        val r = root()

        // Gabungkan MediaStore (cepat, ter-index) + scan filesystem (menangkap yang belum ter-index).
        val merged = LinkedHashMap<String, File>(2048)
        runCatching { storeEntries(cat, q, r) }.getOrDefault(emptyList())
            .forEach { merged[it.absolutePath] = it }
        runCatching { scanAll() }.getOrDefault(emptyList())
            .forEach { merged.putIfAbsent(it.absolutePath, it) }
        val candidates = merged.values

        val arr = JSONArray()
        var count = 0
        for (f in candidates.sortedByDescending { it.lastModified() }) {
            if (count >= limit) break
            if (q.isNotEmpty() && !f.name.lowercase().contains(q)) continue
            val e = f.name.substringAfterLast('.', "").lowercase()
            val ok = when (cat) {
                "images" -> e in IMAGE_EXT
                "videos" -> e in VIDEO_EXT
                "music" -> e in AUDIO_EXT
                "documents" -> e in DOC_EXT
                "archives" -> e in ARCHIVE_EXT
                "others" -> e !in IMAGE_EXT && e !in VIDEO_EXT && e !in AUDIO_EXT &&
                    e !in DOC_EXT && e !in ARCHIVE_EXT
                else -> true
            }
            if (!ok) continue
            arr.put(
                JSONObject()
                    .put("name", f.name)
                    .put("path", Fs.rel(r, f))
                    .put("dir", false)
                    .put("size", f.length())
                    .put("mtime", f.lastModified())
            )
            count++
        }
        res.sendJson(JSONObject().put("cat", cat).put("q", q).put("entries", arr))
    }

    private fun peers(res: HttpResult) {
        val arr = JSONArray()
        for (p in PeerDiscovery.list()) {
            arr.put(JSONObject().put("name", p.name).put("host", p.host).put("port", p.port))
        }
        res.sendJson(JSONObject().put("peers", arr))
    }

    /**
     * Permintaan pairing dari HP lain. Permintaan ini MENUNGGU pemilik HP ini menekan
     * Approve/Decline (maks 30 detik) lalu membalas token kalau disetujui.
     */
    private fun pairRequest(req: HttpRequest, res: HttpResult) {
        val body = JSONObject(req.bodyString())
        val peerId = body.optString("id")
        if (peerId.isEmpty()) throw IllegalArgumentException("id required")
        val peerName = body.optString("name", "Android").take(40)
        val (token, error) = Pairing.requestApproval(ctx, peerId, peerName, req.remote)
        if (token == null) {
            res.sendJson(JSONObject().put("ok", false).put("error", error ?: "declined"))
        } else {
            res.sendJson(
                JSONObject()
                    .put("ok", true)
                    .put("token", token)
                    .put("name", Prefs.displayName())
            )
        }
    }

    private fun upload(req: HttpRequest, res: HttpResult) {
        // Pairing: device yang ditolak tidak boleh menaruh file selama sesi ini.
        if (Pairing.isDeclined(req.remote)) throw SecurityException("this device was declined")
        val r = root()
        val dir = Fs.resolve(r, req.q("path") ?: "")
        if (!dir.isDirectory && !dir.mkdirs()) throw SecurityException("Cannot create destination folder")
        val name = Fs.sanitizeName(req.q("name") ?: "upload.bin")
        val target = Fs.unique(dir, name)
        val tmp = File(dir, ".sharebox-part-${System.currentTimeMillis()}-${(0..9999).random()}")
        val trackId = TransferTracker.start(name, TransferDir.IN, req.contentLength, Fs.rel(r, dir))
        TransferChannels.register(trackId, req.channel)
        var done = 0L
        var lastReport = 0L
        try {
            FileOutputStream(tmp).use { out ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    while (TransferChannels.isPaused(trackId)) {
                        if (!req.channel.isOpen) throw java.io.IOException("cancelled")
                        Thread.sleep(150)
                    }
                    val n = req.body.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
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
            notifyReceived(target.name)
            res.sendJson(JSONObject().put("ok", true).put("name", target.name).put("size", done))
        } catch (t: Throwable) {
            tmp.delete()
            TransferTracker.fail(trackId, t.message ?: "failed")
            throw t
        } finally {
            TransferChannels.unregister(trackId)
        }
    }

    private fun mkdir(req: HttpRequest, res: HttpResult) {
        val body = JSONObject(req.bodyString())
        val parent = Fs.resolve(root(), body.optString("path"))
        if (!parent.isDirectory) throw FileNotFoundException()
        val dir = Fs.unique(parent, Fs.sanitizeName(body.optString("name", "New folder")))
        if (!dir.mkdirs()) throw IllegalStateException("Failed to create folder")
        res.sendJson(JSONObject().put("ok", true).put("name", dir.name))
    }

    private fun rename(req: HttpRequest, res: HttpResult) {
        val body = JSONObject(req.bodyString())
        val src = Fs.resolve(root(), body.optString("path"))
        if (!src.exists()) throw FileNotFoundException()
        val parent = src.parentFile ?: throw IllegalArgumentException("invalid path")
        val newName = Fs.sanitizeName(body.optString("newName", src.name))
        var dest = File(parent, newName)
        if (dest.canonicalPath != src.canonicalPath && dest.exists()) {
            dest = Fs.unique(parent, newName)
        }
        if (!src.renameTo(dest)) throw IllegalStateException("Rename failed")
        res.sendJson(JSONObject().put("ok", true).put("name", dest.name))
    }

    private fun delete(req: HttpRequest, res: HttpResult) {
        val body = JSONObject(req.bodyString())
        val arr = body.optJSONArray("paths") ?: JSONArray()
        val r = root().canonicalFile
        var deleted = 0
        for (i in 0 until arr.length()) {
            val f = Fs.resolve(root(), arr.optString(i))
            if (f.canonicalFile == r) throw SecurityException("Cannot delete the root")
            if (f.exists() && f.deleteRecursively()) deleted++
        }
        res.sendJson(JSONObject().put("ok", true).put("deleted", deleted))
    }

    // ---------- helpers ----------

    private fun notifyReceived(name: String) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val pi = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = android.app.Notification.Builder(ctx, App.CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_share)
                .setContentTitle(ctx.getString(R.string.notif_received, name))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(App.NOTIF_TRANSFER, notif)
        } catch (_: Exception) {
        }
    }

    private fun mimeOf(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "mkv" -> "video/x-matroska"
            "flac" -> "audio/flac"
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    companion object {
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "avif", "svg")
        private val VIDEO_EXT = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "3gp", "webm", "m4v", "ts")
        private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "amr", "mid")
        private val DOC_EXT = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "md", "rtf", "odt")
        private val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "gz", "xz", "bz2", "iso")
    }
}
