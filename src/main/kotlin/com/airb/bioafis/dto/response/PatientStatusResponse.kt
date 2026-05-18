package com.airb.bioafis.dto.response

import com.airb.bioafis.domain.enums.EnrollmentStatus
import com.airb.bioafis.domain.enums.FingerPosition
import java.time.Instant

data class PatientStatusResponse(
    val uid: String,
    val enrollmentStatus: EnrollmentStatus,
    val facilityCode: String?,
    val enrolledFingers: List<FingerPosition>,
    val enrolledAt: Instant?,
    val updatedAt: Instant,
)
