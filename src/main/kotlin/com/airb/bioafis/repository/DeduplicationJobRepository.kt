package com.airb.bioafis.repository

import com.airb.bioafis.domain.model.DeduplicationJob
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DeduplicationJobRepository : JpaRepository<DeduplicationJob, String> {
    fun findAllByTenantId(tenantId: Long): List<DeduplicationJob>
}
