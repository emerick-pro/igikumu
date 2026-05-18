package com.airb.bioafis.matching

import com.airb.bioafis.domain.enums.FingerPosition
import com.airb.bioafis.repository.FingerprintRepository
import com.airb.bioafis.repository.PatientRepository
import com.machinezoo.sourceafis.FingerprintTemplate
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock

@Component
class TemplateCache(
    private val fingerprintRepository: FingerprintRepository,
    private val patientRepository: PatientRepository,
    private val encryption: TemplateEncryption,
    private val engine: SourceAfisEngine,
    @Value("\${bioafis.cache.reload-on-startup:true}") private val reloadOnStartup: Boolean,
) {
    private val logger = LoggerFactory.getLogger(TemplateCache::class.java)

    data class CachedTemplate(
        val fingerprintId: Long,
        val position: FingerPosition,
        val template: FingerprintTemplate,
    )

    // tenantId -> uid -> list of CachedTemplate
    private val cache = ConcurrentHashMap<Long, ConcurrentHashMap<String, List<CachedTemplate>>>()
    private val lock = ReentrantReadWriteLock()

    @PostConstruct
    fun warmUp() {
        if (!reloadOnStartup) {
            logger.info("Cache warm-up skipped (reload-on-startup=false)")
            return
        }
        logger.info("Starting template cache warm-up...")
        try {
            loadAll()
        } catch (e: Exception) {
            logger.error("Cache warm-up failed — aborting startup", e)
            throw IllegalStateException("Template cache warm-up failed", e)
        }
    }

    private fun loadAll() {
        val lock = lock.writeLock()
        lock.lock()
        try {
            cache.clear()
            val allFingerprints = fingerprintRepository.findAll()
            val patientMap = patientRepository.findAll().associateBy { it.id }

            var templateCount = 0
            var patientCount = 0
            var tenantCount = 0

            val grouped = allFingerprints.groupBy { fp ->
                val patient = patientMap[fp.patientId]
                patient?.tenantId to (patient?.uid ?: "")
            }

            grouped.forEach { (key, fingerprints) ->
                val (tenantId, uid) = key
                if (tenantId == null || uid.isEmpty()) return@forEach

                val templates = fingerprints.mapNotNull { fp ->
                    try {
                        val decrypted = encryption.decrypt(fp.templateEncrypted)
                        val template = engine.deserialize(decrypted)
                        CachedTemplate(fp.id, fp.fingerPosition, template)
                    } catch (e: Exception) {
                        logger.warn("Failed to decrypt/deserialize fingerprint id={}: {}", fp.id, e.message)
                        null
                    }
                }

                if (templates.isNotEmpty()) {
                    cache.getOrPut(tenantId) { ConcurrentHashMap() }[uid] = templates
                    patientCount++
                    templateCount += templates.size
                }

                if (templateCount % 10_000 == 0 && templateCount > 0) {
                    logger.info("Cache warm-up progress: {} templates loaded...", templateCount)
                }
            }

            tenantCount = cache.size
            logger.info(
                "Cache loaded: {} templates for {} patients across {} tenants",
                templateCount,
                patientCount,
                tenantCount,
            )
        } finally {
            lock.unlock()
        }
    }

    fun getCandidates(tenantId: Long): Map<String, List<CachedTemplate>> {
        val rl = lock.readLock()
        rl.lock()
        try {
            return cache[tenantId]?.toMap() ?: emptyMap()
        } finally {
            rl.unlock()
        }
    }

    fun put(tenantId: Long, uid: String, templates: List<CachedTemplate>) {
        val wl = lock.writeLock()
        wl.lock()
        try {
            cache.getOrPut(tenantId) { ConcurrentHashMap() }[uid] = templates
        } finally {
            wl.unlock()
        }
    }

    fun remove(tenantId: Long, uid: String) {
        val wl = lock.writeLock()
        wl.lock()
        try {
            cache[tenantId]?.remove(uid)
        } finally {
            wl.unlock()
        }
    }

    fun reload() {
        logger.info("Cache reload requested")
        loadAll()
    }

    fun totalSize(): Int {
        val rl = lock.readLock()
        rl.lock()
        try {
            return cache.values.sumOf { it.values.sumOf { templates -> templates.size } }
        } finally {
            rl.unlock()
        }
    }
}
