package com.curiodesk.curiogo.service;

import com.curiodesk.curiogo.repository.UrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;


@Component
public class ExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(ExpirySweeper.class);

    private final UrlRepository repository;

    public ExpirySweeper(UrlRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "${app.expiry-sweep-cron:0 0 * * * *}")
    @Transactional
    public void sweep() {
        int removed = repository.deleteByExpiresAtBefore(Instant.now());
        if (removed > 0) {
            log.info("Expiry sweep removed {} expired link(s).", removed);
        }
    }
}
