package com.curiodesk.curiogo.service;

import com.curiodesk.curiogo.repository.UrlRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Component
public class ClickCounter {

    private final ConcurrentHashMap<String, LongAdder> buffer = new ConcurrentHashMap<>();
    private final UrlRepository repository;

    public ClickCounter(UrlRepository repository) {
        this.repository = repository;
    }

    /** Records one click for {@code code} -- lock-free, no DB call. */
    public void record(String code) {
        buffer.computeIfAbsent(code, k -> new LongAdder()).increment();
    }

    /**
     * Drains the buffer and writes each code's accumulated delta once.
     *
     * <p>Each key is {@code remove}d before its sum is read: any click that lands
     * mid-flush recreates a fresh adder under the same key and is simply counted
     * in the next window -- no clicks are silently dropped.
     */
    @Scheduled(fixedDelayString = "${app.click-flush-ms:5000}")
    @Transactional
    public void flush() {
        for (String code : buffer.keySet()) {
            LongAdder adder = buffer.remove(code);
            if (adder == null) {
                continue;
            }
            long delta = adder.sum();
            if (delta > 0) {
                repository.addClicks(code, delta);
            }
        }
    }
}
