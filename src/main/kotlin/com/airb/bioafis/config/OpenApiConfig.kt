package com.airb.bioafis.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("BioAFIS API")
                .version("1.0")
                .description("Biometric Fingerprint Matching Service — AIRB"),
        )
        .addSecurityItem(SecurityRequirement().addList("BearerAuth"))
        .components(
            Components().addSecuritySchemes(
                "BearerAuth",
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("API Key"),
            ),
        )
}
