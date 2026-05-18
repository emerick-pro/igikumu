package com.airb.bioafis.dto.response

import com.airb.bioafis.domain.enums.FingerPosition

data class VerifyResponse(
    val uid: String,
    val matched: Boolean,
    val score: Double,
    val matchedPosition: FingerPosition?,
    val threshold: Double,
)
