package com.airb.bioafis.dto.response

import java.time.Instant

data class TenantResponse(
    val id: Long,
    val name: String,
    val isAdmin: Boolean,
    val isActive: Boolean,
    val createdAt: Instant,
)

data class CreateTenantResponse(
    val id: Long,
    val name: String,
    val isAdmin: Boolean,
    val apiKey: String,
    val createdAt: Instant,
)
