package com.simats.courtease.data

import com.simats.courtease.model.AccountStatus
import com.simats.courtease.model.AppState
import com.simats.courtease.model.Role
import com.simats.courtease.model.User

// Starting data on a fresh install, before the first sign-in. After signing in, the server's data replaces it.
object SeedData {
    fun build(): AppState {
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val users = listOf(
            User(1, "Court Registry", "registry@courtease.in", "9000000001", "",
                Role.REGISTRY, AccountStatus.APPROVED, registryId = "REG-0001", createdAt = now - 30 * day),
            User(2, "Justice R. Meenakshi", "judge1@courtease.in", "9000000002", "",
                Role.JUDGE, AccountStatus.APPROVED, "TN-EMP-1201", "Civil Court, Chennai", "JDG-2026-0001",
                "Civil Court, Chennai", "Court Hall 1", createdAt = now - 25 * day),
            User(3, "Justice A. Karthik", "judge2@courtease.in", "9000000003", "",
                Role.JUDGE, AccountStatus.APPROVED, "TN-EMP-1302", "District Court, Madurai", "JDG-2026-0002",
                "District Court, Madurai", "Court Hall 2", createdAt = now - 24 * day),
            User(4, "Ananya Rao", "lawyer1@courtease.in", "9000000004", "",
                Role.LAWYER, AccountStatus.APPROVED, "TN/1234/2015", "Bar Council of Tamil Nadu", "ADV-2026-0001",
                createdAt = now - 20 * day),
            User(5, "Vikram Iyer", "lawyer2@courtease.in", "9000000005", "",
                Role.LAWYER, AccountStatus.APPROVED, "TN/5678/2012", "Bar Council of Tamil Nadu", "ADV-2026-0002",
                createdAt = now - 19 * day)
        )
        return AppState(users = users, nextId = 100)
    }
}