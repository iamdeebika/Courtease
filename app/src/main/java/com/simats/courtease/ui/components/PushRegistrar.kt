package com.simats.courtease.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.messaging.FirebaseMessaging
import com.simats.courtease.data.Session
import com.simats.courtease.data.api.PushRepo
import com.simats.courtease.push.PushStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private suspend fun fcmToken(): String? = suspendCancellableCoroutine { cont ->
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
        if (cont.isActive) cont.resume(if (task.isSuccessful) task.result else null)
    }
}

private suspend fun step(context: Context) {
    if (Session.token != null) {
        val fcm = fcmToken() ?: return
        val signature = "${Session.userId}|$fcm"
        if (PushStore.signature(context) != signature && PushRepo.register(fcm)) {
            PushStore.save(context, signature, fcm)
        }
    } else {
        val old = PushStore.token(context)
        if (old.isNotEmpty() && PushRepo.unregister(old)) PushStore.clear(context)
    }
}

// Every few seconds: while someone is signed in, makes sure this phone is registered for them.
// After sign-out, removes the phone from the server so the next person's alerts do not reach it.
@Composable
fun PushRegistrar() {
    val context = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        while (true) {
            try {
                step(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Try again at the next round.
            }
            delay(5000)
        }
    }
}
