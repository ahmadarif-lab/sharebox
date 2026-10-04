package id.my.bontot.sharebox.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.media.ThumbnailUtils
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import java.io.File
import java.util.concurrent.Executors

/**
 * Thumbnail gambar & video untuk daftar Files. Dimuat di thread latar (3 worker), disimpan di
 * cache memori, dan hasilnya dikirim balik ke main thread. Tanpa library tambahan.
 */
object Thumbs {

    enum class Kind { IMAGE, VIDEO }

    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "avif")
    private val VIDEO_EXT = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "3gp", "webm", "m4v", "ts")

    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 16).toInt().coerceIn(4 shl 20, 32 shl 20)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val pool = Executors.newFixedThreadPool(3) { r ->
        Thread(r, "sharebox-thumb").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }
    private val main = Handler(Looper.getMainLooper())
    private val inflight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun kindOf(name: String): Kind? = when (name.substringAfterLast('.', "").lowercase()) {
        in IMAGE_EXT -> Kind.IMAGE
        in VIDEO_EXT -> Kind.VIDEO
        else -> null
    }

    fun key(file: File): String = file.absolutePath + "|" + file.lastModified()

    fun cached(file: File): Bitmap? = cache.get(key(file))

    /**
     * Minta thumbnail. [wanted] dicek tepat sebelum decode supaya baris yang sudah di-recycle
     * saat scroll cepat tidak membuang waktu; [onLoaded] dipanggil di main thread.
     */
    fun load(file: File, kind: Kind, px: Int, wanted: () -> Boolean, onLoaded: (Bitmap) -> Unit) {
        val k = key(file)
        // Permintaan yang sama sedang berjalan: baris berikutnya akan kena cache saat dibind ulang.
        if (!inflight.add(k)) return
        pool.execute {
            try {
                if (!wanted()) return@execute
                val bmp = cache.get(k) ?: decode(file, kind, px)?.also { cache.put(k, it) } ?: return@execute
                main.post { onLoaded(bmp) }
            } finally {
                inflight.remove(k)
            }
        }
    }

    private fun decode(file: File, kind: Kind, px: Int): Bitmap? = try {
        val raw = if (Build.VERSION.SDK_INT >= 29) {
            if (kind == Kind.IMAGE) ThumbnailUtils.createImageThumbnail(file, Size(px, px), null)
            else ThumbnailUtils.createVideoThumbnail(file, Size(px, px), null)
        } else if (kind == Kind.IMAGE) {
            decodeSampled(file, px)
        } else {
            @Suppress("DEPRECATION")
            ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Images.Thumbnails.MINI_KIND)
        }
        raw?.let { square(it, px).let { sq -> if (kind == Kind.VIDEO) withPlayBadge(sq) else sq } }
    } catch (_: Throwable) {
        null
    }

    /** Frame video untuk poster di viewer (tanpa crop persegi / badge play). */
    fun videoFrame(file: File, px: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 29) {
            ThumbnailUtils.createVideoThumbnail(file, Size(px, px), null)
        } else {
            @Suppress("DEPRECATION")
            ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Images.Thumbnails.MINI_KIND)
        }
    } catch (_: Throwable) {
        null
    }

    /** Android 8–9: decode dengan inSampleSize supaya foto 12 MP tidak dimuat penuh. */
    private fun decodeSampled(file: File, px: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** Potong tengah jadi persegi px×px, jadi tampilan di list seragam. */
    private fun square(src: Bitmap, px: Int): Bitmap {
        val side = minOf(src.width, src.height)
        val x = (src.width - side) / 2
        val y = (src.height - side) / 2
        val cropped = Bitmap.createBitmap(src, x, y, side, side)
        return if (side == px) cropped else Bitmap.createScaledBitmap(cropped, px, px, true)
    }

    /** Lingkaran gelap transparan + segitiga putih di tengah, penanda "ini video". */
    private fun withPlayBadge(src: Bitmap): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val cx = out.width / 2f
        val cy = out.height / 2f
        val r = out.width * 0.22f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0x99000000.toInt()
        c.drawCircle(cx, cy, r, p)
        p.color = 0xFFFFFFFF.toInt()
        val t = r * 0.5f
        val path = Path().apply {
            moveTo(cx - t * 0.6f, cy - t)
            lineTo(cx - t * 0.6f, cy + t)
            lineTo(cx + t, cy)
            close()
        }
        c.drawPath(path, p)
        return out
    }
}
