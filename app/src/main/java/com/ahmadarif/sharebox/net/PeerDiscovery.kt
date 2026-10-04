package com.ahmadarif.sharebox.net

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.ahmadarif.sharebox.core.Prefs
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Peer discovery over UDP broadcast — no central server; every device on the same LAN
 * (including the hotspot's LAN) can see each other.
 */
object PeerDiscovery {

    const val PORT = 47777
    private const val INTERVAL_MS = 2000L
    private const val TIMEOUT_MS = 8000L

    data class Peer(val id: String, val name: String, val host: String, val port: Int, val lastSeen: Long)

    private val myId: String = UUID.randomUUID().toString()
    private val peers = LinkedHashMap<String, Peer>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    private var rx: DatagramSocket? = null
    @Volatile private var running = false
    private var refs = 0

    @Synchronized
    fun acquire(ctx: Context) {
        refs++
        if (refs == 1) start(ctx.applicationContext)
    }

    @Synchronized
    fun release() {
        refs--
        if (refs <= 0) {
            refs = 0
            stop()
        }
    }

    fun addListener(l: () -> Unit) = listeners.add(l)
    fun removeListener(l: () -> Unit) = listeners.remove(l)

    /** Identitas device ini di jaringan (dipakai juga saat pairing). */
    fun myId(): String = myId

    private fun notifyChanged() {
        if (listeners.isEmpty()) return
        val snapshot = listeners.toList()
        main.post { snapshot.forEach { runCatching { it() } } }
    }

    fun list(): List<Peer> {
        val now = System.currentTimeMillis()
        synchronized(peers) {
            peers.entries.removeAll { now - it.value.lastSeen > TIMEOUT_MS }
            return peers.values.sortedBy { it.name.lowercase() }
        }
    }

    private fun start(ctx: Context) {
        running = true
        try {
            val socket = DatagramSocket(null)
            socket.reuseAddress = true
            socket.broadcast = true
            socket.bind(InetSocketAddress(PORT))
            rx = socket
            Thread({ receiveLoop(socket) }, "sharebox-discovery-rx").apply {
                isDaemon = true
                start()
            }
        } catch (_: Exception) {
            rx = null // port dipakai proses lain; kirim saja tetap jalan
        }
        Thread({ sendLoop() }, "sharebox-discovery-tx").apply {
            isDaemon = true
            start()
        }
    }

    private fun stop() {
        running = false
        try {
            rx?.close()
        } catch (_: Exception) {
        }
        rx = null
    }

    private fun receiveLoop(socket: DatagramSocket) {
        val buf = ByteArray(2048)
        while (running) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                socket.receive(packet)
                val obj = JSONObject(String(packet.data, 0, packet.length, Charsets.UTF_8))
                if (obj.optString("app") != "sharebox") continue
                val id = obj.optString("id")
                if (id.isEmpty() || id == myId) continue
                val host = packet.address?.hostAddress ?: continue
                val peer = Peer(
                    id = id,
                    name = obj.optString("name", "Android"),
                    host = host,
                    port = obj.optInt("port", 2999),
                    lastSeen = System.currentTimeMillis(),
                )
                synchronized(peers) { peers[id] = peer }
                notifyChanged()
            } catch (_: Exception) {
                if (!running) break
            }
        }
    }

    private fun sendLoop() {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply { broadcast = true }
        } catch (_: Exception) {
        }
        while (running) {
            try {
                val msg = JSONObject()
                    .put("app", "sharebox")
                    .put("id", myId)
                    .put("name", Prefs.displayName())
                    .put("port", Prefs.port)
                    .toString()
                    .toByteArray(Charsets.UTF_8)
                val targets = mutableSetOf<InetAddress>()
                targets.add(InetAddress.getByName("255.255.255.255"))
                try {
                    for (nif in NetworkInterface.getNetworkInterfaces()) {
                        if (!nif.isUp || nif.isLoopback) continue
                        for (ia in nif.interfaceAddresses) {
                            ia.broadcast?.let { targets.add(it) }
                        }
                    }
                } catch (_: Exception) {
                }
                for (addr in targets) {
                    try {
                        socket?.send(DatagramPacket(msg, msg.size, addr, PORT))
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
            try {
                Thread.sleep(INTERVAL_MS)
            } catch (_: InterruptedException) {
                break
            }
        }
        try {
            socket?.close()
        } catch (_: Exception) {
        }
    }
}
