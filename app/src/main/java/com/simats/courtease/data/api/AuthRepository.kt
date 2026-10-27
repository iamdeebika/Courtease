package com.simats.courtease.data.api

import com.google.gson.JsonParser
import retrofit2.Response
import java.io.IOException

sealed class ApiResult<out T> {
    data class Ok<T>(val data: T) : ApiResult<T>()
    data class Err(val message: String) : ApiResult<Nothing>()
}

object AuthRepository {
    private val api get() = ApiClient.api

    suspend fun signup(name: String, email: String, phone: String, role: String, password: String) =
        safeCall { api.signup(SignupRequest(name, email, phone, role, password)) }

    suspend fun login(email: String, password: String) =
        safeCall { api.login(LoginRequest(email, password)) }

    suspend fun resetPassword(email: String, phone: String, newPassword: String) =
        safeCall { api.reset(ResetRequest(email, phone, newPassword)) }

    private suspend fun <T> safeCall(block: suspend () -> Response<T>): ApiResult<T> = try {
        val response = block()
        val body = response.body()
        when {
            response.isSuccessful && body != null -> ApiResult.Ok(body)
            response.isSuccessful -> ApiResult.Err("Empty response")
            else -> ApiResult.Err(errorMessage(response))
        }
    } catch (e: IOException) {
        ApiResult.Err("Can't reach the server (${e.javaClass.simpleName}: ${e.message})")
    } catch (e: Exception) {
        ApiResult.Err("Error (${e.javaClass.simpleName}: ${e.message})")
    }

    private fun errorMessage(response: Response<*>): String = try {
        val text = response.errorBody()?.string().orEmpty()
        val detail = JsonParser.parseString(text).asJsonObject.get("detail")
        if (detail != null && detail.isJsonPrimitive) detail.asString else "Something went wrong."
    } catch (e: Exception) {
        "Something went wrong (${response.code()})."
    }
}