package com.airb.bioafis.domain.model

import com.airb.bioafis.domain.enums.JobStatus
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "dedup_jobs")
class DeduplicationJob(
    @Id
    val id: String,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: JobStatus = JobStatus.PENDING,

    @Column(name = "scope_facility", length = 50)
    val scopeFacility: String? = null,

    @Column(name = "progress_pct", nullable = false)
    var progressPct: Int = 0,

    @Column(name = "result_count")
    var resultCount: Int? = null,

    @Column(name = "started_at")
    var startedAt: Instant? = null,

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
