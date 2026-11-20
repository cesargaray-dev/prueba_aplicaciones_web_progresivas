package com.proyecto.servicios.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CorsConfigTest {

    private CorsConfig corsConfig;

    @BeforeEach
    void setUp() {
        corsConfig = new CorsConfig();
    }

    @Test
    void testAddCorsMappings() {
        CorsRegistry registry = new CorsRegistry();
        corsConfig.addCorsMappings(registry);
        assertNotNull(registry);
    }

    @Test
    void testCorsFilterCreationAndConfiguration() throws Exception {
        CorsFilter filter = corsConfig.corsFilter();
        assertNotNull(filter);

        Field configSourceField = CorsFilter.class.getDeclaredField("configSource");
        configSourceField.setAccessible(true);
        UrlBasedCorsConfigurationSource source = (UrlBasedCorsConfigurationSource) configSourceField.get(filter);
        assertNotNull(source);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/login");
        CorsConfiguration config = source.getCorsConfiguration(request);
        assertNotNull(config);

        List<String> allowedOriginPatterns = config.getAllowedOriginPatterns();
        assertNotNull(allowedOriginPatterns);
        assertTrue(allowedOriginPatterns.contains("http://localhost:5173"));
        assertTrue(allowedOriginPatterns.contains("http://localhost:8085"));
        assertTrue(allowedOriginPatterns.contains("http://localhost:3000"));
        assertFalse(allowedOriginPatterns.contains("*"));

        List<String> allowedHeaders = config.getAllowedHeaders();
        assertNotNull(allowedHeaders);
        assertTrue(allowedHeaders.contains("Authorization"));
        assertTrue(allowedHeaders.contains("Content-Type"));
        assertFalse(allowedHeaders.contains("*"));

        List<String> allowedMethods = config.getAllowedMethods();
        assertNotNull(allowedMethods);
        assertTrue(allowedMethods.contains("GET"));
        assertTrue(allowedMethods.contains("POST"));
        assertTrue(allowedMethods.contains("PUT"));
        assertTrue(allowedMethods.contains("DELETE"));
        assertTrue(allowedMethods.contains("OPTIONS"));
        assertTrue(allowedMethods.contains("PATCH"));

        assertEquals(3600L, config.getMaxAge());
    }

    @Test
    void testCorsConfigurationSourceFiltraWildcardYUsaDefaultsCuandoVacio() {
        ReflectionTestUtils.setField(corsConfig, "allowedOriginsConfig", "*, https://app.example.com,  ");
        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/gestopago/productos");
        CorsConfiguration cfg = source.getCorsConfiguration(req);
        assertNotNull(cfg);
        assertEquals(List.of("https://app.example.com"), cfg.getAllowedOriginPatterns());

        ReflectionTestUtils.setField(corsConfig, "allowedOriginsConfig", "   ");
        CorsConfiguration fallback = corsConfig.corsConfigurationSource().getCorsConfiguration(req);
        assertNotNull(fallback);
        assertTrue(fallback.getAllowedOriginPatterns().contains("http://localhost:5173"));

        ReflectionTestUtils.setField(corsConfig, "allowedOriginsConfig", "*");
        CorsConfiguration fallbackStar = corsConfig.corsConfigurationSource().getCorsConfiguration(req);
        assertNotNull(fallbackStar);
        assertFalse(fallbackStar.getAllowedOriginPatterns().contains("*"));
        assertTrue(fallbackStar.getAllowedOriginPatterns().contains("http://localhost:8085"));
    }
}
