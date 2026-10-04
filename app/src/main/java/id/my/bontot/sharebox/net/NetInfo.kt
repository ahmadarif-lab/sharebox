package id.my.bontot.sharebox.net

import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface

object NetInfo {

    data class UrlInfo(val label: String, val url: String)

    fun urls(port: Int): List<UrlInfo> {
        val out = mutableListOf<UrlInfo>()
        try {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr !is Inet4Address) continue
                    if (addr.isLoopbackAddress || addr.isLinkLocalAddress) continue
                    val ip = addr.hostAddress ?: continue
                    out += UrlInfo(nif.name, "http://$ip:$port/")
                }
            }
        } catch (_: Exception) {
        }
        return out.sortedBy { rank(it.label) }
    }

    private fun ipv4s(): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        try {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr !is Inet4Address || addr.isLoopbackAddress || addr.isLinkLocalAddress) continue
                    addr.hostAddress?.let { out += nif.name to it }
                }
            }
        } catch (_: Exception) {
        }
        return out.sortedBy { rank(it.first) }
    }

    private fun isApIface(name: String) = name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("wlan1")

    /** Alamat HP ini di hotspot sendiri (interface AP), kalau hotspot sedang menyala. */
    fun hotspotIp(): String? = ipv4s().firstOrNull { isApIface(it.first) }?.second

    /** Alamat HP ini di jaringan Wi-Fi/LAN yang sama dengan device lain. */
    fun lanIp(): String? = ipv4s().firstOrNull { !isApIface(it.first) }?.second

    private fun rank(name: String): Int = when {
        name.startsWith("wlan") -> 0
        name.startsWith("ap") -> 1
        name.startsWith("eth") -> 2
        name.startsWith("rndis") -> 3
        name.startsWith("usb") -> 4
        else -> 5
    }

    fun deviceName(): String = listOfNotNull(Build.MANUFACTURER?.takeIf { it.isNotBlank() }, Build.MODEL)
        .joinToString(" ")
        .ifBlank { "Android" }
}
