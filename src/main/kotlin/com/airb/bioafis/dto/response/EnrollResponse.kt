package com.airb.bioafis.dto.response

import com.airb.bioafis.domain.enums.EnrollmentStatus
import com.airb.bioafis.domain.enums.FingerPosition
import java.time.Instant

data class EnrollResponse(
    val uid: String,
    val enrollmentStatus: EnrollmentStatus,
    val enrolledFingers: List<FingerPosition>,
    val enrolledAt: Instant,
    val qualityScores: Map<String, Int>,
)
