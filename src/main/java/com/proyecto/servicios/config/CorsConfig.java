package com.proyecto.servicios.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final List<String> DEFAULT_ORIGINS = List.of(
            "http://localhost:5173",
            "http://localhost:8085",
            "http://localhost:3000"
    );

    private static final List<String> ALLOWED_METHODS = List.of(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
    );

    private static final List<String> ALLOWED_HEADERS = List.of(
            "Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"
    );

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:8085,http://localhost:3000}")
    private String allowedOriginsConfig = "http://localhost:5173,http://localhost:8085,http://localhost:3000";

    private List<String> resolverOrigenesPermitidos() {
        if (allowedOriginsConfig == null || allowedOriginsConfig.isBlank()) {
            return DEFAULT_ORIGINS;
        }
        List<String> origenes = Arrays.stream(allowedOriginsConfig.split(","))
                .map(String::trim)
                .filter(o -> !o.isEmpty() && !"*".equals(o))
                .toList();
        return origenes.isEmpty() ? DEFAULT_ORIGINS : origenes;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origenes = resolverOrigenesPermitidos();
        registry.addMapping("/**")
                .allowedOriginPatterns(origenes.toArray(String[]::new))
                .allowedMethods(ALLOWED_METHODS.toArray(String[]::new))
                .allowedHeaders(ALLOWED_HEADERS.toArray(String[]::new))
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(resolverOrigenesPermitidos());
        config.setAllowedMethods(ALLOWED_METHODS);
        config.setAllowedHeaders(ALLOWED_HEADERS);
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public CorsFilter corsFilter() {
        return new CorsFilter(corsConfigurationSource());
    }
}
