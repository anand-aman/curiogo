package com.curiodesk.curiogo.domain;

import java.time.Instant;

public record CreateUrlResponse (
        String shortUrl,
        String code,
        Instant expiresAt
){}
