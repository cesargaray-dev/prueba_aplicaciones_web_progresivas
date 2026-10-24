package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.proyecto.servicios.client.GestoPagoTxClient;
import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.clientes.Cuenta;
import com.proyecto.servicios.entity.clientes.Saldo;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.entity.gestopago.PagoServicio;
import com.proyecto.servicios.enums.EstatusCuentaEnum;
import com.proyecto.servicios.exception.*;
import com.proyecto.servicios.model.gestopago.*;
import com.proyecto.servicios.repositorys.clientes.CuentaRepository;
import com.proyecto.servicios.repositorys.clientes.SaldoRepository;
import com.proyecto.servicios.repositorys.gestopago.PagoServicioRepository;
import com.proyecto.servicios.service.GestoPagoProductService;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.service.PagoServicioService;
import com.proyecto.servicios.service.Soporte2FAService;
import com.proyecto.servicios.util.AesGestoPagoUtil;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Implementación del servicio bancario de Pago de Servicios con GestoPago (PuntoRed).
 * Hereda el soporte de autenticación 2FA temporal de {@link Soporte2FAService}.
 */
@Service
@Slf4j
public class PagoServicioServiceImpl extends Soporte2FAService implements PagoServicioService {

    private static final DateTimeFormatter HORA_LOCAL_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String TELEFONO_TIPO_FRONT_2 = "1111111111";
    private static final String TELEFONO_CAT_10_11 = "#1111111111";
    private static final String CODIGO_EXITO_GESTOPAGO = "01";
    private static final String MENSAJE_FALTA_TELEFONO_O_REFERENCIA =
            "Debe ingresar el número de teléfono para recargas o el número de referencia para pago de servicios";

    private final CuentaRepository cuentaRepository;
    private final SaldoRepository saldoRepository;
    private final PagoServicioRepository pagoServicioRepository;
    private final GestoPagoTokenService tokenService;
    private final GestoPagoProductService productService;
    private final GestoPagoTxClient gestoPagoTxClient;
    private final AesGestoPagoUtil aesGestoPagoUtil;
    private final ObjectMapper objectMapper;
    private final XmlMapper xmlMapper;

    @Value("${gestopago.api.secret-key:HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=}")
    private String secretKey;

    @Value("${gestopago.api.iv:p5ldmBPdd/9pjC0bDC/nSg==}")
    private String iv;

    @Value("${gestopago.api.unidad:083}")
    private String unidad;

    @Value("${gestopago.api.key:YSX1HpAFum4TpCecyFBxs4eljAlbhKqK6fpcSQp8}")
    private String apiKey;

    @Value("${gestopago.auth.id-distribuidor:83}")
    private Integer idDistribuidor;

    @Value("${gestopago.auth.codigo-dispositivo:GPS83-TPV-17}")
    private String codigoDispositivo;

    @Autowired
    public PagoServicioServiceImpl(
            CuentaRepository cuentaRepository,
            SaldoRepository saldoRepository,
            PagoServicioRepository pagoServicioRepository,
            GestoPagoTokenService tokenService,
            GestoPagoProductService productService,
            GestoPagoTxClient gestoPagoTxClient,
            AesGestoPagoUtil aesGestoPagoUtil,
            ObjectMapper objectMapper,
            @Autowired(required = false) RedisTemplate<String, String> redisTemplate) {
        super(redisTemplate);
        this.cuentaRepository = cuentaRepository;
        this.saldoRepository = saldoRepository;
        this.pagoServicioRepository = pagoServicioRepository;
        this.tokenService = tokenService;
        this.productService = productService;
        this.gestoPagoTxClient = gestoPagoTxClient;
        this.aesGestoPagoUtil = aesGestoPagoUtil;
        this.objectMapper = objectMapper;
        this.xmlMapper = new XmlMapper();
    }

