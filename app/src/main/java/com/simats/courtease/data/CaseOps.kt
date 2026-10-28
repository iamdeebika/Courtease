package com.simats.courtease.data

import com.simats.courtease.model.*

// Lawyer-side rules. Every function checks who is allowed to act and returns an error text, or null on success.
object CaseOps {

    // ---------- lookups ----------

    fun casesOf(userId: Int): List<CaseFile> =
        Store.state.cases
            .filter { userId in it.claimantTeam || userId in it.respondentTeam }
            .sortedByDescending { it.id }

    fun sideOf(c: CaseFile, userId: Int): Side? = when {
        userId in c.claimantTeam -> Side.CLAIMANT
        userId in c.respondentTeam -> Side.RESPONDENT
        else -> null
    }

    fun isLead(c: CaseFile, userId: Int): Boolean =
        c.claimantTeam.firstOrNull() == userId || c.respondentTeam.firstOrNull() == userId

    fun teamOf(c: CaseFile, side: Side): List<Int> =
        if (side == Side.CLAIMANT) c.claimantTeam else c.respondentTeam

    fun editable(c: CaseFile): Boolean =
        c.status == CaseStatus.DRAFT || c.status == CaseStatus.RETURNED

    // Own side sees its drafts and filed documents. The other side sees only what is filed.
    fun visibleDocs(c: CaseFile, userId: Int): List<Doc> {
        val side = sideOf(c, userId)
        return Store.state.docs
            .filter { it.caseId == c.id && (it.side == side || it.filed) }
            .sortedByDescending { it.at }
    }

    fun pendingInvites(user: User): List<Invite> =
        Store.state.invites.filter { it.status == "pending" && it.email.equals(user.email, true) }

    fun previewByCode(code: String): CaseFile? {
        val wanted = code.trim()
        if (wanted.isBlank()) return null
        return Store.state.cases.firstOrNull { it.caseCode.isNotBlank() && it.caseCode.equals(wanted, true) }
    }

    // ---------- draft, edit, file ----------

    fun createDraft(
        userId: Int, title: String, type: String, court: String,
        claimant: String, respondent: String, fee: String, receipt: String
    ): Int {
        val id = Store.nextId()
        Store.update { s ->
            s.copy(cases = s.cases + CaseFile(
                id = id, title = title.trim(), caseType = type, court = court,
                claimant = claimant.trim(), respondent = respondent.trim(),
                status = CaseStatus.DRAFT, claimantTeam = listOf(userId),
                feeAmount = fee.trim(), feeReceipt = receipt.trim()
            ))
        }
        Store.log(userId, "Created draft", title.trim())
        return id
    }

    fun updateDraft(
        caseId: Int, userId: Int, title: String, type: String, court: String,
        claimant: String, respondent: String, fee: String, receipt: String
    ): String? {
        val c = Store.caseById(caseId) ?: return "Case not found"
        if (c.claimantTeam.firstOrNull() != userId) return "Only the lead advocate can edit this case"
        if (!editable(c)) return "This case can no longer be edited"
        Store.updateCase(caseId) {
            it.copy(
                title = title.trim(), caseType = type, court = court,
                claimant = claimant.trim(), respondent = respondent.trim(),
                feeAmount = fee.trim(), feeReceipt = receipt.trim()
            )
        }
        Store.log(userId, "Edited case", title.trim())
        return null
    }

    fun deleteDraft(caseId: Int, userId: Int): String? {
        val c = Store.caseById(caseId) ?: return "Case not found"
        if (c.claimantTeam.firstOrNull() != userId) return "Only the lead advocate can delete this case"
        if (c.status != CaseStatus.DRAFT) return "Only a draft can be deleted"
        Store.update { s ->
            s.copy(
                cases = s.cases.filter { it.id != caseId },
                docs = s.docs.filter { it.caseId != caseId },
                messages = s.messages.filter { it.caseId != caseId },
                notes = s.notes.filter { it.caseId != caseId },
                tasks = s.tasks.filter { it.caseId != caseId },
                invites = s.invites.filter { it.caseId != caseId }
            )
        }
        Store.log(userId, "Deleted draft", c.title)
        return null
    }

