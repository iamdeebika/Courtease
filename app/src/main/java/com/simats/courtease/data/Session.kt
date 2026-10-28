package com.simats.courtease.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.simats.courtease.model.Role
import com.simats.courtease.model.User

// Who is signed in on this phone.
object Session {
    var userId by mutableStateOf(0)
        private set

    val user: User? get() = Store.user(userId)
    val isLoggedIn: Boolean get() = userId != 0 && user != null
    val role: Role get() = user?.role ?: Role.LAWYER

    // Used in greetings: judges keep their title, others show the first name.
    val shortName: String
        get() {
            val u = user ?: return ""
            return if (u.role == Role.JUDGE) u.name else u.name.trim().split(" ").firstOrNull().orEmpty()
        }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("courtease_session", Context.MODE_PRIVATE)

    fun login(context: Context, id: Int) {
        userId = id
        prefs(context).edit().putInt("user_id", id).apply()
    }

    fun restore(context: Context) {
        userId = prefs(context).getInt("user_id", 0)
    }

    fun logout(context: Context) {
        userId = 0
        prefs(context).edit().clear().apply()
    }
}