package com.simats.courtease.data

import com.simats.courtease.model.*

// Judge-side rules. Every function checks who is allowed to act and returns an error text, or null on success.
object JudgeOps {

    // ---------- lookups ----------

    fun casesOf(judgeId: Int): List<CaseFile> =
        Store.state.cases.filter { it.judgeId == judgeId }.sortedByDescending { it.id }

    fun overdue(c: CaseFile): Boolean =
        c.status == CaseStatus.NOTICE && c.respondentTeam.isEmpty() && Dates.isPast(c.responseDue)

    fun needsAttention(c: CaseFile): Boolean =
        c.status == CaseStatus.PLEADINGS || c.status == CaseStatus.REVIEW || overdue(c)

    // The judge never sees drafts, only what each side has filed.
    fun filedDocs(c: CaseFile): List<Doc> =
        Store.state.docs.filter { it.caseId == c.id && it.filed }.sortedByDescending { it.at }

    fun ordersOf(c: CaseFile): List<Order> =
        Store.state.orders.filter { it.caseId == c.id }.sortedByDescending { it.at }

    fun notesOf(c: CaseFile, judgeId: Int): List<PrivateNote> =
        Store.state.privateNotes.filter { it.caseId == c.id && it.userId == judgeId }.sortedByDescending { it.at }

    private fun everyone(c: CaseFile): List<Int> = (c.claimantTeam + c.respondentTeam).distinct()

    private fun mine(caseId: Int, judgeId: Int): CaseFile? =
        Store.caseById(caseId)?.takeIf { it.judgeId == judgeId }

    // ---------- review ----------

    fun startReview(caseId: Int, judgeId: Int): String? {
        val c = mine(caseId, judgeId) ?: return "This case is not allocated to you"
        if (c.status != CaseStatus.PLEADINGS && !overdue(c)) {
            return "Review starts after the respondent appears or the response period ends"
        }
        Store.updateCase(caseId) { it.copy(status = CaseStatus.REVIEW) }
        everyone(c).forEach { Store.push(it, "Under review", "${c.label}: the judge has started the review", caseId) }
        Store.log(judgeId, "Started review", c.label)
        return null
    }

    // ---------- orders ----------

    fun issueOrder(caseId: Int, judgeId: Int, kind: String, title: String, body: String): String? {
        val c = mine(caseId, judgeId) ?: return "This case is not allocated to you"
        val allowed = c.status == CaseStatus.NOTICE || c.status == CaseStatus.PLEADINGS || c.status == CaseStatus.REVIEW
        if (!allowed) return "An order can't be issued at this stage"
        if (kind == "Judgment" && c.status != CaseStatus.REVIEW) return "Start the review before issuing a judgment"
        if (title.trim().length < 3) return "Give the order a title"
        if (body.trim().length < 10) return "Write the order text"
        val id = Store.nextId()
        Store.update { s ->
            s.copy(
                orders = s.orders + Order(id, caseId, judgeId, kind, title.trim(), body.trim(), System.currentTimeMillis()),
                cases = if (kind == "Judgment")
                    s.cases.map { if (it.id == caseId) it.copy(status = CaseStatus.JUDGMENT) else it }
                else s.cases
            )
        }
        everyone(c).forEach { Store.push(it, "$kind issued", "${c.label}: ${title.trim()}", caseId) }
        Store.log(judgeId, "Issued $kind", "${c.label} · ${title.trim()}")
        return null
    }

    fun closeCase(caseId: Int, judgeId: Int, reason: String): String? {
        val c = mine(caseId, judgeId) ?: return "This case is not allocated to you"
        if (c.status == CaseStatus.CLOSED) return "This case is already closed"
        if (reason.trim().length < 5) return "Give the reason for closing"
        val id = Store.nextId()
        Store.update { s ->
            s.copy(
                orders = s.orders + Order(
                    id, caseId, judgeId, "Closing order", "Case closed", reason.trim(), System.currentTimeMillis()
                ),
                cases = s.cases.map { if (it.id == caseId) it.copy(status = CaseStatus.CLOSED) else it }
            )
        }
        everyone(c).forEach { Store.push(it, "Case closed", "${c.label}: ${reason.trim()}", caseId) }
        Store.pushRegistry("Case closed", c.label, caseId)
        Store.log(judgeId, "Closed case", "${c.label} · ${reason.trim()}")
        return null
    }

    // ---------- recusal ----------

    fun recuse(caseId: Int, judgeId: Int, reason: String): String? {
        val c = mine(caseId, judgeId) ?: return "This case is not allocated to you"
        val allowed = c.status == CaseStatus.ALLOCATED || c.status == CaseStatus.NOTICE ||
                c.status == CaseStatus.PLEADINGS || c.status == CaseStatus.REVIEW
        if (!allowed) return "You can't step aside at this stage"
        if (reason.trim().length < 5) return "Give the reason for stepping aside"
        val name = Store.user(judgeId)?.name.orEmpty()
        Store.updateCase(caseId) {
            it.copy(
                judgeId = 0, courtroom = "", status = CaseStatus.REGISTERED,
                registryNote = "Recused by $name: ${reason.trim()}"
            )
        }
        Store.pushRegistry("Judge stepped aside", "${c.label} needs a new judge", caseId)
        everyone(c).forEach {
            Store.push(it, "Judge changed", "${c.label} will be allocated to another judge", caseId)
        }
        Store.log(judgeId, "Recused", "${c.label} · ${reason.trim()}")
        return null
    }

    // ---------- private notes ----------

    fun addNote(caseId: Int, judgeId: Int, text: String): String? {
        mine(caseId, judgeId) ?: return "This case is not allocated to you"
        if (text.isBlank()) return "Write the note first"
        val id = Store.nextId()
        Store.update { it.copy(privateNotes = it.privateNotes + PrivateNote(id, caseId, judgeId, text.trim(), System.currentTimeMillis())) }
        return null
    }

    fun deleteNote(noteId: Int, judgeId: Int): String? {
        val n = Store.state.privateNotes.firstOrNull { it.id == noteId } ?: return "Note not found"
        if (n.userId != judgeId) return "This is not your note"
        Store.update { s -> s.copy(privateNotes = s.privateNotes.filter { it.id != noteId }) }
        return null
    }
}