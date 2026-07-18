package com.curiodesk.curiogo.service;

import com.curiodesk.curiogo.domain.CreateUrlRequest;
import com.curiodesk.curiogo.domain.CreateUrlResponse;
import com.curiodesk.curiogo.domain.Url;
import com.curiodesk.curiogo.exception.AliasTakenException;
import com.curiodesk.curiogo.exception.LinkExpiredException;
import com.curiodesk.curiogo.exception.ReservedAliasException;
import com.curiodesk.curiogo.exception.UrlNotFoundException;
import com.curiodesk.curiogo.repository.UrlRepository;
import com.curiodesk.curiogo.util.ReservedAliases;
import com.curiodesk.curiogo.util.ShortCodeEncoder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class UrlService {

    private static final Logger log =  LoggerFactory.getLogger(UrlService.class);

    private static final String URL_KEY_PREFIX = "url:";

    private final UrlRepository repository;
    private final ShortCodeEncoder encoder;
    private final ClickCounter clickCounter;
    private final StringRedisTemplate redis;
    private final String baseUrl;

    private final Duration ttlNonExpiring;
    private final Duration ttlExpiringCap;

    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final Counter cacheErrors;

    private final Counter linksCreatedCustom;
    private final Counter linksCreatedGenerated;

    public UrlService(
            UrlRepository repository,
            ShortCodeEncoder encoder,
            ClickCounter clickCounter,
            MeterRegistry meterRegistry,
            StringRedisTemplate redis,
            @Value("${app.base-url}") String baseUrl,
            @Value("${app.cache.ttl-non-expiring:1h}")Duration ttlNonExpiring,
            @Value("${app.cache.ttl-expiring-cap:1h}") Duration ttlExpiringCap
            ) {
        this.repository = repository;
        this.encoder = encoder;
        this.clickCounter = clickCounter;
        this.redis = redis;
        this.baseUrl = baseUrl;
        this.ttlNonExpiring = ttlNonExpiring;
        this.ttlExpiringCap = ttlExpiringCap;

        this.cacheHits = Counter.builder("curiogo.cache.access")
                .description("Redirect cache lookups")
                .tag("result", "hit")
                .register(meterRegistry);
        this.cacheMisses = Counter.builder("curiogo.cache.access")
                .description("Redirect cache lookups")
                .tag("result", "miss")
                .register(meterRegistry);
        this.cacheErrors = Counter.builder("curiogo.cache.access")
                .description("Redirect cache lookups")
                .tag("result", "error")
                .register(meterRegistry);
        this.linksCreatedCustom = Counter.builder("curiogo.links.created")
                .description("Short links created")
                .tag("type", "custom")
                .register(meterRegistry);
        this.linksCreatedGenerated = Counter.builder("curiogo.links.created")
                .description("Short links created")
                .tag("type", "generated")
                .register(meterRegistry);
    }

    /**
     * Creates a short link — custom alias if one was supplied, otherwise a
     * Base62-of-id generated code.
     */
    @Transactional
    public CreateUrlResponse create(CreateUrlRequest request) {
        Instant expiry = resolveExpiry(request.expiresAt(), request.ttlSeconds());
        String alias = request.customAlias();
        boolean isCustom = alias != null && !alias.isBlank();

        log.debug("Creating short link custom={} hasExpiry={}", isCustom, expiry);

        Url saved = isCustom
                ? createCustom(request.url(), alias, expiry)
                : createGenerated(request.url(), expiry);

        log.info("Created short link code={} custom-flag={} expiresAt={}", saved.getShortCode(), isCustom, saved.getExpiresAt());
        (isCustom ? linksCreatedCustom : linksCreatedGenerated).increment();
        return toResponse(saved);
    }

    /**
     * Resolves a code to its original URL, enforcing expiry and recording the
     * click. This is the redirect hot path.
     *
     * @throws UrlNotFoundException if no link matches the code
     * @throws LinkExpiredException if the link exists but has expired
     */
    public String resolveToTarget(String code) {
        log.debug("Resolving short link code={}", code);

        String cached = cacheGet(code);
        if (cached != null) {
            cacheHits.increment();
            clickCounter.record(code);
            log.debug("Cache hit code {} (click recorded)", code);
            return cached;
        }

        Url url = repository.findByShortCode(code)
                .orElseThrow(() -> new UrlNotFoundException(code));

        if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(Instant.now())) {
            throw new LinkExpiredException(code);
        }

        clickCounter.record(code);
        maybeCache(code, url);
        log.debug("Cache miss code={} (Served from DB, Click recorded)", code);
        return url.getOriginalUrl();
    }

    // ---Cache---
    private String cacheGet(String code) {
        try{
            String cached = redis.opsForValue().get(URL_KEY_PREFIX + code);
            if(cached != null) {
                cacheMisses.increment();
            }
            return cached;
        } catch (DataAccessException e) {
            cacheErrors.increment();
            log.warn("Redis GET failed, failing open to db code={} corrId={}:", code, UUID.randomUUID(), e);
            return null;
        }
    }

    private void maybeCache(String code, Url url) {
        Duration ttl = cacheTtl(url.getExpiresAt());
        if(ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            redis.opsForValue().set(URL_KEY_PREFIX + code, url.getOriginalUrl(), ttl);
            log.debug("Cached code={} ttl={}", code, ttl);
        } catch (DataAccessException e) {
            cacheErrors.increment();
            log.warn("Redis SET failed, skipping cache write code={} corrId={}:", code, UUID.randomUUID(), e);
        }
    }

    private Duration cacheTtl(Instant expiresAt) {
        if (expiresAt == null) {
            return ttlNonExpiring;
        }

        // For expiring link: TTL is minimum of remaining duration
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        return remaining.compareTo(ttlExpiringCap) > 0 ?  ttlExpiringCap : remaining;
    }

    private Url createCustom(String originalUrl, String alias, Instant expiry) {
        if (ReservedAliases.isReserved(alias)) {
            throw new ReservedAliasException(alias);
        }
        if (repository.existsByShortCode(alias)) {
            throw new AliasTakenException(alias);
        }

        Url url = new Url();
        url.setShortCode(alias);
        url.setOriginalUrl(originalUrl);
        url.setCustom(true);
        url.setExpiresAt(expiry);

        try {
            // saveAndFlush forces the INSERT now, so a concurrent duplicate
            // surfaces here as a unique-constraint violation we can translate.
            return repository.saveAndFlush(url);
        } catch (DataIntegrityViolationException race) {
            log.debug("Custom alias '{}' lost a create race, translating to 409", alias);
            throw new AliasTakenException(alias);
        }
    }

    private Url createGenerated(String originalUrl, Instant expiry) {
        Url url = new Url();
        // Two-phase save: the code is Base62(id), but the id only exists after
        // the INSERT. short_code is NOT NULL UNIQUE, so seed a unique temporary
        // token first, then overwrite it with the real code.
        url.setShortCode("tmp-" + UUID.randomUUID());
        url.setOriginalUrl(originalUrl);
        url.setCustom(false);
        url.setExpiresAt(expiry);

        Url saved = repository.saveAndFlush(url);
        saved.setShortCode(encoder.encode(saved.getId()));
        log.debug("Generated code for Id={} -> {}", saved.getId(), saved.getShortCode());
        return repository.saveAndFlush(saved);
    }

    /**
     * Resolves the effective expiry from the two mutually exclusive inputs.
     *
     * @throws IllegalArgumentException if both {@code expiresAt} and
     *         {@code ttlSeconds} are supplied
     */
    private Instant resolveExpiry(Instant expiresAt, Long ttlSeconds) {
        if (expiresAt != null && ttlSeconds != null) {
            throw new IllegalArgumentException(
                    "Provide either expiresAt or ttlSeconds, not both.");
        }
        if (ttlSeconds != null) {
            return Instant.now().plusSeconds(ttlSeconds);
        }
        return expiresAt; // may be null = never expires
    }

    private CreateUrlResponse toResponse(Url url) {
        return new CreateUrlResponse(
                baseUrl + "/" + url.getShortCode(),
                url.getShortCode(),
                url.getExpiresAt()
        );
    }


}
