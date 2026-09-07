// 提醒接收器：到点弹系统通知；开机后重排全部提醒
package com.family.health.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.family.health.MainActivity

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(X_ID, 0)
        val title = intent.getStringExtra(X_TITLE) ?: "家庭健康提醒"
        val text = intent.getStringExtra(X_TEXT) ?: ""
        NotificationHelper.notify(context, id, title, text)
        ReminderScheduler(context).onFired(id)
    }

    companion object {
        const val X_ID = "id"
        const val X_TITLE = "title"
        const val X_TEXT = "text"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler(context).rescheduleAll()
        }
    }
}

object NotificationHelper {
    const val CHANNEL_ID = "family_health_reminders"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "家庭健康提醒", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun notify(context: Context, id: Int, title: String, text: String) {
        ensureChannel(context)
        val tap = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id, n)
    }
}
