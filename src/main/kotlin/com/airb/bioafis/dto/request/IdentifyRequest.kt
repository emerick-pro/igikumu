package com.airb.bioafis.dto.request

import com.airb.bioafis.domain.enums.FingerPosition
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class IdentifyRequest(
    @field:NotBlank
    val imageBase64: String,

    val imageFormat: ImageFormat = ImageFormat.BMP,

    val position: FingerPosition? = null,

    val threshold: Double? = null,

    @field:Min(1)
    @field:Max(100)
    val maxResults: Int = 10,
)
