package com.simats.courtease.data.api

import com.simats.courtease.data.Session
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import java.util.concurrent.TimeUnit

data class PushOptionDto(val key: String, val label: String, val detail: String, val enabled: Boolean)

data class PushSettingsDto(val enabled: Boolean, val options: List<PushOptionDto>)

data class PushSettingsBody(val enabled: Boolean, val muted: List<String>)

interface PushPrefsService {
    @GET("push/settings")
    suspend fun get(): Response<PushSettingsDto>

    @PUT("push/settings")
    suspend fun put(@Body body: PushSettingsBody): Response<PushSettingsDto>
}

object PushPrefsClient {
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

    val api: PushPrefsService by lazy {
        Retrofit.Builder()
            .baseUrl(ApiClient.BASE_URL)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PushPrefsService::class.java)
    }
}

object PushPrefsRepo {
    suspend fun load(): ApiResult<PushSettingsDto> = safeCall { PushPrefsClient.api.get() }

    suspend fun save(enabled: Boolean, muted: List<String>): ApiResult<PushSettingsDto> =
        safeCall { PushPrefsClient.api.put(PushSettingsBody(enabled, muted)) }
}
