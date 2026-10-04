package id.my.bontot.sharebox.core

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import id.my.bontot.sharebox.App
import id.my.bontot.sharebox.MainActivity
import id.my.bontot.sharebox.R

object Notifier {

    /** Notifikasi "File received" — dipakai penerima via web server (upload) maupun transfer langsung. */
    fun received(ctx: Context, name: String) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val pi = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = android.app.Notification.Builder(ctx, App.CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_share)
                .setContentTitle(ctx.getString(R.string.notif_received, name))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(App.NOTIF_TRANSFER, notif)
        } catch (_: Exception) {
        }
    }
}
