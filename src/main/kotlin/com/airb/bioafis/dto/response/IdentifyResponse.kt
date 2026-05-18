package com.airb.bioafis.dto.response

import com.airb.bioafis.domain.enums.FingerPosition

data class IdentifyResponse(
    val candidates: List<CandidateResult>,
    val totalSearched: Int,
    val threshold: Double,
)

data class CandidateResult(
    val uid: String,
    val score: Double,
    val matchedPosition: FingerPosition,
)
