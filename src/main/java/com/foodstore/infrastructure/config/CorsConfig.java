package com.foodstore.infrastructure.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS: el navegador solo deja que una web (tienda-web, en otro puerto = otro origen) llame a esta API si la API lo
 * autoriza. Se autoriza solo lo que el catalogo web necesita: lectura (GET/HEAD) desde los origenes configurados.
 * Escrituras (POST/PUT/DELETE) no se abren a ningun navegador. Nunca "*": el origen sale de app.cors.allowed-origins.
 *
 * Es un bean de Spring Security (no un WebMvcConfigurer) porque el filtro de seguridad corre antes que MVC y, sin
 * esto, rechazaria el "preflight" (la consulta OPTIONS previa del navegador) por no llevar token.
 */
@Configuration
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String[] origenesPermitidos) {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(List.of(origenesPermitidos));
        configuracion.setAllowedMethods(List.of("GET", "HEAD", "OPTIONS"));
        configuracion.setAllowCredentials(false);
        configuracion.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", configuracion);
        return fuente;
    }
}
