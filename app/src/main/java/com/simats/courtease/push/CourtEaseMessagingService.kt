package com.simats.courtease.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

// Receives pushes while the app is open. When the app is closed, Android shows the notification itself.
class CourtEaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val shown = message.notification
        val title = shown?.title ?: message.data["title"] ?: "CourtEase"
        val body = shown?.body ?: message.data["body"] ?: "You have a new alert."
        val caseId = message.data["case_id"]?.toIntOrNull() ?: 0
        PushSetup.show(applicationContext, title, body, caseId)
    }
}
