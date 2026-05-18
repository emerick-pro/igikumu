package com.airb.bioafis.repository

import com.airb.bioafis.domain.model.AuditLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AuditLogRepository : JpaRepository<AuditLog, Long> {
    fun findAllByTenantId(tenantId: Long): List<AuditLog>
}
