package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.proyecto.servicios.client.GestoPagoProductClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoProductCache;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.exception.GestoPagoException;
import com.proyecto.servicios.model.gestopago.ProductGroupDTO;
import com.proyecto.servicios.model.gestopago.ProductGroupedResponse;
import com.proyecto.servicios.model.gestopago.ProductListResponse;
import com.proyecto.servicios.enums.DataSourceEnum;
import com.proyecto.servicios.repositorys.gestopago.GestoPagoProductCacheRepository;
import com.proyecto.servicios.service.GestoPagoProductService;
import com.proyecto.servicios.service.GestoPagoTokenService;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Implementación del servicio de productos de GestoPago.
 * <p>
 * Estrategia de caché:
 * <ul>
 *   <li>Respuesta 200 → se guarda en Redis (TTL configurable), no en BD.</li>
 *   <li>Si Redis falla → fallback: se guarda en la tabla {@code gestopago_product_cache} de la BD.</li>
 *   <li>Cron diario a las 06:00 AM → refresca la caché automáticamente.</li>
 * </ul>
 * <p>
 * El servicio externo puede responder en XML o JSON. Se detecta automáticamente.
 */
@Service
@Slf4j
public class GestoPagoProductServiceImpl implements GestoPagoProductService {

    private static final String REDIS_KEY = "gestopago:productos:agrupados";

    private final GestoPagoProductClient gestoPagoProductClient;
    private final GestoPagoTokenService tokenService;
    private final RedisTemplate<String, String> redisTemplate;
    private final GestoPagoProductCacheRepository productCacheRepository;
    private final ObjectMapper objectMapper;
    private final XmlMapper xmlMapper;

    @Value("${gestopago.auth.id-distribuidor}")
    private Integer idDistribuidor;

    @Value("${gestopago.auth.codigo-dispositivo}")
    private String codigoDispositivo;

    @Value("${gestopago.api.key}")
    private String apiKey;

    @Value("${gestopago.cache.ttl-horas:1}")
    private long cacheTtlHoras;

    public GestoPagoProductServiceImpl(GestoPagoProductClient gestoPagoProductClient,
                                       GestoPagoTokenService tokenService,
                                       RedisTemplate<String, String> redisTemplate,
                                       GestoPagoProductCacheRepository productCacheRepository,
                                       ObjectMapper objectMapper) {
        this.gestoPagoProductClient = gestoPagoProductClient;
        this.tokenService = tokenService;
        this.redisTemplate = redisTemplate;
        this.productCacheRepository = productCacheRepository;
        this.objectMapper = objectMapper;
        this.xmlMapper = new XmlMapper();
    }

    /**
     * Obtiene la lista de productos plana construyéndola a partir de la agrupada en caché.
     */
    @Override
    public ProductListResponse obtenerListaProductos() {
        log.info("Iniciando obtención de lista de productos plana de GestoPago");
        try {
            // Obtenemos los agrupados (que ya manejan la caché y la petición)
            ProductGroupedResponse agrupados = obtenerProductosAgrupados();

            ProductListResponse flatResponse = new ProductListResponse();
            flatResponse.setStatus(agrupados.getStatus());
            flatResponse.setMessage(agrupados.getMessage());
            
            List<com.proyecto.servicios.model.gestopago.ProductDTO> flatList = new java.util.ArrayList<>();
            if (agrupados.getGrupos() != null) {
                for (ProductGroupDTO grupo : agrupados.getGrupos()) {
                    if (grupo.getProductos() != null) {
                        flatList.addAll(grupo.getProductos());
                    }
                }
            }
            flatResponse.setData(flatList);
            flatResponse.setCodigoCache(agrupados.getCodigoCache());
            flatResponse.setOrigenCache(agrupados.getOrigenCache());
            return flatResponse;

        } finally {
            log.info("Fin de la invocación al servicio de productos planos de GestoPago");
        }
    }

