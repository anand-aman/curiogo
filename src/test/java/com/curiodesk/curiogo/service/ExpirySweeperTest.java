package com.curiodesk.curiogo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.curiodesk.curiogo.repository.UrlRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpirySweeperTest {

    @Mock private UrlRepository repository;

    @InjectMocks private ExpirySweeper sweeper;

    @Test
    @DisplayName("sweep() deletes links whose expiry is before 'now'")
    void sweep_deletesWithNowCutoff() {
        when(repository.deleteByExpiresAtBefore(any(Instant.class))).thenReturn(3);

        Instant before = Instant.now();
        sweeper.sweep();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).deleteByExpiresAtBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before, after);
    }

    @Test
    @DisplayName("sweep() with nothing to remove still completes cleanly")
    void sweep_noExpired_isNoOp() {
        when(repository.deleteByExpiresAtBefore(any(Instant.class))).thenReturn(0);

        sweeper.sweep();

        verify(repository).deleteByExpiresAtBefore(any(Instant.class));
    }
}
