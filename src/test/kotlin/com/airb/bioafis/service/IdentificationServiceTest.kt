package com.airb.bioafis.service

import com.airb.bioafis.domain.enums.FingerPosition
import com.airb.bioafis.dto.request.IdentifyRequest
import com.airb.bioafis.dto.request.ImageFormat
import com.airb.bioafis.matching.ExtractionResult
import com.airb.bioafis.matching.SourceAfisEngine
import com.airb.bioafis.matching.TemplateCache
import com.airb.bioafis.security.AuditLogger
import com.machinezoo.sourceafis.FingerprintTemplate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Base64

class IdentificationServiceTest {
    private val engine: SourceAfisEngine = mock()
    private val templateCache: TemplateCache = mock()
    private val auditLogger: AuditLogger = mock()

    private val service = IdentificationService(
        engine = engine,
        templateCache = templateCache,
        auditLogger = auditLogger,
        defaultThreshold = 40.0,
        defaultDpi = 500,
    )

    @Test
    fun `should return InvalidImage when base64 is invalid`() {
        val request = IdentifyRequest(
            imageBase64 = "INVALID!!!!",
            imageFormat = ImageFormat.BMP,
            maxResults = 10,
        )
        val result = service.identify(1L, request, "127.0.0.1")
        assertInstanceOf(IdentifyResult.InvalidImage::class.java, result)
    }

    @Test
    fun `should return empty candidates when no templates in cache`() {
        val fakeImage = Base64.getEncoder().encodeToString(ByteArray(100))
        val mockTemplate: FingerprintTemplate = mock()
        val extractionResult = ExtractionResult(mockTemplate, ByteArray(200), 80)

        whenever(engine.extractTemplate(any(), any())).thenReturn(extractionResult)
        whenever(templateCache.getCandidates(1L)).thenReturn(emptyMap())

        val request = IdentifyRequest(imageBase64 = fakeImage, maxResults = 10)
        val result = service.identify(1L, request, "127.0.0.1")

        assertInstanceOf(IdentifyResult.Success::class.java, result)
        assertEquals(0, (result as IdentifyResult.Success).response.candidates.size)
    }
}
