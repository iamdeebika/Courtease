package com.simats.courtease.data

import com.simats.courtease.model.Notif

// Rules shared by all three roles: notifications, profile, password.
object CommonOps {

    fun notifsOf(userId: Int): List<Notif> =
        Store.state.notifs.filter { it.userId == userId }.sortedByDescending { it.at }

    fun markRead(notifId: Int) {
        Store.update { s -> s.copy(notifs = s.notifs.map { if (it.id == notifId) it.copy(read = true) else it }) }
    }

    fun updateProfile(userId: Int, name: String, phone: String): String? {
        val u = Store.user(userId) ?: return "Not signed in"
        if (name.trim().length < 2) return "Enter your full name"
        if (phone.filter { it.isDigit() }.length < 10) return "Enter a valid phone number"
        Store.update { s ->
            s.copy(users = s.users.map {
                if (it.id == userId) it.copy(name = name.trim(), phone = phone.trim()) else it
            })
        }
        Store.log(userId, "Updated profile", u.email)
        return null
    }

    fun changePassword(userId: Int, current: String, newPassword: String): String? {
        val u = Store.user(userId) ?: return "Not signed in"
        if (u.password != current) return "Current password is wrong"
        if (newPassword.length < 8 || newPassword.none { it.isDigit() } || newPassword.none { it.isLetter() }) {
            return "Password needs 8+ characters with letters and a number"
        }
        if (newPassword == current) return "Choose a different password"
        Store.update { s ->
            s.copy(users = s.users.map { if (it.id == userId) it.copy(password = newPassword) else it })
        }
        Store.log(userId, "Changed password", u.email)
        return null
    }
}