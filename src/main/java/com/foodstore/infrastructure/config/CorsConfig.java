package com.foodstore.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS: el navegador solo deja que una web (tienda-web, en otro puerto = otro origen) llame a esta API si la API lo
 * autoriza. Se autoriza solo lo que el catalogo web necesita: lectura (GET/HEAD) desde los origenes configurados.
 * Escrituras (POST/PUT/DELETE) no se abren a ningun navegador. Nunca "*": el origen sale de app.cors.allowed-origins.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] origenesPermitidos;

    public CorsConfig(@Value("${app.cors.allowed-origins}") String[] origenesPermitidos) {
        this.origenesPermitidos = origenesPermitidos;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(origenesPermitidos)
                .allowedMethods("GET", "HEAD", "OPTIONS")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