    /**
     * Obtiene los productos agrupados por {@code tipoFront}.
     * Se prioriza la caché de Redis/BD. Si no hay, consulta y agrupa.
     */
    @Override
    public ProductGroupedResponse obtenerProductosAgrupados() {
        log.info("Iniciando obtención de productos agrupados por tipoFront");
        try {
            // 1. Intentar servir desde Redis
            String json = redisTemplate.opsForValue().get(REDIS_KEY);
            if (json != null && !json.isEmpty()) {
                ProductGroupedResponse desdeRedis = objectMapper.readValue(json, ProductGroupedResponse.class);
                desdeRedis.setOrigenDatos(DataSourceEnum.EXITO);
                log.info("Productos agrupados obtenidos desde caché Redis");
                return desdeRedis;
            }

            // 2. Si no hay caché, consulta externa
            ProductListResponse listaPlana = consultarServicioExterno();
            
            // 3. Agrupar
            ProductGroupedResponse groupedResponse = agruparProductos(listaPlana);
            groupedResponse.setOrigenDatos(DataSourceEnum.EXITO);
            
            // 4. Guardar agrupados en caché
            guardarEnCache(groupedResponse);
            
            return groupedResponse;

        } catch (Exception eRedis) {
            log.error("Error leyendo de Redis, intentando BD de respaldo", eRedis);
            return obtenerDesdeBdFallback();
        } finally {
            log.info("Fin de la obtención de productos agrupados");
        }
    }

    /**
     * Fallback para leer de la base de datos si Redis falla.
     */
    private ProductGroupedResponse obtenerDesdeBdFallback() {
        try {
            GestoPagoProductCache cacheBd = productCacheRepository.findTopByOrderByFechaActualizacionDesc().orElse(null);
            if (cacheBd != null && cacheBd.getProductosJson() != null) {
                ProductGroupedResponse datos = objectMapper.readValue(cacheBd.getProductosJson(), ProductGroupedResponse.class);
                if (datos != null && !"VACIO".equalsIgnoreCase(datos.getStatus())
                        && datos.getGrupos() != null && !datos.getGrupos().isEmpty()) {
                    datos.setOrigenDatos(DataSourceEnum.ERROR_REDIS);
                    return datos;
                }
                ProductListResponse listaPlana = consultarServicioExterno();
                if ((listaPlana == null || listaPlana.getData() == null || listaPlana.getData().isEmpty())
                        && java.nio.file.Files.exists(java.nio.file.Path.of("response.xml"))) {
                    listaPlana = parsearRespuesta(java.nio.file.Files.readString(java.nio.file.Path.of("response.xml")));
                    if (listaPlana != null) {
                        listaPlana.calcularStatus();
                    }
                }
                ProductGroupedResponse groupedResponse = agruparProductos(listaPlana);
                if (groupedResponse.getGrupos() != null && !groupedResponse.getGrupos().isEmpty()) {
                    guardarEnBd(groupedResponse);
                    groupedResponse.setOrigenDatos(DataSourceEnum.ERROR_REDIS);
                    return groupedResponse;
                }
            }
            throw new RuntimeException("Caché en BD vacía");
        } catch (Exception eBd) {
            log.error("Fallo lectura BD de respaldo", eBd);
            ProductGroupedResponse fallbackResponse = new ProductGroupedResponse();
            fallbackResponse.setStatus("ERROR");
            fallbackResponse.setMessage("Error al obtener productos de la caché de respaldo");
            fallbackResponse.setOrigenDatos(DataSourceEnum.ERROR_BD);
            return fallbackResponse;
        }
    }
    
