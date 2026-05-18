package com.airb.bioafis.domain.model

import com.airb.bioafis.domain.enums.EnrollmentSource
import com.airb.bioafis.domain.enums.FingerPosition
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "fingerprints")
class Fingerprint(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "patient_id", nullable = false)
    val patientId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "finger_position", nullable = false)
    val fingerPosition: FingerPosition,

    @Lob
    @Column(name = "template_encrypted", nullable = false)
    val templateEncrypted: ByteArray,

    @Column(name = "quality_score", nullable = false)
    val qualityScore: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_source", nullable = false)
    val enrollmentSource: EnrollmentSource,

    @Column(name = "enrolled_by", length = 100)
    val enrolledBy: String? = null,

    @Column(name = "enrolled_at", nullable = false)
    val enrolledAt: Instant = Instant.now(),
)