    @Override
    @Transactional(readOnly = true)
    public GenerarCodigo2FAResponseDTO generarCodigo2FA(GenerarCodigo2FARequestDTO request) {
        log.info("Generando código 2FA temporal para la cuenta: {}", request.getNumeroCuenta());

        Cuenta cuenta = validarCuentaYClienteActivo(request.getNumeroCuenta());
        CodigoTemporal2FA temporal = generarYPropagarCodigo2FA(cuenta.getNumeroCuenta());

        GenerarCodigo2FAResponseDTO response = GenerarCodigo2FAResponseDTO.builder()
                .numeroCuenta(cuenta.getNumeroCuenta())
                .codigo2FA(temporal.getCodigo())
                .vigenciaSegundos(VIGENCIA_SEGUNDOS_DEFAULT)
                .fechaExpiracion(temporal.getFechaExpiracion())
                .build();
        response.setCodigo(0);
        response.setMensaje("Código 2FA temporal generado exitosamente. Úselo para autorizar su pago de servicio.");
        return response;
    }

    @Override
    @Transactional
    public PagoServicioResponseDTO procesarPagoServicio(PagoServicioRequestDTO request) {
        log.info("Iniciando proceso de pago de servicio para cuenta={}, idServicio={}, idProducto={}, monto={}",
                request.getNumeroCuenta(), request.getIdServicio(), request.getIdProducto(), request.getMonto());

        // Validación inicial de presencia de teléfono o referencia
        boolean tieneTelefono = request.getTelefono() != null && !request.getTelefono().isBlank();
        boolean tieneReferencia = request.getReferencia() != null && !request.getReferencia().isBlank();
        if (!tieneTelefono && !tieneReferencia) {
            throw new ValidacionException(MENSAJE_FALTA_TELEFONO_O_REFERENCIA);
        }

        // Paso 1, 2 y 3: Validar existencia de cuenta, Cliente ACTIVO y Cuenta ACTIVA
        Cuenta cuenta = validarCuentaYClienteActivo(request.getNumeroCuenta());

        // Paso 4: Validar código 2FA de 6 dígitos (heredado de Soporte2FAService)
        validarCodigo2FA(request.getNumeroCuenta(), request.getCodigo2FA());

        // Paso 5: Validar Saldo Disponible (saldo >= monto)
        Saldo saldo = cuenta.getSaldo();
        if (saldo == null || saldo.getSaldo() == null) {
            throw new SaldoInsuficienteException(
                    String.format("Saldo insuficiente en la cuenta. Saldo disponible: $0.00, Monto a pagar: $%s",
                            request.getMonto().toPlainString()));
        }
        if (saldo.getSaldo().compareTo(request.getMonto()) < 0) {
            throw new SaldoInsuficienteException(
                    String.format("Saldo insuficiente en la cuenta. Saldo disponible: $%s, Monto a pagar: $%s",
                            saldo.getSaldo().toPlainString(), request.getMonto().toPlainString()));
        }

        // Paso 6: Resolver producto en catálogo y aplicar reglas de Recarga (tipoFront=1) vs Pago de Servicio (tipoFront=2)
        Optional<ProductDTO> productoCatalogo = buscarProductoEnCatalogo(request.getIdServicio(), request.getIdProducto());
        DatosDestinoTx datosDestino = resolverTelefonoYReferenciaSegunTipoFront(request, productoCatalogo.orElse(null));

        String nombreServicio = productoCatalogo.map(ProductDTO::getService)
                .filter(s -> !s.isBlank())
                .orElse("SERVICIO " + request.getIdServicio());
        String nombreProducto = productoCatalogo.map(ProductDTO::getName)
                .filter(s -> !s.isBlank())
                .orElse("PRODUCTO " + request.getIdProducto());

        String horaLocal = LocalDateTime.now().format(HORA_LOCAL_FORMATTER);
        String upc = aesGestoPagoUtil.generarUpc();
        String unidadConfig = (unidad != null && !unidad.isBlank()) ? unidad : "083";

        GestoPagoTxPayload payload = GestoPagoTxPayload.builder()
                .idProducto(request.getIdProducto())
                .idServicio(request.getIdServicio())
                .telefono(datosDestino.telefono())
                .horaLocal(horaLocal)
                .referencia(datosDestino.referencia())
                .montoPago(request.getMonto())
                .upc(upc)
                .unidad(unidadConfig)
                .build();

        // Paso 7: Cifrado AES-256-CBC y Preparación (random de 10 caracteres + token JWT)
        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Error al serializar payload JSON para GestoPago", e);
            throw new GestoPagoException("Error al construir el payload de pago para el proveedor", e, 500);
        }

