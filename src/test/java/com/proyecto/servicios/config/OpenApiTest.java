package com.proyecto.servicios.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiTest {

    @Test
    void openAPI_agregaServidorConUrlRaiz() {
        OpenApi openApi = new OpenApi();
        OpenAPI result = openApi.openAPI();

        assertNotNull(result);
        assertNotNull(result.getServers());
        assertFalse(result.getServers().isEmpty());
        assertEquals("/", result.getServers().get(0).getUrl());
    }
}

