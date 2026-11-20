package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.client.GestoPagoProductClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoProductCache;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.enums.DataSourceEnum;
import com.proyecto.servicios.model.gestopago.ProductDTO;
import com.proyecto.servicios.model.gestopago.ProductGroupDTO;
import com.proyecto.servicios.model.gestopago.ProductGroupedResponse;
import com.proyecto.servicios.model.gestopago.ProductListResponse;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoProductCacheRepository;
import com.proyecto.servicios.service.GestoPagoTokenService;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GestoPagoProductServiceImplTest {

    @Mock
    private GestoPagoProductClient gestoPagoProductClient;

    @Mock
    private GestoPagoTokenService tokenService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private GestoPagoProductCacheRepository productCacheRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private GestoPagoProductServiceImpl gestoPagoService;

    private Request request;
    private ProductListResponse mockFlatResponse;
    private ProductGroupedResponse mockGroupedResponse;
    private GestoPagoProductCache mockDbCache;

    private static final String FLAT_JSON_RESPONSE = "{\"status\":\"OK\",\"message\":null,\"data\":[{\"id\":\"1\",\"name\":\"Producto 1\",\"frontType\":\"1\"}]}";
    private static final String GROUPED_JSON_RESPONSE = "{\"status\":\"OK\",\"message\":null,\"totalProductos\":1,\"totalGrupos\":1,\"grupos\":[{\"tipoFront\":\"1\",\"total\":1,\"productos\":[{\"id\":\"1\",\"name\":\"Producto 1\",\"frontType\":\"1\"}]}]}";
    private static final String REDIS_KEY = "gestopago:productos:agrupados";

    @BeforeEach
    void setUp() {
        request = Request.create(Request.HttpMethod.GET, "/url", Collections.emptyMap(), null, new RequestTemplate());

        ProductDTO product = new ProductDTO("1", "Producto 1", "Servicio 1", "100", "10.0", "a", "1", "false", "11");

        mockFlatResponse = new ProductListResponse();
        mockFlatResponse.setStatus("OK");
        mockFlatResponse.setData(List.of(product));

        ProductGroupDTO group = new ProductGroupDTO("1", 1, List.of(product));
        mockGroupedResponse = new ProductGroupedResponse();
        mockGroupedResponse.setStatus("OK");
        mockGroupedResponse.setGrupos(List.of(group));

        mockDbCache = new GestoPagoProductCache();
        mockDbCache.setProductosJson(GROUPED_JSON_RESPONSE);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void obtenerListaProductos_desdeRedis_exitoso() throws Exception {
        when(valueOperations.get(REDIS_KEY)).thenReturn(GROUPED_JSON_RESPONSE);
        when(objectMapper.readValue(GROUPED_JSON_RESPONSE, ProductGroupedResponse.class)).thenReturn(mockGroupedResponse);

        ProductListResponse result = gestoPagoService.obtenerListaProductos();

        assertNotNull(result);
        assertEquals("OK", result.getStatus());
        assertEquals(DataSourceEnum.EXITO.getCodigo(), result.getCodigoCache());
        verifyNoInteractions(gestoPagoProductClient);
    }

    @Test
    void obtenerListaProductos_conGruposNulosYProductosNulos() throws Exception {
        ProductGroupedResponse respConNulos = new ProductGroupedResponse();
        respConNulos.setStatus("OK");
        respConNulos.setGrupos(null);

        when(valueOperations.get(REDIS_KEY)).thenReturn(GROUPED_JSON_RESPONSE);
        when(objectMapper.readValue(GROUPED_JSON_RESPONSE, ProductGroupedResponse.class)).thenReturn(respConNulos);

        ProductListResponse result = gestoPagoService.obtenerListaProductos();
        assertNotNull(result);
        assertTrue(result.getData().isEmpty());

        ProductGroupDTO grupoSinProductos = new ProductGroupDTO("1", 0, null);
        respConNulos.setGrupos(List.of(grupoSinProductos));
        ProductListResponse result2 = gestoPagoService.obtenerListaProductos();
        assertNotNull(result2);
        assertTrue(result2.getData().isEmpty());
    }

    @Test
    void obtenerListaProductos_redisVacio_consultaExternoYGuardaEnRedis() throws Exception {
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        when(tokenService.obtenerTokenActivo(any(), any())).thenReturn(Optional.of(new GestoPagoToken()));
        when(gestoPagoProductClient.getProductList(anyString(), any())).thenReturn(FLAT_JSON_RESPONSE);
        when(objectMapper.readValue(FLAT_JSON_RESPONSE, ProductListResponse.class)).thenReturn(mockFlatResponse);
        when(objectMapper.writeValueAsString(any())).thenReturn(GROUPED_JSON_RESPONSE);

        ProductGroupedResponse result = gestoPagoService.obtenerProductosAgrupados();

        assertNotNull(result);
        assertEquals("OK", result.getStatus());
        assertEquals(DataSourceEnum.EXITO.getCodigo(), result.getCodigoCache());
        verify(valueOperations, times(1)).set(eq(REDIS_KEY), anyString(), anyLong(), eq(TimeUnit.HOURS));
        verifyNoInteractions(productCacheRepository);
    }

    @Test
    void obtenerProductosAgrupados_conTokenVacio_consultaConBearerVacio() throws Exception {
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        when(tokenService.obtenerTokenActivo(any(), any())).thenReturn(Optional.empty());
        when(gestoPagoProductClient.getProductList(eq(""), any())).thenReturn(FLAT_JSON_RESPONSE);
        when(objectMapper.readValue(FLAT_JSON_RESPONSE, ProductListResponse.class)).thenReturn(mockFlatResponse);
        when(objectMapper.writeValueAsString(any())).thenReturn(GROUPED_JSON_RESPONSE);

        ProductGroupedResponse result = gestoPagoService.obtenerProductosAgrupados();
        assertNotNull(result);
    }

    @Test
    void obtenerProductosAgrupados_respuestaXmlExterna() throws Exception {
        String xmlResponse = java.nio.file.Files.readString(java.nio.file.Path.of("response.xml"));
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        when(tokenService.obtenerTokenActivo(any(), any())).thenReturn(Optional.of(new GestoPagoToken()));
        when(gestoPagoProductClient.getProductList(anyString(), any())).thenReturn(xmlResponse);
        when(objectMapper.writeValueAsString(any())).thenReturn(GROUPED_JSON_RESPONSE);

        ProductGroupedResponse result = gestoPagoService.obtenerProductosAgrupados();
        assertNotNull(result);
        assertTrue(result.getTotalProductos() > 100, "Expected >100 products from response.xml, got: " + result.getTotalProductos());
    }

    @Test
    void obtenerProductosAgrupados_agrupacionConFrontTypeNoNumericoYNull() throws Exception {
        ProductDTO p1 = new ProductDTO("1", "P1", "S1", "10", "10", "a", "2", "f", "10");
        ProductDTO p2 = new ProductDTO("2", "P2", "S2", "10", "10", "a", "no-numeric", "f", "10");
        ProductDTO p3 = new ProductDTO("3", "P3", "S3", "10", "10", "a", null, "f", "10");

        ProductListResponse customList = new ProductListResponse();
        customList.setStatus("OK");
        customList.setData(List.of(p1, p2, p3));

        when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        when(tokenService.obtenerTokenActivo(any(), any())).thenReturn(Optional.of(new GestoPagoToken()));
        when(gestoPagoProductClient.getProductList(anyString(), any())).thenReturn(FLAT_JSON_RESPONSE);
        when(objectMapper.readValue(FLAT_JSON_RESPONSE, ProductListResponse.class)).thenReturn(customList);
        when(objectMapper.writeValueAsString(any())).thenReturn(GROUPED_JSON_RESPONSE);

        ProductGroupedResponse result = gestoPagoService.obtenerProductosAgrupados();
        assertNotNull(result);
        assertEquals(2, result.getTotalGrupos());
    }

    @Test
    void obtenerListaProductos_redisVacio_externoFalla_leeDeBd() throws Exception {
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        when(tokenService.obtenerTokenActivo(any(), any())).thenReturn(Optional.of(new GestoPagoToken()));
        when(gestoPagoProductClient.getProductList(anyString(), any())).thenThrow(new RuntimeException("API down"));

        when(productCacheRepository.findTopByOrderByFechaActualizacionDesc()).thenReturn(Optional.of(mockDbCache));
        when(objectMapper.readValue(GROUPED_JSON_RESPONSE, ProductGroupedResponse.class)).thenReturn(mockGroupedResponse);

        ProductGroupedResponse result = gestoPagoService.obtenerProductosAgrupados();

        assertNotNull(result);
        assertEquals("OK", result.getStatus());
        assertEquals(DataSourceEnum.ERROR_REDIS.getCodigo(), result.getCodigoCache());
    }

    @Test
    void obtenerListaProductos_redisFallaEscritura_guardaEnBd() throws Exception {
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        when(tokenService.obtenerTokenActivo(any(), any())).thenReturn(Optional.of(new GestoPagoToken()));
        when(gestoPagoProductClient.getProductList(anyString(), any())).thenReturn(FLAT_JSON_RESPONSE);
        when(objectMapper.readValue(FLAT_JSON_RESPONSE, ProductListResponse.class)).thenReturn(mockFlatResponse);
        when(objectMapper.writeValueAsString(any())).thenReturn(GROUPED_JSON_RESPONSE);

        doThrow(new RuntimeException("Redis connection refused"))
                .when(valueOperations).set(anyString(), anyString(), anyLong(), any());

        when(productCacheRepository.findTopByOrderByFechaActualizacionDesc())
                .thenReturn(Optional.empty());

        ProductGroupedResponse result = gestoPagoService.obtenerProductosAgrupados();

        assertNotNull(result);
        assertEquals(DataSourceEnum.EXITO.getCodigo(), result.getCodigoCache());
        verify(productCacheRepository, times(1)).save(any(GestoPagoProductCache.class));
    }

    @Test
    void obtenerListaProductos_fallaTodo() {
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        when(tokenService.obtenerTokenActivo(any(), any())).thenReturn(Optional.of(new GestoPagoToken()));
        when(gestoPagoProductClient.getProductList(anyString(), any())).thenThrow(new RuntimeException("API down"));
        when(productCacheRepository.findTopByOrderByFechaActualizacionDesc()).thenReturn(Optional.empty());

        ProductGroupedResponse result = gestoPagoService.obtenerProductosAgrupados();

        assertNotNull(result);
        assertEquals("ERROR", result.getStatus());
        assertEquals(DataSourceEnum.ERROR_BD.getCodigo(), result.getCodigoCache());
    }

    @Test
    void refrescarCacheProductos_ejecutaCronExitosamente() throws Exception {
        when(tokenService.obtenerTokenActivo(any(), any())).thenReturn(Optional.of(new GestoPagoToken()));
        when(gestoPagoProductClient.getProductList(anyString(), any())).thenReturn(FLAT_JSON_RESPONSE);
        when(objectMapper.readValue(FLAT_JSON_RESPONSE, ProductListResponse.class)).thenReturn(mockFlatResponse);
        when(objectMapper.writeValueAsString(any())).thenReturn(GROUPED_JSON_RESPONSE);

        assertDoesNotThrow(() -> gestoPagoService.refrescarCacheProductos());
        verify(valueOperations, times(1)).set(eq(REDIS_KEY), anyString(), anyLong(), eq(TimeUnit.HOURS));
    }

    @Test
    void refrescarCacheProductos_conErrorNoLanzaExcepcion() {
        when(tokenService.obtenerTokenActivo(any(), any())).thenReturn(Optional.of(new GestoPagoToken()));
        when(gestoPagoProductClient.getProductList(anyString(), any())).thenThrow(new RuntimeException("Cron error"));

        assertDoesNotThrow(() -> gestoPagoService.refrescarCacheProductos());
    }

    @Test
    void consultarServicioExterno_feignUnauthorized() {
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        when(tokenService.obtenerTokenActivo(any(), any())).thenReturn(Optional.of(new GestoPagoToken()));
        when(productCacheRepository.findTopByOrderByFechaActualizacionDesc()).thenReturn(Optional.empty());

        when(gestoPagoProductClient.getProductList(anyString(), any()))
                .thenThrow(new FeignException.Unauthorized("Unauthorized", request, null, null));
        ProductGroupedResponse r1 = gestoPagoService.obtenerProductosAgrupados();
        assertEquals("ERROR", r1.getStatus());
    }

    @Test
    void consultarServicioExterno_feignGatewayTimeout() {
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        when(tokenService.obtenerTokenActivo(any(), any())).thenReturn(Optional.of(new GestoPagoToken()));
        when(productCacheRepository.findTopByOrderByFechaActualizacionDesc()).thenReturn(Optional.empty());

        when(gestoPagoProductClient.getProductList(anyString(), any()))
                .thenThrow(new FeignException.GatewayTimeout("Timeout", request, null, null));
        ProductGroupedResponse r2 = gestoPagoService.obtenerProductosAgrupados();
        assertEquals("ERROR", r2.getStatus());
    }

    @Test
    void consultarServicioExterno_feignBadRequest() {
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        when(tokenService.obtenerTokenActivo(any(), any())).thenReturn(Optional.of(new GestoPagoToken()));
        when(productCacheRepository.findTopByOrderByFechaActualizacionDesc()).thenReturn(Optional.empty());

        when(gestoPagoProductClient.getProductList(anyString(), any()))
                .thenThrow(new FeignException.BadRequest("Bad Request", request, null, null));
        ProductGroupedResponse r3 = gestoPagoService.obtenerProductosAgrupados();
        assertEquals("ERROR", r3.getStatus());
    }

    @Test
    void consultarServicioExterno_respuestaConStatusError() throws Exception {
        ProductListResponse errList = new ProductListResponse();
        errList.setStatus("ERROR");
        errList.setMessage("Error de proveedor");

        when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        when(tokenService.obtenerTokenActivo(any(), any())).thenReturn(Optional.of(new GestoPagoToken()));
        when(gestoPagoProductClient.getProductList(anyString(), any())).thenReturn(FLAT_JSON_RESPONSE);
        when(objectMapper.readValue(FLAT_JSON_RESPONSE, ProductListResponse.class)).thenReturn(errList);
        when(productCacheRepository.findTopByOrderByFechaActualizacionDesc()).thenReturn(Optional.empty());

        ProductGroupedResponse r = gestoPagoService.obtenerProductosAgrupados();
        assertEquals("ERROR", r.getStatus());
    }

    @Test
    void consultarServicioExterno_respuestaNulaOBlank() {
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        when(tokenService.obtenerTokenActivo(any(), any())).thenReturn(Optional.of(new GestoPagoToken()));
        when(gestoPagoProductClient.getProductList(anyString(), any())).thenReturn("");
        when(productCacheRepository.findTopByOrderByFechaActualizacionDesc()).thenReturn(Optional.empty());

        ProductGroupedResponse r = gestoPagoService.obtenerProductosAgrupados();
        assertEquals("ERROR", r.getStatus());
    }

    @Test
    void guardarEnBd_conJsonProcessingException_noFalla() throws Exception {
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        when(tokenService.obtenerTokenActivo(any(), any())).thenReturn(Optional.of(new GestoPagoToken()));
        when(gestoPagoProductClient.getProductList(anyString(), any())).thenReturn(FLAT_JSON_RESPONSE);
        when(objectMapper.readValue(FLAT_JSON_RESPONSE, ProductListResponse.class)).thenReturn(mockFlatResponse);

        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("JSON error") {});

        ProductGroupedResponse result = gestoPagoService.obtenerProductosAgrupados();
        assertNotNull(result);
    }

    @Test
    void obtenerDesdeBdFallback_cacheVaciaSeRecuperaDesdeExternoYActualizaBd() throws Exception {
        ProductGroupedResponse vacio = new ProductGroupedResponse();
        vacio.setStatus("VACIO");
        vacio.setGrupos(List.of());

        when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        when(tokenService.obtenerTokenActivo(any(), any())).thenReturn(Optional.of(new GestoPagoToken()));
        when(gestoPagoProductClient.getProductList(anyString(), any()))
                .thenThrow(new RuntimeException("API temporal"))
                .thenReturn(FLAT_JSON_RESPONSE);
        when(objectMapper.readValue(FLAT_JSON_RESPONSE, ProductListResponse.class))
                .thenReturn(mockFlatResponse);
        when(productCacheRepository.findTopByOrderByFechaActualizacionDesc())
                .thenReturn(Optional.of(mockDbCache));
        when(objectMapper.readValue(GROUPED_JSON_RESPONSE, ProductGroupedResponse.class))
                .thenReturn(vacio);
        when(objectMapper.writeValueAsString(any())).thenReturn(GROUPED_JSON_RESPONSE);

        ProductGroupedResponse recovered = gestoPagoService.obtenerProductosAgrupados();
        assertNotNull(recovered);
        assertEquals(DataSourceEnum.ERROR_REDIS.getCodigo(), recovered.getCodigoCache());
        assertTrue(recovered.getTotalProductos() > 0);
    }
}
