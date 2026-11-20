package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoClient;
import com.proyecto.servicios.exception.GestoPagoException;
import com.proyecto.servicios.model.gestopago.ProductDTO;
import com.proyecto.servicios.model.gestopago.ProductListResponse;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GestoPagoServiceImplTest {

    @Mock
    private GestoPagoClient gestoPagoClient;

    @InjectMocks
    private GestoPagoServiceImpl gestoPagoService;

    private Request request;

    @BeforeEach
    void setUp() {
        request = Request.create(Request.HttpMethod.GET, "/url", Collections.emptyMap(), null, new RequestTemplate());
    }

    @Test
    void obtenerListaProductos_Success() {
        ProductListResponse mockResponse = new ProductListResponse();
        mockResponse.setStatus("OK");
        ProductDTO product = new ProductDTO();
        product.setId("1");
        product.setName("Producto 1");
        mockResponse.setData(Collections.singletonList(product));

        when(gestoPagoClient.getProductList()).thenReturn(mockResponse);

        ProductListResponse result = gestoPagoService.obtenerListaProductos();

        assertNotNull(result);
        assertEquals("OK", result.getStatus());
        assertEquals(1, result.getData().size());
        assertEquals("Producto 1", result.getData().get(0).getName());
        verify(gestoPagoClient, times(1)).getProductList();
    }

    @Test
    void obtenerListaProductos_ErrorLogico() {
        ProductListResponse mockResponse = new ProductListResponse();
        mockResponse.setStatus("ERROR");
        mockResponse.setMessage("Error interno en GestoPago");

        when(gestoPagoClient.getProductList()).thenReturn(mockResponse);

        GestoPagoException exception = assertThrows(GestoPagoException.class, () -> {
            gestoPagoService.obtenerListaProductos();
        });

        assertTrue(exception.getMessage().contains("Error en la respuesta del servicio externo: Error interno en GestoPago"));
        assertEquals(500, exception.getStatusCode());
        verify(gestoPagoClient, times(1)).getProductList();
    }

    @Test
    void obtenerListaProductos_RespuestaNula() {
        when(gestoPagoClient.getProductList()).thenReturn(null);

        GestoPagoException exception = assertThrows(GestoPagoException.class, () -> {
            gestoPagoService.obtenerListaProductos();
        });

        assertTrue(exception.getMessage().contains("Respuesta nula"));
        assertEquals(500, exception.getStatusCode());
    }

    @Test
    void obtenerListaProductos_Unauthorized() {
        FeignException.Unauthorized unauthorized = new FeignException.Unauthorized(
                "Unauthorized", request, null, new HashMap<>());
        
        when(gestoPagoClient.getProductList()).thenThrow(unauthorized);

        GestoPagoException exception = assertThrows(GestoPagoException.class, () -> {
            gestoPagoService.obtenerListaProductos();
        });

        assertEquals(401, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("Error de autenticación"));
        verify(gestoPagoClient, times(1)).getProductList();
    }

    @Test
    void obtenerListaProductos_Timeout() {
        FeignException.GatewayTimeout timeout = new FeignException.GatewayTimeout(
                "Gateway Timeout", request, null, new HashMap<>());

        when(gestoPagoClient.getProductList()).thenThrow(timeout);

        GestoPagoException exception = assertThrows(GestoPagoException.class, () -> {
            gestoPagoService.obtenerListaProductos();
        });

        assertEquals(504, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("Timeout al conectar"));
        verify(gestoPagoClient, times(1)).getProductList();
    }

    @Test
    void obtenerListaProductos_ServiceUnavailable() {
        FeignException.ServiceUnavailable serviceUnavailable = new FeignException.ServiceUnavailable(
                "Service Unavailable", request, null, new HashMap<>());

        when(gestoPagoClient.getProductList()).thenThrow(serviceUnavailable);

        GestoPagoException exception = assertThrows(GestoPagoException.class, () -> {
            gestoPagoService.obtenerListaProductos();
        });

        assertEquals(504, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("Timeout al conectar con el servicio externo"));
    }

    @Test
    void obtenerListaProductos_GenericFeignException() {
        FeignException.BadRequest badRequest = new FeignException.BadRequest(
                "Bad Request", request, null, new HashMap<>());

        when(gestoPagoClient.getProductList()).thenThrow(badRequest);

        GestoPagoException exception = assertThrows(GestoPagoException.class, () -> {
            gestoPagoService.obtenerListaProductos();
        });

        assertEquals(400, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("Error HTTP al comunicarse"));
    }

    @Test
    void obtenerListaProductos_UnexpectedError() {
        when(gestoPagoClient.getProductList()).thenThrow(new RuntimeException("Unexpected error"));

        GestoPagoException exception = assertThrows(GestoPagoException.class, () -> {
            gestoPagoService.obtenerListaProductos();
        });

        assertEquals(500, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("Error interno al procesar la integración"));
        verify(gestoPagoClient, times(1)).getProductList();
    }
}
