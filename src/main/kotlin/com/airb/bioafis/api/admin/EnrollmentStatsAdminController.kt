package com.airb.bioafis.api.admin

import com.airb.bioafis.dto.response.EnrollmentStatsResponse
import com.airb.bioafis.dto.response.PatientStatusResponse
import com.airb.bioafis.service.EnrollmentStatsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/enrollment")
@Tag(name = "Admin - Enrollment Stats", description = "Enrollment statistics and pending lists (admin only)")
class EnrollmentStatsAdminController(
    private val enrollmentStatsService: EnrollmentStatsService,
) {
    @Operation(summary = "Get enrollment statistics")
    @GetMapping("/stats")
    fun getStats(
        @RequestParam tenantId: Long,
        @RequestParam(required = false) facility: String?,
    ): ResponseEntity<EnrollmentStatsResponse> {
        val stats = enrollmentStatsService.getStats(tenantId, facility)
        return ResponseEntity.ok(stats)
    }

    @Operation(summary = "Get list of pending patients")
    @GetMapping("/pending")
    fun getPendingPatients(
        @RequestParam tenantId: Long,
        @RequestParam(required = false) facility: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") pageSize: Int,
    ): ResponseEntity<List<PatientStatusResponse>> {
        val patients = enrollmentStatsService.getPendingPatients(tenantId, facility, page, pageSize)
        return ResponseEntity.ok(patients)
    }
}
