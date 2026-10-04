package com.ahmadarif.sharebox.server

import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry koneksi yang lagi dipakai transfer aktif — biar transfer bisa
 * di-pause (berhenti baca/tulis), di-resume, atau di-cancel (socket ditutup).
 */
object TransferChannels {

    private val channels = ConcurrentHashMap<Long, SocketChannel>()
    private val paused = ConcurrentHashMap.newKeySet<Long>()

    fun register(id: Long, ch: SocketChannel) {
        if (id > 0) channels[id] = ch
    }

    fun unregister(id: Long) {
        if (id > 0) {
            channels.remove(id)
            paused.remove(id)
        }
    }

    fun cancel(id: Long): Boolean {
        paused.remove(id)
        val ch = channels.remove(id) ?: return false
        return try {
            ch.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun pause(id: Long) {
        if (id > 0) paused.add(id)
    }

    fun resume(id: Long) {
        paused.remove(id)
    }

    fun isPaused(id: Long): Boolean = id > 0 && paused.contains(id)
}
