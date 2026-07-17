package com.curiodesk.curiogo.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record CreateUrlResponse (
        @Schema(description = "The full short link", example = "http://localhost:80/srt")
        String shortUrl,

        @Schema(description = "The short code alone", example = "srt")
        String code,

        @Schema(description = "When the link expires, or null if never expires")
        Instant expiresAt
){}
