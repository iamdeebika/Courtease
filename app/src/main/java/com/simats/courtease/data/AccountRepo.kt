package com.simats.courtease.data

import android.content.Context
import com.simats.courtease.data.api.ApiClient
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.ApproveBody
import com.simats.courtease.data.api.AuditDto
import com.simats.courtease.data.api.LoginBody
import com.simats.courtease.data.api.PasswordBody
import com.simats.courtease.data.api.ProfileBody
import com.simats.courtease.data.api.ReasonBody
import com.simats.courtease.data.api.ResetBody
import com.simats.courtease.data.api.SignupBody
import com.simats.courtease.data.api.UserDto
import com.simats.courtease.data.api.safeCall
import com.simats.courtease.model.AccountStatus
import com.simats.courtease.model.Notif
import com.simats.courtease.model.Role
import com.simats.courtease.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.Response

// Talks to the server for accounts, approval and account alerts.
object AccountRepo {
    // Alerts that came from the server get ids above this, so they never clash with local ones.
    const val SERVER_NOTIF_BASE = 1_000_000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api get() = ApiClient.api

    private fun UserDto.toUser() = User(
        id = id, name = fullName, email = email, phone = phone, password = "",
        role = when (role) {
            "registry" -> Role.REGISTRY
            "judge" -> Role.JUDGE
            else -> Role.LAWYER
        },
        status = when (status) {
            "approved" -> AccountStatus.APPROVED
            "rejected" -> AccountStatus.REJECTED
            "suspended" -> AccountStatus.SUSPENDED
            else -> AccountStatus.PENDING
        },
        regNo = regNo, council = council, registryId = registryId,
        court = court, courtroom = courtroom, rejectReason = rejectReason, createdAt = createdAt
    )

    // ---------- accounts ----------

    suspend fun signup(
        name: String, email: String, phone: String, password: String,
        role: Role, regNo: String, council: String
    ): String? {
        val body = SignupBody(
            name.trim(), email.trim(), phone.trim(), role.name.lowercase(),
            regNo.trim(), council.trim(), password
        )
        return when (val r = safeCall { api.signup(body) }) {
            is ApiResult.Ok -> {
                if (Session.userId != 0) syncAll()
                null
            }
            is ApiResult.Err -> r.message
        }
    }

    suspend fun login(context: Context, email: String, password: String): String? =
        when (val r = safeCall { api.login(LoginBody(email.trim(), password)) }) {
            is ApiResult.Ok -> {
                Session.login(context, r.data.user.id, r.data.accessToken)
                Store.upsertUser(r.data.user.toUser())
                syncAll()
                null
            }
            is ApiResult.Err -> r.message
        }

    suspend fun resetPassword(email: String, phone: String, newPassword: String): String? =
        when (val r = safeCall { api.reset(ResetBody(email.trim(), phone.trim(), newPassword)) }) {
            is ApiResult.Ok -> null
            is ApiResult.Err -> r.message
        }

    suspend fun updateProfile(name: String, phone: String): String? =
        when (val r = safeCall { api.updateMe(ProfileBody(name.trim(), phone.trim())) }) {
            is ApiResult.Ok -> {
                Store.upsertUser(r.data.toUser())
                null
            }
            is ApiResult.Err -> r.message
        }

    suspend fun changePassword(current: String, newPassword: String): String? =
        when (val r = safeCall { api.changePassword(PasswordBody(current, newPassword)) }) {
            is ApiResult.Ok -> null
            is ApiResult.Err -> r.message
        }

    // ---------- sync ----------

    /** Refreshes the signed-in user, the directory and the alerts. Returns false only if the session is no longer valid. */
    suspend fun syncAll(): Boolean {
        val me = when (val r = safeCall { api.me() }) {
            is ApiResult.Ok -> r.data
            is ApiResult.Err -> return r.code != 401
        }
        val self = me.toUser()
        Store.upsertUser(self)

        if (self.status == AccountStatus.APPROVED) {
            val list = safeCall { api.syncUsers() }
            if (list is ApiResult.Ok) {
                val mapped = list.data.map { it.toUser() }.map { if (it.id == self.id) self else it }
                Store.replaceUsers(if (mapped.any { it.id == self.id }) mapped else mapped + self)
            }
        }

        val notifs = safeCall { api.notifications() }
        if (notifs is ApiResult.Ok) {
            Store.replaceServerNotifs(
                self.id,
                notifs.data.map {
                    Notif(SERVER_NOTIF_BASE + it.id, self.id, it.title, it.body, it.caseId, it.createdAt, it.isRead)
                }
            )
        }
        return true
    }

    // ---------- registry ----------

    suspend fun approve(userId: Int, court: String, courtroom: String): String? =
        decide { api.approve(userId, ApproveBody(court, courtroom)) }

    suspend fun reject(userId: Int, reason: String): String? =
        decide { api.reject(userId, ReasonBody(reason)) }

    suspend fun setSuspended(userId: Int, doSuspend: Boolean, reason: String): String? =
        decide {
            if (doSuspend) api.suspend(userId, ReasonBody(reason))
            else api.reinstate(userId, ReasonBody(reason))
        }

    suspend fun audit(): ApiResult<List<AuditDto>> = safeCall { api.audit() }

    private suspend fun decide(call: suspend () -> Response<UserDto>): String? =
        when (val r = safeCall(call)) {
            is ApiResult.Ok -> {
                syncAll()
                null
            }
            is ApiResult.Err -> r.message
        }

    // ---------- alerts ----------

    fun markReadInBackground(serverId: Int) {
        scope.launch { safeCall { api.readOne(serverId) } }
    }

    fun markAllReadInBackground() {
        scope.launch { safeCall { api.readAll() } }
    }
}