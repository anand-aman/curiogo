package com.curiodesk.curiogo.controller;

import com.curiodesk.curiogo.domain.CreateUrlResponse;
import com.curiodesk.curiogo.exception.UrlNotFoundException;
import com.curiodesk.curiogo.service.UrlService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UrlController.class)
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrlService urlService;

    @Test
    @DisplayName("POST /api/v1/urls with a valid body -> 201 + Location + body")
    void create_valid_returns201() throws Exception {
        when(urlService.create(any()))
                .thenReturn(new CreateUrlResponse("http://short.test/abc", "abc", null));

        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/very/long/path\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://short.test/abc"))
                .andExpect(jsonPath("$.code").value("abc"))
                .andExpect(jsonPath("$.shortUrl").value("http://short.test/abc"));
    }

    @Test
    @DisplayName("POST /api/v1/urls with a blank url -> 400 from bean validation")
    void create_blankUrl_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("GET /{code} for a live link -> 302 + Location")
    void redirect_live_returns302() throws Exception {
        when(urlService.resolveToTarget("abc")).thenReturn("https://example.com/very/long/path");

        mockMvc.perform(get("/abc"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/very/long/path"));
    }

    @Test
    @DisplayName("GET /{code} for an unknown code -> 404 from the global handler")
    void redirect_unknown_returns404() throws Exception {
        when(urlService.resolveToTarget("nope")).thenThrow(new UrlNotFoundException("nope"));

        mockMvc.perform(get("/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("nope")));
    }
}
