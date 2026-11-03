package com.simats.courtease.data

import com.simats.courtease.data.api.AllocateBody
import com.simats.courtease.data.api.ApiClient
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.CaseDto
import com.simats.courtease.data.api.CaseSaveBody
import com.simats.courtease.data.api.DocBody
import com.simats.courtease.data.api.JoinBody
import com.simats.courtease.data.api.NoteBody
import com.simats.courtease.data.api.OrderBody
import com.simats.courtease.data.api.ReasonBody
import com.simats.courtease.data.api.ReturnBody
import com.simats.courtease.data.api.safeCall
import com.simats.courtease.model.CaseFile
import com.simats.courtease.model.CaseStatus
import com.simats.courtease.model.Doc
import com.simats.courtease.model.Order
import com.simats.courtease.model.PrivateNote
import com.simats.courtease.model.Side
import retrofit2.Response

// Every case action goes to the server. On success the phone downloads the new state.
object CaseRepo {
    private val api get() = ApiClient.api

    private fun CaseDto.toCase() = CaseFile(
        id = id, title = title, caseType = caseType, court = court,
        claimant = claimant, respondent = respondent,
        status = runCatching { CaseStatus.valueOf(status.uppercase()) }.getOrDefault(CaseStatus.DRAFT),
        claimantTeam = claimantTeam, respondentTeam = respondentTeam, caseNo = caseNo,
        feeAmount = feeAmount, feeReceipt = feeReceipt, judgeId = judgeId, courtroom = courtroom,
        caseCode = caseCode, responseDue = responseDue, defects = defects,
        registryNote = registryNote, filedAt = filedAt
    )

    suspend fun sync(): Boolean =
        when (val r = safeCall { api.syncCases() }) {
            is ApiResult.Ok -> {
                val d = r.data
                Store.replaceCaseData(
                    d.cases.map { it.toCase() },
                    d.docs.map {
                        Doc(
                            it.id, it.caseId, it.name, it.docType,
                            if (it.side == "respondent") Side.RESPONDENT else Side.CLAIMANT,
                            it.filed, it.uploadedBy, it.pages, it.createdAt, it.hasFile
                        )
                    },
                    d.orders.map { Order(it.id, it.caseId, it.judgeId, it.kind, it.title, it.body, it.createdAt) },
                    d.notes.map { PrivateNote(it.id, it.caseId, it.userId, it.text, it.createdAt) }
                )
                true
            }
            is ApiResult.Err -> false
        }

    // Runs one action. On success it refreshes everything and returns null. Otherwise it returns the error text.
    private suspend fun <T> act(call: suspend () -> Response<T>): String? =
        when (val r = safeCall(call)) {
            is ApiResult.Ok -> {
                AccountRepo.syncAll()
                null
            }
            is ApiResult.Err -> r.message
        }

    // ---------- lawyer ----------

    suspend fun createDraft(
        title: String, type: String, court: String, claimant: String,
        respondent: String, fee: String, receipt: String
    ): ApiResult<Int> =
        when (val r = safeCall {
            api.createCase(CaseSaveBody(title.trim(), type, court, claimant.trim(), respondent.trim(), fee.trim(), receipt.trim()))
        }) {
            is ApiResult.Ok -> {
                AccountRepo.syncAll()
                ApiResult.Ok(r.data.id)
            }
            is ApiResult.Err -> ApiResult.Err(r.message, r.code)
        }

    suspend fun updateDraft(
        id: Int, title: String, type: String, court: String, claimant: String,
        respondent: String, fee: String, receipt: String
    ): String? = act {
        api.updateCase(id, CaseSaveBody(title.trim(), type, court, claimant.trim(), respondent.trim(), fee.trim(), receipt.trim()))
    }

    suspend fun deleteDraft(id: Int): String? = act { api.deleteCase(id) }
    suspend fun fileCase(id: Int): String? = act { api.fileCase(id) }

    suspend fun preview(code: String): ApiResult<CaseFile> =
        when (val r = safeCall { api.preview(code.trim()) }) {
            is ApiResult.Ok -> ApiResult.Ok(
                CaseFile(
                    id = r.data.id, title = r.data.title, caseType = r.data.caseType, court = r.data.court,
                    claimant = r.data.claimant, respondent = r.data.respondent, status = CaseStatus.NOTICE,
                    caseNo = r.data.caseNo, responseDue = r.data.responseDue
                )
            )
            is ApiResult.Err -> ApiResult.Err(r.message, r.code)
        }

    suspend fun join(code: String, fileName: String, pages: Int): String? =
        act { api.join(JoinBody(code.trim(), fileName, pages)) }

    suspend fun addDoc(caseId: Int, name: String, type: String, pages: Int): String? =
        act { api.addDoc(caseId, DocBody(name, type, pages)) }

    suspend fun removeDoc(docId: Int): String? = act { api.removeDoc(docId) }
    suspend fun fileDoc(docId: Int): String? = act { api.fileDoc(docId) }

    // ---------- registry ----------

    suspend fun returnCase(id: Int, note: String): String? = act { api.returnCase(id, ReturnBody(note)) }
    suspend fun registerCase(id: Int): String? = act { api.registerCase(id) }
    suspend fun allocate(id: Int, judgeId: Int, reason: String): String? =
        act { api.allocate(id, AllocateBody(judgeId, reason)) }
    suspend fun issueNotice(id: Int): String? = act { api.notice(id) }
    suspend fun reissueCode(id: Int): String? = act { api.reissue(id) }
    suspend fun resetCases(): String? = act { api.resetCases() }

    // ---------- judge ----------

    suspend fun startReview(id: Int): String? = act { api.review(id) }
    suspend fun issueOrder(id: Int, kind: String, title: String, body: String): String? =
        act { api.order(id, OrderBody(kind, title, body)) }
    suspend fun closeCase(id: Int, reason: String): String? = act { api.close(id, ReasonBody(reason)) }
    suspend fun recuse(id: Int, reason: String): String? = act { api.recuse(id, ReasonBody(reason)) }
    suspend fun addNote(id: Int, text: String): String? = act { api.addNote(id, NoteBody(text)) }
    suspend fun deleteNote(id: Int): String? = act { api.deleteNote(id) }
}