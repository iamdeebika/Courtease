package com.simats.courtease.data

import com.simats.courtease.data.api.ApiClient
import com.simats.courtease.data.api.ApiResult
import com.simats.courtease.data.api.PushSettingsBody
import com.simats.courtease.data.api.PushSettingsDto
import com.simats.courtease.data.api.TokenBody
import com.simats.courtease.data.api.safeCall

// Sends this phone's push token to the CourtEase server, or removes it.
object PushRepo {
    suspend fun register(token: String): Boolean =
        safeCall { ApiClient.api.registerDevice(TokenBody(token)) } is ApiResult.Ok

    suspend fun unregister(token: String): Boolean =
        safeCall { ApiClient.api.unregisterDevice(TokenBody(token)) } is ApiResult.Ok
}

// The user's push notification choices, stored on the server.
object PushPrefsRepo {
    suspend fun load(): ApiResult<PushSettingsDto> = safeCall { ApiClient.api.pushSettings() }

    suspend fun save(enabled: Boolean, muted: List<String>): ApiResult<PushSettingsDto> =
        safeCall { ApiClient.api.savePushSettings(PushSettingsBody(enabled, muted)) }
}
