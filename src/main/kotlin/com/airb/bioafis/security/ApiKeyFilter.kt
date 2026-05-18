package com.airb.bioafis.security

import com.airb.bioafis.repository.TenantRepository
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant

@Component
class ApiKeyFilter(
    private val tenantRepository: TenantRepository,
    private val passwordEncoder: PasswordEncoder,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    companion object {
        private val log = LoggerFactory.getLogger(ApiKeyFilter::class.java)
    }

    private val publicPaths = listOf(
        "/actuator/health",
        "/v3/api-docs",
        "/swagger-ui",
        "/swagger-ui.html",
    )

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val path = request.requestURI

        if (isPublicPath(path)) {
            filterChain.doFilter(request, response)
            return
        }

        val authHeader = request.getHeader("Authorization")
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, "Missing or invalid Authorization header")
            return
        }

        val rawKey = authHeader.removePrefix("Bearer ").trim()

        val activeTenants = tenantRepository.findAllByIsActiveTrue()
        val matchedTenant = activeTenants.find { tenant ->
            try {
                passwordEncoder.matches(rawKey, tenant.apiKeyHash)
            } catch (_: Exception) {
                false
            }
        }

        if (matchedTenant == null) {
            log.warn("Authentication failed for request: {} {}", request.method, path)
            writeUnauthorized(response, "Invalid API key")
            return
        }

        if (path.startsWith("/admin/") && !matchedTenant.isAdmin) {
            log.warn("Forbidden admin access attempt: tenantId={} path={}", matchedTenant.id, path)
            writeForbidden(response, "Admin access required")
            return
        }

        TenantContext.set(matchedTenant)
        try {
            filterChain.doFilter(request, response)
        } finally {
            TenantContext.clear()
        }
    }

    private fun isPublicPath(path: String): Boolean {
        return publicPaths.any { path.startsWith(it) }
    }

    private fun writeUnauthorized(response: HttpServletResponse, message: String) {
        writeError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message)
    }

    private fun writeForbidden(response: HttpServletResponse, message: String) {
        writeError(response, HttpStatus.FORBIDDEN, "FORBIDDEN", message)
    }

    private fun writeError(
        response: HttpServletResponse,
        status: HttpStatus,
        code: String,
        message: String,
    ) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val body = mapOf(
            "code" to code,
            "message" to message,
            "timestamp" to Instant.now().toString(),
        )
        response.writer.write(objectMapper.writeValueAsString(body))
    }
}
