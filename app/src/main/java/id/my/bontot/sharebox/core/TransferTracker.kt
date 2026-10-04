package id.my.bontot.sharebox.core

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

enum class TransferDir { IN, OUT }

enum class TransferState { RUNNING, DONE, FAILED }

object TransferTracker {

    class Item(
        val id: Long,
        val name: String,
        val dir: TransferDir,
        val total: Long,
        val folder: String? = null,
        /** Lokasi file di disk (untuk thumbnail & viewer); null kalau tidak diketahui. */
        val path: String? = null,
        @Volatile var done: Long = 0,
        @Volatile var state: TransferState = TransferState.RUNNING,
        @Volatile var error: String? = null,
        val startedAt: Long = System.currentTimeMillis(),
    ) {
        @Volatile var speed: Double = 0.0
        @Volatile var paused: Boolean = false

        private var lastDone: Long = 0
        private var lastAt: Long = 0

        val fraction: Float
            get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)

        fun resetSampling() {
            lastAt = 0
            lastDone = done
        }

        fun sample(done: Long) {
            val now = System.currentTimeMillis()
            if (lastAt == 0L) {
                lastAt = now
                lastDone = done
                return
            }
            val dt = now - lastAt
            if (dt < 400) return
            val db = done - lastDone
            if (db >= 0) speed = db * 1000.0 / dt
            lastAt = now
            lastDone = done
        }
    }

    private val seq = AtomicLong()
    private val list = CopyOnWriteArrayList<Item>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun items(): List<Item> = list.toList()

    fun addListener(l: () -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    private fun notifyChanged() {
        if (listeners.isEmpty()) return
        val snapshot = listeners.toList()
        main.post { snapshot.forEach { runCatching { it() } } }
    }

    fun start(name: String, dir: TransferDir, total: Long, folder: String? = null, path: String? = null): Long {
        val item = Item(seq.incrementAndGet(), name, dir, total, folder, path)
        list.add(0, item)
        while (list.size > 40) list.removeAt(list.size - 1)
        notifyChanged()
        return item.id
    }

    fun progress(id: Long, done: Long) {
        val item = list.firstOrNull { it.id == id } ?: return
        if (item.state != TransferState.RUNNING) return
        item.done = done
        item.sample(done)
        notifyChanged()
    }

    fun finish(id: Long) {
        val item = list.firstOrNull { it.id == id } ?: return
        if (item.total > 0) item.done = item.total
        item.state = TransferState.DONE
        notifyChanged()
    }

    fun fail(id: Long, msg: String) {
        val item = list.firstOrNull { it.id == id } ?: return
        item.state = TransferState.FAILED
        item.error = msg
        item.paused = false
        notifyChanged()
    }

    fun setPaused(id: Long, paused: Boolean) {
        val item = list.firstOrNull { it.id == id } ?: return
        item.paused = paused
        if (!paused) item.resetSampling()
        notifyChanged()
    }

    fun clearFinished() {
        list.removeAll { it.state != TransferState.RUNNING }
        notifyChanged()
    }
}
