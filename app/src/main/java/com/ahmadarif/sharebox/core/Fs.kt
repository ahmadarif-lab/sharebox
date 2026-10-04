package com.ahmadarif.sharebox.core

import java.io.File

object Fs {

    /** Resolve a path relative to root; throws SecurityException when it escapes the root. */
    fun resolve(root: File, rel: String): File {
        val clean = rel.replace('\\', '/').trim().trimStart('/')
        val f = if (clean.isEmpty()) root else File(root, clean)
        val rc = root.canonicalFile
        val fc = f.canonicalFile
        if (fc != rc && !fc.path.startsWith(rc.path + File.separator)) {
            throw SecurityException("Outside the shared folder")
        }
        return fc
    }

    fun rel(root: File, f: File): String {
        val rc = root.canonicalFile.path
        val fc = f.canonicalFile.path
        return if (fc == rc) "" else fc.removePrefix(rc).trimStart(File.separatorChar).replace(File.separatorChar, '/')
    }

    fun sanitizeName(n: String): String {
        var s = n.replace('/', '_').replace('\\', '_').replace('\u0000', '_').trim()
        if (s.isEmpty() || s == "." || s == "..") s = "unnamed"
        if (s.length > 180) s = s.take(180)
        return s
    }

    /** Unique name inside dir: "movie.mp4" -> "movie (1).mp4" */
    fun unique(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            f = File(dir, "$base ($i)$ext")
            if (!f.exists()) return f
            i++
        }
    }
}
