package com.airb.bioafis.service

import com.airb.bioafis.domain.enums.EnrollmentStatus
import com.airb.bioafis.dto.response.EnrollmentStatsResponse
import com.airb.bioafis.dto.response.PatientStatusResponse
import com.airb.bioafis.repository.FingerprintRepository
import com.airb.bioafis.repository.PatientRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

@Service
class EnrollmentStatsService(
    private val patientRepository: PatientRepository,
    private val fingerprintRepository: FingerprintRepository,
) {
    fun getStats(tenantId: Long, facilityCode: String?): EnrollmentStatsResponse {
        val total = if (facilityCode != null) {
            patientRepository.findAllByTenantIdAndFacilityCode(tenantId, facilityCode).size.toLong()
        } else {
            patientRepository.countByTenantId(tenantId)
        }

        val enrolled = countByStatus(tenantId, facilityCode, EnrollmentStatus.ENROLLED)
        val pending = countByStatus(tenantId, facilityCode, EnrollmentStatus.PENDING)
        val qualityFailed = countByStatus(tenantId, facilityCode, EnrollmentStatus.QUALITY_FAILED)
        val partial = countByStatus(tenantId, facilityCode, EnrollmentStatus.PARTIAL)

        return EnrollmentStatsResponse(
            tenantId = tenantId,
            facilityCode = facilityCode,
            totalPatients = total,
            enrolled = enrolled,
            pending = pending,
            qualityFailed = qualityFailed,
            partial = partial,
        )
    }

    fun getPendingPatients(
        tenantId: Long,
        facilityCode: String?,
        page: Int,
        pageSize: Int,
    ): List<PatientStatusResponse> {
        val pageable = PageRequest.of(page, pageSize)
        val patients = if (facilityCode != null) {
            patientRepository.findAllByTenantIdAndFacilityCodeAndEnrollmentStatus(
                tenantId,
                facilityCode,
                EnrollmentStatus.PENDING,
                pageable,
            ).content
        } else {
            patientRepository.findAllByTenantIdAndEnrollmentStatus(
                tenantId,
                EnrollmentStatus.PENDING,
                pageable,
            ).content
        }

        return patients.map { patient ->
            val fingers = fingerprintRepository.findAllByPatientId(patient.id)
            PatientStatusResponse(
                uid = patient.uid,
                enrollmentStatus = patient.enrollmentStatus,
                facilityCode = patient.facilityCode,
                enrolledFingers = fingers.map { it.fingerPosition },
                enrolledAt = patient.enrolledAt,
                updatedAt = patient.updatedAt,
            )
        }
    }

    private fun countByStatus(tenantId: Long, facilityCode: String?, status: EnrollmentStatus): Long {
        return if (facilityCode != null) {
            patientRepository.findAllByTenantIdAndFacilityCode(tenantId, facilityCode)
                .count { it.enrollmentStatus == status }.toLong()
        } else {
            patientRepository.countByTenantIdAndEnrollmentStatus(tenantId, status)
        }
    }
}
