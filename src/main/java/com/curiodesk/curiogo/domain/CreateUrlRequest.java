package com.curiodesk.curiogo.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.Instant;

public record CreateUrlRequest (
        @Schema(description = "The long URL to shorten", example = "https://curiodesk.xyz/very/long/path")
        @NotBlank @URL String url,

        @Schema(description = "Optional custom alias to use instead of a generated code", example = "launch")
        @Size(max = 64) String customAlias,

        @Schema(description = "Optional absolute expiry instant (mutual exclusive with ttlSeconds)", example = "2026-12-31T23:59:59Z")
        @Future Instant expiresAt,

        @Schema(description = "Optional relative expiry in seconds from now (mutually exclusive with expiresAt", example = "3600")
        @Positive Long ttlSeconds
){ }
