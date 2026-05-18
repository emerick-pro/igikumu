package com.airb.bioafis.dto.request

import com.airb.bioafis.domain.enums.FingerPosition
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class VerifyRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val uid: String,

    val position: FingerPosition? = null,

    @field:NotBlank
    val imageBase64: String,

    val imageFormat: ImageFormat = ImageFormat.BMP,

    val threshold: Double? = null,
)
