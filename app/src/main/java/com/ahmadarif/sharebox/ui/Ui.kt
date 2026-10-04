package com.ahmadarif.sharebox.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import com.ahmadarif.sharebox.BuildConfig
import com.ahmadarif.sharebox.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Req {
    const val NOTIF = 101
    const val STORAGE = 102
    const val HOTSPOT = 103
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

    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun openFile(ctx: Context, file: File) {
        val uri = Uri.Builder()
            .scheme("content")
            .authority(BuildConfig.APPLICATION_ID + ".files")
            .path(file.absolutePath)
            .build()
        val mime = MimeTypeMap.getSingleton()
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
