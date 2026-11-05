package com.simats.courtease.data.api

import com.simats.courtease.data.Session
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface PapersService {
    @GET("docs/{id}/integrity")
    suspend fun integrity(@Path("id") id: Int): Response<IntegrityCheckDto>

    @GET("cases/{id}/papers")
    suspend fun papers(@Path("id") id: Int): Response<PapersDto>

    @GET("cases/{id}/notice.pdf")
    suspend fun noticePdf(@Path("id") id: Int): Response<ResponseBody>

    @GET("orders/{id}/pdf")
    suspend fun orderPdf(@Path("id") id: Int): Response<ResponseBody>
}

object PapersClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
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

    val api: PapersService by lazy {
        Retrofit.Builder()
            .baseUrl(ApiClient.BASE_URL)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PapersService::class.java)
    }
}