package com.airb.bioafis.api.admin

import com.airb.bioafis.dto.request.CreateTenantRequest
import com.airb.bioafis.dto.response.CreateTenantResponse
import com.airb.bioafis.dto.response.ErrorResponse
import com.airb.bioafis.dto.response.TenantResponse
import com.airb.bioafis.service.TenantService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/tenants")
@Tag(name = "Admin - Tenants", description = "Tenant management (admin only)")
class TenantAdminController(
    private val tenantService: TenantService,
) {
    @Operation(summary = "List all tenants")
    @GetMapping
    fun listTenants(): ResponseEntity<List<TenantResponse>> {
        return ResponseEntity.ok(tenantService.listTenants())
    }

    @Operation(summary = "Create a new tenant")
    @PostMapping
    fun createTenant(
        @RequestBody @Valid request: CreateTenantRequest,
    ): ResponseEntity<CreateTenantResponse> {
        val response = tenantService.createTenant(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(summary = "Deactivate a tenant")
    @DeleteMapping("/{id}")
    fun deleteTenant(@PathVariable id: Long): ResponseEntity<*> {
        return try {
            tenantService.deleteTenant(id)
            ResponseEntity.noContent().build<Void>()
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse(code = "TENANT_NOT_FOUND", message = "Tenant not found: $id"),
            )
        }
    }
}
