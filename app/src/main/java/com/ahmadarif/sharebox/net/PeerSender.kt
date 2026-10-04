package com.ahmadarif.sharebox.net

import android.os.Handler
import android.os.Looper
import java.io.File

object PeerSender {

    /** File + nama yang dilihat penerima (bisa beda dari nama file di disk, mis. APK aplikasi). */
    class Item(val file: File, val name: String = file.name)

    /** Kirim lewat protokol Direct (bukan HTTP); [onDone] dipanggil di main thread. */
    fun send(
        items: List<Item>,
        target: DirectClient.Target,
        folder: String?,
        onDone: (ok: Int, failed: Int, error: String?) -> Unit,
    ) {
        Thread({
            val r = DirectClient.send(target, items, folder)
            Handler(Looper.getMainLooper()).post { onDone(r.ok, r.failed, r.error) }
        }, "sharebox-send").apply { isDaemon = true }.start()
    }
}

fun PeerDiscovery.Peer.toTarget(): DirectClient.Target = DirectClient.Target(id, name, host, port)
