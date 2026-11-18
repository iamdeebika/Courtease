package com.simats.courtease.data.api

import com.simats.courtease.data.ServerConfig
import com.simats.courtease.data.Session
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// The one Retrofit client for the whole app: one base URL, one OkHttp client, one ApiService.
object ApiClient {
    // The address lives in ServerConfig.BASE_URL.
    val BASE_URL: String get() = ServerConfig.url()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()
                // Lets ngrok free tunnels pass API calls through instead of showing their warning page.
                .header("ngrok-skip-browser-warning", "true")
            Session.token?.let { builder.header("Authorization", "Bearer $it") }
            chain.proceed(builder.build())
        }
        .build()

    @Volatile private var builtFor: String? = null
    @Volatile private var service: ApiService? = null

    // Rebuilt automatically when the server address is changed while the app is running.
    val api: ApiService
        get() {
            val url = BASE_URL
            val current = service
            if (current != null && builtFor == url) return current
            synchronized(this) {
                if (service == null || builtFor != url) {
                    service = Retrofit.Builder()
                        .baseUrl(url)
                        .client(http)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                        .create(ApiService::class.java)
                    builtFor = url
                }
                return service!!
            }
        }
}
