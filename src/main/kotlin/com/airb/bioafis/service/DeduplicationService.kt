package com.airb.bioafis.service

import com.airb.bioafis.domain.enums.JobStatus
import com.airb.bioafis.domain.model.DeduplicationJob
import com.airb.bioafis.dto.request.StartDedupRequest
import com.airb.bioafis.dto.response.DedupJobResponse
import com.airb.bioafis.dto.response.DedupResultEntry
import com.airb.bioafis.dto.response.DedupResultsPage
import com.airb.bioafis.matching.TemplateCache
import com.airb.bioafis.repository.DeduplicationJobRepository
import com.airb.bioafis.security.AuditLogger
import com.machinezoo.sourceafis.FingerprintMatcher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class DeduplicationService(
    private val dedupJobRepository: DeduplicationJobRepository,
    private val templateCache: TemplateCache,
    private val auditLogger: AuditLogger,
    @Value("\${bioafis.matching.default-threshold:40.0}") private val defaultThreshold: Double,
) {
    private val logger = LoggerFactory.getLogger(DeduplicationService::class.java)

    // In-memory results storage (for MVP; production would use a DB table)
    private val jobResults = ConcurrentHashMap<String, List<DedupResultEntry>>()

    @Transactional
    fun startJob(tenantId: Long, request: StartDedupRequest, ipAddress: String?): DedupJobResponse {
        val jobId = UUID.randomUUID().toString()
        val job = DeduplicationJob(
            id = jobId,
            tenantId = tenantId,
            status = JobStatus.PENDING,
            scopeFacility = request.scopeFacility,
        )
        dedupJobRepository.save(job)

        auditLogger.log(
            tenantId = tenantId,
            operation = "DEDUP_START",
            ipAddress = ipAddress,
            httpStatus = 202,
        )

        logger.info("Deduplication job created: jobId={} tenant={}", jobId, tenantId)
        runJob(jobId, tenantId, request.threshold ?: defaultThreshold)
        return job.toResponse()
    }

    @Async
    fun runJob(jobId: String, tenantId: Long, threshold: Double) {
        val job = dedupJobRepository.findById(jobId).orElse(null) ?: return

        job.status = JobStatus.RUNNING
        job.startedAt = Instant.now()
        dedupJobRepository.save(job)

        try {
            val candidates = templateCache.getCandidates(tenantId)
            val uidList = candidates.keys.toList()
            val results = mutableListOf<DedupResultEntry>()
            val totalPairs = uidList.size.toLong() * (uidList.size - 1) / 2
            var processedPairs = 0L

            for (i in uidList.indices) {
                val uid1 = uidList[i]
                val templates1 = candidates[uid1] ?: continue

                for (j in i + 1 until uidList.size) {
                    val uid2 = uidList[j]
                    val templates2 = candidates[uid2] ?: continue

                    for (t1 in templates1) {
                        val matcher = FingerprintMatcher(t1.template)
                        for (t2 in templates2) {
                            val score = matcher.match(t2.template)
                            if (score >= threshold) {
                                results.add(
                                    DedupResultEntry(
                                        uid1 = uid1,
                                        uid2 = uid2,
                                        score = score,
                                        matchedPosition1 = t1.position.name,
                                        matchedPosition2 = t2.position.name,
                                    ),
                                )
                            }
                        }
                    }

                    processedPairs++
                    if (totalPairs > 0) {
                        job.progressPct = ((processedPairs * 100) / totalPairs).toInt().coerceIn(0, 99)
                        dedupJobRepository.save(job)
                    }
                }
            }

            jobResults[jobId] = results.sortedByDescending { it.score }
            job.status = JobStatus.DONE
            job.progressPct = 100
            job.resultCount = results.size
            job.completedAt = Instant.now()
            dedupJobRepository.save(job)

            logger.info(
                "Deduplication complete: jobId={} tenant={} duplicates={}",
                jobId,
                tenantId,
                results.size,
            )
        } catch (e: Exception) {
            logger.error("Deduplication job failed: jobId={}: {}", jobId, e.message, e)
            job.status = JobStatus.FAILED
            job.completedAt = Instant.now()
            dedupJobRepository.save(job)
        }
    }

    fun getJobStatus(tenantId: Long, jobId: String): DedupJobResponse? {
        val job = dedupJobRepository.findById(jobId).orElse(null) ?: return null
        if (job.tenantId != tenantId) return null
        return job.toResponse()
    }

    fun getJobResults(tenantId: Long, jobId: String, page: Int, pageSize: Int): DedupResultsPage? {
        val job = dedupJobRepository.findById(jobId).orElse(null) ?: return null
        if (job.tenantId != tenantId) return null

        val allResults = jobResults[jobId] ?: emptyList()
        val fromIndex = (page * pageSize).coerceAtMost(allResults.size)
        val toIndex = (fromIndex + pageSize).coerceAtMost(allResults.size)
        val pageResults = allResults.subList(fromIndex, toIndex)

        return DedupResultsPage(
            jobId = jobId,
            status = job.status,
            results = pageResults,
            page = page,
            pageSize = pageSize,
            totalResults = allResults.size,
        )
    }

    private fun DeduplicationJob.toResponse() = DedupJobResponse(
        id = id,
        tenantId = tenantId,
        status = status,
        scopeFacility = scopeFacility,
        progressPct = progressPct,
        resultCount = resultCount,
        startedAt = startedAt,
        completedAt = completedAt,
        createdAt = createdAt,
    )
}
