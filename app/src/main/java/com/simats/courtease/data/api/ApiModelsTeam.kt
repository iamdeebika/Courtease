package com.simats.courtease.data.api

import com.google.gson.annotations.SerializedName

data class InviteBody(val email: String)

data class RespondBody(val accept: Boolean)

data class TextBody(val text: String)

data class TaskBody(
    @SerializedName("assignee_id") val assigneeId: Int,
    val title: String,
    val due: String
)

data class InviteDto(
    val id: Int,
    @SerializedName("case_id") val caseId: Int,
    val side: String,
    val email: String,
    @SerializedName("invited_by") val invitedBy: Int,
    val status: String
)

data class TextDto(
    val id: Int,
    @SerializedName("case_id") val caseId: Int,
    val side: String,
    @SerializedName("user_id") val userId: Int,
    val text: String,
    @SerializedName("created_at") val createdAt: Long
)

data class TaskDto(
    val id: Int,
    @SerializedName("case_id") val caseId: Int,
    val side: String,
    @SerializedName("creator_id") val creatorId: Int,
    @SerializedName("assignee_id") val assigneeId: Int,
    val title: String,
    val due: String,
    val done: Boolean
)

data class TeamSyncDto(
    val invites: List<InviteDto>,
    val messages: List<TextDto>,
    val notes: List<TextDto>,
    val tasks: List<TaskDto>
)