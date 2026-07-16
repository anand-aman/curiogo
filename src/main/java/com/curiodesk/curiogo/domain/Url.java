package com.curiodesk.curiogo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persistent record for a shortened URL.
 *
 * <p>The numeric {@code id} is the seed: for generated links the public code is
 * {@code Base62(id)}. The schema itself is owned by Liquibase (see
 * {@code db/changelog}); {@code ddl-auto: validate} only checks this mapping
 * against the migrated {@code urls} table.
 */
@Entity
@Table(name = "urls")
@Getter
@Setter
@NoArgsConstructor
public class Url {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // maps to BIGSERIAL
    private Long id;

    @Column(name = "short_code", nullable = false, unique = true, length = 64)
    private String shortCode;

    @Column(name = "original_url", nullable = false, columnDefinition = "text")
    private String originalUrl;

    @Column(name = "is_custom", nullable = false)
    private boolean custom = false;

    @Column(name = "expires_at")
    private Instant expiresAt; // null = never expires

    @Column(name = "click_count", nullable = false)
    private long clickCount = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
