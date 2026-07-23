package com.curiodesk.curiogo.config;

import com.curiodesk.curiogo.service.RateLimiterService;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimiterService rateLimiter;

    public WebMvcConfig(RateLimiterService rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(rateLimiter))
                .addPathPatterns("/api/v1/urls"); // Apply rate limiting to link creation endpoints
    }


}
