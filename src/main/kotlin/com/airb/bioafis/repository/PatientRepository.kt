package com.airb.bioafis.repository

import com.airb.bioafis.domain.enums.EnrollmentStatus
import com.airb.bioafis.domain.model.Patient
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface PatientRepository : JpaRepository<Patient, Long> {
    fun findByUidAndTenantId(uid: String, tenantId: Long): Optional<Patient>
    fun findAllByTenantId(tenantId: Long): List<Patient>
    fun findAllByTenantIdAndFacilityCode(tenantId: Long, facilityCode: String): List<Patient>
    fun findAllByTenantIdAndEnrollmentStatus(
        tenantId: Long,
        enrollmentStatus: EnrollmentStatus,
        pageable: Pageable,
    ): Page<Patient>
    fun findAllByTenantIdAndFacilityCodeAndEnrollmentStatus(
        tenantId: Long,
        facilityCode: String,
        enrollmentStatus: EnrollmentStatus,
        pageable: Pageable,
    ): Page<Patient>
    fun countByTenantIdAndEnrollmentStatus(tenantId: Long, enrollmentStatus: EnrollmentStatus): Long
    fun countByTenantId(tenantId: Long): Long
}
