package com.simats.courtease.data.api

import com.simats.courtease.data.Session
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface OverviewService {
    @GET("upcoming")
    suspend fun upcoming(): Response<UpcomingDto>

    @GET("cases/{id}/history")
    suspend fun history(@Path("id") id: Int): Response<HistoryDto>
}

object OverviewClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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

    val api: OverviewService by lazy {
        Retrofit.Builder()
            .baseUrl(ApiClient.BASE_URL)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OverviewService::class.java)
    }
}