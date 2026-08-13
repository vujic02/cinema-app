package com.cinema.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cinemaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cinema Seat Booking API")
                        .version("v1")
                        .description("""
                                Browse showings, hold seats under a Redis TTL, and complete a
                                simulated purchase. Seat status changes are pushed over STOMP
                                on /topic/showings/{id}.
                                """))
                .components(new Components().addSecuritySchemes("bearer-jwt",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                // Applies the scheme to every operation, which is what puts the "Authorize"
                // button in Swagger UI. The public auth endpoints opt back out with
                // @SecurityRequirements.
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }
}
