package com.airb.bioafis.api.v1

import com.airb.bioafis.dto.request.StartDedupRequest
import com.airb.bioafis.dto.response.DedupJobResponse
import com.airb.bioafis.dto.response.DedupResultsPage
import com.airb.bioafis.dto.response.ErrorResponse
import com.airb.bioafis.security.TenantContext
import com.airb.bioafis.service.DeduplicationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/dedup")
@Tag(name = "Deduplication", description = "Background deduplication jobs")
class DeduplicationController(
    private val deduplicationService: DeduplicationService,
) {
    @Operation(summary = "Start a deduplication job")
    @PostMapping("/jobs")
    fun startJob(
        @RequestBody @Valid request: StartDedupRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<DedupJobResponse> {
        val tenant = TenantContext.require()
        val job = deduplicationService.startJob(tenant.id, request, servletRequest.remoteAddr)
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job)
    }

    @Operation(summary = "Get deduplication job status")
    @GetMapping("/jobs/{jobId}")
    fun getJobStatus(@PathVariable jobId: String): ResponseEntity<*> {
        val tenant = TenantContext.require()
        val job = deduplicationService.getJobStatus(tenant.id, jobId)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse(code = "JOB_NOT_FOUND", message = "Deduplication job not found: $jobId"),
            )
        return ResponseEntity.ok(job)
    }

    @Operation(summary = "Get paginated deduplication results")
    @GetMapping("/jobs/{jobId}/results")
    fun getJobResults(
        @PathVariable jobId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") pageSize: Int,
    ): ResponseEntity<*> {
        val tenant = TenantContext.require()
        val results = deduplicationService.getJobResults(tenant.id, jobId, page, pageSize)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse(code = "JOB_NOT_FOUND", message = "Deduplication job not found: $jobId"),
            )
        return ResponseEntity.ok(results)
    }
}
