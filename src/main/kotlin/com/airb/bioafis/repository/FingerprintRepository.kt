package com.airb.bioafis.repository

import com.airb.bioafis.domain.model.Fingerprint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface FingerprintRepository : JpaRepository<Fingerprint, Long> {
    fun findAllByPatientId(patientId: Long): List<Fingerprint>

    @Query("SELECT f FROM Fingerprint f JOIN Patient p ON f.patientId = p.id WHERE p.tenantId = :tenantId")
    fun findAllByTenantId(tenantId: Long): List<Fingerprint>

    fun deleteAllByPatientId(patientId: Long)
}
