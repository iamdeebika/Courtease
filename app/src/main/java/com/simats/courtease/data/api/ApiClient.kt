package com.simats.courtease.data.api

import com.simats.courtease.data.Session
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface ApiService {
    @POST("auth/signup")
    suspend fun signup(@Body body: SignupRequest): Response<UserOut>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<TokenResponse>

    @POST("auth/reset-password")
    suspend fun reset(@Body body: ResetRequest): Response<MessageResponse>

    @GET("users/me")
    suspend fun me(): Response<UserOut>
}

object ApiClient {
    // With the phone on USB, run: adb reverse tcp:8000 tcp:8000
    // For Wi-Fi instead, use your PC's IP, for example "http://192.168.1.5:8000/".
    const val BASE_URL = "http://127.0.0.1:8000/"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
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

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}