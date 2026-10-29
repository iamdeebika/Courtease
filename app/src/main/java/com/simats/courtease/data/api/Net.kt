package com.simats.courtease.data.api

import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import java.io.IOException

sealed class ApiResult<out T> {
    data class Ok<T>(val data: T) : ApiResult<T>()
    data class Err(val message: String, val code: Int = 0) : ApiResult<Nothing>()
}

suspend fun <T> safeCall(block: suspend () -> Response<T>): ApiResult<T> = try {
    val response = block()
    val body = response.body()
    when {
        response.isSuccessful && body != null -> ApiResult.Ok(body)
        response.isSuccessful -> ApiResult.Err("Empty response", response.code())
        else -> ApiResult.Err(errorMessage(response), response.code())
    }
} catch (e: CancellationException) {
    throw e
} catch (e: IOException) {
    ApiResult.Err("Can't reach the server (${e.javaClass.simpleName}: ${e.message})")
} catch (e: Exception) {
    ApiResult.Err("Something went wrong. Try again.")
}

private fun errorMessage(response: Response<*>): String = try {
    val text = response.errorBody()?.string().orEmpty()
    val detail = JsonParser.parseString(text).asJsonObject.get("detail")
    if (detail != null && detail.isJsonPrimitive) detail.asString else "Something went wrong."
} catch (e: Exception) {
    "Something went wrong (${response.code()})."
}