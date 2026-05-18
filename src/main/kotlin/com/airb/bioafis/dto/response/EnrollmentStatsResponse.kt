package com.airb.bioafis.dto.response

data class EnrollmentStatsResponse(
    val tenantId: Long,
    val facilityCode: String?,
    val totalPatients: Long,
    val enrolled: Long,
    val pending: Long,
    val qualityFailed: Long,
    val partial: Long,
)
