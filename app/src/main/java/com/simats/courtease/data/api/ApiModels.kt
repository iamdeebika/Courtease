package com.simats.courtease.data.api

import com.google.gson.annotations.SerializedName

data class SignupBody(
    @SerializedName("full_name") val fullName: String,
    val email: String,
    val phone: String,
    val role: String,
    @SerializedName("reg_no") val regNo: String,
    val council: String,
    val password: String
)

data class LoginBody(val email: String, val password: String)

data class UserDto(
    val id: Int,
    @SerializedName("full_name") val fullName: String,
    val email: String,
    val phone: String,
    val role: String,
    val status: String,
    @SerializedName("reg_no") val regNo: String,
    val council: String,
    @SerializedName("registry_id") val registryId: String,
    val court: String,
    val courtroom: String,
    @SerializedName("reject_reason") val rejectReason: String,
    @SerializedName("created_at") val createdAt: Long
)

data class TokenDto(
    @SerializedName("access_token") val accessToken: String,
    val user: UserDto
)

data class ResetBody(
    val email: String,
    val phone: String,
    @SerializedName("new_password") val newPassword: String
)

data class MessageDto(val message: String)

data class ProfileBody(
    @SerializedName("full_name") val fullName: String,
    val phone: String
)

data class PasswordBody(
    @SerializedName("current_password") val currentPassword: String,
    @SerializedName("new_password") val newPassword: String
)

data class ApproveBody(val court: String, val courtroom: String)

data class ReasonBody(val reason: String)

data class NotifDto(
    val id: Int,
    val title: String,
    val body: String,
    @SerializedName("case_id") val caseId: Int,
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("is_read") val isRead: Boolean
)

data class AuditDto(
    val id: Int,
    @SerializedName("user_name") val userName: String,
    val action: String,
    val detail: String,
    @SerializedName("created_at") val createdAt: Long
)