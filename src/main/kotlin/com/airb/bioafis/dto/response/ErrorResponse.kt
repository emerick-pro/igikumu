package com.airb.bioafis.dto.response

import java.time.Instant

data class ErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, Any>? = null,
    val timestamp: Instant = Instant.now(),
)
