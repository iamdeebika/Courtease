package com.simats.courtease.data.api

import com.simats.courtease.data.Session
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface LawService {
    @GET("law/search")
    suspend fun search(
        @Query("q") q: String,
        @Query("offset") offset: Int
    ): Response<LawSearchDto>

    @GET("law/document/{id}/pages")
    suspend fun pages(
        @Path("id") id: String,
        @Query("after") after: Int,
        @Query("limit") limit: Int
    ): Response<PagesDto>

    @POST("precedents") suspend fun save(@Body body: SavedBody): Response<SavedDto>
    @GET("precedents") suspend fun saved(@Query("case_id") caseId: Int): Response<List<SavedDto>>
    @DELETE("precedents/{id}") suspend fun unsave(@Path("id") id: Int): Response<MessageDto>
}

object LawClient {
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

    val api: LawService by lazy {
        Retrofit.Builder()
            .baseUrl(ApiClient.BASE_URL)
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LawService::class.java)
    }
}