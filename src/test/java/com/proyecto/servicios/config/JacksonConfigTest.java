package com.proyecto.servicios.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class JacksonConfigTest {

    private JacksonConfig jacksonConfig;

    @BeforeEach
    void setUp() {
        jacksonConfig = new JacksonConfig();
    }

    @Test
    void objectMapper_configuraJavaTimeModule_yNoSerializaFechasComoTimestamps() throws JsonProcessingException {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        ObjectMapper mapper = jacksonConfig.objectMapper(builder);

        assertNotNull(mapper);

        LocalDateTime now = LocalDateTime.of(2026, 11, 6, 12, 0, 0);
        String json = mapper.writeValueAsString(now);

        assertNotNull(json);
        assertTrue(json.contains("2026-11-06T12:00:00") || json.contains("2026-11-06"));
        assertFalse(json.matches("^\\d+$")); // No es timestamp numérico
    }

    @Test
    void objectMapper_deshabilitaCoercionDeStringsANumeros() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        ObjectMapper mapper = jacksonConfig.objectMapper(builder);

        // Intentar deserializar String a Integer directo debe fallar por CoercionAction.Fail
        String jsonStringNumber = "\"12345\"";

        assertThrows(MismatchedInputException.class, () -> mapper.readValue(jsonStringNumber, Integer.class));
        assertThrows(MismatchedInputException.class, () -> mapper.readValue(jsonStringNumber, Float.class));
    }
}

