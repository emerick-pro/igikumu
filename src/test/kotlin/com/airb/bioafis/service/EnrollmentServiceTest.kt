package com.airb.bioafis.service

import com.airb.bioafis.domain.enums.EnrollmentSource
import com.airb.bioafis.domain.enums.FingerPosition
import com.airb.bioafis.domain.model.Patient
import com.airb.bioafis.dto.request.EnrollRequest
import com.airb.bioafis.dto.request.FingerData
import com.airb.bioafis.dto.request.ImageFormat
import com.airb.bioafis.matching.ExtractionResult
import com.airb.bioafis.matching.SourceAfisEngine
import com.airb.bioafis.matching.TemplateCache
import com.airb.bioafis.matching.TemplateEncryption
import com.airb.bioafis.repository.FingerprintRepository
import com.airb.bioafis.repository.PatientRepository
import com.airb.bioafis.security.AuditLogger
import com.machinezoo.sourceafis.FingerprintTemplate
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Base64
import java.util.Optional

class EnrollmentServiceTest {
    private val patientRepository: PatientRepository = mock()
    private val fingerprintRepository: FingerprintRepository = mock()
    private val engine: SourceAfisEngine = mock()
    private val encryption: TemplateEncryption = mock()
    private val templateCache: TemplateCache = mock()
    private val auditLogger: AuditLogger = mock()

    private val service = EnrollmentService(
        patientRepository = patientRepository,
        fingerprintRepository = fingerprintRepository,
        engine = engine,
        encryption = encryption,
        templateCache = templateCache,
        auditLogger = auditLogger,
        minQualityScore = 50,
        defaultDpi = 500,
    )

    @Test
    fun `should return QualityFailed when quality score is below threshold`() {
        val mockTemplate: FingerprintTemplate = mock()
        val fakeImageBytes = ByteArray(100)
        val base64Image = Base64.getEncoder().encodeToString(fakeImageBytes)

        val extractionResult = ExtractionResult(
            template = mockTemplate,
            serialized = ByteArray(200),
            qualityScore = 30, // below 50 threshold
        )

        whenever(patientRepository.findAll()).thenReturn(emptyList())
        whenever(patientRepository.findByUidAndTenantId(any(), eq(1L))).thenReturn(Optional.empty())
        whenever(engine.extractTemplate(any(), any())).thenReturn(extractionResult)

        val request = EnrollRequest(
            uid = "TEST-001",
            fingers = listOf(
                FingerData(
                    position = FingerPosition.RIGHT_INDEX,
                    imageBase64 = base64Image,
                    imageFormat = ImageFormat.BMP,
                ),
            ),
            enrollmentSource = EnrollmentSource.AT_VISIT,
        )

        val result = service.enroll(1L, request, "127.0.0.1")
        assertInstanceOf(EnrollResult.QualityFailed::class.java, result)
    }

    @Test
    fun `should return InvalidImage when base64 is invalid`() {
        whenever(patientRepository.findAll()).thenReturn(emptyList())
        whenever(patientRepository.findByUidAndTenantId(any(), eq(1L))).thenReturn(Optional.empty())

        val request = EnrollRequest(
            uid = "TEST-002",
            fingers = listOf(
                FingerData(
                    position = FingerPosition.RIGHT_INDEX,
                    imageBase64 = "NOT_VALID_BASE64!!!",
                    imageFormat = ImageFormat.BMP,
                ),
            ),
            enrollmentSource = EnrollmentSource.AT_VISIT,
        )

        val result = service.enroll(1L, request, "127.0.0.1")
        assertInstanceOf(EnrollResult.InvalidImage::class.java, result)
    }

    @Test
    fun `should return PatientStatusResult NotFound when patient does not exist`() {
        whenever(patientRepository.findByUidAndTenantId("UNKNOWN", 1L)).thenReturn(Optional.empty())

        val result = service.getPatientStatus(1L, "UNKNOWN")
        assertInstanceOf(PatientStatusResult.NotFound::class.java, result)
    }

    @Test
    fun `should return PatientStatusResult Found when patient exists`() {
        val patient = Patient(id = 1L, uid = "P001", tenantId = 1L)
        whenever(patientRepository.findByUidAndTenantId("P001", 1L)).thenReturn(Optional.of(patient))
        whenever(fingerprintRepository.findAllByPatientId(1L)).thenReturn(emptyList())

        val result = service.getPatientStatus(1L, "P001")
        assertInstanceOf(PatientStatusResult.Found::class.java, result)
    }
}
