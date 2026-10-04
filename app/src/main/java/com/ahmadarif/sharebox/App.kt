package com.ahmadarif.sharebox

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.ahmadarif.sharebox.core.Prefs
import com.ahmadarif.sharebox.core.ServerController

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        ServerController.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    getString(R.string.notif_channel),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notif_channel_desc)
                    setShowBadge(false)
                }
            )
        }
    }

    companion object {
        const val CHANNEL = "sharebox.server"
        const val NOTIF_SERVER = 7
        const val NOTIF_TRANSFER = 8
    }
}
