package com.airb.bioafis.matching

import com.airb.bioafis.domain.enums.FingerPosition
import com.airb.bioafis.repository.FingerprintRepository
import com.airb.bioafis.repository.PatientRepository
import com.machinezoo.sourceafis.FingerprintTemplate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TemplateCacheTest {
    private val fingerprintRepository: FingerprintRepository = mock()
    private val patientRepository: PatientRepository = mock()
    private val encryption: TemplateEncryption = mock()
    private val engine: SourceAfisEngine = mock()

    private lateinit var cache: TemplateCache

    @BeforeEach
    fun setup() {
        whenever(fingerprintRepository.findAll()).thenReturn(emptyList())
        whenever(patientRepository.findAll()).thenReturn(emptyList())

        cache = TemplateCache(
            fingerprintRepository = fingerprintRepository,
            patientRepository = patientRepository,
            encryption = encryption,
            engine = engine,
            reloadOnStartup = true,
        )
        cache.warmUp()
    }

    @Test
    fun `should return empty map when no templates for tenant`() {
        val result = cache.getCandidates(999L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `should store and retrieve templates`() {
        val mockTemplate: FingerprintTemplate = mock()
        val cachedTemplate = TemplateCache.CachedTemplate(
            fingerprintId = 1L,
            position = FingerPosition.RIGHT_INDEX,
            template = mockTemplate,
        )
        cache.put(1L, "UID-001", listOf(cachedTemplate))

        val result = cache.getCandidates(1L)
        assertEquals(1, result.size)
        assertEquals(1, result["UID-001"]?.size)
        assertEquals(FingerPosition.RIGHT_INDEX, result["UID-001"]?.first()?.position)
    }

    @Test
    fun `should remove templates for a patient`() {
        val mockTemplate: FingerprintTemplate = mock()
        cache.put(1L, "UID-001", listOf(TemplateCache.CachedTemplate(1L, FingerPosition.RIGHT_INDEX, mockTemplate)))
        cache.remove(1L, "UID-001")

        val result = cache.getCandidates(1L)
        assertNull(result["UID-001"])
    }

    @Test
    fun `should report total size correctly`() {
        val mockTemplate: FingerprintTemplate = mock()
        cache.put(1L, "UID-001", listOf(
            TemplateCache.CachedTemplate(1L, FingerPosition.RIGHT_INDEX, mockTemplate),
            TemplateCache.CachedTemplate(2L, FingerPosition.LEFT_INDEX, mockTemplate),
        ))
        cache.put(1L, "UID-002", listOf(
            TemplateCache.CachedTemplate(3L, FingerPosition.RIGHT_THUMB, mockTemplate),
        ))

        assertEquals(3, cache.totalSize())
    }
}
