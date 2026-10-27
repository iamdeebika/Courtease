package com.simats.courtease.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class Role(
    val label: String,
    val designation: String,
    val workspace: String,
    val canManageCases: Boolean
) {
    JUDGE("Judge", "Judge (Presiding)", "JUDGE CHAMBERS", false),
    LAWYER("Lawyer", "Lawyer (Assigned)", "LAWYER WORKSPACE", true)
}

val STAGES = listOf("Filing", "Evidence", "Hearing", "Closure")

val CASE_TYPES = listOf(
    "Civil · Contract dispute",
    "Civil · Property dispute",
    "Civil · Lease / tenancy",
    "Civil · Recovery suit",
    "Civil · Partition / inheritance",
    "Civil · Injunction"
)

val COURTS = listOf(
    "Civil Court, Chennai",
    "Civil Court, Bengaluru",
    "District Court, Madurai",
    "District Court, Coimbatore",
    "District Court, Tiruchirappalli"
)

data class CivilCase(
    val caseNo: String,
    val title: String,
    val court: String,
    val caseType: String,
    val nextHearing: String,
    val pages: Int,
    val documents: Int,
    val claimant: String = "",
    val respondent: String = "",
    val stage: String = "Filing"
)

val sampleCases = listOf(
    CivilCase(
        "CE-2026-0147", "Ravi vs Kumar Traders", "Civil Court, Chennai",
        "Civil · Contract dispute", "12 Nov 2026", 512, 38,
        "Ravi", "Kumar Traders", "Evidence"
    ),
    CivilCase(
        "CE-2026-0122", "Property boundary dispute", "District Court, Madurai",
        "Civil · Property dispute", "20 Nov 2026", 240, 17,
        "Meena", "Sundaram", "Evidence"
    )
)

fun stageNote(stage: String): Pair<String, String> = when (stage) {
    "Filing" -> "Filing in progress" to "Next: evidence collection"
    "Evidence" -> "Evidence review in progress" to "Next: hearing preparation · Closure not reached"
    "Hearing" -> "Hearings in progress" to "Next: arguments and orders"
    else -> "Case closed" to "Record retained for reference"
}

object Dates {
    private const val PATTERN = "dd MMM yyyy"

    fun parse(text: String): Date? = try {
        SimpleDateFormat(PATTERN, Locale.ENGLISH).parse(text.trim())
    } catch (e: Exception) {
        null
    }

    // Material3 DatePicker returns UTC midnight millis.
    fun formatUtc(millis: Long): String {
        val f = SimpleDateFormat(PATTERN, Locale.ENGLISH)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date(millis))
    }

    fun today(): String = SimpleDateFormat(PATTERN, Locale.ENGLISH).format(Date())

    fun todayLong(): String =
        SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH).format(Date())

    private fun startOfToday(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun nextUpcoming(cases: List<CivilCase>): CivilCase? {
        val start = startOfToday()
        val dated = cases.mapNotNull { c -> parse(c.nextHearing)?.let { c to it.time } }
        return dated.filter { it.second >= start }.minByOrNull { it.second }?.first
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