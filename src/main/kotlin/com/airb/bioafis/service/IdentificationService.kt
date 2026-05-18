package com.airb.bioafis.service

import com.airb.bioafis.dto.request.IdentifyRequest
import com.airb.bioafis.dto.response.CandidateResult
import com.airb.bioafis.dto.response.IdentifyResponse
import com.airb.bioafis.matching.SourceAfisEngine
import com.airb.bioafis.matching.TemplateCache
import com.airb.bioafis.security.AuditLogger
import com.machinezoo.sourceafis.FingerprintMatcher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Base64

sealed class IdentifyResult {
    data class Success(val response: IdentifyResponse) : IdentifyResult()
    data class InvalidImage(val reason: String) : IdentifyResult()
}

@Service
class IdentificationService(
    private val engine: SourceAfisEngine,
    private val templateCache: TemplateCache,
    private val auditLogger: AuditLogger,
    @Value("\${bioafis.matching.default-threshold:40.0}") private val defaultThreshold: Double,
    @Value("\${bioafis.matching.default-dpi:500}") private val defaultDpi: Int,
) {
    private val logger = LoggerFactory.getLogger(IdentificationService::class.java)

    fun identify(tenantId: Long, request: IdentifyRequest, ipAddress: String?): IdentifyResult {
        val startMs = System.currentTimeMillis()
        val threshold = request.threshold ?: defaultThreshold

        val imageBytes = try {
            Base64.getDecoder().decode(request.imageBase64)
        } catch (e: IllegalArgumentException) {
            return IdentifyResult.InvalidImage("Invalid Base64 encoding")
        }

        val probeExtraction = try {
            engine.extractTemplate(imageBytes, defaultDpi)
        } catch (e: Exception) {
            return IdentifyResult.InvalidImage(e.message ?: "Extraction failed")
        }

        val allCandidates = templateCache.getCandidates(tenantId)
        val totalSearched = allCandidates.values.sumOf { it.size }

        val matcher = FingerprintMatcher(probeExtraction.template)

        val results = allCandidates
            .flatMap { (uid, templates) ->
                val filtered = if (request.position != null) {
                    templates.filter { it.position == request.position }
                } else {
                    templates
                }
                filtered.map { cached ->
                    CandidateResult(
                        uid = uid,
                        score = matcher.match(cached.template),
                        matchedPosition = cached.position,
                    )
                }
            }
            .filter { it.score >= threshold }
            .sortedByDescending { it.score }
            .take(request.maxResults)

        val responseMs = (System.currentTimeMillis() - startMs).toInt()

        logger.info(
            "Identification complete: tenant={} candidates={} totalSearched={} responseMs={}",
            tenantId,
            results.size,
            totalSearched,
            responseMs,
        )

        auditLogger.log(
            tenantId = tenantId,
            operation = "IDENTIFY",
            ipAddress = ipAddress,
            responseMs = responseMs,
            httpStatus = 200,
        )

        return IdentifyResult.Success(
            IdentifyResponse(
                candidates = results,
                totalSearched = totalSearched,
                threshold = threshold,
            ),
        )
    }
}
