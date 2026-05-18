package com.airb.bioafis.api.v1

import com.airb.bioafis.dto.request.EnrollRequest
import com.airb.bioafis.dto.request.IdentifyRequest
import com.airb.bioafis.dto.request.VerifyRequest
import com.airb.bioafis.dto.response.EnrollResponse
import com.airb.bioafis.dto.response.ErrorResponse
import com.airb.bioafis.dto.response.IdentifyResponse
import com.airb.bioafis.dto.response.PatientStatusResponse
import com.airb.bioafis.dto.response.VerifyResponse
import com.airb.bioafis.security.TenantContext
import com.airb.bioafis.service.EnrollResult
import com.airb.bioafis.service.EnrollmentService
import com.airb.bioafis.service.IdentificationService
import com.airb.bioafis.service.IdentifyResult
import com.airb.bioafis.service.PatientStatusResult
import com.airb.bioafis.service.VerificationService
import com.airb.bioafis.service.VerifyResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/patients")
@Tag(name = "Patients", description = "Patient enrollment, verification and identification")
class PatientController(
    private val enrollmentService: EnrollmentService,
    private val verificationService: VerificationService,
    private val identificationService: IdentificationService,
) {
    @Operation(summary = "Enroll a new patient")
    @PostMapping("/enroll")
    fun enroll(
        @RequestBody @Valid request: EnrollRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<*> {
        val tenant = TenantContext.require()
        return when (val result = enrollmentService.enroll(tenant.id, request, servletRequest.remoteAddr)) {
            is EnrollResult.Success -> ResponseEntity.ok(result.response)
            is EnrollResult.QualityFailed -> ResponseEntity.unprocessableEntity().body(
                ErrorResponse(
                    code = "QUALITY_TOO_LOW",
                    message = "Fingerprint quality too low",
                    details = mapOf("finger" to result.finger, "score" to result.score, "minScore" to result.minScore),
                ),
            )
            is EnrollResult.InvalidImage -> ResponseEntity.badRequest().body(
                ErrorResponse(
                    code = "INVALID_IMAGE",
                    message = "Cannot decode fingerprint image",
                    details = mapOf("finger" to result.finger, "reason" to result.reason),
                ),
            )
            is EnrollResult.UidConflict -> ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse(
                    code = "UID_CONFLICT",
                    message = "UID already exists",
                    details = mapOf("uid" to result.uid),
                ),
            )
        }
    }

    @Operation(summary = "Re-enroll an existing patient")
    @PutMapping("/{uid}/enroll")
    fun reEnroll(
        @PathVariable uid: String,
        @RequestBody @Valid request: EnrollRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<*> {
        val tenant = TenantContext.require()
        val effectiveRequest = request.copy(uid = uid)
        return when (val result = enrollmentService.enroll(tenant.id, effectiveRequest, servletRequest.remoteAddr)) {
            is EnrollResult.Success -> ResponseEntity.ok(result.response)
            is EnrollResult.QualityFailed -> ResponseEntity.unprocessableEntity().body(
                ErrorResponse(
                    code = "QUALITY_TOO_LOW",
                    message = "Fingerprint quality too low",
                    details = mapOf("finger" to result.finger, "score" to result.score, "minScore" to result.minScore),
                ),
            )
            is EnrollResult.InvalidImage -> ResponseEntity.badRequest().body(
                ErrorResponse(
                    code = "INVALID_IMAGE",
                    message = "Cannot decode fingerprint image",
                    details = mapOf("finger" to result.finger, "reason" to result.reason),
                ),
            )
            is EnrollResult.UidConflict -> ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse(
                    code = "UID_CONFLICT",
                    message = "UID exists under different tenant",
                    details = mapOf("uid" to result.uid),
                ),
            )
        }
    }

    @Operation(summary = "1:1 verify patient fingerprint")
    @PostMapping("/verify")
    fun verify(
        @RequestBody @Valid request: VerifyRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<*> {
        val tenant = TenantContext.require()
        return when (val result = verificationService.verify(tenant.id, request, servletRequest.remoteAddr)) {
            is VerifyResult.Success -> ResponseEntity.ok(result.response)
            is VerifyResult.PatientNotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse(code = "PATIENT_NOT_FOUND", message = "Patient not found: ${request.uid}"),
            )
            is VerifyResult.NoTemplatesFound -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse(code = "PATIENT_NOT_FOUND", message = "No fingerprints found for patient: ${request.uid}"),
            )
            is VerifyResult.InvalidImage -> ResponseEntity.badRequest().body(
                ErrorResponse(code = "INVALID_IMAGE", message = result.reason),
            )
        }
    }

    @Operation(summary = "1:N identify patient by fingerprint")
    @PostMapping("/identify")
    fun identify(
        @RequestBody @Valid request: IdentifyRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<*> {
        val tenant = TenantContext.require()
        return when (val result = identificationService.identify(tenant.id, request, servletRequest.remoteAddr)) {
            is IdentifyResult.Success -> ResponseEntity.ok(result.response)
            is IdentifyResult.InvalidImage -> ResponseEntity.badRequest().body(
                ErrorResponse(code = "INVALID_IMAGE", message = result.reason),
            )
        }
    }

    @Operation(summary = "Get patient enrollment status")
    @GetMapping("/{uid}")
    fun getStatus(@PathVariable uid: String): ResponseEntity<*> {
        val tenant = TenantContext.require()
        return when (val result = enrollmentService.getPatientStatus(tenant.id, uid)) {
            is PatientStatusResult.NotFound -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse(code = "PATIENT_NOT_FOUND", message = "Patient not found: $uid"),
            )
            is PatientStatusResult.Found -> ResponseEntity.ok(
                PatientStatusResponse(
                    uid = result.patient.uid,
                    enrollmentStatus = result.patient.enrollmentStatus,
                    facilityCode = result.patient.facilityCode,
                    enrolledFingers = result.enrolledPositions,
                    enrolledAt = result.patient.enrolledAt,
                    updatedAt = result.patient.updatedAt,
                ),
            )
        }
    }

    @Operation(summary = "Delete patient and all fingerprints")
    @DeleteMapping("/{uid}")
    fun deletePatient(@PathVariable uid: String): ResponseEntity<*> {
        val tenant = TenantContext.require()
        val deleted = enrollmentService.deletePatient(tenant.id, uid)
        return if (deleted) {
            ResponseEntity.noContent().build<Void>()
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse(code = "PATIENT_NOT_FOUND", message = "Patient not found: $uid"),
            )
        }
    }
}
