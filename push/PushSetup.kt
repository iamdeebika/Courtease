package com.simats.courtease.push

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.simats.courtease.MainActivity
import com.simats.courtease.R

// Notification channel and the notification shown while the app is open.
object PushSetup {
    const val CHANNEL = "courtease_alerts"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "Case alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Updates about your cases"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    fun show(context: Context, title: String, body: String, caseId: Int) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        createChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("case_id", caseId)
        }
        val pending = PendingIntent.getActivity(
            context, caseId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_courtease)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: SecurityException) {
            // The user has not allowed notifications.
        }
    }
}

// Remembers which phone token was sent to the server, and for which user.
object PushStore {
    private fun prefs(context: Context) = context.getSharedPreferences("courtease_push", Context.MODE_PRIVATE)

    fun signature(context: Context): String = prefs(context).getString("signature", "") ?: ""

    fun token(context: Context): String = prefs(context).getString("token", "") ?: ""

    fun save(context: Context, signature: String, token: String) {
        prefs(context).edit().putString("signature", signature).putString("token", token).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}