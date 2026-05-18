package com.airb.bioafis.dto.request

import jakarta.validation.constraints.Size

data class StartDedupRequest(
    @field:Size(max = 50)
    val scopeFacility: String? = null,

    val threshold: Double? = null,
)
