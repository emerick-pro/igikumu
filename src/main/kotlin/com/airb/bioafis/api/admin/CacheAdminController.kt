package com.airb.bioafis.api.admin

import com.airb.bioafis.matching.TemplateCache
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/cache")
@Tag(name = "Admin - Cache", description = "Template cache management (admin only)")
class CacheAdminController(
    private val templateCache: TemplateCache,
) {
    @Operation(summary = "Reload all templates from database into cache")
    @PostMapping("/reload")
    fun reloadCache(): ResponseEntity<Map<String, Any>> {
        templateCache.reload()
        val size = templateCache.totalSize()
        return ResponseEntity.ok(
            mapOf(
                "message" to "Cache reloaded successfully",
                "totalTemplates" to size,
            ),
        )
    }
}
