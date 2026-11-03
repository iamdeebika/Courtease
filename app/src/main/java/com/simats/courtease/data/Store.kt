package com.simats.courtease.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.simats.courtease.model.*

// On-device copy of the data. Every screen reads Store.state, so the UI updates by itself.
// Accounts, cases, documents, orders and the team workspace are owned by the server and copied here.
object Store {
    var state by mutableStateOf(AppState())
        private set

    private var prefs: SharedPreferences? = null
    private val gson = Gson()
    private const val KEY = "state_v1"
    private const val SERVER_NOTIF_BASE = 1_000_000

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("courtease_store", Context.MODE_PRIVATE)
        prefs = p
        val saved = p.getString(KEY, null)
        state = try {
            if (saved != null) gson.fromJson(saved, AppState::class.java) ?: SeedData.build() else SeedData.build()
        } catch (e: Exception) {
            SeedData.build()
        }
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

    // ---------- registry helpers used by the screens ----------

    fun overdue(): List<CaseFile> = state.cases.filter {
        it.status == CaseStatus.NOTICE && it.respondentTeam.isEmpty() && Dates.isPast(it.responseDue)
    }

    // The same five checks the server makes. Used to show the checklist on the scrutiny screen.
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

    fun suggestJudge(court: String): User? =
        state.users.filter { it.role == Role.JUDGE && it.status == AccountStatus.APPROVED && it.court == court }
            .minByOrNull { openCasesOf(it.id) }

    fun approvedJudges(): List<User> =
        state.users.filter { it.role == Role.JUDGE && it.status == AccountStatus.APPROVED }

    // ---------- copies of server data ----------

    internal fun replaceUsers(list: List<User>) {
        if (state.users == list) return
        update { it.copy(users = list) }
    }

    internal fun upsertUser(u: User) {
        val old = state.users.firstOrNull { it.id == u.id }
        if (old == u) return
        update { s ->
            s.copy(users = if (old != null) s.users.map { if (it.id == u.id) u else it } else s.users + u)
        }
    }

    internal fun replaceServerNotifs(userId: Int, items: List<Notif>) {
        val kept = state.notifs.filterNot { it.userId == userId && it.id >= SERVER_NOTIF_BASE }
        val merged = kept + items
        if (merged == state.notifs) return
        update { it.copy(notifs = merged) }
    }

    internal fun replaceCaseData(
        cases: List<CaseFile>, docs: List<Doc>, orders: List<Order>, notes: List<PrivateNote>
    ) {
        if (state.cases == cases && state.docs == docs && state.orders == orders && state.privateNotes == notes) return
        update { it.copy(cases = cases, docs = docs, orders = orders, privateNotes = notes) }
    }

    internal fun replaceTeamData(
        messages: List<Message>, notes: List<Note>, tasks: List<Task>, invites: List<Invite>
    ) {
        if (state.messages == messages && state.notes == notes &&
            state.tasks == tasks && state.invites == invites
        ) return
        update { it.copy(messages = messages, notes = notes, tasks = tasks, invites = invites) }
    }

    // Clears everything on this phone when someone signs out, so the next person starts clean.
    fun clearForSignOut() {
        state = SeedData.build()
        persist()
    }
}