package com.simats.courtease.data.api

import com.google.gson.annotations.SerializedName

data class SignupRequest(
    @SerializedName("full_name") val fullName: String,
    val email: String,
    val phone: String,
    val role: String,
    val password: String
)

data class LoginRequest(val email: String, val password: String)

data class UserOut(
    val id: Int,
    @SerializedName("full_name") val fullName: String,
    val email: String,
    val phone: String,
    val role: String
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String?,
    val user: UserOut
)

data class ResetRequest(
    val email: String,
    val phone: String,
    @SerializedName("new_password") val newPassword: String
)

data class MessageResponse(val message: String)