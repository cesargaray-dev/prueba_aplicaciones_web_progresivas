package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.proyecto.servicios.util.AesGestoPagoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagoServicioServiceImplTest {

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private SaldoRepository saldoRepository;

    @Mock
    private PagoServicioRepository pagoServicioRepository;

    @Mock
    private GestoPagoTokenService tokenService;

    @Mock
    private GestoPagoProductService productService;

    @Mock
    private GestoPagoTxClient gestoPagoTxClient;

    @Mock
    private com.proyecto.servicios.service.ComprobanteEmailService comprobanteEmailService;

    private AesGestoPagoUtil aesGestoPagoUtil;
    private ObjectMapper objectMapper;
    private PagoServicioServiceImpl pagoServicioService;

    private static final String SECRET_KEY = "HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=";
    private static final String IV = "p5ldmBPdd/9pjC0bDC/nSg==";
    private static final String NUMERO_CUENTA = "1234567890123456";

    private Cliente clienteActivo;
    private Cuenta cuentaActiva;
    private Saldo saldoCuenta;

    @BeforeEach
    void setUp() {
        aesGestoPagoUtil = new AesGestoPagoUtil();
        ReflectionTestUtils.setField(aesGestoPagoUtil, "defaultSecretKey", SECRET_KEY);
        ReflectionTestUtils.setField(aesGestoPagoUtil, "defaultIv", IV);

        objectMapper = new ObjectMapper();

        pagoServicioService = new PagoServicioServiceImpl(
                cuentaRepository,
                saldoRepository,
                pagoServicioRepository,
                tokenService,
                productService,
                gestoPagoTxClient,
                aesGestoPagoUtil,
                objectMapper,
                null,
                comprobanteEmailService
        );

        ReflectionTestUtils.setField(pagoServicioService, "secretKey", SECRET_KEY);
        ReflectionTestUtils.setField(pagoServicioService, "iv", IV);
        ReflectionTestUtils.setField(pagoServicioService, "unidad", "083");
        ReflectionTestUtils.setField(pagoServicioService, "apiKey", "TEST_API_KEY");
        ReflectionTestUtils.setField(pagoServicioService, "idDistribuidor", 83);
        ReflectionTestUtils.setField(pagoServicioService, "codigoDispositivo", "GPS83-TPV-17");

        clienteActivo = new Cliente();
        clienteActivo.setId(1L);
        clienteActivo.setNombre("Juan");
        clienteActivo.setApellidoPaterno("Perez");
        clienteActivo.setActivo(true);

        saldoCuenta = new Saldo();
        saldoCuenta.setId(10L);
        saldoCuenta.setSaldo(new BigDecimal("1000.00"));
        saldoCuenta.setConcepto("SALDO INICIAL");

        cuentaActiva = new Cuenta();
        cuentaActiva.setId(5L);
        cuentaActiva.setNumeroCuenta(NUMERO_CUENTA);
        cuentaActiva.setEstatus(EstatusCuentaEnum.ACTIVA.getCodigo());
        cuentaActiva.setCliente(clienteActivo);
        cuentaActiva.setSaldo(saldoCuenta);
        saldoCuenta.setCuenta(cuentaActiva);
    }

    @Test
    void aesGestoPagoUtil_cifraYDescifraCorrectamente_yRandom10Caracteres() {
        String random = aesGestoPagoUtil.generarRandom10();
        assertNotNull(random);
        assertEquals(10, random.length());
        assertTrue(random.matches("^[A-Za-z0-9]{10}$"));

        String json = "{\"idProducto\":205,\"idServicio\":76,\"telefono\":\"1111111111\",\"montoPago\":150.50}";
        String encrypted = AesGestoPagoUtil.encrypt(json, SECRET_KEY, IV);
        assertNotNull(encrypted);
        assertNotEquals(json, encrypted);

        String decrypted = AesGestoPagoUtil.decrypt(encrypted, SECRET_KEY, IV);
        assertEquals(json, decrypted);
    }

    @Test
    void procesarPagoServicio_tipoFront2_exitoso_codigo01_fijaTelefono1111111111YEnviaReferencia() throws Exception {
        // Arrange: generar código 2FA temporal para la cuenta
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));
        GenerarCodigo2FAResponseDTO gen2FA = pagoServicioService.generarCodigo2FA(
                new GenerarCodigo2FARequestDTO(NUMERO_CUENTA));
        assertNotNull(gen2FA.getCodigo2FA());
        assertTrue(gen2FA.getCodigo2FA().matches("^\\d{6}$"));

        ProductDTO izziProduct = ProductDTO.builder()
                .id("205")
                .serviceId("76")
                .service("IZZI Telecom")
                .name("Izzi Telecom")
                .frontType("2")
                .categoryId("5")
                .build();
        ProductListResponse productListResponse = new ProductListResponse();
        productListResponse.setData(List.of(izziProduct));
        when(productService.obtenerListaProductos()).thenReturn(productListResponse);

        GestoPagoToken token = new GestoPagoToken();
        token.setToken("jwt-token-activo");
        when(tokenService.obtenerTokenActivo(83, "GPS83-TPV-17")).thenReturn(Optional.of(token));

        String xmlSuccess = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<RESPONSE>" +
                "  <ID_TX>291620941</ID_TX>" +
                "  <NUM_AUTORIZACION>100074211</NUM_AUTORIZACION>" +
                "  <SALDO>154997.91</SALDO>" +
                "  <COMISION>6.00</COMISION>" +
                "  <FECHA>25/Feb/2022 18:59:34</FECHA>" +
                "  <MONTO>250.00</MONTO>" +
                "  <MENSAJE>" +
                "    <CODIGO>01</CODIGO>" +
                "    <TEXTO>Operacion realizada con exito</TEXTO>" +
                "    <REFERENCIA>25720381</REFERENCIA>" +
                "  </MENSAJE>" +
                "</RESPONSE>";

        ArgumentCaptor<String> randomCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> signedCaptor = ArgumentCaptor.forClass(String.class);
        when(gestoPagoTxClient.sendTx(randomCaptor.capture(), signedCaptor.capture(), eq("Bearer jwt-token-activo"), eq("TEST_API_KEY")))
                .thenReturn(xmlSuccess);
        when(pagoServicioRepository.existsByFolioTicket(anyString())).thenReturn(false);

        PagoServicioRequestDTO request = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .idServicio(76)
                .idProducto(205)
                .monto(new BigDecimal("250.00"))
                .referencia("25720381") // Solo referencia para pago de recibo (tipoFront=2)
                .codigo2FA(gen2FA.getCodigo2FA())
                .build();

        // Act
        PagoServicioResponseDTO response = pagoServicioService.procesarPagoServicio(request);

        // Assert
        assertNotNull(response);
        assertEquals(0, response.getCodigo());
        assertEquals(291620941L, response.getIdTransaccion());
        assertEquals("100074211", response.getNumeroAutorizacion());
        assertEquals(new BigDecimal("250.00"), response.getMontoPagado());
        assertEquals(new BigDecimal("6.00"), response.getComision());
        assertEquals(new BigDecimal("750.00"), response.getSaldoRestanteCuenta());
        assertEquals(new BigDecimal("750.00"), saldoCuenta.getSaldo());

        // Verificar que random tiene 10 caracteres, telefono es "1111111111" y referencia es "25720381"
        assertEquals(10, randomCaptor.getValue().length());
        String decryptedJson = AesGestoPagoUtil.decrypt(signedCaptor.getValue(), SECRET_KEY, IV);
        GestoPagoTxPayload sentPayload = objectMapper.readValue(decryptedJson, GestoPagoTxPayload.class);
        assertEquals("1111111111", sentPayload.getTelefono());
        assertEquals("25720381", sentPayload.getReferencia());
        assertEquals("083", sentPayload.getUnidad());
        assertEquals(14, sentPayload.getHoraLocal().length());

        verify(saldoRepository, times(1)).save(saldoCuenta);
        verify(pagoServicioRepository, times(1)).save(any(PagoServicio.class));
    }

    @Test
    void procesarPagoServicio_recargaTipoFront1_exitoso_enviaTelefonoCelularYReferenciaVacia() throws Exception {
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));
        pagoServicioService.propagarCodigo2FA(NUMERO_CUENTA, "778899");

        ProductDTO recargaTelcel = ProductDTO.builder()
                .id("582")
                .serviceId("133")
                .service("Telcel")
                .name("Recarga $100")
                .frontType("1")
                .categoryId("3")
                .build();
        ProductListResponse productListResponse = new ProductListResponse();
        productListResponse.setData(List.of(recargaTelcel));
        when(productService.obtenerListaProductos()).thenReturn(productListResponse);

        GestoPagoToken token = new GestoPagoToken();
        token.setToken("jwt-token-activo");
        when(tokenService.obtenerTokenActivo(83, "GPS83-TPV-17")).thenReturn(Optional.of(token));

        String xmlSuccess = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<RESPONSE>" +
                "  <ID_TX>291437188</ID_TX>" +
                "  <NUM_AUTORIZACION>100024988</NUM_AUTORIZACION>" +
                "  <SALDO>157731.83</SALDO>" +
                "  <COMISION>0.00</COMISION>" +
                "  <FECHA>25/Feb/2022 18:15:25</FECHA>" +
                "  <MONTO>100.00</MONTO>" +
                "  <MENSAJE>" +
                "    <CODIGO>01</CODIGO>" +
                "    <TEXTO>Operacion realizada con exito</TEXTO>" +
                "    <REFERENCIA></REFERENCIA>" +
                "  </MENSAJE>" +
                "</RESPONSE>";

        ArgumentCaptor<String> signedCaptor = ArgumentCaptor.forClass(String.class);
        when(gestoPagoTxClient.sendTx(anyString(), signedCaptor.capture(), eq("Bearer jwt-token-activo"), eq("TEST_API_KEY")))
                .thenReturn(xmlSuccess);
        when(pagoServicioRepository.existsByFolioTicket(anyString())).thenReturn(false);

        PagoServicioRequestDTO request = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .idServicio(133)
                .idProducto(582)
                .monto(new BigDecimal("100.00"))
                .telefono("5512345678")
                .referencia("IGNORA_ESTA_REF") // En tipoFront=1 la referencia debe enviarse vacía ""
                .codigo2FA("778899")
                .build();

        PagoServicioResponseDTO response = pagoServicioService.procesarPagoServicio(request);

        assertNotNull(response);
        assertEquals(0, response.getCodigo());
        assertEquals(new BigDecimal("900.00"), response.getSaldoRestanteCuenta());

        String decryptedJson = AesGestoPagoUtil.decrypt(signedCaptor.getValue(), SECRET_KEY, IV);
        GestoPagoTxPayload sentPayload = objectMapper.readValue(decryptedJson, GestoPagoTxPayload.class);
        assertEquals("5512345678", sentPayload.getTelefono());
        assertEquals("", sentPayload.getReferencia());
    }

    @Test
    void procesarPagoServicio_sinTelefonoNiReferencia_lanzaValidacionException400() {
        PagoServicioRequestDTO request = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .idServicio(76)
                .idProducto(205)
                .monto(new BigDecimal("100.00"))
                .telefono(null)
                .referencia("   ")
                .codigo2FA("123456")
                .build();

        ValidacionException ex = assertThrows(ValidacionException.class,
                () -> pagoServicioService.procesarPagoServicio(request));

        assertEquals("Debe ingresar el número de teléfono para recargas o el número de referencia para pago de servicios",
                ex.getMessage());
        verifyNoInteractions(gestoPagoTxClient);
        verifyNoInteractions(saldoRepository);
    }

    @Test
    void procesarPagoServicio_proveedorFallaCodigo02_noDescuentaSaldoCuenta() {
        // Arrange
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));
        pagoServicioService.propagarCodigo2FA(NUMERO_CUENTA, "654321");

        GestoPagoToken token = new GestoPagoToken();
        token.setToken("jwt-token-activo");
        when(tokenService.obtenerTokenActivo(83, "GPS83-TPV-17")).thenReturn(Optional.of(token));

        String xmlError02 = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<RESPONSE>" +
                "  <NUM_AUTORIZACION>-1</NUM_AUTORIZACION>" +
                "  <MENSAJE>" +
                "    <CODIGO>02</CODIGO>" +
                "    <TEXTO>Saldo insuficiente</TEXTO>" +
                "  </MENSAJE>" +
                "</RESPONSE>";
        when(gestoPagoTxClient.sendTx(anyString(), anyString(), anyString(), anyString())).thenReturn(xmlError02);

        PagoServicioRequestDTO request = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .idServicio(76)
                .idProducto(205)
                .monto(new BigDecimal("300.00"))
                .referencia("25720381")
                .codigo2FA("654321")
                .build();

        // Act & Assert
        PagoServicioProveedorException ex = assertThrows(PagoServicioProveedorException.class,
                () -> pagoServicioService.procesarPagoServicio(request));

        assertEquals("02", ex.getCodigoProveedor());
        assertEquals(400, ex.getStatusCode());
        // El saldo del cliente debe permanecer intacto ($1000.00)
        assertEquals(new BigDecimal("1000.00"), saldoCuenta.getSaldo());
        verifyNoInteractions(saldoRepository);
        verify(pagoServicioRepository, never()).save(any());
    }

    @Test
    void procesarPagoServicio_saldoInsuficienteEnCuenta_lanzaExcepcionAntesDeLlamarProveedor() {
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));
        pagoServicioService.propagarCodigo2FA(NUMERO_CUENTA, "112233");

        PagoServicioRequestDTO request = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .idServicio(76)
                .idProducto(205)
                .monto(new BigDecimal("1500.00")) // Mayor a $1000.00
                .referencia("25720381")
                .codigo2FA("112233")
                .build();

        SaldoInsuficienteException ex = assertThrows(SaldoInsuficienteException.class,
                () -> pagoServicioService.procesarPagoServicio(request));

        assertTrue(ex.getMessage().contains("Saldo disponible: $1000.00, Monto a pagar: $1500.00"));
        verifyNoInteractions(gestoPagoTxClient);
        verifyNoInteractions(saldoRepository);
    }

    @Test
    void procesarPagoServicio_codigo2FAIncorrecto_lanzaAutenticacion2FAException() {
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));
        pagoServicioService.propagarCodigo2FA(NUMERO_CUENTA, "123456");

        PagoServicioRequestDTO request = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .idServicio(76)
                .idProducto(205)
                .monto(new BigDecimal("100.00"))
                .referencia("25720381")
                .codigo2FA("999999")
                .build();

        assertThrows(Autenticacion2FAException.class,
                () -> pagoServicioService.procesarPagoServicio(request));
        verifyNoInteractions(gestoPagoTxClient);
    }

    @Test
    void procesarPagoServicio_clienteInactivo_lanzaClienteInactivoException() {
        clienteActivo.setActivo(false);
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));

        PagoServicioRequestDTO request = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .idServicio(76)
                .idProducto(205)
                .monto(new BigDecimal("100.00"))
                .referencia("25720381")
                .codigo2FA("123456")
                .build();

        assertThrows(ClienteInactivoException.class,
                () -> pagoServicioService.procesarPagoServicio(request));
        verifyNoInteractions(gestoPagoTxClient);
    }

    @Test
    void procesarPagoServicio_cuentaInactiva_lanzaCuentaInactivaException() {
        cuentaActiva.setEstatus(EstatusCuentaEnum.INACTIVA.getCodigo());
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));

        PagoServicioRequestDTO request = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .idServicio(76)
                .idProducto(205)
                .monto(new BigDecimal("100.00"))
                .referencia("25720381")
                .codigo2FA("123456")
                .build();

        assertThrows(CuentaInactivaException.class,
                () -> pagoServicioService.procesarPagoServicio(request));
        verifyNoInteractions(gestoPagoTxClient);
    }

    @Test
    void consultarHistorial_cuentaExisteConPagos_retornaHistorialExitoso() {
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));

        PagoServicio p1 = new PagoServicio();
        p1.setId(10L);
        p1.setCuenta(cuentaActiva);
        p1.setNumeroCuenta(NUMERO_CUENTA);
        p1.setIdServicio(76);
        p1.setIdProducto(205);
        p1.setNombreServicio("TELCEL");
        p1.setNombreProducto("PAQUETE 20");
        p1.setTelefono("5512345678");
        p1.setReferencia("");
        p1.setMontoPagado(new BigDecimal("20.00"));
        p1.setComision(BigDecimal.ZERO);
        p1.setSaldoAnterior(new BigDecimal("100.00"));
        p1.setSaldoRestante(new BigDecimal("80.00"));
        p1.setIdTxGestopago(998877L);
        p1.setNumAutorizacion("AUTH123");
        p1.setFolioTicket("FT-20261023-ABCD");
        p1.setUpc("083");
        p1.setCodigoRespuesta("01");
        p1.setMensajeRespuesta("Exitoso");
        p1.setConcepto("RECARGA TELCEL");
        p1.setFechaOperacion(java.time.LocalDateTime.now());

        when(pagoServicioRepository.findByNumeroCuentaOrderByFechaOperacionDesc(NUMERO_CUENTA))
                .thenReturn(List.of(p1));

        HistorialPagosRequestDTO req = HistorialPagosRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .build();

        HistorialPagosResponseDTO response = pagoServicioService.consultarHistorial(req);

        assertNotNull(response);
        assertEquals(0, response.getCodigo());
        assertEquals(1, response.getTotalMovimientos());
        assertEquals(NUMERO_CUENTA, response.getNumeroCuenta());
        assertEquals("FT-20261023-ABCD", response.getPagos().get(0).getFolioTicket());
        assertEquals("AUTH123", response.getPagos().get(0).getNumeroAutorizacion());
        assertEquals(new BigDecimal("20.00"), response.getPagos().get(0).getMonto());
    }

    @Test
    void consultarHistorial_cuentaExisteSinPagos_retornaListaVacia() {
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));
        when(pagoServicioRepository.findByNumeroCuentaOrderByFechaOperacionDesc(NUMERO_CUENTA))
                .thenReturn(List.of());

        HistorialPagosRequestDTO req = HistorialPagosRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .build();

        HistorialPagosResponseDTO response = pagoServicioService.consultarHistorial(req);

        assertNotNull(response);
        assertEquals(0, response.getCodigo());
        assertEquals(0, response.getTotalMovimientos());
        assertTrue(response.getPagos().isEmpty());
    }

    @Test
    void consultarHistorial_cuentaNoExiste_lanzaCuentaNoEncontradaException() {
        when(cuentaRepository.findByNumeroCuenta("9999999999999999")).thenReturn(Optional.empty());

        HistorialPagosRequestDTO req = HistorialPagosRequestDTO.builder()
                .numeroCuenta("9999999999999999")
                .build();

        assertThrows(CuentaNoEncontradaException.class,
                () -> pagoServicioService.consultarHistorial(req));
    }

    @Test
    void procesarPagoServicio_productoNoExisteEnCatalogo_lanzaValidacionException400() {
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));
        pagoServicioService.propagarCodigo2FA(NUMERO_CUENTA, "123456");

        ProductDTO otroProducto = ProductDTO.builder()
                .id("582")
                .serviceId("133")
                .service("Telcel")
                .name("Recarga $100")
                .frontType("1")
                .price("100.0")
                .build();
        ProductListResponse productListResponse = new ProductListResponse();
        productListResponse.setData(List.of(otroProducto));
        when(productService.obtenerListaProductos()).thenReturn(productListResponse);

        PagoServicioRequestDTO request = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .idServicio(999)
                .idProducto(999)
                .monto(new BigDecimal("100.00"))
                .referencia("25720381")
                .codigo2FA("123456")
                .build();

        ValidacionException ex = assertThrows(ValidacionException.class,
                () -> pagoServicioService.procesarPagoServicio(request));

        assertTrue(ex.getMessage().contains("no existe en el catálogo autorizado de GestoPago"));
        verifyNoInteractions(gestoPagoTxClient);
        verifyNoInteractions(saldoRepository);
    }

    @Test
    void procesarPagoServicio_montoManipuladoNoCoincideConPrecioCatalogo_lanzaValidacionException400() {
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));
        pagoServicioService.propagarCodigo2FA(NUMERO_CUENTA, "123456");

        ProductDTO recargaPrecioFijo = ProductDTO.builder()
                .id("582")
                .serviceId("133")
                .service("Telcel")
                .name("Recarga $100")
                .frontType("1")
                .price("100.00")
                .build();
        ProductListResponse productListResponse = new ProductListResponse();
        productListResponse.setData(List.of(recargaPrecioFijo));
        when(productService.obtenerListaProductos()).thenReturn(productListResponse);

        PagoServicioRequestDTO request = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .idServicio(133)
                .idProducto(582)
                .monto(new BigDecimal("50.00")) // Manipulado: el producto cuesta $100.00
                .telefono("5512345678")
                .codigo2FA("123456")
                .build();

        ValidacionException ex = assertThrows(ValidacionException.class,
                () -> pagoServicioService.procesarPagoServicio(request));

        assertTrue(ex.getMessage().contains("no corresponde con el precio autorizado en el catálogo"));
        verifyNoInteractions(gestoPagoTxClient);
        verifyNoInteractions(saldoRepository);
    }

    @Test
    void procesarPagoServicio_operacionDuplicadaEnVentanaIdempotencia_lanzaConflicto409() {
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));
        pagoServicioService.propagarCodigo2FA(NUMERO_CUENTA, "123456");

        when(pagoServicioRepository.existsByNumeroCuentaAndIdServicioAndIdProductoAndReferenciaAndTelefonoAndMontoPagadoAndFechaOperacionAfter(
                eq(NUMERO_CUENTA), eq(76), eq(205), eq("25720381"), eq("1111111111"), eq(new BigDecimal("200.00")), any()))
                .thenReturn(true);

        PagoServicioRequestDTO request = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .idServicio(76)
                .idProducto(205)
                .monto(new BigDecimal("200.00"))
                .referencia("25720381")
                .codigo2FA("123456")
                .build();

        PagoServicioProveedorException ex = assertThrows(PagoServicioProveedorException.class,
                () -> pagoServicioService.procesarPagoServicio(request));

        assertEquals("06", ex.getCodigoProveedor());
        assertEquals(409, ex.getStatusCode());
        verifyNoInteractions(gestoPagoTxClient);
        verifyNoInteractions(saldoRepository);
    }

    @Test
    void procesarPagoServicio_timeoutCodigo82ConciliadoConExitoEnVerifyTx_descuentaSaldoYRegistra() {
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));
        pagoServicioService.propagarCodigo2FA(NUMERO_CUENTA, "445566");

        GestoPagoToken token = new GestoPagoToken();
        token.setToken("jwt-token-activo");
        when(tokenService.obtenerTokenActivo(83, "GPS83-TPV-17")).thenReturn(Optional.of(token));

        String xmlTimeout82 = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<RESPONSE>" +
                "  <NUM_AUTORIZACION>-1</NUM_AUTORIZACION>" +
                "  <MENSAJE>" +
                "    <CODIGO>82</CODIGO>" +
                "    <TEXTO>Tiempo de espera agotado</TEXTO>" +
                "  </MENSAJE>" +
                "</RESPONSE>";

        String xmlVerifySuccess = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<RESPONSE>" +
                "  <ID_TX>291999111</ID_TX>" +
                "  <NUM_AUTORIZACION>100099887</NUM_AUTORIZACION>" +
                "  <SALDO>154000.00</SALDO>" +
                "  <COMISION>7.00</COMISION>" +
                "  <FECHA>25/Feb/2022 19:00:00</FECHA>" +
                "  <MONTO>200.00</MONTO>" +
                "  <MENSAJE>" +
                "    <CODIGO>01</CODIGO>" +
                "    <TEXTO>Operacion realizada con exito</TEXTO>" +
                "  </MENSAJE>" +
                "</RESPONSE>";

        when(gestoPagoTxClient.sendTx(anyString(), anyString(), anyString(), anyString())).thenReturn(xmlTimeout82);
        when(gestoPagoTxClient.verifyTx(anyString(), anyString(), anyString(), anyString())).thenReturn(xmlVerifySuccess);
        when(pagoServicioRepository.existsByFolioTicket(anyString())).thenReturn(false);

        PagoServicioRequestDTO request = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .idServicio(76)
                .idProducto(205)
                .monto(new BigDecimal("200.00"))
                .referencia("25720381")
                .codigo2FA("445566")
                .build();

        PagoServicioResponseDTO response = pagoServicioService.procesarPagoServicio(request);

        assertNotNull(response);
        assertEquals(0, response.getCodigo());
        assertEquals("100099887", response.getNumeroAutorizacion());
        assertEquals(new BigDecimal("800.00"), saldoCuenta.getSaldo());
        verify(gestoPagoTxClient, times(1)).sendTx(anyString(), anyString(), anyString(), anyString());
        verify(gestoPagoTxClient, times(1)).verifyTx(anyString(), anyString(), anyString(), anyString());
        verify(saldoRepository, times(1)).save(saldoCuenta);
    }
}
