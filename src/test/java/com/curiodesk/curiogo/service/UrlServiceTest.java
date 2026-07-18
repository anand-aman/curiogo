package com.curiodesk.curiogo.service;

import com.curiodesk.curiogo.domain.CreateUrlRequest;
import com.curiodesk.curiogo.domain.CreateUrlResponse;
import com.curiodesk.curiogo.domain.Url;
import com.curiodesk.curiogo.exception.AliasTakenException;
import com.curiodesk.curiogo.exception.LinkExpiredException;
import com.curiodesk.curiogo.exception.ReservedAliasException;
import com.curiodesk.curiogo.exception.UrlNotFoundException;
import com.curiodesk.curiogo.repository.UrlRepository;
import com.curiodesk.curiogo.util.ShortCodeEncoder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UrlServiceTest {

    private static final String BASE_URL = "http://short.test";

    @Mock private UrlRepository repository;
    @Mock private ShortCodeEncoder encoder;
    @Mock private ClickCounter clickCounter;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private  UrlService service;

    @BeforeEach
    public void setUp() {
        service = new UrlService(repository, encoder, clickCounter, new SimpleMeterRegistry(),
                redis, BASE_URL, Duration.ofHours(1), Duration.ofHours(1));
    }

    @Test
    @DisplayName("generated: saves twice and returns Base62(id) as the code")
    void createGenerated_returnsBase62Code() {
        when(repository.saveAndFlush(any(Url.class))).thenAnswer(inv -> {
            Url u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(125L); // phase-1 INSERT assigns the IDENTITY id
            }
            return u;
        });
        when(encoder.encode(125L)).thenReturn("21");

        CreateUrlResponse res = service.create(
                new CreateUrlRequest("https://example.com", null, null, null));

        assertThat(res.code()).isEqualTo("21");
        assertThat(res.shortUrl()).isEqualTo(BASE_URL + "/21");
        assertThat(res.expiresAt()).isNull();
    }

    @Test
    @DisplayName("custom: valid alias is persisted and echoed back")
    void createCustom_success() {
        when(repository.existsByShortCode("promo")).thenReturn(false);
        when(repository.saveAndFlush(any(Url.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateUrlResponse res = service.create(
                new CreateUrlRequest("https://example.com", "promo", null, null));

        assertThat(res.code()).isEqualTo("promo");
        assertThat(res.shortUrl()).isEqualTo(BASE_URL + "/promo");
    }

    @Test
    @DisplayName("custom: reserved alias -> ReservedAliasException, nothing saved")
    void createCustom_reservedAlias_throws() {
        assertThatThrownBy(() -> service.create(
                new CreateUrlRequest("https://example.com", "api", null, null)))
                .isInstanceOf(ReservedAliasException.class);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("custom: pre-existing alias -> AliasTakenException")
    void createCustom_alreadyTaken_throws() {
        when(repository.existsByShortCode("promo")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new CreateUrlRequest("https://example.com", "promo", null, null)))
                .isInstanceOf(AliasTakenException.class);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("custom: unique-constraint race on save -> AliasTakenException")
    void createCustom_raceOnSave_throws() {
        when(repository.existsByShortCode("promo")).thenReturn(false);
        when(repository.saveAndFlush(any(Url.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate short_code"));

        assertThatThrownBy(() -> service.create(
                new CreateUrlRequest("https://example.com", "promo", null, null)))
                .isInstanceOf(AliasTakenException.class);
    }

    @Test
    @DisplayName("create: both expiresAt and ttlSeconds -> IllegalArgumentException")
    void create_bothExpiryInputs_throws() {
        CreateUrlRequest req = new CreateUrlRequest(
                "https://example.com", null, Instant.now().plusSeconds(60), 60L);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create: ttlSeconds is converted to an absolute expiry ~now+ttl")
    void create_ttlSeconds_setsRelativeExpiry() {
        Instant before = Instant.now();
        when(repository.saveAndFlush(any(Url.class))).thenAnswer(inv -> {
            Url u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(1L);
            }
            return u;
        });
        when(encoder.encode(1L)).thenReturn("1");

        CreateUrlResponse res = service.create(
                new CreateUrlRequest("https://example.com", null, null, 3600L));

        assertThat(res.expiresAt())
                .isBetween(before.plusSeconds(3599), Instant.now().plusSeconds(3601));
    }

    @Test
    @DisplayName("resolve: live link returns target and records a click")
    void resolveToTarget_live_recordsClick() {
        Url url = new Url();
        url.setShortCode("21");
        url.setOriginalUrl("https://example.com");
        url.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("url:21")).thenReturn(null);
        when(repository.findByShortCode("21")).thenReturn(Optional.of(url));

        String target = service.resolveToTarget("21");

        assertThat(target).isEqualTo("https://example.com");
        verify(clickCounter).record("21");
    }

    @Test
    @DisplayName("resolve: unknown code -> UrlNotFoundException, no click")
    void resolveToTarget_notFound_throws() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("url:nope")).thenReturn(null);
        when(repository.findByShortCode("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveToTarget("nope"))
                .isInstanceOf(UrlNotFoundException.class);

        verify(clickCounter, never()).record(any());
    }

    @Test
    @DisplayName("resolve: expired link -> LinkExpiredException, no click")
    void resolveToTarget_expired_throws() {
        Url url = new Url();
        url.setShortCode("21");
        url.setOriginalUrl("https://example.com");
        url.setExpiresAt(Instant.now().minusSeconds(1));
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("url:21")).thenReturn(null);
        when(repository.findByShortCode("21")).thenReturn(Optional.of(url));

        assertThatThrownBy(() -> service.resolveToTarget("21"))
                .isInstanceOf(LinkExpiredException.class);

        verify(clickCounter, never()).record(any());
    }

    @Test
    @DisplayName("resolve: cache miss caches the target with a bounded TTL")
    void resolveToTarget_miss_recordsClickAndCaches() {
        Url url = new Url();
        url.setShortCode("21");
        url.setOriginalUrl("https://example.com");
        url.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("url:21")).thenReturn(null); // cache miss
        when(repository.findByShortCode("21")).thenReturn(Optional.of(url));

        String target = service.resolveToTarget("21");

        assertThat(target).isEqualTo("https://example.com");
        verify(clickCounter).record("21");
        // no admission gate: every miss caches (TTL capped at 1h)
        verify(valueOps).set("url:21", "https://example.com", Duration.ofHours(1));
    }

    @Test
    @DisplayName("resolve: cache hit serves target without touching the database")
    void resolveToTarget_cacheHit_servesFromCache() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("url:21")).thenReturn("https://example.com");

        String target = service.resolveToTarget("21");

        assertThat(target).isEqualTo("https://example.com");
        verify(clickCounter).record("21");
        verify(repository, never()).findByShortCode(any());
    }

    @Test
    @DisplayName("resolve: non-expiring link is cached with the flat 1h TTL")
    void resolveToTarget_nonExpiring_cachedFor1h() {
        Url url = new Url();
        url.setShortCode("21");
        url.setOriginalUrl("https://example.com");
        url.setExpiresAt(null); // never expires
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("url:21")).thenReturn(null);
        when(repository.findByShortCode("21")).thenReturn(Optional.of(url));

        service.resolveToTarget("21");

        verify(valueOps).set("url:21", "https://example.com", Duration.ofHours(1));
    }

    @Test
    @DisplayName("resolve: far-future-expiry link has its TTL capped at 1h")
    void resolveToTarget_expiring_ttlCappedAt1h() {
        Url url = new Url();
        url.setShortCode("21");
        url.setOriginalUrl("https://example.com");
        url.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS)); // remaining >> cap
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("url:21")).thenReturn(null);
        when(repository.findByShortCode("21")).thenReturn(Optional.of(url));

        service.resolveToTarget("21");

        verify(valueOps).set(eq("url:21"), eq("https://example.com"), eq(Duration.ofHours(1)));
    }

    @Test
    @DisplayName("resolve: Redis GET failure fails open – served from db, click recorded")
    void resolveToTarget_cacheGetFails_failsOpenToDb() {
        Url url = new Url();
        url.setShortCode("21");
        url.setOriginalUrl("https://example.com");
        url.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("url:21"))
                .thenThrow(new RedisConnectionFailureException("redis down")); // cache down
        when(repository.findByShortCode("21")).thenReturn(Optional.of(url));

        String target = service.resolveToTarget("21");

        // Fail open: the redirect still resolves from Postgres and the click counts.
        assertThat(target).isEqualTo("https://example.com");
        verify(clickCounter).record("21");
    }

    @Test
    @DisplayName("resolve: Redis SET failure on a miss never breaks the redirect")
    void resolveToTarget_cacheSetFails_stillReturnsTarget() {
        Url url = new Url();
        url.setShortCode("21");
        url.setOriginalUrl("https://example.com");
        url.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("url:21")).thenReturn(null); // cache miss
        when(repository.findByShortCode("21")).thenReturn(Optional.of(url));
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(valueOps).set(eq("url:21"), eq("https://example.com"), any(Duration.class));

        String target = service.resolveToTarget("21");

        // The write blew up, but the target was already resolved from the db.
        assertThat(target).isEqualTo("https://example.com");
        verify(clickCounter).record("21");
    }


}
