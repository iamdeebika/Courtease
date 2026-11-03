package com.simats.courtease.data

import com.simats.courtease.data.api.ApiClient
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.InviteBody
import com.simats.courtease.data.api.RespondBody
import com.simats.courtease.data.api.TaskBody
import com.simats.courtease.data.api.TextBody
import com.simats.courtease.data.api.safeCall
import com.simats.courtease.model.Invite
import com.simats.courtease.model.Message
import com.simats.courtease.model.Note
import com.simats.courtease.model.Side
import com.simats.courtease.model.Task
import retrofit2.Response

// Team workspace actions. Everything goes to the server, then the phone downloads the new state.
object TeamRepo {
    private val api get() = ApiClient.api

    private fun side(text: String) = if (text == "respondent") Side.RESPONDENT else Side.CLAIMANT

    suspend fun sync(): Boolean =
        when (val r = safeCall { api.syncTeam() }) {
            is ApiResult.Ok -> {
                val d = r.data
                Store.replaceTeamData(
                    d.messages.map { Message(it.id, it.caseId, side(it.side), it.userId, it.text, it.createdAt) },
                    d.notes.map { Note(it.id, it.caseId, side(it.side), it.userId, it.text, it.createdAt) },
                    d.tasks.map {
                        Task(it.id, it.caseId, side(it.side), it.creatorId, it.assigneeId, it.title, it.due, it.done)
                    },
                    d.invites.map { Invite(it.id, it.caseId, side(it.side), it.email, it.invitedBy, it.status) }
                )
                true
            }
            is ApiResult.Err -> false
        }

    private suspend fun <T> act(refreshAll: Boolean = false, call: suspend () -> Response<T>): String? =
        when (val r = safeCall(call)) {
            is ApiResult.Ok -> {
                if (refreshAll) AccountRepo.syncAll() else sync()
                null
            }
            is ApiResult.Err -> r.message
        }

    suspend fun invite(caseId: Int, email: String): String? =
        act { api.invite(caseId, InviteBody(email.trim())) }

    suspend fun respondInvite(inviteId: Int, accept: Boolean): String? =
        act(refreshAll = true) { api.respond(inviteId, RespondBody(accept)) }

    suspend fun sendMessage(caseId: Int, text: String): String? =
        act { api.message(caseId, TextBody(text)) }

    suspend fun addNote(caseId: Int, text: String): String? =
        act { api.teamNote(caseId, TextBody(text)) }

    suspend fun addTask(caseId: Int, assigneeId: Int, title: String, due: String): String? =
        act { api.task(caseId, TaskBody(assigneeId, title, due)) }

    suspend fun toggleTask(taskId: Int): String? = act { api.toggle(taskId) }
}