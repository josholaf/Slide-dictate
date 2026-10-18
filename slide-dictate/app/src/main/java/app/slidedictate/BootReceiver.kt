package app.slidedictate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * After a reboot the accessibility service only comes back if the user kept it
 * enabled. If it is off, post a one-tap reminder so dictation does not silently
 * stop working after boot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(c: Context, i: Intent) {
        val enabled = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').any { it.startsWith(c.packageName + "/") }
        if (enabled) return

        val mgr = c.getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL, "Setup reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val pi = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val note = Notification.Builder(c, CHANNEL)
            .setContentTitle("Slide Dictate is off")
            .setContentText("Tap to re-enable the edge handle under Accessibility.")
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        // No-op on Android 13+ without the notification permission; the in-app
        // banner in Settings covers that case.
        runCatching { mgr.notify(7, note) }
    }

    companion object { private const val CHANNEL = "setup" }
}
