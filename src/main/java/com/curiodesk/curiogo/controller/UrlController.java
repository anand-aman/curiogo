package com.curiodesk.curiogo.controller;

import com.curiodesk.curiogo.domain.CreateUrlRequest;
import com.curiodesk.curiogo.domain.CreateUrlResponse;
import com.curiodesk.curiogo.exception.ApiError;
import com.curiodesk.curiogo.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    @Operation(
            summary = "Create a short URL",
            description = "Accepts a long URL (plus optional custom alias and expiry) and "
                    + "returns the generated short code and full short link.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Short URL created"),
            @ApiResponse(responseCode = "400", description = "Invalid request body",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Custom alias already taken",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422", description = "Custom alias is reserved",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/api/v1/urls")
    public ResponseEntity<CreateUrlResponse> createUrl(@Valid @RequestBody CreateUrlRequest request) {
        CreateUrlResponse response = urlService.create(request);
        return ResponseEntity
                .created(URI.create(response.shortUrl()))
                .body(response);
    }

    @Operation(
            summary = "Resolve a short code",
            description = "Redirects (302) to the original URL and records the click. "
                    + "Returns 404 for unknown and 410 for expired links.")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Redirect to the original URL"),
            @ApiResponse(responseCode = "404", description = "Unknown short code",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "410", description = "Link has expired",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String target = urlService.resolveToTarget(code);
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .location(URI.create(target))
                .build();
    }
}
