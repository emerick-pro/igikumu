package com.airb.bioafis.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateTenantRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,

    val isAdmin: Boolean = false,

    val configJson: String? = null,
)
