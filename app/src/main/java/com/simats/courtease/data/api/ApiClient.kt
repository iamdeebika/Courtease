package com.simats.courtease.data.api

import com.simats.courtease.data.Session
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface ApiService {
    @POST("auth/signup") suspend fun signup(@Body body: SignupBody): Response<UserDto>
    @POST("auth/login") suspend fun login(@Body body: LoginBody): Response<TokenDto>
    @POST("auth/reset-password") suspend fun reset(@Body body: ResetBody): Response<MessageDto>

    @GET("users/me") suspend fun me(): Response<UserDto>
    @PUT("users/me") suspend fun updateMe(@Body body: ProfileBody): Response<UserDto>
    @POST("users/me/password") suspend fun changePassword(@Body body: PasswordBody): Response<MessageDto>
    @GET("sync/users") suspend fun syncUsers(): Response<List<UserDto>>

    @GET("notifications") suspend fun notifications(): Response<List<NotifDto>>
    @POST("notifications/read-all") suspend fun readAll(): Response<MessageDto>
    @POST("notifications/{id}/read") suspend fun readOne(@Path("id") id: Int): Response<MessageDto>

    @POST("registry/accounts/{id}/approve")
    suspend fun approve(@Path("id") id: Int, @Body body: ApproveBody): Response<UserDto>
    @POST("registry/accounts/{id}/reject")
    suspend fun reject(@Path("id") id: Int, @Body body: ReasonBody): Response<UserDto>
    @POST("registry/accounts/{id}/suspend")
    suspend fun suspend(@Path("id") id: Int, @Body body: ReasonBody): Response<UserDto>
    @POST("registry/accounts/{id}/reinstate")
    suspend fun reinstate(@Path("id") id: Int, @Body body: ReasonBody): Response<UserDto>
    @GET("registry/audit") suspend fun audit(): Response<List<AuditDto>>
}

object ApiClient {
    // USB cable: run "adb reverse tcp:8000 tcp:8000" and keep 127.0.0.1.
    const val BASE_URL = "http://127.0.0.1:8000/"

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

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}