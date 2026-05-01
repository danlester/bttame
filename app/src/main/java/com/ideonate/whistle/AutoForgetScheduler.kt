package com.ideonate.whistle

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object AutoForgetScheduler {
    private const val REQUEST_CODE = 1001

    fun reschedule(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(app)
        am.cancel(pi)
        if (!DeviceStore(app).anyAutoForget()) return

        val s = SettingsStore(app)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, s.forgetHour())
            set(Calendar.MINUTE, s.forgetMinute())
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        am.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            cal.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pi
        )
    }

    fun runNow(context: Context) {
        val app = context.applicationContext
        val intent = Intent(app, AutoForgetReceiver::class.java).apply {
            action = AutoForgetReceiver.ACTION_FORGET
        }
        app.sendBroadcast(intent)
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AutoForgetReceiver::class.java).apply {
            action = AutoForgetReceiver.ACTION_FORGET
        }
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
