package com.airb.bioafis.domain.model

import com.airb.bioafis.domain.enums.EnrollmentStatus
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "patients")
class Patient(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 100)
    val uid: String,

    @Column(name = "tenant_id", nullable = false)
    val tenantId: Long,

    @Column(name = "facility_code", length = 50)
    val facilityCode: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_status", nullable = false)
    var enrollmentStatus: EnrollmentStatus = EnrollmentStatus.PENDING,

    @Column(name = "enrolled_at")
    var enrolledAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
