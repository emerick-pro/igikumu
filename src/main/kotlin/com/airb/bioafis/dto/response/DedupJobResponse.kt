package com.airb.bioafis.dto.response

import com.airb.bioafis.domain.enums.JobStatus
import java.time.Instant

data class DedupJobResponse(
    val id: String,
    val tenantId: Long,
    val status: JobStatus,
    val scopeFacility: String?,
    val progressPct: Int,
    val resultCount: Int?,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val createdAt: Instant,
)

data class DedupResultEntry(
    val uid1: String,
    val uid2: String,
    val score: Double,
    val matchedPosition1: String,
    val matchedPosition2: String,
)

data class DedupResultsPage(
    val jobId: String,
    val status: JobStatus,
    val results: List<DedupResultEntry>,
    val page: Int,
    val pageSize: Int,
    val totalResults: Int,
)
