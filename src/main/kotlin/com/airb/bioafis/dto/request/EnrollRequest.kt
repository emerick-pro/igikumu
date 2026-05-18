package com.airb.bioafis.dto.request

import com.airb.bioafis.domain.enums.EnrollmentSource
import com.airb.bioafis.domain.enums.FingerPosition
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class EnrollRequest(
    @field:Size(max = 100)
    val uid: String? = null,

    @field:NotEmpty
    @field:Size(max = 10)
    @field:Valid
    val fingers: List<FingerData>,

    @field:Size(max = 50)
    val facilityCode: String? = null,

    val enrollmentSource: EnrollmentSource = EnrollmentSource.AT_VISIT,

    @field:Size(max = 100)
    val enrolledBy: String? = null,
)

data class FingerData(
    val position: FingerPosition,

    @field:jakarta.validation.constraints.NotBlank
    val imageBase64: String,

    val imageFormat: ImageFormat = ImageFormat.BMP,
)

enum class ImageFormat {
    BMP,
    PNG,
    WSQ,
}