        String random10 = aesGestoPagoUtil.generarRandom10();
        String signed = (secretKey != null && iv != null)
                ? AesGestoPagoUtil.encrypt(jsonPayload, secretKey, iv)
                : aesGestoPagoUtil.encrypt(jsonPayload);
        String bearerToken = obtenerBearerTokenActivo();

        // Paso 8: Envío a GestoPago sendTx.do
        String xmlRawResponse = enviarTransaccionAGestoPago(random10, signed, bearerToken);

        // Paso 9: MOMENTO CRÍTICO DE DEDUCCIÓN DEL DINERO
        GestoPagoTxResponse txResponse = parsearRespuestaTxXml(xmlRawResponse);
        String codigoProveedor = txResponse.getMensaje() != null && txResponse.getMensaje().getCodigo() != null
                ? txResponse.getMensaje().getCodigo().trim()
                : "";
        String textoProveedor = txResponse.getMensaje() != null && txResponse.getMensaje().getTexto() != null
                ? txResponse.getMensaje().getTexto().trim()
                : "Sin mensaje del proveedor";

        if (!CODIGO_EXITO_GESTOPAGO.equals(codigoProveedor)) {
            // Falla en proveedor (ej. 02 Saldo insuficiente en GestoPago, 06 Duplicada, 82 Timeout)
            // NO SE MODIFICA NI DESCUENTA NADA DE LA CUENTA DEL CLIENTE
            log.warn("Pago rechazado por GestoPago (CODIGO={}): {}. No se descontó saldo de la cuenta {}",
                    codigoProveedor, textoProveedor, request.getNumeroCuenta());
            throw new PagoServicioProveedorException(
                    codigoProveedor,
                    String.format("Error en el proveedor de servicios (Código %s): %s", codigoProveedor, textoProveedor)
            );
        }

        // ÉXITO (CODIGO == "01"): Proceder con deducción atómica y registro de auditoría
        BigDecimal saldoAnterior = saldo.getSaldo();
        BigDecimal nuevoSaldo = saldoAnterior.subtract(request.getMonto());
        LocalDateTime fechaOperacion = LocalDateTime.now();

        Long idTx = txResponse.getIdTx() != null ? txResponse.getIdTx() : 0L;
        String numAutorizacion = txResponse.getNumAutorizacion() != null ? txResponse.getNumAutorizacion() : "N/A";
        BigDecimal comision = txResponse.getComision() != null ? txResponse.getComision() : BigDecimal.ZERO;
        String folioTicket = generarFolioTicketUnico(fechaOperacion);

        String concepto = "1".equals(datosDestino.tipoFront())
                ? String.format("RECARGA %s (%s) - TEL: %s - AUT: %s",
                        nombreServicio, nombreProducto, datosDestino.telefono(), numAutorizacion)
                : String.format("PAGO DE SERVICIO %s (%s) - REF: %s - AUT: %s",
                        nombreServicio, nombreProducto, datosDestino.referencia(), numAutorizacion);

        // 1 y 2. Descontar monto del Saldo y actualizar fecha_movimiento y concepto
        saldo.setSaldo(nuevoSaldo);
        saldo.setFechaMovimiento(fechaOperacion);
        saldo.setConcepto(concepto);
        saldoRepository.save(saldo);

        // 3. Registrar la transacción en la tabla 'pagos_servicios'
        PagoServicio pagoServicio = new PagoServicio();
        pagoServicio.setCuenta(cuenta);
        pagoServicio.setNumeroCuenta(cuenta.getNumeroCuenta());
        pagoServicio.setIdServicio(request.getIdServicio());
        pagoServicio.setIdProducto(request.getIdProducto());
        pagoServicio.setNombreServicio(nombreServicio);
        pagoServicio.setNombreProducto(nombreProducto);
        pagoServicio.setReferencia(datosDestino.referencia());
        pagoServicio.setTelefono(datosDestino.telefono());
        pagoServicio.setMontoPagado(request.getMonto());
        pagoServicio.setComision(comision);
        pagoServicio.setSaldoAnterior(saldoAnterior);
        pagoServicio.setSaldoRestante(nuevoSaldo);
        pagoServicio.setIdTxGestopago(idTx);
        pagoServicio.setNumAutorizacion(numAutorizacion);
        pagoServicio.setFolioTicket(folioTicket);
        pagoServicio.setUpc(upc);
        pagoServicio.setCodigoRespuesta(codigoProveedor);
        pagoServicio.setMensajeRespuesta(textoProveedor);
        pagoServicio.setConcepto(concepto);
        pagoServicio.setFechaOperacion(fechaOperacion);
        pagoServicioRepository.save(pagoServicio);

