package com.pictet.adventurebook.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API metadata shown at the top of the Swagger UI page ({@code /swagger-ui.html}).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI adventureBookOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Adventure Book API")
                .description("Backend for the interactive adventure book application")
                .version("v1"));
    }
}
