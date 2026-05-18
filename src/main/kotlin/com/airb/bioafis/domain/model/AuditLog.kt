package com.airb.bioafis.domain.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "audit_logs")
class AuditLog(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "tenant_id")
    val tenantId: Long? = null,

    @Column(nullable = false, length = 50)
    val operation: String,

    @Column(length = 100)
    val uid: String? = null,

    @Column(name = "ip_address", length = 45)
    val ipAddress: String? = null,

    @Column(name = "response_ms")
    val responseMs: Int? = null,

    @Column(name = "http_status")
    val httpStatus: Short? = null,

    @Column(name = "error_code", length = 50)
    val errorCode: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
