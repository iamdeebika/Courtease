package com.simats.courtease.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.simats.courtease.data.api.TokenResponse
import com.simats.courtease.data.api.UserOut
import com.simats.courtease.model.DocItem
import com.simats.courtease.model.Role

// The signed-in user. Saved on the phone so the app opens straight to the dashboard next time.
object Session {
    var role by mutableStateOf(Role.LAWYER)
    var fullName by mutableStateOf("")
    var name by mutableStateOf("Counsel") // first name, shown in greetings
    var email by mutableStateOf("")
    var phone by mutableStateOf("")

    var token: String? = null
        private set

    val isLoggedIn: Boolean get() = token != null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("courtease_session", Context.MODE_PRIVATE)

    private fun apply(newToken: String, user: UserOut) {
        token = newToken
        role = if (user.role.equals("judge", ignoreCase = true)) Role.JUDGE else Role.LAWYER
        fullName = user.fullName
        email = user.email
        phone = user.phone
        name = user.fullName.trim().split(" ").firstOrNull { it.isNotBlank() } ?: "Counsel"
    }

    fun save(context: Context, response: TokenResponse) {
        apply(response.accessToken, response.user)
        prefs(context).edit()
            .putString("token", response.accessToken)
            .putString("full_name", response.user.fullName)
            .putString("email", response.user.email)
            .putString("phone", response.user.phone)
            .putString("role", response.user.role)
            .apply()
    }

    fun restore(context: Context) {
        val p = prefs(context)
        val saved = p.getString("token", null) ?: return
        apply(
            saved,
            UserOut(
                id = 0,
                fullName = p.getString("full_name", "") ?: "",
                email = p.getString("email", "") ?: "",
                phone = p.getString("phone", "") ?: "",
                role = p.getString("role", "lawyer") ?: "lawyer"
            )
        )
    }

    fun clear(context: Context) {
        token = null
        fullName = ""
        name = "Counsel"
        email = ""
        phone = ""
        prefs(context).edit().clear().apply()
    }
}

// Files picked on the Documents screen (in memory only until the backend upload exists).
object DocumentStore {
    private val map = mutableMapOf<String, List<DocItem>>()
    private val version = mutableStateOf(0)

    fun list(caseNo: String): List<DocItem> {
        version.value // read so Compose recomposes on change
        return map[caseNo].orEmpty()
    }

    fun add(caseNo: String, item: DocItem) {
        map[caseNo] = listOf(item) + map[caseNo].orEmpty()
        version.value = version.value + 1
    }
}