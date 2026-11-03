package com.example.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

object NotificationScheduler {

    private const val CHANNEL_ID = "weekly_backup_reminders"
    private const val NOTIFICATION_ID = 1001
    private const val REQUEST_CODE = 2001
    private const val ONE_WEEK_MILLIS = 7 * 24 * 60 * 60 * 1000L

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Напоминания о резервных копиях"
            val descriptionText = "Еженедельные напоминания для ручного экспорта и сохранения замеров"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun scheduleWeeklyReminder(context: Context) {
        createNotificationChannel(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Schedule repeating alarm every 7 days (first trigger in 7 days from now)
        val firstTriggerMillis = System.currentTimeMillis() + ONE_WEEK_MILLIS

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            firstTriggerMillis,
            ONE_WEEK_MILLIS,
            pendingIntent
        )
    }

    fun cancelWeeklyReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun showReminderNotification(context: Context) {
        createNotificationChannel(context)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Резервная копия данных здоровья")
            .setContentText("Напоминание: сохраните ваши замеры в CSV файл или на Google Диск для надёжности.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Время сделать еженедельную резервную копию замеров тела! Откройте BodyMetrics и сохраните CSV файл или отправьте копию на Google Диск.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission not granted
        }
    }
}
