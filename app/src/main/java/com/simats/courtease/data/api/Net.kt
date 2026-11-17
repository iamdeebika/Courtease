package com.simats.courtease.data.api

import com.google.gson.JsonParser
import com.simats.courtease.data.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import retrofit2.Response
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException

sealed class ApiResult<out T> {
    data class Ok<T>(val data: T) : ApiResult<T>()
    data class Err(val message: String, val code: Int = 0) : ApiResult<Nothing>()
}

private const val MAX_ATTEMPTS = 5
private const val WAIT_MS = 1500L

// A broken connection (tunnel missing, server restarting). Server answers such as 400 or 500 are never retried.
private fun connectionProblem(e: IOException): Boolean =
    e is SocketException || e.message?.contains("unexpected end of stream", ignoreCase = true) == true

private fun friendly(e: IOException): String = when {
    connectionProblem(e) ->
        "Can't reach the server. Make sure the CourtEase server is running on the PC and the phone is connected."
    e is SocketTimeoutException -> "The server took too long to answer. Please try again."
    else -> "Network problem (${e.javaClass.simpleName}). Please try again."
}

suspend fun <T> safeCall(block: suspend () -> Response<T>): ApiResult<T> {
    var attempt = 0
    while (true) {
        try {
            val response = block()
            val body = response.body()
            // A 401 while signed in means the login has expired. The app then signs the user out.
            if (response.code() == 401) Session.markExpired()
            return when {
                response.isSuccessful && body != null -> ApiResult.Ok(body)
                response.isSuccessful -> ApiResult.Err("Empty response", response.code())
                else -> ApiResult.Err(errorMessage(response), response.code())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            attempt++
            if (connectionProblem(e) && attempt < MAX_ATTEMPTS) {
                delay(WAIT_MS)
            } else {
                return ApiResult.Err(friendly(e))
            }
        } catch (e: Exception) {
            return ApiResult.Err("Something went wrong. Try again.")
        }
    }
}

private fun errorMessage(response: Response<*>): String = try {
    val text = response.errorBody()?.string().orEmpty()
    val detail = JsonParser.parseString(text).asJsonObject.get("detail")
    if (detail != null && detail.isJsonPrimitive) detail.asString else "Something went wrong."
} catch (e: Exception) {
    "Something went wrong (${response.code()})."
}
