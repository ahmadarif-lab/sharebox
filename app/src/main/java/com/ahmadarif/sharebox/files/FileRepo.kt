package com.ahmadarif.sharebox.files

import android.content.Context
import android.os.Environment
import com.ahmadarif.sharebox.R
import java.io.File

object FileRepo {

    data class Entry(
        val name: String,
        val file: File?,
        val dir: Boolean,
        val size: Long,
        val mtime: Long,
        val iconRes: Int,
        val key: String,
        /** Nama file di sisi penerima. Untuk aplikasi: "Nama_versi.apk", bukan "base.apk". */
        val sendName: String = name,
        /** Keterangan tambahan di baris (mis. "Split APK"). */
        val note: String? = null,
    )

    enum class Cat(val labelRes: Int) {
        IMAGES(R.string.cat_images),
        VIDEOS(R.string.cat_videos),
        AUDIO(R.string.cat_audio),
        DOCS(R.string.cat_docs),
        APPS(R.string.cat_apps),
        DOWNLOADS(R.string.cat_downloads),
    }

    fun listDir(dir: File): List<Entry> =
        (dir.listFiles() ?: emptyArray())
            .asSequence()
            .filter { !it.name.startsWith(".") }
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            .take(3000)
            .map { f ->
                Entry(
                    name = f.name,
                    file = f,
                    dir = f.isDirectory,
                    size = if (f.isFile) f.length() else 0L,
                    mtime = f.lastModified(),
                    iconRes = iconFor(f.name, f.isDirectory),
                    key = f.absolutePath,
                )
            }
            .toList()

    fun category(ctx: Context, cat: Cat): List<Entry> {
        if (cat == Cat.APPS) return apps(ctx)
        val ext = Environment.getExternalStorageDirectory()
        if (!ext.exists()) return emptyList()
        val roots = when (cat) {
            Cat.IMAGES -> listOf("DCIM", "Pictures", "Download", "Download/Telegram", "Pictures/Screenshots")
            Cat.VIDEOS -> listOf("DCIM", "Movies", "Download", "Pictures")
            Cat.AUDIO -> listOf("Music", "Download", "Ringtones", "Recordings", "Notifications", "Alarms")
            Cat.DOCS -> listOf("Documents", "Download")
            Cat.DOWNLOADS -> listOf("Download", "Download/Telegram")
            else -> emptyList()
        }
        val out = ArrayList<Entry>()
        for (rel in roots) {
            if (out.size >= 800) break
            scan(File(ext, rel), cat, out, 0)
        }
        return out.sortedByDescending { it.mtime }
    }

    private fun scan(dir: File, cat: Cat, out: MutableList<Entry>, depth: Int) {
        if (depth > 3 || out.size >= 800) return
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                scan(f, cat, out, depth + 1)
            } else if (matches(f.name, cat)) {
                out.add(
                    Entry(
                        name = f.name,
                        file = f,
                        dir = false,
                        size = f.length(),
                        mtime = f.lastModified(),
                        iconRes = iconFor(f.name, false),
                        key = f.absolutePath,
                    )
                )
            }
            if (out.size >= 800) return
        }
    }

    private fun matches(name: String, cat: Cat): Boolean {
        val e = name.substringAfterLast('.', "").lowercase()
        return when (cat) {
            Cat.IMAGES -> e in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "avif")
            Cat.VIDEOS -> e in setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "3gp", "webm", "m4v", "ts")
            Cat.AUDIO -> e in setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "amr", "mid")
            Cat.DOCS -> e in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "md", "rtf", "odt")
            Cat.DOWNLOADS -> true
            else -> false
        }
    }

    private fun apps(ctx: Context): List<Entry> {
        val pm = ctx.packageManager
        val out = ArrayList<Entry>()
        for (app in pm.getInstalledApplications(0)) {
            if (pm.getLaunchIntentForPackage(app.packageName) == null) continue
            val src = app.publicSourceDir ?: app.sourceDir ?: continue
            val f = File(src)
            if (!f.isFile) continue
            val label = pm.getApplicationLabel(app).toString()
            val version = runCatching { pm.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
            // Semua APK bernama "base.apk"; kirim dengan nama aplikasinya supaya bisa dikenali.
            val safe = (label + (version?.takeIf { it.isNotBlank() }?.let { "_$it" } ?: ""))
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            // App bundle: base.apk saja belum tentu bisa dipasang berdiri sendiri.
            val split = app.splitSourceDirs?.isNotEmpty() == true
            out.add(
                Entry(
                    name = label,
                    file = f,
                    dir = false,
                    size = f.length(),
                    mtime = f.lastModified(),
                    iconRes = R.drawable.ic_apk,
                    key = "app:" + app.packageName,
                    sendName = "$safe.apk",
                    note = if (split) "Split APK" else version,
                )
            )
        }
        return out.sortedBy { it.name.lowercase() }
    }

    fun iconFor(name: String, dir: Boolean): Int {
        if (dir) return R.drawable.ic_folder
        return when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "avif" -> R.drawable.ic_image
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "3gp", "webm", "m4v", "ts" -> R.drawable.ic_video
            "mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "amr", "mid" -> R.drawable.ic_music
            "apk" -> R.drawable.ic_apk
            else -> R.drawable.ic_doc
        }
    }
}
