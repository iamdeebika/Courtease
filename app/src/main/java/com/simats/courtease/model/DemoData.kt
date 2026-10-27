package com.simats.courtease.model


// Sample data for the screens that will be fed by the backend later.
// Replace these lists with API responses; the screens only read these types.

data class DocItem(
    val title: String,
    val kind: String,        // "Contract", "Payment record", "Statement", "Pleading", "Notice"
    val pages: Int,
    val added: String,
    val status: String
)

data class TimelineEvent(val date: String, val title: String, val detail: String, val source: String)

enum class EvidenceKind { SUPPORTING, CONFLICTING, RELATED }

data class EvidenceItem(
    val kind: EvidenceKind,
    val tag: String,
    val source: String,
    val quote: String,
    val ref: String
)

data class IssueItem(
    val title: String,
    val sources: String,
    val summary: String,
    val evidence: List<EvidenceItem>
)

data class StatementCheck(
    val statement: String,
    val source: String,
    val checks: List<Pair<String, String>> // label to result: Found / Not found / Needs review
)

data class LawItem(val title: String, val summary: String)
data class JudgmentItem(val title: String, val court: String, val note: String)
data class Reminder(val title: String, val detail: String)

object DemoData {
    val documents = listOf(
        DocItem("Doc A · Signed contract", "Contract", 24, "12 Mar 2026", "OCR complete · Indexed"),
        DocItem("Doc C · Submitted record", "Payment record", 12, "18 Jun 2026", "OCR complete · Indexed"),
        DocItem("Statement B · Witness statement", "Statement", 8, "14 Jul 2026", "OCR complete · Indexed"),
        DocItem("Doc F · Plaint", "Pleading", 18, "12 Mar 2026", "OCR complete · Indexed"),
        DocItem("Doc E · Legal notice", "Notice", 4, "02 Feb 2026", "OCR complete · Indexed"),
        DocItem("Statement D · Defendant statement", "Statement", 6, "30 Jul 2026", "Indexing")
    )

    val timeline = listOf(
        TimelineEvent("20 Dec 2025", "Contract signed", "Terms and payment schedule recorded.", "Doc A · pp. 1–3"),
        TimelineEvent("02 Feb 2026", "Legal notice issued", "Notice sent demanding performance.", "Doc E · p. 1"),
        TimelineEvent("12 Mar 2026", "Complaint filed", "Contract and payment issues raised.", "Doc F · pp. 1–2"),
        TimelineEvent("18 Jun 2026", "Payment record submitted", "Payment record placed on the court file.", "Doc C · p. 4"),
        TimelineEvent("14 Jul 2026", "Witness statement recorded", "Witness describes the payment date.", "Statement B · p. 2")
    )

    val issues = listOf(
        IssueItem(
            "Contract validity", "Doc A · Terms and execution",
            "Whether the contract was validly executed on the stated terms.",
            listOf(
                EvidenceItem(EvidenceKind.SUPPORTING, "Supporting source · Execution", "Doc A · Signed contract",
                    "Both parties' signatures appear on the final page.", "Doc A · p. 24"),
                EvidenceItem(EvidenceKind.RELATED, "Related source · Terms", "Doc F · Plaint",
                    "The plaint relies on the clauses of the signed contract.", "Doc F · p. 2")
            )
        ),
        IssueItem(
            "Payment dispute", "Doc C vs Statement B",
            "Whether the stated ₹5 lakh payment was made and how its date relates to the submitted record remains disputed.",
            listOf(
                EvidenceItem(EvidenceKind.SUPPORTING, "Supporting source · Payment claim", "Statement B · Witness statement",
                    "I paid 5 lakh on 10 January", "Statement B · p. 2"),
                EvidenceItem(EvidenceKind.CONFLICTING, "Conflicting source · Date discrepancy", "Doc C · Submitted record",
                    "Payment on 10 Jan: bank record not found in the submitted record.", "Doc C · p. 4")
            )
        ),
        IssueItem(
            "Alleged breach", "Doc A, Doc F · Obligations and claim",
            "Whether the delivery obligation in the contract was breached.",
            listOf(
                EvidenceItem(EvidenceKind.SUPPORTING, "Supporting source · Obligation", "Doc A · Signed contract",
                    "Clause 6 sets the delivery schedule.", "Doc A · p. 9"),
                EvidenceItem(EvidenceKind.RELATED, "Related source · Claim", "Doc F · Plaint",
                    "The plaint alleges delivery was not made by the agreed date.", "Doc F · p. 5")
            )
        )
    )

