package com.curiodesk.curiogo.service;

import com.curiodesk.curiogo.repository.UrlRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClickCounterTest {

    @Mock
    private UrlRepository repository;

    @Test
    @DisplayName("flush: batches N clicks per code into one addClicks(code, N)")
    void flush_batchesClicksPerCode() {
        ClickCounter counter = new ClickCounter(repository);
        counter.record("21");
        counter.record("21");
        counter.record("21");
        counter.record("9k");

        counter.flush();

        verify(repository).addClicks("21", 3L);
        verify(repository).addClicks("9k", 1L);
    }

    @Test
    @DisplayName("flush: drains the buffer so a second flush is a no-op")
    void flush_clearsBuffer() {
        ClickCounter counter = new ClickCounter(repository);
        counter.record("21");
        counter.flush();
        verify(repository).addClicks("21", 1L);

        counter.flush(); // buffer already drained
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("flush: empty buffer never touches the repository")
    void flush_emptyBuffer_noRepoCall() {
        ClickCounter counter = new ClickCounter(repository);

        counter.flush();

        verifyNoInteractions(repository);
    }
}
