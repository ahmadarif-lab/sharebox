package id.my.bontot.sharebox.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.res.ColorStateList
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import id.my.bontot.sharebox.core.TransferDir
import id.my.bontot.sharebox.files.Thumbs
import id.my.bontot.sharebox.core.TransferState
import id.my.bontot.sharebox.core.TransferTracker
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import id.my.bontot.sharebox.BuildConfig
import id.my.bontot.sharebox.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Req {
    const val NOTIF = 101
    const val STORAGE = 102
    const val HOTSPOT = 103
    const val HOTSPOT_AUTO = 104
}

object Ui {

    fun toast(ctx: Context, msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }

    fun alert(ctx: Context, title: String, msg: String) {
        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    fun confirm(ctx: Context, title: String, msg: String, onOk: () -> Unit) {
        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton(android.R.string.ok) { _, _ -> onOk() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun input(ctx: Context, title: String, initial: String, onOk: (String) -> Unit) {
        val edit = EditText(ctx)
        edit.setText(initial)
        edit.setSelection(initial.length)
        edit.isSingleLine = true
        val pad = dp(ctx, 20f)
        val container = FrameLayout(ctx)
        container.setPadding(pad, dp(ctx, 8f), pad, 0)
        container.addView(
            edit,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ -> onOk(edit.text.toString().trim()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun copy(ctx: Context, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ShareBox", text))
        toast(ctx, ctx.getString(R.string.copied, text))
    }

    fun bytes(n: Long): String {
        if (n < 1024) return "$n B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = n.toDouble() / 1024.0
        var i = 0
        while (v >= 1024 && i < units.lastIndex) {
            v /= 1024
            i++
        }
        val fmt = if (v >= 100) "%.0f %s" else "%.1f %s"
        return String.format(Locale.US, fmt, v, units[i])
    }

    fun date(t: Long): String =
        SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(t))

    /**
     * Isi satu baris transfer (dipakai tab Transfers dan aktivitas di Home). Arah transfer
     * dibedakan jelas: SENT = biru/ungu + panah naik, RECEIVED = mint + panah turun.
     */
    private fun roundedOutline(radiusDp: Float) = object : android.view.ViewOutlineProvider() {
        override fun getOutline(v: View, outline: android.graphics.Outline) {
            outline.setRoundRect(0, 0, v.width, v.height, dp(v.context, radiusDp).toFloat())
        }
    }

    /**
     * Tampilkan thumbnail gambar/video di [icon] (kotak membulat, crop penuh). Placeholder
     * ikon vektor dulu kalau belum ada di cache, lalu thumbnail menyusul dari thread latar.
     */
    fun bindThumb(icon: ImageView, key: String, file: File, kind: Thumbs.Kind, placeholderRes: Int) {
        val ctx = icon.context
        icon.tag = key
        icon.setPadding(0, 0, 0, 0)
        icon.setBackgroundResource(R.drawable.bg_path)
        icon.outlineProvider = roundedOutline(12f)
        icon.clipToOutline = true
        val hit = Thumbs.cached(file)
        if (hit != null) {
            icon.imageTintList = null
            icon.scaleType = ImageView.ScaleType.CENTER_CROP
            icon.setImageBitmap(hit)
            return
        }
        icon.setImageResource(placeholderRes)
        icon.imageTintList = ColorStateList.valueOf(ctx.getColor(R.color.accent))
        icon.scaleType = ImageView.ScaleType.CENTER_INSIDE
        Thumbs.load(file, kind, dp(ctx, 48f), { icon.tag == key }) { bmp ->
            if (icon.tag == key) {
                icon.imageTintList = null
                icon.scaleType = ImageView.ScaleType.CENTER_CROP
                icon.setImageBitmap(bmp)
            }
        }
    }

    /** Ikon vektor biasa di lingkaran berwarna (kebalikan dari [bindThumb]). */
    fun bindPlainIcon(icon: ImageView, key: String, iconRes: Int, bgRes: Int, tint: Int) {
        icon.tag = key
        val pad = dp(icon.context, 10f)
        icon.setPadding(pad, pad, pad, pad)
        icon.clipToOutline = false
        icon.scaleType = ImageView.ScaleType.CENTER_INSIDE
        icon.setBackgroundResource(bgRes)
        icon.imageTintList = ColorStateList.valueOf(tint)
        icon.setImageResource(iconRes)
    }

    /** Item transfer yang sudah punya file media di disk (gambar/video) — bisa dibuka di viewer. */
    fun viewablePath(item: TransferTracker.Item): String? {
        val path = item.path ?: return null
        if (Thumbs.kindOf(item.name) == null) return null
        val ready = item.dir == TransferDir.OUT || item.state == TransferState.DONE
        return if (ready && item.state != TransferState.FAILED && File(path).exists()) path else null
    }

    fun bindTransfer(ctx: Context, view: View, item: TransferTracker.Item) {
        val incoming = item.dir == TransferDir.IN
        val icon = view.findViewById<ImageView>(R.id.img_dir)
        val tag = view.findViewById<TextView>(R.id.tv_tag)
        val name = view.findViewById<TextView>(R.id.tv_name)
        val meta = view.findViewById<TextView>(R.id.tv_meta)
        val progress = view.findViewById<ProgressBar>(R.id.progress)

        val viewable = viewablePath(item)
        val kind = Thumbs.kindOf(item.name)
        if (viewable != null && kind != null) {
            // Gambar/video: thumbnail langsung; arah (Sent/Received) tetap terbaca dari tag di kanan.
            bindThumb(
                icon, "t${item.id}", File(viewable), kind,
                if (incoming) R.drawable.ic_download else R.drawable.ic_upload
            )
        } else {
            bindPlainIcon(
                icon, "t${item.id}",
                if (incoming) R.drawable.ic_download else R.drawable.ic_upload,
                if (incoming) R.drawable.bg_icon_recv else R.drawable.bg_icon_send,
                ctx.getColor(if (incoming) R.color.ok else R.color.accent)
            )
        }
        tag.setText(if (incoming) R.string.tag_received else R.string.tag_sent)
        tag.setBackgroundResource(if (incoming) R.drawable.bg_badge_recv else R.drawable.bg_badge_send)
        tag.setTextColor(ctx.getColor(if (incoming) R.color.ok else R.color.accent))

        val dest = item.folder?.takeIf { it.isNotEmpty() }?.let { " \u2192 $it" } ?: ""
        name.text = item.name
        meta.text = when (item.state) {
            TransferState.RUNNING -> {
                val size = if (item.total > 0) "${bytes(item.done)} / ${bytes(item.total)}" else bytes(item.done)
                if (item.paused) "Paused \u00B7 $size" else "${if (incoming) "Receiving" else "Sending"}\u2026 $size$dest"
            }
            TransferState.DONE ->
                "Done \u00B7 ${bytes(if (item.total > 0) item.total else item.done)}$dest"
            TransferState.FAILED -> "Failed: ${item.error ?: "?"}"
        }
        when {
            item.state == TransferState.DONE -> {
                progress.isIndeterminate = false
                progress.progress = 1000
                progress.progressTintList = ColorStateList.valueOf(ctx.getColor(R.color.ok))
            }
            item.state == TransferState.FAILED -> {
                progress.isIndeterminate = false
                progress.progress = (item.fraction * 1000).toInt()
                progress.progressTintList = ColorStateList.valueOf(ctx.getColor(R.color.danger))
            }
            item.total > 0 -> {
                progress.isIndeterminate = false
                progress.progress = (item.fraction * 1000).toInt()
                progress.progressTintList =
                    ColorStateList.valueOf(ctx.getColor(if (incoming) R.color.ok else R.color.accent))
            }
            else -> progress.isIndeterminate = true
        }
    }

    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density).toInt()

    /** Buka layar izin "semua file" (API 30+) atau minta izin storage runtime (API < 30). */
    fun grantAllFiles(act: android.app.Activity) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                act.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + act.packageName)
                    )
                )
            } catch (e: Exception) {
                act.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            act.requestPermissions(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                Req.STORAGE
            )
        }
    }

    fun isApk(name: String): Boolean = name.endsWith(".apk", ignoreCase = true)

    /**
     * Buka file [file]. APK langsung ke pemasang paket sistem; Android 8+ mewajibkan izin
     * "Install unknown apps" per-aplikasi, jadi kalau belum aktif user diarahkan ke halamannya dulu.
     */
    fun openFile(ctx: Context, file: File) {
        if (isApk(file.name)) {
            installApk(ctx, file)
            return
        }
        viewFile(ctx, file)
    }

    private fun installApk(ctx: Context, file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(ctx)
                .setTitle(R.string.install_title)
                .setMessage(R.string.install_msg)
                .setPositiveButton(R.string.install_open_settings) { _, _ ->
                    try {
                        ctx.startActivity(
                            Intent(
                                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + ctx.packageName)
                            )
                        )
                    } catch (_: Exception) {
                        toast(ctx, ctx.getString(R.string.no_app_open))
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        viewFile(ctx, file, "application/vnd.android.package-archive")
    }

    private fun viewFile(ctx: Context, file: File, forcedMime: String? = null) {
        val uri = Uri.Builder()
            .scheme("content")
            .authority(BuildConfig.APPLICATION_ID + ".files")
            .path(file.absolutePath)
            .build()
        val mime = forcedMime ?: MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            toast(ctx, ctx.getString(R.string.no_app_open))
        }
    }
}
