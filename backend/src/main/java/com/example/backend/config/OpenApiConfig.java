package com.example.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    static {
        org.springdoc.core.utils.SpringDocUtils.getConfig().replaceWithSchema(
                java.time.LocalDateTime.class,
                new io.swagger.v3.oas.models.media.StringSchema()
                        .example("2026-10-18 20:00:00")
                        .pattern("yyyy-MM-dd HH:mm:ss")
                        .description("Date and time formatted as yyyy-MM-dd HH:mm:ss")
        );
        org.springdoc.core.utils.SpringDocUtils.getConfig().replaceWithSchema(
                java.time.LocalDate.class,
                new io.swagger.v3.oas.models.media.StringSchema()
                        .example("2026-10-18")
                        .pattern("yyyy-MM-dd")
                        .description("Date formatted as yyyy-MM-dd")
        );
    }

    private static final String SECURITY_SCHEME_NAME = "Bearer Authentication";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SIMRS Delegated Access Control (DAC) API")
                        .version("1.0.0")
                        .description("REST API documentation for Smart Contract-based Delegated Access Control on Hyperledger Fabric for SIMRS Morbis.")
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter JWT Bearer token obtained from /api/auth/login")));
    }
}
