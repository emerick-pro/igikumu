package com.airb.bioafis.service

import com.airb.bioafis.domain.model.Tenant
import com.airb.bioafis.dto.request.CreateTenantRequest
import com.airb.bioafis.dto.response.CreateTenantResponse
import com.airb.bioafis.dto.response.TenantResponse
import com.airb.bioafis.repository.TenantRepository
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

@Service
class TenantService(
    private val tenantRepository: TenantRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    private val logger = LoggerFactory.getLogger(TenantService::class.java)
    private val random = SecureRandom()

    fun listTenants(): List<TenantResponse> {
        return tenantRepository.findAll().map { it.toResponse() }
    }

    @Transactional
    fun createTenant(request: CreateTenantRequest): CreateTenantResponse {
        val rawKey = generateApiKey()
        val keyHash = passwordEncoder.encode(rawKey)

        val tenant = Tenant(
            name = request.name,
            apiKeyHash = keyHash,
            isAdmin = request.isAdmin,
            configJson = request.configJson,
        )
        val saved = tenantRepository.save(tenant)

        logger.info("Tenant created: id={} name={} isAdmin={}", saved.id, saved.name, saved.isAdmin)

        return CreateTenantResponse(
            id = saved.id,
            name = saved.name,
            isAdmin = saved.isAdmin,
            apiKey = rawKey,
            createdAt = saved.createdAt,
        )
    }

    @Transactional
    fun deleteTenant(id: Long) {
        val tenant = tenantRepository.findById(id).orElseThrow {
            NoSuchElementException("Tenant not found: $id")
        }
        tenant.isActive = false
        tenant.updatedAt = Instant.now()
        tenantRepository.save(tenant)
        logger.info("Tenant deactivated: id={}", id)
    }

    private fun generateApiKey(): String {
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun Tenant.toResponse() = TenantResponse(
        id = id,
        name = name,
        isAdmin = isAdmin,
        isActive = isActive,
        createdAt = createdAt,
    )
}
