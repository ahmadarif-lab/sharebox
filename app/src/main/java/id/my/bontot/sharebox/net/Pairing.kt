package id.my.bontot.sharebox.net

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import id.my.bontot.sharebox.App
import id.my.bontot.sharebox.MainActivity
import id.my.bontot.sharebox.R
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Pairing ala SHAREit: device yang mau mengirim harus minta izin dulu, dan pemilik
 * HP penerima menekan Approve. Sampai di-approve, kiriman ditolak.
 *
 * Catatan: [declinedIps] menolak kiriman dari device yang ditolak selama sesi ini,
 * supaya penolakan benar-benar berlaku (bukan cuma sopan-santun di sisi pengirim).
 */
object Pairing {

    const val ACTION_APPROVE = "id.my.bontot.sharebox.PAIR_APPROVE"
    const val ACTION_DECLINE = "id.my.bontot.sharebox.PAIR_DECLINE"
    const val EXTRA_ID = "request_id"

    private const val TIMEOUT_MS = 30_000L
    private const val NOTIF_PAIR = 9

    private val pending = ConcurrentHashMap<String, (Boolean) -> Unit>()
    private val tokens = ConcurrentHashMap<String, String>()
    private val declinedIps = ConcurrentHashMap.newKeySet<String>()

    fun tokenFor(peerId: String): String? = tokens[peerId]

    fun storeToken(peerId: String, token: String) {
        tokens[peerId] = token
    }

    /** Setujui langsung (pengirim membawa kunci sesi dari QR) dan terbitkan token. */
    fun grant(peerId: String): String {
        val token = UUID.randomUUID().toString()
        tokens[peerId] = token
        return token
    }

    fun isDeclined(ip: String?): Boolean = ip != null && declinedIps.contains(ip)

    fun clear() {
        tokens.clear()
        declinedIps.clear()
        pending.clear()
    }

    /**
     * Dipanggil dari DirectServer di thread koneksi: menampilkan notifikasi
     * persetujuan lalu menunggu keputusan user (maks 30 detik).
     * Hasil: token (kalau disetujui) atau alasan kegagalan ("declined" / "timeout").
     */
    fun requestApproval(ctx: Context, peerId: String, peerName: String, remoteIp: String?): Pair<String?, String?> {
        val requestId = UUID.randomUUID().toString()
        val latch = CountDownLatch(1)
        var approved = false
        var decided = false
        pending[requestId] = { ok ->
            approved = ok
            decided = true
            latch.countDown()
        }
        showNotification(ctx, requestId, peerName)
        try {
            latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
        }
        pending.remove(requestId)
        cancelNotification(ctx)
        if (!approved) {
            // Blokir HANYA kalau user benar-benar menolak. Timeout (notifikasi tak
            // terlihat / device lain sibuk) bukan penolakan.
            if (decided) {
                if (remoteIp != null) declinedIps.add(remoteIp)
                return null to "declined"
            }
            return null to "timeout"
        }
        if (remoteIp != null) declinedIps.remove(remoteIp)
        val token = UUID.randomUUID().toString()
        tokens[peerId] = token
        return token to null
    }

    fun resolve(requestId: String, approve: Boolean) {
        pending.remove(requestId)?.invoke(approve)
    }

    private fun showNotification(ctx: Context, requestId: String, peerName: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        fun action(action: String, requestCode: Int, label: String, iconRes: Int): Notification.Action {
            val pi = PendingIntent.getBroadcast(
                ctx, requestCode,
                Intent(ctx, PairingReceiver::class.java).setAction(action).putExtra(EXTRA_ID, requestId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return Notification.Action.Builder(Icon.createWithResource(ctx, iconRes), label, pi).build()
        }
        val notif = Notification.Builder(ctx, App.CHANNEL_PAIR)
            .setSmallIcon(R.drawable.ic_stat_share)
            .setContentTitle(ctx.getString(R.string.pair_request_title, peerName))
            .setContentText(ctx.getString(R.string.pair_request_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(Notification.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setTimeoutAfter(TIMEOUT_MS)
            .addAction(action(ACTION_APPROVE, 1, ctx.getString(R.string.pair_approve), R.drawable.ic_check))
            .addAction(action(ACTION_DECLINE, 2, ctx.getString(R.string.pair_decline), R.drawable.ic_close))
            .build()
        nm.notify(NOTIF_PAIR, notif)
    }

    private fun cancelNotification(ctx: Context) {
        try {
            ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_PAIR)
        } catch (_: Exception) {
        }
    }
}

/** Penerima aksi Approve/Decline dari notifikasi pairing. */
class PairingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(Pairing.EXTRA_ID) ?: return
        Pairing.resolve(id, intent.action == Pairing.ACTION_APPROVE)
    }
}
