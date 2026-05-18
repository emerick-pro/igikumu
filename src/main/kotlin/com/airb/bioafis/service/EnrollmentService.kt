package com.airb.bioafis.service

import com.airb.bioafis.domain.enums.EnrollmentStatus
import com.airb.bioafis.domain.model.Fingerprint
import com.airb.bioafis.domain.model.Patient
import com.airb.bioafis.dto.request.EnrollRequest
import com.airb.bioafis.dto.response.EnrollResponse
import com.airb.bioafis.matching.SourceAfisEngine
import com.airb.bioafis.matching.TemplateCache
import com.airb.bioafis.matching.TemplateEncryption
import com.airb.bioafis.repository.FingerprintRepository
import com.airb.bioafis.repository.PatientRepository
import com.airb.bioafis.security.AuditLogger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Base64
import java.util.UUID

sealed class EnrollResult {
    data class Success(val response: EnrollResponse) : EnrollResult()
    data class QualityFailed(val finger: String, val score: Int, val minScore: Int) : EnrollResult()
    data class InvalidImage(val finger: String, val reason: String) : EnrollResult()
    data class UidConflict(val uid: String) : EnrollResult()
}

@Service
class EnrollmentService(
    private val patientRepository: PatientRepository,
    private val fingerprintRepository: FingerprintRepository,
    private val engine: SourceAfisEngine,
    private val encryption: TemplateEncryption,
    private val templateCache: TemplateCache,
    private val auditLogger: AuditLogger,
    @Value("\${bioafis.matching.min-quality-score:50}") private val minQualityScore: Int,
    @Value("\${bioafis.matching.default-dpi:500}") private val defaultDpi: Int,
) {
    private val logger = LoggerFactory.getLogger(EnrollmentService::class.java)

    @Transactional
    fun enroll(tenantId: Long, request: EnrollRequest, ipAddress: String?): EnrollResult {
        val startMs = System.currentTimeMillis()
        val uid = request.uid ?: UUID.randomUUID().toString()

        // Check for UID conflict across tenants
        val existingOtherTenant = patientRepository.findAll()
            .find { it.uid == uid && it.tenantId != tenantId }
        if (existingOtherTenant != null) {
            return EnrollResult.UidConflict(uid)
        }

        val existingPatient = patientRepository.findByUidAndTenantId(uid, tenantId).orElse(null)
        if (existingPatient != null && existingPatient.enrollmentStatus == EnrollmentStatus.ENROLLED) {
            return EnrollResult.UidConflict(uid)
        }

        val qualityScores = mutableMapOf<String, Int>()
        val cachedTemplates = mutableListOf<TemplateCache.CachedTemplate>()
        val fingerprints = mutableListOf<Fingerprint>()

        for (fingerData in request.fingers) {
            val imageBytes = try {
                Base64.getDecoder().decode(fingerData.imageBase64)
            } catch (e: IllegalArgumentException) {
                return EnrollResult.InvalidImage(fingerData.position.name, "Invalid Base64 encoding")
            }

            val extraction = try {
                engine.extractTemplate(imageBytes, defaultDpi)
            } catch (e: Exception) {
                logger.warn("Template extraction failed for position={}: {}", fingerData.position, e.message)
                return EnrollResult.InvalidImage(fingerData.position.name, e.message ?: "Extraction failed")
            }

            if (extraction.qualityScore < minQualityScore) {
                logger.warn(
                    "Quality too low: position={} score={} min={}",
                    fingerData.position,
                    extraction.qualityScore,
                    minQualityScore,
                )
                return EnrollResult.QualityFailed(fingerData.position.name, extraction.qualityScore, minQualityScore)
            }

            val encrypted = encryption.encrypt(extraction.serialized)
            qualityScores[fingerData.position.name] = extraction.qualityScore

            fingerprints.add(
                Fingerprint(
                    patientId = 0, // set after patient save
                    fingerPosition = fingerData.position,
                    templateEncrypted = encrypted,
                    qualityScore = extraction.qualityScore,
                    enrollmentSource = request.enrollmentSource,
                    enrolledBy = request.enrolledBy,
                ),
            )

            cachedTemplates.add(
                TemplateCache.CachedTemplate(
                    fingerprintId = 0,
                    position = fingerData.position,
                    template = extraction.template,
                ),
            )
        }

        val now = Instant.now()
        val patient = existingPatient ?: patientRepository.save(
            Patient(
                uid = uid,
                tenantId = tenantId,
                facilityCode = request.facilityCode,
                enrollmentStatus = EnrollmentStatus.ENROLLED,
                enrolledAt = now,
                updatedAt = now,
            ),
        )

        if (existingPatient != null) {
            existingPatient.enrollmentStatus = EnrollmentStatus.ENROLLED
            existingPatient.enrolledAt = now
            existingPatient.updatedAt = now
            patientRepository.save(existingPatient)
            fingerprintRepository.deleteAllByPatientId(patient.id)
        }

        val savedFingerprints = fingerprints.map { fp ->
            fingerprintRepository.save(
                Fingerprint(
                    patientId = patient.id,
                    fingerPosition = fp.fingerPosition,
                    templateEncrypted = fp.templateEncrypted,
                    qualityScore = fp.qualityScore,
                    enrollmentSource = fp.enrollmentSource,
                    enrolledBy = fp.enrolledBy,
                ),
            )
        }

        val updatedCached = savedFingerprints.zip(cachedTemplates).map { (saved, cached) ->
            cached.copy(fingerprintId = saved.id)
        }
        templateCache.put(tenantId, uid, updatedCached)

        val responseMs = (System.currentTimeMillis() - startMs).toInt()
        logger.info(
            "Enrollment complete: uid={} tenant={} fingers={} responseMs={}",
            uid,
            tenantId,
            savedFingerprints.size,
            responseMs,
        )

        auditLogger.log(
            tenantId = tenantId,
            operation = "ENROLL",
            uid = uid,
            ipAddress = ipAddress,
            responseMs = responseMs,
            httpStatus = 200,
        )

        return EnrollResult.Success(
            EnrollResponse(
                uid = uid,
                enrollmentStatus = EnrollmentStatus.ENROLLED,
                enrolledFingers = savedFingerprints.map { it.fingerPosition },
                enrolledAt = now,
                qualityScores = qualityScores,
            ),
        )
    }

    fun getPatientStatus(tenantId: Long, uid: String): PatientStatusResult {
        val patient = patientRepository.findByUidAndTenantId(uid, tenantId).orElse(null)
            ?: return PatientStatusResult.NotFound

        val fingerprints = fingerprintRepository.findAllByPatientId(patient.id)
        return PatientStatusResult.Found(patient, fingerprints.map { it.fingerPosition })
    }

    @Transactional
    fun deletePatient(tenantId: Long, uid: String): Boolean {
        val patient = patientRepository.findByUidAndTenantId(uid, tenantId).orElse(null)
            ?: return false

        fingerprintRepository.deleteAllByPatientId(patient.id)
        patientRepository.delete(patient)
        templateCache.remove(tenantId, uid)

        logger.info("Patient deleted: uid={} tenant={}", uid, tenantId)
        return true
    }
}

sealed class PatientStatusResult {
    object NotFound : PatientStatusResult()
    data class Found(
        val patient: Patient,
        val enrolledPositions: List<com.airb.bioafis.domain.enums.FingerPosition>,
    ) : PatientStatusResult()
}
