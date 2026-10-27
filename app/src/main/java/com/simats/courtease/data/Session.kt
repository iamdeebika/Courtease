package com.simats.courtease.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.simats.courtease.model.DocItem
import com.simats.courtease.model.Role

// Demo session. Real authentication replaces signIn() later.
object Session {
    var role by mutableStateOf(Role.LAWYER)
    var name by mutableStateOf("Counsel")

    fun signIn(email: String, selected: Role) {
        role = selected
        val first = email.substringBefore("@").split('.', '_', '-', ' ').firstOrNull { it.isNotBlank() }
        name = first?.replaceFirstChar { it.uppercase() } ?: "Counsel"
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