    fun fileCase(caseId: Int, userId: Int): String? {
        val c = Store.caseById(caseId) ?: return "Case not found"
        if (c.claimantTeam.firstOrNull() != userId) return "Only the lead advocate can file a case"
        if (!editable(c)) return "This case has already been filed"
        if (c.title.isBlank() || c.claimant.isBlank() || c.respondent.isBlank()) {
            return "Complete the case details first"
        }
        Store.update { s ->
            s.copy(
                cases = s.cases.map {
                    if (it.id == caseId) it.copy(
                        status = CaseStatus.FILED, filedAt = System.currentTimeMillis(), defects = emptyList()
                    ) else it
                },
                docs = s.docs.map {
                    if (it.caseId == caseId && it.side == Side.CLAIMANT) it.copy(filed = true) else it
                }
            )
        }
        Store.pushRegistry("New filing", "${c.title} · filed for scrutiny", caseId)
        Store.log(userId, "Filed case", c.title)
        return null
    }

    // ---------- respondent appears ----------

    fun joinAsRespondent(userId: Int, code: String, fileName: String, pages: Int): String? {
        val u = Store.user(userId) ?: return "Not signed in"
        if (u.role != Role.LAWYER || u.status != AccountStatus.APPROVED) return "Only approved advocates can appear"
        val c = previewByCode(code) ?: return "No case found for this code"
        if (userId in c.claimantTeam) return "You act for the claimant in this case"
        if (c.respondentTeam.isNotEmpty()) {
            return "A respondent's advocate has already appeared. Ask the lead advocate to invite you."
        }
        if (c.status != CaseStatus.NOTICE) return "This case is not open for appearance"
        if (fileName.isBlank()) return "Upload your vakalatnama"
        val docId = Store.nextId()
        Store.update { s ->
            s.copy(
                cases = s.cases.map {
                    if (it.id == c.id) it.copy(respondentTeam = listOf(userId), status = CaseStatus.PLEADINGS) else it
                },
                docs = s.docs + Doc(
                    docId, c.id, fileName, "Vakalatnama", Side.RESPONDENT, true, userId,
                    pages.coerceAtLeast(1), System.currentTimeMillis()
                )
            )
        }
        c.claimantTeam.firstOrNull()?.let {
            Store.push(it, "Respondent appeared", "${u.name} appears for the respondent in ${c.label}", c.id)
        }
        if (c.judgeId != 0) Store.push(c.judgeId, "Respondent appeared", "${c.label} · ${u.name}", c.id)
        Store.pushRegistry("Respondent appeared", "${c.label} · ${u.name}", c.id)
        Store.log(userId, "Appeared for respondent", "${c.label} · code ${c.caseCode}")
        return null
    }

    // ---------- documents ----------

    fun addDoc(caseId: Int, userId: Int, name: String, type: String, pages: Int): String? {
        val c = Store.caseById(caseId) ?: return "Case not found"
        val side = sideOf(c, userId) ?: return "You are not on this case"
        if (c.status == CaseStatus.CLOSED) return "This case is closed"
        if (name.isBlank()) return "Choose a file"
        val id = Store.nextId()
        Store.update { s ->
            s.copy(docs = s.docs + Doc(
                id, caseId, name, type, side, false, userId, pages.coerceAtLeast(1), System.currentTimeMillis()
            ))
        }
        Store.log(userId, "Uploaded document", "${c.label} · $type · $name")
        return null
    }

    fun removeDoc(docId: Int, userId: Int): String? {
        val d = Store.state.docs.firstOrNull { it.id == docId } ?: return "Document not found"
        if (d.filed) return "Filed documents can't be removed"
        if (d.uploadedBy != userId) return "Only the uploader can remove this draft"
        Store.update { s -> s.copy(docs = s.docs.filter { it.id != docId }) }
        Store.log(userId, "Removed draft document", d.name)
        return null
    }

    fun fileDoc(docId: Int, userId: Int): String? {
        val d = Store.state.docs.firstOrNull { it.id == docId } ?: return "Document not found"
        val c = Store.caseById(d.caseId) ?: return "Case not found"
        if (sideOf(c, userId) != d.side) return "This is not your side's document"
        if (!isLead(c, userId)) return "Only the lead advocate can file to the court"
        if (d.filed) return "Already filed"
        Store.update { s -> s.copy(docs = s.docs.map { if (it.id == docId) it.copy(filed = true) else it }) }
        if (c.judgeId != 0) Store.push(c.judgeId, "Document filed", "${c.label} · ${d.type}: ${d.name}", c.id)
        val other = if (d.side == Side.CLAIMANT) c.respondentTeam else c.claimantTeam
        other.firstOrNull()?.let { Store.push(it, "Document filed", "${c.label} · ${d.type}: ${d.name}", c.id) }
        Store.log(userId, "Filed document", "${c.label} · ${d.name}")
        return null
    }