        // Consumir el código 2FA temporal tras operación exitosa
        consumirCodigo2FA(request.getNumeroCuenta());

        log.info("Pago de servicio exitoso. Cuenta={}, ID_TX={}, Autorización={}, Folio={}, SaldoRestante={}",
                cuenta.getNumeroCuenta(), idTx, numAutorizacion, folioTicket, nuevoSaldo);

        // 4. Retornar respuesta exitosa con codigo=0
        PagoServicioResponseDTO response = PagoServicioResponseDTO.builder()
                .idTransaccion(idTx)
                .numeroAutorizacion(numAutorizacion)
                .folioTicket(folioTicket)
                .montoPagado(request.getMonto())
                .comision(comision)
                .saldoRestanteCuenta(nuevoSaldo)
                .fechaHoraOperacion(fechaOperacion)
                .concepto(concepto)
                .build();
        response.setCodigo(0);
        response.setMensaje(textoProveedor);
        return response;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Métodos Privados de Validación y Apoyo
    // ─────────────────────────────────────────────────────────────────────────

    private Cuenta validarCuentaYClienteActivo(String numeroCuenta) {
        // Paso 1: Validar existencia de la cuenta
        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .orElseThrow(() -> new CuentaNoEncontradaException(
                        "No se encontró la cuenta bancaria con número: " + numeroCuenta));

        // Paso 2: Validar que el Cliente asociado esté ACTIVO
        Cliente cliente = cuenta.getCliente();
        if (cliente == null || !Boolean.TRUE.equals(cliente.getActivo())) {
            throw new ClienteInactivoException(
                    "El cliente titular de la cuenta " + numeroCuenta + " se encuentra inactivo y no puede operar.");
        }

        // Paso 3: Validar que la Cuenta esté en estatus 'ACTIVA'
        if (!EstatusCuentaEnum.ACTIVA.getCodigo().equalsIgnoreCase(cuenta.getEstatus())) {
            throw new CuentaInactivaException(
                    "La cuenta bancaria " + numeroCuenta + " no se encuentra activa (estatus actual: " + cuenta.getEstatus() + ").");
        }

        return cuenta;
    }

