package com.simats.courtease.data.api

import com.simats.courtease.data.Session
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class TokenBody(val token: String)

interface PushService {
    @POST("devices/register")
    suspend fun register(@Body body: TokenBody): Response<MessageDto>

    @POST("devices/unregister")
    suspend fun unregister(@Body body: TokenBody): Response<MessageDto>
}

object PushClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val token = Session.token
            val request = if (token != null) {
                chain.request().newBuilder().header("Authorization", "Bearer $token").build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
        .build()

    val api: PushService by lazy {
        Retrofit.Builder()
            .baseUrl(ApiClient.BASE_URL)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PushService::class.java)
    }
}

// Sends this phone's push token to the CourtEase server, or removes it.
object PushRepo {
    suspend fun register(token: String): Boolean =
        safeCall { PushClient.api.register(TokenBody(token)) } is ApiResult.Ok

    suspend fun unregister(token: String): Boolean =
        safeCall { PushClient.api.unregister(TokenBody(token)) } is ApiResult.Ok
}
