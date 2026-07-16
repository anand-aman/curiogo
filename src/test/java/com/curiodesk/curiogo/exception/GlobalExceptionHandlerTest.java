package com.curiodesk.curiogo.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GlobalExceptionHandlerTest {
    private static final String PATH = "/api/urls";

    @Mock private HttpServletRequest request;

    private GlobalExceptionHandler handler;

    @BeforeEach
    public void setUp() {
        handler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn(PATH);
    }

    @Test
    @DisplayName("UrlNotFoundException -> 404 with populated body")
    void notFound_maps404() {
        ResponseEntity<ApiError> res =
                handler.handleNotFound(new UrlNotFoundException("abc"), request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ApiError body = res.getBody();
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.error()).isEqualTo("Not Found");
        assertThat(body.path()).isEqualTo(PATH);
        assertThat(body.message()).contains("abc");
        assertThat(body.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("LinkExpiredException -> 410 Gone")
    void expired_maps410() {
        ResponseEntity<ApiError> res =
                handler.handleExpired(new LinkExpiredException("abc"), request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(res.getBody().status()).isEqualTo(410);
    }

    @Test
    @DisplayName("AliasTakenException -> 409 Conflict")
    void taken_maps409() {
        ResponseEntity<ApiError> res =
                handler.handleTaken(new AliasTakenException("promo"), request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody().status()).isEqualTo(409);
    }

    @Test
    @DisplayName("ReservedAliasException -> 422 Unprocessable Entity")
    void reserved_maps422() {
        ResponseEntity<ApiError> res =
                handler.handleReserved(new ReservedAliasException("api"), request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody().status()).isEqualTo(422);
    }

    @Test
    @DisplayName("IllegalArgumentException -> 400 Bad Request")
    void badArgument_maps400() {
        ResponseEntity<ApiError> res = handler.handleBadArgument(
                new IllegalArgumentException("Provide either expiresAt or ttlSeconds, not both."),
                request);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().message()).contains("not both");
    }

}
