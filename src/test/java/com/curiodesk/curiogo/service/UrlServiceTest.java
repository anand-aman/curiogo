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

    private  UrlService service;

    @BeforeEach
    public void setUp() {
        service = new UrlService(repository, encoder, clickCounter, new SimpleMeterRegistry(), BASE_URL);
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
        when(repository.findByShortCode("21")).thenReturn(Optional.of(url));

        String target = service.resolveToTarget("21");

        assertThat(target).isEqualTo("https://example.com");
        verify(clickCounter).record("21");
    }

    @Test
    @DisplayName("resolve: unknown code -> UrlNotFoundException, no click")
    void resolveToTarget_notFound_throws() {
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
        when(repository.findByShortCode("21")).thenReturn(Optional.of(url));

        assertThatThrownBy(() -> service.resolveToTarget("21"))
                .isInstanceOf(LinkExpiredException.class);

        verify(clickCounter, never()).record(any());
    }


}
