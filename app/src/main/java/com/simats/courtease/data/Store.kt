package com.simats.courtease.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.simats.courtease.model.*
import java.util.Calendar
import kotlin.random.Random

// On-device demo database. Every screen reads Store.state, so the UI updates by itself.
// The backend replaces this later; the screens keep calling the same functions.
object Store {
    var state by mutableStateOf(AppState())
        private set

    private var prefs: SharedPreferences? = null
    private val gson = Gson()
    private const val KEY = "state_v1"

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("courtease_store", Context.MODE_PRIVATE)
        prefs = p
        val saved = p.getString(KEY, null)
        state = try {
            if (saved != null) gson.fromJson(saved, AppState::class.java) ?: seed() else seed()
        } catch (e: Exception) {
            seed()
        }
        persist()
    }

    fun resetDemo() {
        state = seed()
        persist()
    }

    private fun persist() {
        prefs?.edit()?.putString(KEY, gson.toJson(state))?.apply()
    }

    internal fun update(block: (AppState) -> AppState) {
        state = block(state)
        persist()
    }

    internal fun nextId(): Int {
        val id = state.nextId
        state = state.copy(nextId = id + 1)
        return id
    }

    // ---------- lookups ----------

    fun user(id: Int): User? = state.users.firstOrNull { it.id == id }
    fun caseById(id: Int): CaseFile? = state.cases.firstOrNull { it.id == id }
    fun users(role: Role): List<User> = state.users.filter { it.role == role }
    fun openCasesOf(judgeId: Int): Int =
        state.cases.count { it.judgeId == judgeId && it.status != CaseStatus.CLOSED }

    internal fun updateCase(id: Int, change: (CaseFile) -> CaseFile) {
        update { s -> s.copy(cases = s.cases.map { if (it.id == id) change(it) else it }) }
    }

    // ---------- notifications and audit ----------

    internal fun push(userId: Int, title: String, body: String, caseId: Int = 0) {
        val id = nextId()
        update { it.copy(notifs = it.notifs + Notif(id, userId, title, body, caseId, System.currentTimeMillis())) }
    }

    internal fun pushRegistry(title: String, body: String, caseId: Int = 0) {
        users(Role.REGISTRY).forEach { push(it.id, title, body, caseId) }
    }

    internal fun log(userId: Int, action: String, detail: String) {
        val id = nextId()
        update { it.copy(audit = it.audit + Audit(id, userId, action, detail, System.currentTimeMillis())) }
    }

    fun unread(userId: Int): Int = state.notifs.count { it.userId == userId && !it.read }

    fun markAllRead(userId: Int) {
        update { s -> s.copy(notifs = s.notifs.map { if (it.userId == userId) it.copy(read = true) else it }) }
    }

    // ---------- accounts ----------

    fun signup(
        name: String, email: String, phone: String, password: String,
        role: Role, regNo: String, council: String
    ): String? {
        val mail = email.trim().lowercase()
        val number = regNo.trim()
        val existing = state.users.firstOrNull { it.email == mail }
        if (existing != null && existing.status != AccountStatus.REJECTED) {
            return "This email is already registered"
        }
        val clash = state.users.any {
            it.role == role && it.regNo.equals(number, true) && it.id != existing?.id
        }
        if (clash) {
            return if (role == Role.LAWYER) "This bar number is already registered"
            else "This employee ID is already registered"
        }
        val id: Int
        if (existing != null) {
            id = existing.id
            update { s ->
                s.copy(users = s.users.map {
                    if (it.id == id) it.copy(
                        name = name.trim(), phone = phone.trim(), password = password, role = role,
                        regNo = number, council = council.trim(),
                        status = AccountStatus.PENDING, rejectReason = ""
                    ) else it
                })
            }
        } else {
            id = nextId()
            update { s ->
                s.copy(users = s.users + User(
                    id, name.trim(), mail, phone.trim(), password, role, AccountStatus.PENDING,
                    number, council.trim(), createdAt = System.currentTimeMillis()
                ))
            }
        }
        log(id, "Account request", "${role.label}: $mail")
        pushRegistry("New account request", "${name.trim()} applied as ${role.label}")
        return null
    }

    fun login(email: String, password: String): User? =
        state.users.firstOrNull { it.email == email.trim().lowercase() && it.password == password }

    fun resetPassword(email: String, phone: String, newPassword: String): String? {
        val u = state.users.firstOrNull { it.email == email.trim().lowercase() }
        val given = phone.filter { it.isDigit() }.takeLast(10)
        if (u == null || u.phone.filter { it.isDigit() }.takeLast(10) != given) {
            return "Email and phone don't match our records"
        }
        update { s -> s.copy(users = s.users.map { if (it.id == u.id) it.copy(password = newPassword) else it }) }
        log(u.id, "Password reset", u.email)
        return null
    }

    // ---------- registry: accounts ----------

    fun pendingAccounts(): List<User> =
        state.users.filter { it.status == AccountStatus.PENDING }.sortedBy { it.createdAt }

    private fun nextRegistryId(role: Role): String {
        val prefix = if (role == Role.JUDGE) "JDG" else "ADV"
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val n = state.users.count { it.role == role && it.registryId.isNotBlank() } + 1
        return String.format("%s-%d-%04d", prefix, year, n)
    }

    fun approveAccount(userId: Int, registrarId: Int, court: String, courtroom: String): String? {
        val u = user(userId) ?: return "Account not found"
        if (u.status != AccountStatus.PENDING) return "This request is already decided"
        val isJudge = u.role == Role.JUDGE
        if (isJudge && (court.isBlank() || courtroom.isBlank())) {
            return "Choose the judge's court and courtroom"
        }
        val rid = nextRegistryId(u.role)
        update { s ->
            s.copy(users = s.users.map {
                if (it.id == userId) it.copy(
                    status = AccountStatus.APPROVED, registryId = rid,
                    court = if (isJudge) court else "", courtroom = if (isJudge) courtroom else ""
                ) else it
            })
        }
        push(userId, "Account approved", "Your registry ID is $rid")
        log(registrarId, "Approved account", "${u.email} → $rid")
        return null
    }

    fun rejectAccount(userId: Int, registrarId: Int, reason: String): String? {
        if (reason.isBlank()) return "Enter a reason for rejecting"
        val u = user(userId) ?: return "Account not found"
        if (u.status != AccountStatus.PENDING) return "This request is already decided"
        update { s ->
            s.copy(users = s.users.map {
                if (it.id == userId) it.copy(status = AccountStatus.REJECTED, rejectReason = reason.trim()) else it
            })
        }
        log(registrarId, "Rejected account", "${u.email}: ${reason.trim()}")
        return null
    }

    fun setSuspended(userId: Int, registrarId: Int, suspend: Boolean, reason: String): String? {
        val u = user(userId) ?: return "Account not found"
        if (u.role == Role.REGISTRY || userId == registrarId) return "This account can't be changed"
        if (suspend && u.status != AccountStatus.APPROVED) return "Only approved accounts can be suspended"
        if (!suspend && u.status != AccountStatus.SUSPENDED) return "This account is not suspended"
        if (suspend && reason.isBlank()) return "Enter a reason for suspending"
        update { s ->
            s.copy(users = s.users.map {
                if (it.id == userId) it.copy(
                    status = if (suspend) AccountStatus.SUSPENDED else AccountStatus.APPROVED,
                    rejectReason = if (suspend) reason.trim() else ""
                ) else it
            })
        }
        log(registrarId, if (suspend) "Suspended account" else "Reinstated account", u.email)
        return null
    }

    // ---------- registry: filings (screens come in part F2) ----------

    fun filingQueue(): List<CaseFile> =
        state.cases.filter { it.status == CaseStatus.FILED }.sortedBy { it.filedAt }

    fun awaitingAllocation(): List<CaseFile> =
        state.cases.filter { it.status == CaseStatus.REGISTERED }

    fun awaitingNotice(): List<CaseFile> =
        state.cases.filter { it.status == CaseStatus.ALLOCATED }

    fun overdue(): List<CaseFile> = state.cases.filter {
        it.status == CaseStatus.NOTICE && it.respondentTeam.isEmpty() && Dates.isPast(it.responseDue)
    }

    fun scrutiny(c: CaseFile): List<Pair<String, Boolean>> {
        val docs = state.docs.filter { it.caseId == c.id && it.side == Side.CLAIMANT }
        return listOf(
            "Case details complete" to (c.title.isNotBlank() && c.caseType.isNotBlank() && c.court.isNotBlank()),
            "Both parties named" to (c.claimant.isNotBlank() && c.respondent.isNotBlank()),
            "Plaint uploaded" to docs.any { it.type == "Plaint" },
            "Vakalatnama uploaded" to docs.any { it.type == "Vakalatnama" },
            "Court fee entered" to (c.feeAmount.isNotBlank() && c.feeReceipt.isNotBlank())
        )
    }

    fun returnCase(caseId: Int, registrarId: Int, note: String): String? {
        val c = caseById(caseId) ?: return "Case not found"
        if (c.status != CaseStatus.FILED) return "Only filed cases can be returned"
        val defects = scrutiny(c).filter { !it.second }.map { "Needs attention: ${it.first}" } +
                (if (note.isNotBlank()) listOf(note.trim()) else emptyList())
        if (defects.isEmpty()) return "Add a note: no checklist item has failed"
        updateCase(caseId) { it.copy(status = CaseStatus.RETURNED, defects = defects) }
        c.claimantTeam.firstOrNull()?.let { push(it, "Case returned", "${c.title}: ${defects.first()}", caseId) }
        log(registrarId, "Returned case", "${c.title} · ${defects.size} defect(s)")
        return null
    }

    fun registerCase(caseId: Int, registrarId: Int): String? {
        val c = caseById(caseId) ?: return "Case not found"
        if (c.status != CaseStatus.FILED) return "Only filed cases can be registered"
        if (scrutiny(c).any { !it.second }) return "Scrutiny checks have failed. Return the case instead"
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val last = state.cases
            .mapNotNull { it.caseNo.removePrefix("OS ").substringBefore("/").toIntOrNull() }
            .maxOrNull() ?: 0
        val number = "OS ${last + 1}/$year"
        updateCase(caseId) { it.copy(status = CaseStatus.REGISTERED, caseNo = number, defects = emptyList()) }
        c.claimantTeam.firstOrNull()?.let { push(it, "Case registered", "${c.title} is now $number", caseId) }
        log(registrarId, "Registered case", "${c.title} → $number")
        return null
    }

    fun suggestJudge(court: String): User? =
        state.users.filter { it.role == Role.JUDGE && it.status == AccountStatus.APPROVED && it.court == court }
            .minByOrNull { openCasesOf(it.id) }

    fun approvedJudges(): List<User> =
        state.users.filter { it.role == Role.JUDGE && it.status == AccountStatus.APPROVED }

    fun allocate(caseId: Int, registrarId: Int, judgeId: Int, reason: String): String? {
        val c = caseById(caseId) ?: return "Case not found"
        if (c.status != CaseStatus.REGISTERED) return "Only registered cases can be allocated"
        val judge = user(judgeId)
        if (judge == null || judge.role != Role.JUDGE || judge.status != AccountStatus.APPROVED) {
            return "Choose an approved judge"
        }
        val suggested = suggestJudge(c.court)
        if (suggested != null && suggested.id != judgeId && reason.isBlank()) {
            return "Give a reason for choosing a different judge"
        }
        updateCase(caseId) {
            it.copy(
                judgeId = judgeId, court = judge.court, courtroom = judge.courtroom,
                status = CaseStatus.ALLOCATED,
                registryNote = if (reason.isNotBlank()) "Allocation note: ${reason.trim()}" else ""
            )
        }
        push(judgeId, "New case allocated", "${c.label} · ${c.title}", caseId)
        c.claimantTeam.firstOrNull()?.let {
            push(it, "Case allocated", "${c.label} is before ${judge.name}, ${judge.courtroom}", caseId)
        }
        log(registrarId, "Allocated case", "${c.label} → ${judge.name}, ${judge.court}, ${judge.courtroom}")
        return null
    }

    private fun newCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        while (true) {
            val code = "CE-" + (1..5).map { chars[Random.nextInt(chars.length)] }.joinToString("")
            if (state.cases.none { it.caseCode == code }) return code
        }
    }

    fun issueNotice(caseId: Int, registrarId: Int): String? {
        val c = caseById(caseId) ?: return "Case not found"
        if (c.status != CaseStatus.ALLOCATED) return "Allocate the case to a judge first"
        val code = newCode()
        val due = Dates.plusDays(30)
        updateCase(caseId) { it.copy(status = CaseStatus.NOTICE, caseCode = code, responseDue = due) }
        c.claimantTeam.firstOrNull()?.let {
            push(it, "Notice issued", "${c.label}: case code $code, response due $due", caseId)
        }
        if (c.judgeId != 0) push(c.judgeId, "Notice issued", "${c.label}: response due $due", caseId)
        log(registrarId, "Issued notice", "${c.label} · code $code · due $due")
        return null
    }

    fun reissueCode(caseId: Int, registrarId: Int): String? {
        val c = caseById(caseId) ?: return "Case not found"
        if (c.caseCode.isBlank() || c.respondentTeam.isNotEmpty()) return "A new code can't be issued now"
        val code = newCode()
        updateCase(caseId) { it.copy(caseCode = code) }
        log(registrarId, "Reissued case code", "${c.label} → $code")
        return null
    }

    // ---------- demo data ----------

    private fun seed(): AppState {
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val users = listOf(
            User(1, "Court Registry", "registry@courtease.in", "9000000001", "Registry#2026",
                Role.REGISTRY, AccountStatus.APPROVED, registryId = "REG-0001", createdAt = now - 30 * day),
            User(2, "Justice R. Meenakshi", "judge1@courtease.in", "9000000002", "Judge#2026",
                Role.JUDGE, AccountStatus.APPROVED, "TN-EMP-1201", "Civil Court, Chennai", "JDG-2026-0001",
                "Civil Court, Chennai", "Court Hall 1", createdAt = now - 25 * day),
            User(3, "Justice A. Karthik", "judge2@courtease.in", "9000000003", "Judge#2026",
                Role.JUDGE, AccountStatus.APPROVED, "TN-EMP-1302", "District Court, Madurai", "JDG-2026-0002",
                "District Court, Madurai", "Court Hall 2", createdAt = now - 24 * day),
            User(4, "Ananya Rao", "lawyer1@courtease.in", "9000000004", "Lawyer#2026",
                Role.LAWYER, AccountStatus.APPROVED, "TN/1234/2015", "Bar Council of Tamil Nadu", "ADV-2026-0001",
                createdAt = now - 20 * day),
            User(5, "Vikram Iyer", "lawyer2@courtease.in", "9000000005", "Lawyer#2026",
                Role.LAWYER, AccountStatus.APPROVED, "TN/5678/2012", "Bar Council of Tamil Nadu", "ADV-2026-0002",
                createdAt = now - 19 * day),
            User(6, "Divya Nair", "divya@courtease.in", "9000000006", "Lawyer#2026",
                Role.LAWYER, AccountStatus.PENDING, "KA/9012/2018", "Bar Council of Karnataka",
                createdAt = now - 2 * day),
            User(7, "Justice S. Prakash", "judge3@courtease.in", "9000000007", "Judge#2026",
                Role.JUDGE, AccountStatus.PENDING, "TN-EMP-1455", "Civil Court, Chennai",
                createdAt = now - day)
        )
        val cases = listOf(
            CaseFile(
                8, "Ravi vs Kumar Traders", "Civil · Contract dispute", "Civil Court, Chennai",
                "Ravi", "Kumar Traders", CaseStatus.FILED, claimantTeam = listOf(4),
                feeAmount = "5,000", feeReceipt = "RC-88231", filedAt = now - day
            ),
            CaseFile(
                9, "Meena vs Sundaram", "Civil · Property dispute", "District Court, Madurai",
                "Meena", "Sundaram", CaseStatus.NOTICE, claimantTeam = listOf(4),
                caseNo = "OS 7/2026", feeAmount = "3,500", feeReceipt = "RC-77120",
                judgeId = 3, courtroom = "Court Hall 2", caseCode = "CE-7K2P9",
                responseDue = Dates.plusDays(30), filedAt = now - 6 * day
            )
        )
        val docs = listOf(
            Doc(10, 8, "Plaint - Ravi vs Kumar Traders.pdf", "Plaint", Side.CLAIMANT, true, 4, 24, now - day),
            Doc(11, 8, "Vakalatnama.pdf", "Vakalatnama", Side.CLAIMANT, true, 4, 2, now - day),
            Doc(12, 9, "Plaint - Meena vs Sundaram.pdf", "Plaint", Side.CLAIMANT, true, 4, 18, now - 6 * day),
            Doc(13, 9, "Vakalatnama.pdf", "Vakalatnama", Side.CLAIMANT, true, 4, 2, now - 6 * day)
        )
        return AppState(users = users, cases = cases, docs = docs, nextId = 100)
    }
}