    private ProductGroupedResponse agruparProductos(ProductListResponse listaPlana) {
        if (listaPlana == null || listaPlana.getData() == null || listaPlana.getData().isEmpty()) {
            return ProductGroupedResponse.builder()
                    .status("VACIO")
                    .message("No se encontraron productos")
                    .totalProductos(0)
                    .totalGrupos(0)
                    .grupos(List.of())
                    .build();
        }

        Map<String, List<com.proyecto.servicios.model.gestopago.ProductDTO>> agrupados = listaPlana.getData().stream()
                .filter(p -> p.getFrontType() != null)
                .collect(Collectors.groupingBy(
                        com.proyecto.servicios.model.gestopago.ProductDTO::getFrontType,
                        Collectors.toList()
                ));

        List<ProductGroupDTO> grupos = agrupados.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparingInt(k -> {
                    try { return Integer.parseInt(k); } catch (NumberFormatException e) { return Integer.MAX_VALUE; }
                })))
                .map(entry -> ProductGroupDTO.builder()
                        .tipoFront(entry.getKey())
                        .total(entry.getValue().size())
                        .productos(entry.getValue())
                        .build())
                .collect(Collectors.toList());

        return ProductGroupedResponse.builder()
                .status("OK")
                .message(listaPlana.getMessage())
                .totalProductos(listaPlana.getData().size())
                .totalGrupos(grupos.size())
                .grupos(grupos)
                .build();
    }

    /**
     * Cron que se ejecuta todos los días a las 06:00 AM para refrescar la caché de productos.
     */
    @Scheduled(cron = "0 0 6 * * *")
    public void refrescarCacheProductos() {
        log.info("Cron 06:00 AM - Iniciando refresco automático de caché de productos GestoPago");
        try {
            ProductListResponse response = consultarServicioExterno();
            ProductGroupedResponse grouped = agruparProductos(response);
            guardarEnCache(grouped);
            log.info("Cron 06:00 AM - Caché de productos agrupados refrescada correctamente");
        } catch (Exception e) {
            log.error("Cron 06:00 AM - Error al refrescar caché de productos", e);
        }
    }

    // -------------------------------------------------------------------------
    // Métodos privados de apoyo
    // -------------------------------------------------------------------------

    /**
     * Intenta leer los productos agrupados desde Redis.
     *
     * @return {@link ProductGroupedResponse} deserializado o {@code null} si no hay datos.
     */
    private ProductGroupedResponse obtenerDesdeRedis() {
        try {
            String json = redisTemplate.opsForValue().get(REDIS_KEY);
            if (json != null && !json.isEmpty()) {
                return objectMapper.readValue(json, ProductGroupedResponse.class);
            }
        } catch (Exception e) {
            log.error("No se pudo leer el caché de Redis (clave: {})", REDIS_KEY, e);
            throw new RuntimeException("Error en Redis", e); // Lanza para activar fallback en método principal
        }
        return null;
    }

    /**
     * Llama al cliente Feign para obtener los productos directamente del servicio externo.
     * Detecta automáticamente si la respuesta es XML o JSON y la parsea correctamente.
     *
     * @return {@link ProductListResponse} con los datos de GestoPago.
     * @throws GestoPagoException si hay un error de comunicación o la respuesta es errónea.
     */
    private ProductListResponse consultarServicioExterno() {
        log.info("Consultando lista de productos al servicio externo de GestoPago");
        try {
            // Obtener el token activo de la BD dinámicamente
            Optional<GestoPagoToken> tokenOpt = tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo);
            String bearerToken = tokenOpt.map(t -> "Bearer " + t.getToken()).orElse("");

            if (bearerToken.isEmpty()) {
                log.error("No se encontró un token activo en la base de datos para GestoPago.");
            }

            // Llamar al servicio externo (respuesta en crudo: puede ser XML o JSON)
            String rawResponse = gestoPagoProductClient.getProductList(bearerToken, apiKey);
            log.debug("Respuesta cruda recibida del servicio externo (primeros 300 chars): {}",
                    rawResponse != null ? rawResponse.substring(0, Math.min(rawResponse.length(), 300)) : "null");

            // Parsear la respuesta según su formato
            ProductListResponse response = parsearRespuesta(rawResponse);

            // Para respuestas XML el estado se calcula desde los datos recibidos
            if (response != null && (response.getStatus() == null || response.getStatus().isBlank())) {
                response.calcularStatus();
            }

            if (response == null || "ERROR".equalsIgnoreCase(response.getStatus())) {
                String errorMsg = response != null ? response.getMessage() : "Respuesta nula o inválida";
                log.error("El servicio externo respondió con error lógico: {}", errorMsg);
                throw new GestoPagoException("Error en la respuesta del servicio externo: " + errorMsg, 500);
            }

            log.info("Invocación exitosa al servicio externo, productos recibidos correctamente");
            return response;

        } catch (FeignException.Unauthorized e) {
            log.error("Error de autenticación al consumir el servicio externo: Unauthorized (401)", e);
            throw new GestoPagoException("Error de autenticación con el servicio externo. Verifique las credenciales/token.", e, 401);
        } catch (FeignException.GatewayTimeout | FeignException.ServiceUnavailable e) {
            log.error("Timeout o servicio no disponible al intentar conectar con GestoPago", e);
            throw new GestoPagoException("Timeout al conectar con el servicio externo.", e, 504);
        } catch (FeignException e) {
            log.error("Error de comunicación HTTP ({}) al consumir el servicio externo", e.status(), e);
            throw new GestoPagoException("Error HTTP al comunicarse con el servicio externo", e, e.status());
        } catch (GestoPagoException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error inesperado al obtener la lista de productos de GestoPago", e);
            throw new GestoPagoException("Error interno al procesar la integración con GestoPago", e, 500);
        }
    }

    /**
     * Detecta si la respuesta es XML o JSON y la parsea al objeto {@link ProductListResponse}.
     *
     * @param rawResponse Respuesta en crudo del servicio externo.
     * @return {@link ProductListResponse} parseado.
     */
    private ProductListResponse parsearRespuesta(String rawResponse) throws Exception {
        if (rawResponse == null || rawResponse.isBlank()) {
            return null;
        }
        String trimmed = rawResponse.replace("\uFEFF", "").trim();
        if (trimmed.contains("┬í") || trimmed.contains("Ã¡") || trimmed.contains("Ã©")) {
            byte[] cp437OrLatin1 = trimmed.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            String candidate = new String(cp437OrLatin1, java.nio.charset.StandardCharsets.UTF_8);
            if (!candidate.contains("\uFFFD")) {
                trimmed = candidate;
            }
        }
        if (trimmed.startsWith("<")) {
            // Respuesta en formato XML
            log.debug("Respuesta detectada como XML, parseando con XmlMapper");
            return xmlMapper.readValue(trimmed, ProductListResponse.class);
        } else {
            // Respuesta en formato JSON
            log.debug("Respuesta detectada como JSON, parseando con ObjectMapper");
            return objectMapper.readValue(trimmed, ProductListResponse.class);
        }
    }

    /**
     * Guarda el resultado AGRUPADO en Redis. Si Redis falla, guarda en la BD como fallback.
     *
     * @param response La respuesta agrupada.
     */
    private void guardarEnCache(ProductGroupedResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(REDIS_KEY, json, cacheTtlHoras, TimeUnit.HOURS);
            log.info("Productos agrupados guardados en Redis (TTL: {} hora(s))", cacheTtlHoras);
        } catch (Exception redisEx) {
            log.warn("Redis no disponible ({}). Guardando caché agrupada en BD como respaldo.", redisEx.getMessage());
            guardarEnBd(response);
        }
    }

    /**
     * Fallback: serializa y persiste los productos agrupados en la BD.
     *
     * @param response La respuesta agrupada a persistir.
     */
    private void guardarEnBd(ProductGroupedResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            GestoPagoProductCache cache = productCacheRepository
                    .findTopByOrderByFechaActualizacionDesc()
                    .orElseGet(GestoPagoProductCache::new);
            cache.setProductosJson(json);
            productCacheRepository.save(cache);
            log.info("Caché agrupada guardada correctamente en BD (fallback)");
        } catch (JsonProcessingException e) {
            log.error("Error al serializar productos agrupados para guardar en BD", e);
        } catch (Exception e) {
            log.error("Error al guardar caché agrupada en BD", e);
        }
    }
}