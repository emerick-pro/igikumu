package com.airb.bioafis.security

import com.airb.bioafis.domain.model.AuditLog
import com.airb.bioafis.repository.AuditLogRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class AuditLogger(
    private val auditLogRepository: AuditLogRepository,
) {
    private val logger = LoggerFactory.getLogger(AuditLogger::class.java)

    @Async
    fun log(
        tenantId: Long?,
        operation: String,
        uid: String? = null,
        ipAddress: String? = null,
        responseMs: Int? = null,
        httpStatus: Short? = null,
        errorCode: String? = null,
    ) {
        try {
            val auditLog = AuditLog(
                tenantId = tenantId,
                operation = operation,
                uid = uid,
                ipAddress = ipAddress,
                responseMs = responseMs,
                httpStatus = httpStatus,
                errorCode = errorCode,
            )
            auditLogRepository.save(auditLog)
        } catch (e: Exception) {
            logger.error("Failed to write audit log for operation={}: {}", operation, e.message)
        }
    }
}
