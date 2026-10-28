package com.simats.courtease.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class Role(val label: String, val designation: String) {
    REGISTRY("Registry", "Court Registry"),
    JUDGE("Judge", "Judge (Presiding)"),
    LAWYER("Lawyer", "Advocate")
}

enum class AccountStatus { PENDING, APPROVED, REJECTED, SUSPENDED }

enum class Side { CLAIMANT, RESPONDENT }

enum class CaseStatus(val label: String) {
    DRAFT("Draft"),
    FILED("Filed"),
    RETURNED("Returned"),
    REGISTERED("Registered"),
    ALLOCATED("Allocated"),
    NOTICE("Notice issued"),
    PLEADINGS("Pleadings"),
    REVIEW("Under review"),
    JUDGMENT("Judgment"),
    CLOSED("Closed")
}

val CASE_TYPES = listOf(
    "Civil · Contract dispute",
    "Civil · Property dispute",
    "Civil · Lease / tenancy",
    "Civil · Recovery suit",
    "Civil · Partition / inheritance",
    "Civil · Injunction"
)

val COURT_ROOMS: Map<String, List<String>> = linkedMapOf(
    "Civil Court, Chennai" to listOf("Court Hall 1", "Court Hall 2", "Court Hall 3"),
    "Civil Court, Bengaluru" to listOf("Court Hall 1", "Court Hall 2"),
    "District Court, Madurai" to listOf("Court Hall 1", "Court Hall 2", "Court Hall 3"),
    "District Court, Coimbatore" to listOf("Court Hall 1", "Court Hall 2")
)

val COURTS: List<String> = COURT_ROOMS.keys.toList()

data class User(
    val id: Int,
    val name: String,
    val email: String,
    val phone: String,
    val password: String,
    val role: Role,
    val status: AccountStatus,
    val regNo: String = "",          // bar enrolment number (lawyer) or employee ID (judge)
    val council: String = "",        // state bar council (lawyer) or court of posting (judge)
    val registryId: String = "",     // ADV-2026-0001, JDG-2026-0001
    val court: String = "",          // judges only, set at approval
    val courtroom: String = "",      // judges only, set at approval
    val rejectReason: String = "",
    val createdAt: Long = 0L
)

data class CaseFile(
    val id: Int,
    val title: String,
    val caseType: String,
    val court: String,
    val claimant: String,
    val respondent: String,
    val status: CaseStatus,
    val claimantTeam: List<Int> = emptyList(),   // first id is the lead advocate
    val respondentTeam: List<Int> = emptyList(),
    val caseNo: String = "",
    val feeAmount: String = "",
    val feeReceipt: String = "",
    val judgeId: Int = 0,
    val courtroom: String = "",
    val caseCode: String = "",
    val responseDue: String = "",
    val defects: List<String> = emptyList(),
    val registryNote: String = "",
    val filedAt: Long = 0L
)

val CaseFile.label: String
    get() = when {
        caseNo.isNotBlank() -> caseNo
        status == CaseStatus.DRAFT -> "Draft #$id"
        else -> "Filing #$id"
    }

data class Doc(
    val id: Int,
    val caseId: Int,
    val name: String,
    val type: String,          // Plaint, Vakalatnama, Written statement, Evidence
    val side: Side,
    val filed: Boolean,
    val uploadedBy: Int,
    val pages: Int,
    val at: Long
)

data class Order(
    val id: Int,
    val caseId: Int,
    val judgeId: Int,
    val kind: String,          // Direction, Interim order, Judgment
    val title: String,
    val body: String,
    val at: Long
)

data class Message(val id: Int, val caseId: Int, val side: Side, val userId: Int, val text: String, val at: Long)
data class Note(val id: Int, val caseId: Int, val side: Side, val userId: Int, val text: String, val at: Long)

data class Task(
    val id: Int,
    val caseId: Int,
    val side: Side,
    val creatorId: Int,
    val assigneeId: Int,
    val title: String,
    val due: String,
    val done: Boolean = false
)

data class Invite(
    val id: Int,
    val caseId: Int,
    val side: Side,
    val email: String,
    val invitedBy: Int,
    val status: String = "pending"   // pending, accepted, declined
)

data class PrivateNote(val id: Int, val caseId: Int, val userId: Int, val text: String, val at: Long)

data class Notif(
    val id: Int,
    val userId: Int,
    val title: String,
    val body: String,
    val caseId: Int,
    val at: Long,
    val read: Boolean = false
)

data class Audit(val id: Int, val userId: Int, val action: String, val detail: String, val at: Long)

data class AppState(
    val users: List<User> = emptyList(),
    val cases: List<CaseFile> = emptyList(),
    val docs: List<Doc> = emptyList(),
    val orders: List<Order> = emptyList(),
    val messages: List<Message> = emptyList(),
    val notes: List<Note> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val invites: List<Invite> = emptyList(),
    val privateNotes: List<PrivateNote> = emptyList(),
    val notifs: List<Notif> = emptyList(),
    val audit: List<Audit> = emptyList(),
    val nextId: Int = 100
)

object Dates {
    private val loc = Locale.ENGLISH

    fun stamp(ms: Long): String = SimpleDateFormat("dd MMM yyyy, hh:mm a", loc).format(Date(ms))

    fun today(): String = SimpleDateFormat("dd MMM yyyy", loc).format(Date())

    fun todayLong(): String = SimpleDateFormat("EEEE, d MMMM yyyy", loc).format(Date())

    fun plusDays(days: Int): String {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, days)
        return SimpleDateFormat("dd MMM yyyy", loc).format(c.time)
    }

    fun parse(text: String): Date? = try {
        SimpleDateFormat("dd MMM yyyy", loc).parse(text.trim())
    } catch (e: Exception) {
        null
    }

    // Material3 DatePicker returns UTC midnight millis.
    fun formatUtc(millis: Long): String {
        val f = SimpleDateFormat("dd MMM yyyy", loc)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date(millis))
    }

    private fun startOfToday(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun isPast(text: String): Boolean {
        val d = parse(text) ?: return false
        return d.time < startOfToday()
    }

    fun greeting(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            h < 12 -> "Good morning"
            h < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}