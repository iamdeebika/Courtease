package com.simats.courtease.data.api

import com.simats.courtease.data.Session
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface ApiService {
    // accounts
    @POST("auth/signup") suspend fun signup(@Body body: SignupBody): Response<UserDto>
    @POST("auth/login") suspend fun login(@Body body: LoginBody): Response<TokenDto>
    @POST("auth/reset-password") suspend fun reset(@Body body: ResetBody): Response<MessageDto>
    @GET("users/me") suspend fun me(): Response<UserDto>
    @PUT("users/me") suspend fun updateMe(@Body body: ProfileBody): Response<UserDto>
    @POST("users/me/password") suspend fun changePassword(@Body body: PasswordBody): Response<MessageDto>
    @GET("sync/users") suspend fun syncUsers(): Response<List<UserDto>>

    // alerts
    @GET("notifications") suspend fun notifications(): Response<List<NotifDto>>
    @POST("notifications/read-all") suspend fun readAll(): Response<MessageDto>
    @POST("notifications/{id}/read") suspend fun readOne(@Path("id") id: Int): Response<MessageDto>

    // registry: accounts
    @POST("registry/accounts/{id}/approve")
    suspend fun approve(@Path("id") id: Int, @Body body: ApproveBody): Response<UserDto>
    @POST("registry/accounts/{id}/reject")
    suspend fun reject(@Path("id") id: Int, @Body body: ReasonBody): Response<UserDto>
    @POST("registry/accounts/{id}/suspend")
    suspend fun suspend(@Path("id") id: Int, @Body body: ReasonBody): Response<UserDto>
    @POST("registry/accounts/{id}/reinstate")
    suspend fun reinstate(@Path("id") id: Int, @Body body: ReasonBody): Response<UserDto>
    @GET("registry/audit") suspend fun audit(): Response<List<AuditDto>>
    @GET("registry/report") suspend fun report(): Response<ReportDto>
    @POST("registry/run-reminders") suspend fun runReminders(): Response<MessageDto>

    // cases
    @GET("sync/cases") suspend fun syncCases(): Response<SyncDto>
    @POST("cases") suspend fun createCase(@Body body: CaseSaveBody): Response<CaseDto>
    @PUT("cases/{id}") suspend fun updateCase(@Path("id") id: Int, @Body body: CaseSaveBody): Response<CaseDto>
    @DELETE("cases/{id}") suspend fun deleteCase(@Path("id") id: Int): Response<MessageDto>
    @POST("cases/{id}/file") suspend fun fileCase(@Path("id") id: Int): Response<CaseDto>
    @GET("cases/by-code/{code}") suspend fun preview(@Path("code") code: String): Response<PreviewDto>
    @POST("cases/join") suspend fun join(@Body body: JoinBody): Response<CaseDto>

    // documents
    @POST("cases/{id}/docs") suspend fun addDoc(@Path("id") id: Int, @Body body: DocBody): Response<DocDto>
    @DELETE("docs/{id}") suspend fun removeDoc(@Path("id") id: Int): Response<MessageDto>
    @POST("docs/{id}/file") suspend fun fileDoc(@Path("id") id: Int): Response<DocDto>

    @Multipart
    @POST("cases/{id}/upload")
    suspend fun upload(
        @Path("id") id: Int,
        @Part("doc_type") docType: RequestBody,
        @Part("pages") pages: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<DocDto>

    @Multipart
    @POST("cases/join-file")
    suspend fun joinFile(
        @Part("code") code: RequestBody,
        @Part("pages") pages: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<CaseDto>

    @GET("docs/{id}/content") suspend fun content(@Path("id") id: Int): Response<ResponseBody>

    // team workspace
    @GET("sync/team") suspend fun syncTeam(): Response<TeamSyncDto>
    @POST("cases/{id}/invites") suspend fun invite(@Path("id") id: Int, @Body body: InviteBody): Response<InviteDto>
    @POST("invites/{id}/respond") suspend fun respond(@Path("id") id: Int, @Body body: RespondBody): Response<InviteDto>
    @POST("cases/{id}/messages") suspend fun message(@Path("id") id: Int, @Body body: TextBody): Response<TextDto>
    @POST("cases/{id}/notes") suspend fun teamNote(@Path("id") id: Int, @Body body: TextBody): Response<TextDto>
    @POST("cases/{id}/tasks") suspend fun task(@Path("id") id: Int, @Body body: TaskBody): Response<TaskDto>
    @POST("tasks/{id}/toggle") suspend fun toggle(@Path("id") id: Int): Response<TaskDto>

    // registry: filings
    @POST("registry/cases/{id}/return")
    suspend fun returnCase(@Path("id") id: Int, @Body body: ReturnBody): Response<CaseDto>
    @POST("registry/cases/{id}/register") suspend fun registerCase(@Path("id") id: Int): Response<CaseDto>
    @POST("registry/cases/{id}/allocate")
    suspend fun allocate(@Path("id") id: Int, @Body body: AllocateBody): Response<CaseDto>
    @POST("registry/cases/{id}/notice") suspend fun notice(@Path("id") id: Int): Response<CaseDto>
    @POST("registry/cases/{id}/reissue") suspend fun reissue(@Path("id") id: Int): Response<CaseDto>
    @POST("registry/reset-cases") suspend fun resetCases(): Response<MessageDto>

    // judge
    @POST("judge/cases/{id}/review") suspend fun review(@Path("id") id: Int): Response<CaseDto>
    @POST("judge/cases/{id}/orders")
    suspend fun order(@Path("id") id: Int, @Body body: OrderBody): Response<OrderDto>
    @POST("judge/cases/{id}/close")
    suspend fun close(@Path("id") id: Int, @Body body: ReasonBody): Response<CaseDto>
    @POST("judge/cases/{id}/recuse")
    suspend fun recuse(@Path("id") id: Int, @Body body: ReasonBody): Response<CaseDto>
    @POST("judge/cases/{id}/notes")
    suspend fun addNote(@Path("id") id: Int, @Body body: NoteBody): Response<NoteDto>
    @DELETE("judge/notes/{id}") suspend fun deleteNote(@Path("id") id: Int): Response<MessageDto>
}

object ApiClient {
    // USB cable: run "adb reverse tcp:8000 tcp:8000" and keep 127.0.0.1.
    const val BASE_URL = "http://127.0.0.1:8000/"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
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