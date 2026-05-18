package com.airb.bioafis.service

import com.airb.bioafis.dto.request.VerifyRequest
import com.airb.bioafis.dto.response.VerifyResponse
import com.airb.bioafis.matching.SourceAfisEngine
import com.airb.bioafis.matching.TemplateCache
import com.airb.bioafis.security.AuditLogger
import com.machinezoo.sourceafis.FingerprintMatcher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Base64

sealed class VerifyResult {
    data class Success(val response: VerifyResponse) : VerifyResult()
    object PatientNotFound : VerifyResult()
    data class InvalidImage(val reason: String) : VerifyResult()
    object NoTemplatesFound : VerifyResult()
}

@Service
class VerificationService(
    private val engine: SourceAfisEngine,
    private val templateCache: TemplateCache,
    private val auditLogger: AuditLogger,
    @Value("\${bioafis.matching.default-threshold:40.0}") private val defaultThreshold: Double,
    @Value("\${bioafis.matching.default-dpi:500}") private val defaultDpi: Int,
) {
    private val logger = LoggerFactory.getLogger(VerificationService::class.java)

    fun verify(tenantId: Long, request: VerifyRequest, ipAddress: String?): VerifyResult {
        val startMs = System.currentTimeMillis()
        val threshold = request.threshold ?: defaultThreshold

        val candidates = templateCache.getCandidates(tenantId)
        val patientTemplates = candidates[request.uid]
            ?: return VerifyResult.PatientNotFound

        if (patientTemplates.isEmpty()) {
            return VerifyResult.NoTemplatesFound
        }

        val imageBytes = try {
            Base64.getDecoder().decode(request.imageBase64)
        } catch (e: IllegalArgumentException) {
            return VerifyResult.InvalidImage("Invalid Base64 encoding")
        }

        val probeExtraction = try {
            engine.extractTemplate(imageBytes, defaultDpi)
        } catch (e: Exception) {
            return VerifyResult.InvalidImage(e.message ?: "Extraction failed")
        }

        val filteredTemplates = if (request.position != null) {
            patientTemplates.filter { it.position == request.position }
        } else {
            patientTemplates
        }

        val matcher = FingerprintMatcher(probeExtraction.template)
        var bestScore = 0.0
        var bestPosition = filteredTemplates.firstOrNull()?.position

        for (cached in filteredTemplates) {
            val score = matcher.match(cached.template)
            if (score > bestScore) {
                bestScore = score
                bestPosition = cached.position
            }
        }

        val matched = bestScore >= threshold
        val responseMs = (System.currentTimeMillis() - startMs).toInt()

        logger.info(
            "Verification: uid={} tenant={} score={} matched={} responseMs={}",
            request.uid,
            tenantId,
            bestScore,
            matched,
            responseMs,
        )

        auditLogger.log(
            tenantId = tenantId,
            operation = "VERIFY",
            uid = request.uid,
            ipAddress = ipAddress,
            responseMs = responseMs,
            httpStatus = 200,
        )

        return VerifyResult.Success(
            VerifyResponse(
                uid = request.uid,
                matched = matched,
                score = bestScore,
                matchedPosition = if (matched) bestPosition else null,
                threshold = threshold,
            ),
        )
    }
}
