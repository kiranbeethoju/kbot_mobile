package com.offlinebot.recorder.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.offlinebot.MainActivity
import com.offlinebot.R

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra("label") ?: "KBot Reminder"
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel("kbot_alarms", "KBot Alarms", NotificationManager.IMPORTANCE_HIGH)
        manager.createNotificationChannel(channel)

        val tapIntent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        val pendingIntent = PendingIntent.getActivity(context, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, "kbot_alarms")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("KBot")
            .setContentText(label)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
