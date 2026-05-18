package com.airb.bioafis.matching

import com.machinezoo.sourceafis.FingerprintImage
import com.machinezoo.sourceafis.FingerprintImageOptions
import com.machinezoo.sourceafis.FingerprintMatcher
import com.machinezoo.sourceafis.FingerprintTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

data class ExtractionResult(
    val template: FingerprintTemplate,
    val serialized: ByteArray,
    val qualityScore: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExtractionResult) return false
        return serialized.contentEquals(other.serialized) && qualityScore == other.qualityScore
    }

    override fun hashCode(): Int {
        var result = serialized.contentHashCode()
        result = 31 * result + qualityScore
        return result
    }
}

@Component
class SourceAfisEngine(
    @Value("\${bioafis.matching.default-dpi:500}") private val defaultDpi: Int,
) {
    fun extractTemplate(imageBytes: ByteArray, dpi: Int = defaultDpi): ExtractionResult {
        val options = FingerprintImageOptions().dpi(dpi.toDouble())
        val image = FingerprintImage(imageBytes, options)
        val template = FingerprintTemplate(image)
        val serialized = serialize(template)
        val qualityScore = calculateQuality(template)
        return ExtractionResult(template, serialized, qualityScore)
    }

    fun match(probe: FingerprintTemplate, candidate: FingerprintTemplate): Double {
        return FingerprintMatcher(probe).match(candidate)
    }

    fun deserialize(bytes: ByteArray): FingerprintTemplate {
        return ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as FingerprintTemplate }
    }

    fun serialize(template: FingerprintTemplate): ByteArray {
        val bos = ByteArrayOutputStream()
        ObjectOutputStream(bos).use { it.writeObject(template) }
        return bos.toByteArray()
    }

    private fun calculateQuality(template: FingerprintTemplate): Int {
        // SourceAFIS does not expose a direct quality score; use minutia count as proxy
        // A typical high-quality fingerprint has 30-50 minutiae
        // We clamp to 0-100 range based on a reasonable expected range
        val minutiaeCount = try {
            val field = template.javaClass.getDeclaredField("minutiae")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (field.get(template) as? Array<*>)?.size ?: 0
        } catch (_: Exception) {
            // If reflection fails, fall back to serialized size as a proxy
            // Average template byte size ~1KB, high quality ~2KB+
            val bytes = serialize(template)
            bytes.size / 20
        }
        return (minutiaeCount * 2).coerceIn(0, 100)
    }
}