    private Optional<ProductDTO> buscarProductoEnCatalogo(Integer idServicio, Integer idProducto) {
        try {
            ProductListResponse catalogo = productService.obtenerListaProductos();
            if (catalogo != null && catalogo.getData() != null) {
                String idServStr = String.valueOf(idServicio);
                String idProdStr = String.valueOf(idProducto);
                return catalogo.getData().stream()
                        .filter(p -> idServStr.equals(p.getServiceId()) && idProdStr.equals(p.getId()))
                        .findFirst();
            }
        } catch (Exception e) {
            log.warn("No se pudo consultar el catálogo en caché para enriquecer datos del producto ({}-{}): {}",
                    idServicio, idProducto, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Aplica la regla de negocio para Recargas (tipoFront = 1) vs Pago de Servicios con Recibo (tipoFront = 2):
     * <ul>
     *   <li>Si no se envió ni teléfono ni referencia → lanza {@link ValidacionException} (400).</li>
     *   <li>Si {@code tipoFront = 1} (Recarga de Tiempo Aire / Telefonía):
     *       {@code telefono} (10 dígitos) es obligatorio; en sendTx {@code telefono} lleva el celular y {@code referencia} va vacío {@code ""}.</li>
     *   <li>Si {@code tipoFront = 2} (Pago de Servicios con Recibo):
     *       {@code referencia} es obligatoria; en sendTx {@code referencia} lleva el recibo y {@code telefono} se fija en {@code "1111111111"}
     *       (o {@code "#1111111111"} si {@code idCatTipoServicio} es 10 u 11).</li>
     * </ul>
     */
    private DatosDestinoTx resolverTelefonoYReferenciaSegunTipoFront(PagoServicioRequestDTO request, ProductDTO producto) {
        boolean tieneTelefono = request.getTelefono() != null && !request.getTelefono().isBlank();
        boolean tieneReferencia = request.getReferencia() != null && !request.getReferencia().isBlank();

        if (!tieneTelefono && !tieneReferencia) {
            throw new ValidacionException(MENSAJE_FALTA_TELEFONO_O_REFERENCIA);
        }

        String idCatTipoServicio = producto != null ? producto.getCategoryId() : null;
        boolean esCat10u11 = "10".equals(idCatTipoServicio) || "11".equals(idCatTipoServicio);

        // Determinar tipoFront desde el catálogo, o inferirlo si el catálogo no está cargado
        String tipoFront;
        if (producto != null && producto.getFrontType() != null && !producto.getFrontType().isBlank()) {
            tipoFront = producto.getFrontType().trim();
        } else {
            tipoFront = (tieneReferencia) ? "2" : "1";
        }

        // Caso 1: Recarga de Tiempo Aire / Telefonía (tipoFront = 1)
        if ("1".equals(tipoFront)) {
            if (!tieneTelefono) {
                throw new ValidacionException(
                        "Para recargas de tiempo aire o telefonía (tipoFront = 1) el número de teléfono de 10 dígitos es obligatorio");
            }
            String telefonoEnviar = esCat10u11 ? TELEFONO_CAT_10_11 : request.getTelefono().trim();
            return new DatosDestinoTx(tipoFront, telefonoEnviar, "");
        }

        // Caso 2: Pago de Servicios con Recibo (CFE, Agua, Telmex, etc. / tipoFront = 2)
        if (!tieneReferencia) {
            throw new ValidacionException(
                    "Para el pago de servicios con recibo (tipoFront = 2) el número de referencia es obligatorio");
        }
        String telefonoComodin = esCat10u11 ? TELEFONO_CAT_10_11 : TELEFONO_TIPO_FRONT_2;
        return new DatosDestinoTx("2", telefonoComodin, request.getReferencia().trim());
    }

    private record DatosDestinoTx(String tipoFront, String telefono, String referencia) {}

    private String obtenerBearerTokenActivo() {
        Optional<GestoPagoToken> tokenOpt = tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo);
        if (tokenOpt.isEmpty() || tokenOpt.get().getToken() == null || tokenOpt.get().getToken().isBlank()) {
            log.info("No se encontró token activo de GestoPago en BD, solicitando renovación...");
            tokenService.renovarToken();
            tokenOpt = tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo);
        }

        String jwt = tokenOpt.map(GestoPagoToken::getToken)
                .filter(t -> !t.isBlank())
                .orElseThrow(() -> new GestoPagoException(
                        "No fue posible obtener un token de autenticación activo para GestoPago", 401));

        return jwt.startsWith("Bearer ") ? jwt : "Bearer " + jwt;
    }

    private String enviarTransaccionAGestoPago(String random, String signed, String bearerToken) {
        try {
            String response = gestoPagoTxClient.sendTx(random, signed, bearerToken, apiKey);
            if (esRespuestaTokenExpirado(response)) {
                log.info("El token JWT de GestoPago expiró según respuesta del servidor. Renovando y reintentando...");
                tokenService.renovarToken();
                String nuevoBearer = obtenerBearerTokenActivo();
                response = gestoPagoTxClient.sendTx(random, signed, nuevoBearer, apiKey);
            }
            return response;
        } catch (FeignException.Forbidden | FeignException.Unauthorized e) {
            log.warn("Token expirado o rechazado ({}). Renovando token de GestoPago y reintentando sendTx...", e.status());
            try {
                tokenService.renovarToken();
                String nuevoBearer = obtenerBearerTokenActivo();
                return gestoPagoTxClient.sendTx(random, signed, nuevoBearer, apiKey);
            } catch (Exception retryEx) {
                log.error("Fallo de autenticación con GestoPago tras reintento", retryEx);
                throw new GestoPagoException("Error de autenticación con el proveedor de servicios GestoPago", retryEx, 401);
            }
        } catch (FeignException.GatewayTimeout | FeignException.ServiceUnavailable e) {
            log.error("Timeout al conectar con GestoPago sendTx.do", e);
            throw new PagoServicioProveedorException("82",
                    "Tiempo de espera agotado al comunicarse con el proveedor de servicios. No se realizó ningún cargo a su cuenta.", 504);
        } catch (FeignException e) {
            log.error("Error HTTP ({}) al invocar sendTx.do en GestoPago", e.status(), e);
            throw new GestoPagoException("Error de comunicación con el proveedor de pagos GestoPago", e, e.status());
        }
    }

    private boolean esRespuestaTokenExpirado(String rawResponse) {
        return rawResponse != null && rawResponse.contains("\"EXPIRED\"");
    }

    private GestoPagoTxResponse parsearRespuestaTxXml(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new PagoServicioProveedorException("ERR", "El proveedor de pagos devolvió una respuesta vacía");
        }
        String trimmed = rawResponse.trim();
        if (esRespuestaTokenExpirado(trimmed)) {
            throw new GestoPagoException("El token de sesión con el proveedor GestoPago ha expirado", 401);
        }
        try {
            return xmlMapper.readValue(trimmed, GestoPagoTxResponse.class);
        } catch (Exception e) {
            log.error("Error al parsear respuesta XML de GestoPago sendTx: {}", trimmed, e);
            throw new GestoPagoException("Error al procesar la respuesta XML del proveedor de pagos", e, 500);
        }
    }

