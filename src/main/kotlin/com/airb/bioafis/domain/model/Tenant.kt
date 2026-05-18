package com.airb.bioafis.domain.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "tenants")
class Tenant(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 100)
    val name: String,

    @Column(name = "api_key_hash", nullable = false, unique = true, length = 255)
    val apiKeyHash: String,

    @Column(name = "is_admin", nullable = false)
    val isAdmin: Boolean = false,

    @Column(name = "config_json", columnDefinition = "JSON")
    val configJson: String? = null,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
