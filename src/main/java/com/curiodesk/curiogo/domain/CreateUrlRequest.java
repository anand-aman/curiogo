package com.curiodesk.curiogo.domain;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.Instant;

public record CreateUrlRequest (
        @NotBlank @URL String url,
        @Size(max = 64) String customAlias,
        @Future Instant expiresAt,
        @Positive Long ttlSeconds
){ }
