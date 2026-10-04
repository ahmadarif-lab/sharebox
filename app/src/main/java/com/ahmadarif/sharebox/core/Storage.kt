package com.ahmadarif.sharebox.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File

object Storage {

    fun hasAllFiles(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ctx.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    fun appRoot(ctx: Context): File {
        val base = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        return File(base, "share").apply { mkdirs() }
    }

    fun root(ctx: Context): File {
        if (Prefs.useAllFiles && hasAllFiles(ctx)) {
            val ext = Environment.getExternalStorageDirectory()
            if (ext.exists()) return ext
        }
        return appRoot(ctx)
    }

    fun inbox(ctx: Context): File = File(root(ctx), "Inbox").apply { mkdirs() }

    fun freeTotal(path: File): Pair<Long, Long> = try {
        val sf = StatFs(path.absolutePath)
        (sf.blockCountLong * sf.blockSizeLong) to (sf.availableBlocksLong * sf.blockSizeLong)
    } catch (e: Exception) {
        0L to 0L
    }
}
