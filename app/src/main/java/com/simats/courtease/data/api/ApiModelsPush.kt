package com.simats.courtease.data.api

data class TokenBody(val token: String)

data class PushOptionDto(val key: String, val label: String, val detail: String, val enabled: Boolean)

data class PushSettingsDto(val enabled: Boolean, val options: List<PushOptionDto>)

data class PushSettingsBody(val enabled: Boolean, val muted: List<String>)
