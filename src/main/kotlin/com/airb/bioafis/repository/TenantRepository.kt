package com.airb.bioafis.repository

import com.airb.bioafis.domain.model.Tenant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TenantRepository : JpaRepository<Tenant, Long> {
    fun findAllByIsActiveTrue(): List<Tenant>
    fun findByName(name: String): Tenant?
}