    private String generarFolioTicketUnico(LocalDateTime fechaOperacion) {
        String folio;
        do {
            folio = "FT-" + fechaOperacion.format(HORA_LOCAL_FORMATTER) + "-"
                    + AesGestoPagoUtil.generarCadenaAlfanumerica(4).toUpperCase();
        } while (pagoServicioRepository.existsByFolioTicket(folio));
        return folio;
    }

    @Override
    @Transactional(readOnly = true)
    public HistorialPagosResponseDTO consultarHistorial(HistorialPagosRequestDTO request) {
        log.info("Consultando historial de pagos para cuenta={}", request.getNumeroCuenta());

        // 1. Validar existencia de la cuenta bancaria
        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(request.getNumeroCuenta())
                .orElseThrow(() -> new CuentaNoEncontradaException(request.getNumeroCuenta()));

        // 2. Obtener lista de pagos ordenados descendente por fecha
        List<PagoServicio> lista = pagoServicioRepository.findByNumeroCuentaOrderByFechaOperacionDesc(cuenta.getNumeroCuenta());

        // 3. Mapear entidades a ItemHistorialPagoDTO
        List<ItemHistorialPagoDTO> items = lista.stream()
                .map(this::mapToItemHistorialDTO)
                .toList();

        // 4. Construir respuesta
        HistorialPagosResponseDTO response = HistorialPagosResponseDTO.builder()
                .numeroCuenta(cuenta.getNumeroCuenta())
                .totalMovimientos(items.size())
                .pagos(items)
                .build();
        response.setCodigo(0);
        response.setMensaje(items.isEmpty()
                ? "No se encontraron movimientos de pago de servicios para la cuenta indicada"
                : "Historial de pagos consultado exitosamente");

        return response;
    }

    private ItemHistorialPagoDTO mapToItemHistorialDTO(PagoServicio p) {
        return ItemHistorialPagoDTO.builder()
                .idPago(p.getId())
                .folioTicket(p.getFolioTicket())
                .numeroAutorizacion(p.getNumAutorizacion())
                .idTxGestopago(p.getIdTxGestopago())
                .idServicio(p.getIdServicio())
                .servicio(p.getNombreServicio())
                .idProducto(p.getIdProducto())
                .producto(p.getNombreProducto())
                .telefono(p.getTelefono())
                .referencia(p.getReferencia())
                .monto(p.getMontoPagado())
                .comision(p.getComision())
                .saldoAnterior(p.getSaldoAnterior())
                .saldoRestante(p.getSaldoRestante())
                .concepto(p.getConcepto())
                .fechaOperacion(p.getFechaOperacion())
                .build();
    }
}