    val statements = listOf(
        StatementCheck(
            "I paid 5 lakh on 10 January", "Statement B · p. 2",
            listOf(
                "Payment documents on file" to "Found",
                "Bank record submitted to court" to "Not found",
                "Mentioned in other documents" to "Found",
                "Earlier statements with different dates" to "Needs review",
                "Timeline consistency" to "Needs review"
            )
        ),
        StatementCheck(
            "The notice was never received", "Statement D · p. 3",
            listOf(
                "Proof of delivery on file" to "Found",
                "Acknowledgement signed" to "Not found",
                "Mentioned in other documents" to "Found",
                "Earlier statements on receipt" to "Needs review",
                "Timeline consistency" to "Needs review"
            )
        ),
        StatementCheck(
            "Delivery was completed in March", "Statement D · p. 5",
            listOf(
                "Delivery documents on file" to "Not found",
                "Invoice for delivery" to "Not found",
                "Mentioned in other documents" to "Needs review",
                "Earlier statements with different dates" to "Needs review",
                "Timeline consistency" to "Needs review"
            )
        )
    )

    val laws = listOf(
        LawItem("Indian Contract Act, 1872 · Section 73",
            "Compensation for loss or damage caused by breach of contract."),
        LawItem("Indian Contract Act, 1872 · Section 10",
            "What agreements are contracts: free consent, lawful consideration and object."),
        LawItem("Limitation Act, 1963 · Article 55",
            "Limitation period for a suit for compensation for breach of contract."),
        LawItem("Code of Civil Procedure, 1908 · Order VII Rule 1",
            "Particulars to be contained in a plaint.")
    )

    val judgments = listOf(
        JudgmentItem("Sample result · Damages for breach of contract", "High Court (sample)",
            "Placeholder until live Indian Kanoon search is connected."),
        JudgmentItem("Sample result · Proof of payment by oral evidence", "District Court (sample)",
            "Placeholder until live Indian Kanoon search is connected."),
        JudgmentItem("Sample result · Limitation in contract suits", "Supreme Court (sample)",
            "Placeholder until live Indian Kanoon search is connected.")
    )

    val reminders = listOf(
        Reminder("Hearing preparation pending", "CE-2026-0147 · review before the next hearing"),
        Reminder("3 statements need verification", "CE-2026-0147 · payment dates need professional review"),
        Reminder("Filing deadline in 5 days", "CE-2026-0122 · written statement"),
        Reminder("Documents added", "CE-2026-0122 · 2 new documents indexed")
    )

    val hearingHistory = listOf(
        "14 Aug 2026 · Appearance recorded, issues framed",
        "30 Sep 2026 · Evidence of claimant, adjourned"
    )

    fun buildReport(c: CivilCase): String = buildString {
        appendLine("CourtEase case report")
        appendLine("Case: ${c.caseNo} · ${c.title}")
        appendLine("Court: ${c.court}")
        appendLine("Type: ${c.caseType}")
        appendLine("Parties: ${c.claimant.ifBlank { "-" }} vs ${c.respondent.ifBlank { "-" }}")
        appendLine("Stage: ${c.stage} · Next hearing: ${c.nextHearing}")
        appendLine()
        appendLine("Disputed issues")
        issues.forEach { appendLine("- ${it.title}: ${it.sources}") }
        appendLine()
        appendLine("Statements requiring verification")
        statements.forEach { appendLine("- \"${it.statement}\" (${it.source})") }
        appendLine()
        appendLine("AI-generated support, sample data. Source documents must be reviewed.")
        appendLine("The checks compare statements with the record and do not decide truth.")
    }
}
