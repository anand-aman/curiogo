package com.curiodesk.curiogo.exception;

import com.curiodesk.curiogo.config.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import java.time.Instant;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId
) {
    public static ApiError of(HttpStatus status, String message, String path) {
        return new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)
        );
    }
}

