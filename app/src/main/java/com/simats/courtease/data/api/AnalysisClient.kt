package com.simats.courtease.data.api

import com.simats.courtease.data.Session
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface AnalysisService {
    @GET("cases/{id}/reading")
    suspend fun reading(@Path("id") id: Int): Response<ReadingDto>

    @POST("cases/{id}/reading")
    suspend fun startReading(@Path("id") id: Int, @Query("force") force: Boolean): Response<ReadingDto>

    @GET("docs/{id}/pages")
    suspend fun pages(@Path("id") id: Int): Response<DocPagesDto>

    @GET("cases/{id}/timeline")
    suspend fun timeline(@Path("id") id: Int): Response<TimelineDto>
}

object AnalysisClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
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

    val api: AnalysisService by lazy {
        Retrofit.Builder()
            .baseUrl(ApiClient.BASE_URL)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AnalysisService::class.java)
    }
}