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
import com.proyecto.servicios.service.ComprobanteEmailService;
import com.proyecto.servicios.service.GestoPagoProductService;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.service.PagoServicioService;
import com.proyecto.servicios.service.Soporte2FAService;
import com.proyecto.servicios.util.AesGestoPagoUtil;
import feign.FeignException;
import feign.RetryableException;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementación del servicio bancario de Pago de Servicios con GestoPago (PuntoRed).
 * Hereda el soporte de autenticación 2FA temporal de {@link Soporte2FAService}.
 * <p>
 * Garantías de arquitectura bancaria:
 * <ul>
 *   <li>Validación estricta de existencia de producto y precio en catálogo (anti-manipulación de montos).</li>
 *   <li>Control de concurrencia por cuenta en memoria ({@link ReentrantLock}) y en BD ({@code PESSIMISTIC_WRITE}).</li>
 *   <li>Prevención de cargos duplicados mediante ventana de idempotencia local y consumo atómico de 2FA.</li>
 *   <li>Deducción de saldo post-confirmación ({@code CODIGO = 01}) con mecanismo de conciliación ({@code verifyTx.do}) ante timeouts.</li>
 *   <li>Sanitización de logs y protección de credenciales/tokens sin exposición en código ni respuestas HTTP.</li>
 * </ul>
 */
@Service
@Slf4j
public class PagoServicioServiceImpl extends Soporte2FAService implements PagoServicioService {

    private static final DateTimeFormatter HORA_LOCAL_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String TELEFONO_TIPO_FRONT_2 = "1111111111";
    private static final String TELEFONO_CAT_10_11 = "#1111111111";
    private static final String CODIGO_EXITO_GESTOPAGO = "01";
    private static final String CODIGO_TIMEOUT_PROVEEDOR = "82";
    private static final BigDecimal MONTO_MAXIMO_PERMITIDO = new BigDecimal("50000.00");
    private static final int VENTANA_IDEMPOTENCIA_MINUTOS = 2;
    private static final String MENSAJE_FALTA_TELEFONO_O_REFERENCIA =
            "Debe ingresar el número de teléfono para recargas o el número de referencia para pago de servicios";

    /**
     * Control de concurrencia a nivel de aplicación para rechazar solicitudes simultáneas sobre la misma cuenta.
     */
    private final ConcurrentHashMap<String, ReentrantLock> locksPorCuenta = new ConcurrentHashMap<>();

    private final CuentaRepository cuentaRepository;
    private final SaldoRepository saldoRepository;
    private final PagoServicioRepository pagoServicioRepository;
    private final GestoPagoTokenService tokenService;
    private final GestoPagoProductService productService;
    private final GestoPagoTxClient gestoPagoTxClient;
    private final AesGestoPagoUtil aesGestoPagoUtil;
    private final ObjectMapper objectMapper;
    private final XmlMapper xmlMapper;
    private final ComprobanteEmailService comprobanteEmailService;

    @Value("${gestopago.api.secret-key:}")
    private String secretKey;

    @Value("${gestopago.api.iv:}")
    private String iv;

    @Value("${gestopago.api.unidad:083}")
    private String unidad;

    @Value("${gestopago.api.key:}")
    private String apiKey;

