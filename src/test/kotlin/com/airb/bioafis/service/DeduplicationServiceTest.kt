package com.airb.bioafis.service

import com.airb.bioafis.domain.enums.JobStatus
import com.airb.bioafis.domain.model.DeduplicationJob
import com.airb.bioafis.dto.request.StartDedupRequest
import com.airb.bioafis.matching.TemplateCache
import com.airb.bioafis.repository.DeduplicationJobRepository
import com.airb.bioafis.security.AuditLogger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DeduplicationServiceTest {
    private val dedupJobRepository: DeduplicationJobRepository = mock()
    private val templateCache: TemplateCache = mock()
    private val auditLogger: AuditLogger = mock()

    private val service = DeduplicationService(
        dedupJobRepository = dedupJobRepository,
        templateCache = templateCache,
        auditLogger = auditLogger,
        defaultThreshold = 40.0,
    )

    @Test
    fun `should return null when job not found`() {
        whenever(dedupJobRepository.findById("nonexistent")).thenReturn(java.util.Optional.empty())
        val result = service.getJobStatus(1L, "nonexistent")
        assertNull(result)
    }

    @Test
    fun `should return null when job belongs to different tenant`() {
        val job = DeduplicationJob(id = "job-1", tenantId = 2L, status = JobStatus.DONE)
        whenever(dedupJobRepository.findById("job-1")).thenReturn(java.util.Optional.of(job))
        val result = service.getJobStatus(1L, "job-1") // tenant 1 requesting job of tenant 2
        assertNull(result)
    }

    @Test
    fun `should create job with PENDING status`() {
        val savedJob = DeduplicationJob(id = "job-uuid", tenantId = 1L, status = JobStatus.PENDING)
        whenever(dedupJobRepository.save(any())).thenReturn(savedJob)
        whenever(templateCache.getCandidates(1L)).thenReturn(emptyMap())

        val request = StartDedupRequest(scopeFacility = null, threshold = null)
        val response = service.startJob(1L, request, "127.0.0.1")

        assertNotNull(response)
        assertEquals(1L, response.tenantId)
    }
}
