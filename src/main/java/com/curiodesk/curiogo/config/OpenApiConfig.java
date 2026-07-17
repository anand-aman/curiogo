package com.curiodesk.curiogo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI curioGoOpenApi() {
        return new OpenAPI()
                .info(new Info().title("CurioGo URL Shortener API")
                        .description("Create short links, resolve them with a 302 redirect, and track clicks")
                        .version("v1")
                        .contact(new Contact().name("CurioDesk").email("curiodesk@gmail.com"))
                        .license(new License().name("Apache-2.0"))
                );
    }
}