    @Value("${gestopago.api.token:}")
    private String configuredBearerToken;

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
            @Autowired(required = false) RedisTemplate<String, String> redisTemplate,
            @Autowired(required = false) ComprobanteEmailService comprobanteEmailService) {
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
        this.comprobanteEmailService = comprobanteEmailService;
    }

    @Override
    @Transactional(readOnly = true)
    public GenerarCodigo2FAResponseDTO generarCodigo2FA(GenerarCodigo2FARequestDTO request) {
        log.info("Generando código 2FA temporal para la cuenta: {}", enmascararCuenta(request.getNumeroCuenta()));

        Cuenta cuenta = validarCuentaYClienteActivo(request.getNumeroCuenta(), false);
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
        String cuentaMask = enmascararCuenta(request.getNumeroCuenta());
        log.info("Iniciando proceso de pago de servicio para cuenta={}, idServicio={}, idProducto={}, monto={}",
                cuentaMask, request.getIdServicio(), request.getIdProducto(), request.getMonto());

        // Validación inicial de presencia de teléfono o referencia y restricciones de monto
        boolean tieneTelefono = request.getTelefono() != null && !request.getTelefono().isBlank();
        boolean tieneReferencia = request.getReferencia() != null && !request.getReferencia().isBlank();
        if (!tieneTelefono && !tieneReferencia) {
            throw new ValidacionException(MENSAJE_FALTA_TELEFONO_O_REFERENCIA);
        }
        validarRestriccionesGeneralesMonto(request.getMonto());

        // Control de concurrencia por cuenta para bloquear solicitudes simultáneas
        ReentrantLock lockCuenta = locksPorCuenta.computeIfAbsent(request.getNumeroCuenta(), k -> new ReentrantLock());
        if (!lockCuenta.tryLock()) {
            log.warn("Solicitud simultánea bloqueada por control de concurrencia para la cuenta {}", cuentaMask);
            throw new PagoServicioProveedorException("CONC",
                    "Ya existe una operación de pago en curso para esta cuenta. Por favor espere a que concluya antes de enviar otra solicitud.",
                    409);
        }

        try {
            // Paso 1, 2 y 3: Validar existencia de cuenta (con bloqueo pesimista en BD), Cliente ACTIVO y Cuenta ACTIVA
            Cuenta cuenta = validarCuentaYClienteActivo(request.getNumeroCuenta(), true);

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

            // Paso 6: Verificar existencia del producto en el catálogo, validar precio/monto y resolver destino
            Optional<ProductDTO> productoCatalogo = validarProductoYMontoEnCatalogo(
                    request.getIdServicio(), request.getIdProducto(), request.getMonto());
            DatosDestinoTx datosDestino = resolverTelefonoYReferenciaSegunTipoFront(request, productoCatalogo.orElse(null));

            // Paso 6.1: Prevención de cargos duplicados en ventana de idempotencia local
            verificarOperacionDuplicadaReciente(
                    cuenta.getNumeroCuenta(),
                    request.getIdServicio(),
                    request.getIdProducto(),
                    datosDestino.referencia(),
                    datosDestino.telefono(),
                    request.getMonto()
            );

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
                log.error("Error al serializar payload JSON para GestoPago");
                throw new GestoPagoException("Error al construir el payload de pago para el proveedor", e, 500);
            }

            String random10 = aesGestoPagoUtil.generarRandom10();
            String signed = (secretKey != null && !secretKey.isBlank() && iv != null && !iv.isBlank())
                    ? AesGestoPagoUtil.encrypt(jsonPayload, secretKey, iv)
                    : aesGestoPagoUtil.encrypt(jsonPayload);
            String bearerToken = obtenerBearerTokenActivo();

            // Paso 8: Envío a GestoPago sendTx.do con soporte de conciliación ante timeouts
            GestoPagoTxResponse txResponse = ejecutarYConciliarTransaccionGestoPago(
                    random10, signed, bearerToken, upc, cuentaMask);

            // Paso 9: MOMENTO CRÍTICO DE DEDUCCIÓN DEL DINERO
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
                        codigoProveedor, textoProveedor, cuentaMask);
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

            // Consumir el código 2FA temporal tras operación exitosa (un solo uso)
            consumirCodigo2FA(request.getNumeroCuenta());

            // Enviar comprobante oficial por correo electrónico vía Brevo (fail-safe)
            if (comprobanteEmailService != null) {
                comprobanteEmailService.enviarComprobantePago(cuenta, cuenta.getCliente(), pagoServicio);
            }

            log.info("Pago de servicio exitoso. Cuenta={}, ID_TX={}, Autorización={}, Folio={}, SaldoRestante={}",
                    cuentaMask, idTx, numAutorizacion, folioTicket, nuevoSaldo);

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
        } finally {
            lockCuenta.unlock();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Métodos Privados de Validación, Conciliación y Apoyo
    // ─────────────────────────────────────────────────────────────────────────

    private void validarRestriccionesGeneralesMonto(BigDecimal monto) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidacionException("El monto a pagar debe ser mayor a cero");
        }
        if (monto.scale() > 2) {
            throw new ValidacionException("El monto a pagar no puede tener más de 2 decimales");
        }
        if (monto.compareTo(MONTO_MAXIMO_PERMITIDO) > 0) {
            throw new ValidacionException(
                    String.format("El monto solicitado ($%s) excede el límite máximo permitido por operación ($%s)",
                            monto.toPlainString(), MONTO_MAXIMO_PERMITIDO.toPlainString()));
        }
    }

    private Cuenta validarCuentaYClienteActivo(String numeroCuenta, boolean bloquearParaEscritura) {
        // Paso 1: Validar existencia de la cuenta (aplicando PESSIMISTIC_WRITE si es operación de cargo)
        Optional<Cuenta> cuentaOpt = bloquearParaEscritura
                ? cuentaRepository.findByNumeroCuentaForUpdate(numeroCuenta)
                        .or(() -> cuentaRepository.findByNumeroCuenta(numeroCuenta))
                : cuentaRepository.findByNumeroCuenta(numeroCuenta);

        Cuenta cuenta = cuentaOpt.orElseThrow(() -> new CuentaNoEncontradaException(
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

    /**
     * Verifica que el producto exista en el catálogo de GestoPago y que el monto solicitado
     * corresponda con el precio oficial del catálogo (cuando el producto tiene precio fijo > 0),
     * evitando que se procesen productos inexistentes o montos manipulados.
     */
    private Optional<ProductDTO> validarProductoYMontoEnCatalogo(
            Integer idServicio, Integer idProducto, BigDecimal montoSolicitado) {
        ProductListResponse catalogo = null;
        try {
            catalogo = productService.obtenerListaProductos();
        } catch (Exception e) {
            log.warn("No se pudo consultar el catálogo en caché para validar el producto ({}-{})",
                    idServicio, idProducto);
        }

        if (catalogo != null && catalogo.getData() != null && !catalogo.getData().isEmpty()) {
            String idServStr = String.valueOf(idServicio);
            String idProdStr = String.valueOf(idProducto);

            ProductDTO producto = catalogo.getData().stream()
                    .filter(p -> idServStr.equals(p.getServiceId()) && idProdStr.equals(p.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ValidacionException(
                            String.format("El producto solicitado (idServicio=%d, idProducto=%d) no existe en el catálogo autorizado de GestoPago",
                                    idServicio, idProducto)));

            // Validar que el monto coincida con el precio del catálogo cuando el producto tiene precio fijo (> 0)
            if (producto.getPrice() != null && !producto.getPrice().isBlank()) {
                try {
                    BigDecimal precioCatalogo = new BigDecimal(producto.getPrice().trim());
                    if (precioCatalogo.compareTo(BigDecimal.ZERO) > 0
                            && montoSolicitado.compareTo(precioCatalogo) != 0) {
                        throw new ValidacionException(
                                String.format("El monto solicitado ($%s) no corresponde con el precio autorizado en el catálogo para el producto '%s' ($%s)",
                                        montoSolicitado.toPlainString(), producto.getName(), precioCatalogo.toPlainString()));
                    }
                } catch (NumberFormatException ignored) {
                    // Si el campo precio en el XML no es numérico, se mantiene la validación de rango general
                }
            }

            return Optional.of(producto);
        }

        return Optional.empty();
    }

    private void verificarOperacionDuplicadaReciente(
            String numeroCuenta,
            Integer idServicio,
            Integer idProducto,
            String referencia,
            String telefono,
            BigDecimal monto) {
        LocalDateTime limiteVentana = LocalDateTime.now().minusMinutes(VENTANA_IDEMPOTENCIA_MINUTOS);
        boolean existeDuplicada = pagoServicioRepository
                .existsByNumeroCuentaAndIdServicioAndIdProductoAndReferenciaAndTelefonoAndMontoPagadoAndFechaOperacionAfter(
                        numeroCuenta, idServicio, idProducto, referencia, telefono, monto, limiteVentana);
        if (existeDuplicada) {
            log.warn("Cargo duplicado prevenido por ventana de idempotencia local para cuenta={}, servicio={}, producto={}",
                    enmascararCuenta(numeroCuenta), idServicio, idProducto);
            throw new PagoServicioProveedorException("06",
                    "Operación duplicada detectada: ya se registró un pago idéntico para esta cuenta, servicio, destino y monto en los últimos minutos.",
                    409);
        }
    }

    /**
     * Aplica la regla de negocio para Recargas (tipoFront = 1) vs Pago de Servicios con Recibo (tipoFront = 2).
     */
    private DatosDestinoTx resolverTelefonoYReferenciaSegunTipoFront(PagoServicioRequestDTO request, ProductDTO producto) {
        boolean tieneTelefono = request.getTelefono() != null && !request.getTelefono().isBlank();
        boolean tieneReferencia = request.getReferencia() != null && !request.getReferencia().isBlank();

        if (!tieneTelefono && !tieneReferencia) {
            throw new ValidacionException(MENSAJE_FALTA_TELEFONO_O_REFERENCIA);
        }

        String idCatTipoServicio = producto != null ? producto.getCategoryId() : null;
        boolean esCat10u11 = "10".equals(idCatTipoServicio) || "11".equals(idCatTipoServicio);

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
                .orElseGet(() -> (configuredBearerToken != null && !configuredBearerToken.isBlank())
                        ? configuredBearerToken
                        : null);

        if (jwt == null || jwt.isBlank()) {
            throw new GestoPagoException(
                    "No fue posible obtener un token de autenticación activo para GestoPago", 401);
        }

        return jwt.startsWith("Bearer ") ? jwt : "Bearer " + jwt;
    }

    /**
     * Ejecuta la transacción en GestoPago (sendTx.do) y, en caso de timeout de red o respuesta indeterminada
     * (código 82), NO asume que la operación falló ni reintenta ciegamente el cargo, sino que invoca el
     * protocolo de conciliación {@code verifyTx.do} usando el mismo identificador único {@code upc}.
     */
    private GestoPagoTxResponse ejecutarYConciliarTransaccionGestoPago(
            String random, String signed, String bearerToken, String upc, String cuentaMask) {
        try {
            String xmlRawResponse = enviarTransaccionAGestoPago(random, signed, bearerToken);
            GestoPagoTxResponse txResponse = parsearRespuestaTxXml(xmlRawResponse);

            String codigo = txResponse.getMensaje() != null && txResponse.getMensaje().getCodigo() != null
                    ? txResponse.getMensaje().getCodigo().trim()
                    : "";

            // Si el proveedor reporta código 82 (Timeout del operador / estado indeterminado), intentar conciliar con verifyTx.do
            if (CODIGO_TIMEOUT_PROVEEDOR.equals(codigo)) {
                log.warn("GestoPago devolvió CODIGO=82 (estado indeterminado) para UPC={}. Iniciando conciliación con verifyTx.do...", upc);
                return conciliarTransaccionConProveedor(random, signed, bearerToken, upc, cuentaMask)
                        .orElse(txResponse);
            }

            return txResponse;
        } catch (FeignException.GatewayTimeout | FeignException.ServiceUnavailable | RetryableException timeoutEx) {
            log.error("Timeout de comunicación en sendTx.do para cuenta={} y UPC={}. Ejecutando protocolo de conciliación verifyTx.do sin reintentar cargo...",
                    cuentaMask, upc);
            Optional<GestoPagoTxResponse> conciliada = conciliarTransaccionConProveedor(random, signed, bearerToken, upc, cuentaMask);
            if (conciliada.isPresent()) {
                return conciliada.get();
            }
            throw new PagoServicioProveedorException(
                    CODIGO_TIMEOUT_PROVEEDOR,
                    String.format("Tiempo de espera agotado al comunicarse con el proveedor de servicios y no fue posible confirmar el estado final (Folio UPC de conciliación: %s). Por seguridad no se realizó ningún cargo ni debe reintentarse ciegamente.", upc),
                    504
            );
        }
    }

    private String enviarTransaccionAGestoPago(String random, String signed, String bearerToken) {
        try {
            String response = gestoPagoTxClient.sendTx(random, signed, bearerToken, apiKey);
            if (esRespuestaTokenExpirado(response)) {
                log.info("El token JWT de GestoPago expiró antes de procesar la transacción. Renovando y reenviando con el mismo UPC...");
                tokenService.renovarToken();
                String nuevoBearer = obtenerBearerTokenActivo();
                response = gestoPagoTxClient.sendTx(random, signed, nuevoBearer, apiKey);
            }
            return response;
        } catch (FeignException.Forbidden | FeignException.Unauthorized e) {
            log.warn("Token expirado o rechazado ({}). Renovando token de GestoPago y reintentando con el mismo UPC...", e.status());
            try {
                tokenService.renovarToken();
                String nuevoBearer = obtenerBearerTokenActivo();
                return gestoPagoTxClient.sendTx(random, signed, nuevoBearer, apiKey);
            } catch (Exception retryEx) {
                log.error("Fallo de autenticación con GestoPago tras renovación de token");
                throw new GestoPagoException("Error de autenticación con el proveedor de servicios GestoPago", retryEx, 401);
            }
        } catch (FeignException.GatewayTimeout | FeignException.ServiceUnavailable | RetryableException e) {
            throw e;
        } catch (FeignException e) {
            log.error("Error HTTP ({}) al invocar sendTx.do en GestoPago", e.status());
            throw new GestoPagoException("Error de comunicación con el proveedor de pagos GestoPago", e, e.status());
        }
    }

    /**
     * Consulta en GestoPago (verifyTx.do) el estado real de una transacción cuyo resultado quedó
     * indeterminado por timeout, utilizando exactamente el mismo payload cifrado (mismo UPC).
     */
    private Optional<GestoPagoTxResponse> conciliarTransaccionConProveedor(
            String random, String signed, String bearerToken, String upc, String cuentaMask) {
        try {
            String verifyXml = gestoPagoTxClient.verifyTx(random, signed, bearerToken, apiKey);
            if (verifyXml != null && !verifyXml.isBlank() && !esRespuestaTokenExpirado(verifyXml)) {
                GestoPagoTxResponse verifyResponse = parsearRespuestaTxXml(verifyXml);
                String codigoVerif = verifyResponse.getMensaje() != null && verifyResponse.getMensaje().getCodigo() != null
                        ? verifyResponse.getMensaje().getCodigo().trim()
                        : "";
                log.info("Resultado de conciliación verifyTx.do para cuenta={}, UPC={}: CODIGO={}",
                        cuentaMask, upc, codigoVerif);
                return Optional.of(verifyResponse);
            }
        } catch (Exception ex) {
            log.error("No fue posible completar la conciliación automática verifyTx.do para cuenta={}, UPC={}",
                    cuentaMask, upc);
        }
        return Optional.empty();
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
            log.error("Error al parsear respuesta XML del proveedor GestoPago");
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

    private String enmascararCuenta(String numeroCuenta) {
        if (numeroCuenta == null || numeroCuenta.length() < 4) {
            return "****";
        }
        return "************" + numeroCuenta.substring(numeroCuenta.length() - 4);
    }

    @Override
    @Transactional(readOnly = true)
    public HistorialPagosResponseDTO consultarHistorial(HistorialPagosRequestDTO request) {
        log.info("Consultando historial de pagos para cuenta={}", enmascararCuenta(request.getNumeroCuenta()));

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
