package com.simats.courtease.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

// Every CourtEase server endpoint in one interface. All calls go through ApiClient.api.
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

    // reading and analysis
    @GET("cases/{id}/reading") suspend fun reading(@Path("id") id: Int): Response<ReadingDto>
    @POST("cases/{id}/reading")
    suspend fun startReading(@Path("id") id: Int, @Query("force") force: Boolean): Response<ReadingDto>
    @GET("docs/{id}/pages") suspend fun docPages(@Path("id") id: Int): Response<DocPagesDto>
    @GET("cases/{id}/timeline") suspend fun timeline(@Path("id") id: Int): Response<TimelineDto>
    @GET("cases/{id}/issues") suspend fun issues(@Path("id") id: Int): Response<IssuesDto>
    @GET("cases/{id}/flags") suspend fun flags(@Path("id") id: Int): Response<FlagsDto>
    @GET("cases/{id}/summary") suspend fun summary(@Path("id") id: Int): Response<SummaryDto>
    @POST("cases/{id}/analysis/seen") suspend fun markSeen(@Path("id") id: Int): Response<MessageDto>
    @GET("cases/{id}/report") suspend fun caseReport(@Path("id") id: Int): Response<ResponseBody>

    // law search and saved precedents
    @GET("law/search")
    suspend fun lawSearch(@Query("q") q: String, @Query("offset") offset: Int): Response<LawSearchDto>
    @GET("law/document/{id}/pages")
    suspend fun lawPages(
        @Path("id") id: String,
        @Query("after") after: Int,
        @Query("limit") limit: Int
    ): Response<PagesDto>
    @POST("precedents") suspend fun savePrecedent(@Body body: SavedBody): Response<SavedDto>
    @POST("precedents/manual") suspend fun saveManualPrecedent(@Body body: ManualBody): Response<SavedDto>
    @GET("precedents") suspend fun savedPrecedents(@Query("case_id") caseId: Int): Response<List<SavedDto>>
    @DELETE("precedents/{id}") suspend fun deletePrecedent(@Path("id") id: Int): Response<MessageDto>

    // overview
    @GET("upcoming") suspend fun upcoming(): Response<UpcomingDto>
    @GET("cases/{id}/history") suspend fun history(@Path("id") id: Int): Response<HistoryDto>

    // papers
    @GET("docs/{id}/integrity") suspend fun integrity(@Path("id") id: Int): Response<IntegrityCheckDto>
    @GET("cases/{id}/papers") suspend fun papers(@Path("id") id: Int): Response<PapersDto>
    @GET("cases/{id}/notice.pdf") suspend fun noticePdf(@Path("id") id: Int): Response<ResponseBody>
    @GET("orders/{id}/pdf") suspend fun orderPdf(@Path("id") id: Int): Response<ResponseBody>

    // push notifications
    @POST("devices/register") suspend fun registerDevice(@Body body: TokenBody): Response<MessageDto>
    @POST("devices/unregister") suspend fun unregisterDevice(@Body body: TokenBody): Response<MessageDto>
    @GET("push/settings") suspend fun pushSettings(): Response<PushSettingsDto>
    @PUT("push/settings") suspend fun savePushSettings(@Body body: PushSettingsBody): Response<PushSettingsDto>
}