    // ---------- team ----------

    fun invite(caseId: Int, userId: Int, email: String): String? {
        val c = Store.caseById(caseId) ?: return "Case not found"
        val side = sideOf(c, userId) ?: return "You are not on this case"
        if (!isLead(c, userId)) return "Only the lead advocate can invite"
        val mail = email.trim().lowercase()
        if (mail.isBlank()) return "Enter the advocate's email"
        val target = Store.state.users.firstOrNull { it.email == mail } ?: return "No registered advocate has this email"
        if (target.role != Role.LAWYER || target.status != AccountStatus.APPROVED) {
            return "This person is not an approved advocate"
        }
        if (target.id in c.claimantTeam || target.id in c.respondentTeam) return "Already on this case"
        if (Store.state.invites.any { it.caseId == caseId && it.email == mail && it.status == "pending" }) {
            return "An invitation is already pending"
        }
        val id = Store.nextId()
        Store.update { it.copy(invites = it.invites + Invite(id, caseId, side, mail, userId)) }
        Store.push(target.id, "Case invitation", "${Store.user(userId)?.name} invited you to ${c.title}", caseId)
        Store.log(userId, "Invited advocate", "${c.label} · $mail")
        return null
    }

    fun respondInvite(inviteId: Int, userId: Int, accept: Boolean): String? {
        val u = Store.user(userId) ?: return "Not signed in"
        val inv = Store.state.invites.firstOrNull { it.id == inviteId } ?: return "Invitation not found"
        if (!inv.email.equals(u.email, true) || inv.status != "pending") return "This invitation is not available"
        val c = Store.caseById(inv.caseId) ?: return "Case not found"
        if (accept) {
            if (userId in c.claimantTeam || userId in c.respondentTeam) return "You are already on this case"
            Store.updateCase(c.id) {
                if (inv.side == Side.CLAIMANT) it.copy(claimantTeam = it.claimantTeam + userId)
                else it.copy(respondentTeam = it.respondentTeam + userId)
            }
        }
        Store.update { s ->
            s.copy(invites = s.invites.map {
                if (it.id == inviteId) it.copy(status = if (accept) "accepted" else "declined") else it
            })
        }
        Store.push(
            inv.invitedBy, if (accept) "Invitation accepted" else "Invitation declined",
            "${u.name} · ${c.label}", c.id
        )
        Store.log(userId, if (accept) "Joined team" else "Declined invitation", c.label)
        return null
    }

    fun sendMessage(caseId: Int, userId: Int, text: String): String? {
        val c = Store.caseById(caseId) ?: return "Case not found"
        val side = sideOf(c, userId) ?: return "You are not on this case"
        if (text.isBlank()) return "Type a message"
        val id = Store.nextId()
        Store.update { it.copy(messages = it.messages + Message(id, caseId, side, userId, text.trim(), System.currentTimeMillis())) }
        return null
    }

    fun addNote(caseId: Int, userId: Int, text: String): String? {
        val c = Store.caseById(caseId) ?: return "Case not found"
        val side = sideOf(c, userId) ?: return "You are not on this case"
        if (text.isBlank()) return "Write the note first"
        val id = Store.nextId()
        Store.update { it.copy(notes = it.notes + Note(id, caseId, side, userId, text.trim(), System.currentTimeMillis())) }
        return null
    }

    fun addTask(caseId: Int, userId: Int, assigneeId: Int, title: String, due: String): String? {
        val c = Store.caseById(caseId) ?: return "Case not found"
        val side = sideOf(c, userId) ?: return "You are not on this case"
        if (title.isBlank()) return "Enter the task"
        if (assigneeId !in teamOf(c, side)) return "Choose someone from your team"
        if (Dates.parse(due) == null) return "Pick a due date"
        val id = Store.nextId()
        Store.update { it.copy(tasks = it.tasks + Task(id, caseId, side, userId, assigneeId, title.trim(), due)) }
        if (assigneeId != userId) {
            Store.push(assigneeId, "Task assigned", "${title.trim()} · due $due", caseId)
        }
        return null
    }

    fun toggleTask(taskId: Int, userId: Int): String? {
        val t = Store.state.tasks.firstOrNull { it.id == taskId } ?: return "Task not found"
        val c = Store.caseById(t.caseId) ?: return "Case not found"
        if (sideOf(c, userId) != t.side) return "This task is not on your team"
        Store.update { s -> s.copy(tasks = s.tasks.map { if (it.id == taskId) it.copy(done = !it.done) else it }) }
        return null
    }
}