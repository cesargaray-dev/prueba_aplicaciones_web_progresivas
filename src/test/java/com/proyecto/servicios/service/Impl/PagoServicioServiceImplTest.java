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

    @Test
    void testValidarReferenciaTipoFront1Exitoso() {
        ProductDTO recarga = ProductDTO.builder()
                .serviceId("133")
                .id("582")
                .service("Telcel")
                .name("Recarga $100")
                .frontType("1")
                .referenceType("a")
                .price("100.00")
                .build();
        ProductListResponse cat = new ProductListResponse();
        cat.setData(List.of(recarga));
        when(productService.obtenerListaProductos()).thenReturn(cat);

        ValidarReferenciaRequestDTO req = ValidarReferenciaRequestDTO.builder()
                .idServicio(133)
                .idProducto(582)
                .referencia("5512345678")
                .build();

        ValidarReferenciaResponseDTO res = pagoServicioService.validarReferencia(req);

        assertTrue(res.isEsValida());
        assertEquals("Número telefónico válido para recarga", res.getMensaje());
        assertEquals(1, res.getTipoFront());
        assertEquals("a", res.getTipoReferencia());
        assertFalse(res.getRequiereMonto());
        assertEquals(new BigDecimal("100.00"), res.getMontoSugerido());
        assertEquals("Telcel", res.getServicio());
    }

    @Test
    void testValidarReferenciaTipoFront1Invalido() {
        ProductDTO recarga = ProductDTO.builder()
                .serviceId("133")
                .id("582")
                .service("Telcel")
                .frontType("1")
                .referenceType("a")
                .build();
        ProductListResponse cat = new ProductListResponse();
        cat.setData(List.of(recarga));
        when(productService.obtenerListaProductos()).thenReturn(cat);

        ValidarReferenciaRequestDTO req = ValidarReferenciaRequestDTO.builder()
                .idServicio(133)
                .idProducto(582)
                .referencia("55123") // Menos de 10 dígitos
                .build();

        ValidarReferenciaResponseDTO res = pagoServicioService.validarReferencia(req);

        assertFalse(res.isEsValida());
        assertEquals("El número de teléfono debe contener exactamente 10 dígitos numéricos", res.getMensaje());
    }

    @Test
    void testValidarReferenciaTipoReferenciaA_Alfanumerica() {
        ProductDTO serv = ProductDTO.builder()
                .serviceId("76")
                .id("205")
                .frontType("2")
                .referenceType("a")
                .service("Sky")
                .build();
        ProductListResponse cat = new ProductListResponse();
        cat.setData(List.of(serv));
        when(productService.obtenerListaProductos()).thenReturn(cat);

        ValidarReferenciaRequestDTO reqValida = ValidarReferenciaRequestDTO.builder()
                .idServicio(76)
                .idProducto(205)
                .referencia("SKY12345ABC")
                .build();
        ValidarReferenciaResponseDTO resValida = pagoServicioService.validarReferencia(reqValida);
        assertTrue(resValida.isEsValida());

        ValidarReferenciaRequestDTO reqInvalida = ValidarReferenciaRequestDTO.builder()
                .idServicio(76)
                .idProducto(205)
                .referencia("SKY 123#$$")
                .build();
        ValidarReferenciaResponseDTO resInvalida = pagoServicioService.validarReferencia(reqInvalida);
        assertFalse(resInvalida.isEsValida());
    }

    @Test
    void testValidarReferenciaTipoReferenciaB_CodigoBarras() {
        ProductDTO serv = ProductDTO.builder()
                .serviceId("200")
                .id("300")
                .frontType("2")
                .referenceType("b")
                .build();
        ProductListResponse cat = new ProductListResponse();
        cat.setData(List.of(serv));
        when(productService.obtenerListaProductos()).thenReturn(cat);

        // 8 a 30 dígitos numéricos
        ValidarReferenciaRequestDTO reqValida = ValidarReferenciaRequestDTO.builder()
                .idServicio(200)
                .idProducto(300)
                .referencia("12345678901234") // 14 dígitos
                .build();
        assertTrue(pagoServicioService.validarReferencia(reqValida).isEsValida());

        ValidarReferenciaRequestDTO reqInvalida = ValidarReferenciaRequestDTO.builder()
                .idServicio(200)
                .idProducto(300)
                .referencia("12345") // 5 dígitos (menor a 8)
                .build();
        assertFalse(pagoServicioService.validarReferencia(reqInvalida).isEsValida());
    }

    @Test
    void testValidarReferenciaTipoReferenciaC_Contrato() {
        ProductDTO serv = ProductDTO.builder()
                .serviceId("201")
                .id("301")
                .frontType("2")
                .referenceType("c")
                .build();
        ProductListResponse cat = new ProductListResponse();
        cat.setData(List.of(serv));
        when(productService.obtenerListaProductos()).thenReturn(cat);

        // 6 a 16 dígitos numéricos
        ValidarReferenciaRequestDTO reqValida = ValidarReferenciaRequestDTO.builder()
                .idServicio(201)
                .idProducto(301)
                .referencia("12345678") // 8 dígitos
                .build();
        assertTrue(pagoServicioService.validarReferencia(reqValida).isEsValida());

        ValidarReferenciaRequestDTO reqInvalida = ValidarReferenciaRequestDTO.builder()
                .idServicio(201)
                .idProducto(301)
                .referencia("123") // 3 dígitos
                .build();
        assertFalse(pagoServicioService.validarReferencia(reqInvalida).isEsValida());
    }

    @Test
    void testValidarReferenciaTipoReferenciaBC_DualMode() {
        ProductDTO serv = ProductDTO.builder()
                .serviceId("202")
                .id("302")
                .frontType("2")
                .referenceType("bc")
                .build();
        ProductListResponse cat = new ProductListResponse();
        cat.setData(List.of(serv));
        when(productService.obtenerListaProductos()).thenReturn(cat);

        // Contrato: 10-12 dígitos
        ValidarReferenciaRequestDTO reqContrato = ValidarReferenciaRequestDTO.builder()
                .idServicio(202)
                .idProducto(302)
                .referencia("123456789012") // 12 dígitos
                .build();
        assertTrue(pagoServicioService.validarReferencia(reqContrato).isEsValida());

        // Código de barras: 20-30 dígitos
        ValidarReferenciaRequestDTO reqBarras = ValidarReferenciaRequestDTO.builder()
                .idServicio(202)
                .idProducto(302)
                .referencia("12345678901234567890") // 20 dígitos
                .build();
        assertTrue(pagoServicioService.validarReferencia(reqBarras).isEsValida());

        // Inválido (15 dígitos)
        ValidarReferenciaRequestDTO reqInvalida = ValidarReferenciaRequestDTO.builder()
                .idServicio(202)
                .idProducto(302)
                .referencia("123456789012345")
                .build();
        assertFalse(pagoServicioService.validarReferencia(reqInvalida).isEsValida());
    }

    @Test
    void testValidarReferenciaConDigitoVerificador() {
        ProductDTO serv = ProductDTO.builder()
                .serviceId("203")
                .id("303")
                .frontType("2")
                .referenceType("c")
                .hasVerificationDigit("true")
                .build();
        ProductListResponse cat = new ProductListResponse();
        cat.setData(List.of(serv));
        when(productService.obtenerListaProductos()).thenReturn(cat);

        // Referencia con Luhn válido: "79927398713"
        ValidarReferenciaRequestDTO reqLuhn = ValidarReferenciaRequestDTO.builder()
                .idServicio(203)
                .idProducto(303)
                .referencia("79927398713")
                .build();
        assertTrue(pagoServicioService.validarReferencia(reqLuhn).isEsValida());

        // Referencia con dígito verificador inválido
        ValidarReferenciaRequestDTO reqInvalido = ValidarReferenciaRequestDTO.builder()
                .idServicio(203)
                .idProducto(303)
                .referencia("79927398714")
                .build();
        ValidarReferenciaResponseDTO resInvalido = pagoServicioService.validarReferencia(reqInvalido);
        assertFalse(resInvalido.isEsValida());
        assertTrue(resInvalido.getMensaje().contains("dígito verificador"));
    }

    @Test
    void testValidarReferenciaRequestIncompleto() {
        assertFalse(pagoServicioService.validarReferencia(null).isEsValida());
        assertFalse(pagoServicioService.validarReferencia(new ValidarReferenciaRequestDTO()).isEsValida());

        ValidarReferenciaRequestDTO reqVacio = ValidarReferenciaRequestDTO.builder()
                .idServicio(76)
                .referencia("")
                .build();
        assertFalse(pagoServicioService.validarReferencia(reqVacio).isEsValida());
    }

    @Test
    void testConsultarHistorialYObtenerHistorialPagosPopulaLogoUrlYEstado() {
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));

        PagoServicio p1 = new PagoServicio();
        p1.setId(101L);
        p1.setCuenta(cuentaActiva);
        p1.setNumeroCuenta(NUMERO_CUENTA);
        p1.setIdServicio(76);
        p1.setNombreServicio("TELCEL");
        p1.setIdProducto(205);
        p1.setNombreProducto("PAQUETE 20");
        p1.setMontoPagado(new BigDecimal("20.00"));
        p1.setSaldoAnterior(new BigDecimal("100.00"));
        p1.setSaldoRestante(new BigDecimal("80.00"));
        p1.setCodigoRespuesta("01");
        p1.setFolioTicket("FT-001");
        p1.setNumAutorizacion("AUTH123");
        p1.setConcepto("RECARGA");
        p1.setReferencia("5512345678");
        p1.setTelefono("5512345678");

        when(pagoServicioRepository.findByNumeroCuentaOrderByFechaOperacionDesc(NUMERO_CUENTA))
                .thenReturn(List.of(p1));

        HistorialPagosResponseDTO historial = pagoServicioService.consultarHistorial(
                new HistorialPagosRequestDTO(NUMERO_CUENTA));

        assertNotNull(historial);
        assertEquals(1, historial.getTotalMovimientos());
        ItemHistorialPagoDTO item = historial.getPagos().get(0);
        assertEquals("EXITOSA", item.getEstado());
        assertEquals("https://gestopago.portalventas.net/sistema/images/gestopago/servicios/76.png", item.getLogoUrl());

        List<ItemHistorialPagoDTO> listaDirecta = pagoServicioService.obtenerHistorialPagos(NUMERO_CUENTA);
        assertNotNull(listaDirecta);
        assertEquals(1, listaDirecta.size());
        assertEquals("EXITOSA", listaDirecta.get(0).getEstado());
        assertEquals("https://gestopago.portalventas.net/sistema/images/gestopago/servicios/76.png", listaDirecta.get(0).getLogoUrl());
    }

    @Test
    void procesarPagoServicio_pagoServicioMontoAbiertoMayorQuePrecioBase_ejecutaExitosamente() {
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));
        pagoServicioService.propagarCodigo2FA(NUMERO_CUENTA, "998877");

        // Servicio de luz CFE tipoFront="2" con precio base 10.0 pero el recibo es de $250.00
        ProductDTO cfeProduct = ProductDTO.builder()
                .id("1001")
                .serviceId("50")
                .service("CFE")
                .name("CFE Suministrador")
                .frontType("2")
                .price("10.0")
                .categoryId("2")
                .build();
        ProductListResponse productListResponse = new ProductListResponse();
        productListResponse.setData(List.of(cfeProduct));
        when(productService.obtenerListaProductos()).thenReturn(productListResponse);

        GestoPagoToken token = new GestoPagoToken();
        token.setToken("jwt-token-cfe");
        when(tokenService.obtenerTokenActivo(83, "GPS83-TPV-17")).thenReturn(Optional.of(token));

        String xmlSuccess = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<RESPONSE>" +
                "  <ID_TX>554433221</ID_TX>" +
                "  <NUM_AUTORIZACION>CFE889900</NUM_AUTORIZACION>" +
                "  <SALDO>154747.00</SALDO>" +
                "  <COMISION>0.00</COMISION>" +
                "  <FECHA>25/Feb/2022 19:00:00</FECHA>" +
                "  <MONTO>250.00</MONTO>" +
                "  <MENSAJE>" +
                "    <CODIGO>01</CODIGO>" +
                "    <TEXTO>Operacion realizada con exito</TEXTO>" +
                "  </MENSAJE>" +
                "</RESPONSE>";

        when(gestoPagoTxClient.sendTx(anyString(), anyString(), anyString(), anyString())).thenReturn(xmlSuccess);
        when(pagoServicioRepository.existsByFolioTicket(anyString())).thenReturn(false);

        PagoServicioRequestDTO request = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .idServicio(50)
                .idProducto(1001)
                .monto(new BigDecimal("250.00")) // $250.00 permitido para tipoFront=2 aunque precio sea 10.0
                .referencia("123456789012")
                .codigo2FA("998877")
                .build();

        PagoServicioResponseDTO response = pagoServicioService.procesarPagoServicio(request);

        assertNotNull(response);
        assertEquals(0, response.getCodigo());
        assertEquals("CFE889900", response.getNumeroAutorizacion());
        assertEquals(new BigDecimal("750.00"), saldoCuenta.getSaldo()); // 1000 - 250 = 750
        verify(saldoRepository).save(saldoCuenta);
    }

    @Test
    void procesarPagoServicio_montoInvalido_lanzaValidacionException() {
        PagoServicioRequestDTO reqNull = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .monto(null)
                .build();
        assertThrows(ValidacionException.class, () -> pagoServicioService.procesarPagoServicio(reqNull));

        PagoServicioRequestDTO reqCero = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .monto(BigDecimal.ZERO)
                .build();
        assertThrows(ValidacionException.class, () -> pagoServicioService.procesarPagoServicio(reqCero));

        PagoServicioRequestDTO reqDec = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .monto(new BigDecimal("10.555"))
                .build();
        assertThrows(ValidacionException.class, () -> pagoServicioService.procesarPagoServicio(reqDec));

        PagoServicioRequestDTO reqMax = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .monto(new BigDecimal("15000.00"))
                .build();
        assertThrows(ValidacionException.class, () -> pagoServicioService.procesarPagoServicio(reqMax));
    }

    @Test
    void testValidarModulo10Luhn_casosLimite() {
        assertFalse(PagoServicioServiceImpl.validarModulo10Luhn(null));
        assertFalse(PagoServicioServiceImpl.validarModulo10Luhn("1"));
        assertFalse(PagoServicioServiceImpl.validarModulo10Luhn("12a"));
        assertTrue(PagoServicioServiceImpl.validarModulo10Luhn("79927398713"));
        assertFalse(PagoServicioServiceImpl.validarModulo10Luhn("79927398714"));
    }

    @Test
    void testValidarModulo11_casosLimite() {
        assertFalse(PagoServicioServiceImpl.validarModulo11(null));
        assertFalse(PagoServicioServiceImpl.validarModulo11("1"));
        assertFalse(PagoServicioServiceImpl.validarModulo11("12a"));
        assertTrue(PagoServicioServiceImpl.validarModulo11("12343"));
        assertFalse(PagoServicioServiceImpl.validarModulo11("12344"));
    }

    @Test
    void testValidarReferencia_referenciaVaciaTipoFront2() {
        ProductDTO serv = ProductDTO.builder()
                .serviceId("50")
                .id("1001")
                .frontType("2")
                .build();
        ProductListResponse cat = new ProductListResponse();
        cat.setData(List.of(serv));
        when(productService.obtenerListaProductos()).thenReturn(cat);

        ValidarReferenciaRequestDTO req = ValidarReferenciaRequestDTO.builder()
                .idServicio(50)
                .idProducto(1001)
                .referencia("   ")
                .build();

        ValidarReferenciaResponseDTO res = pagoServicioService.validarReferencia(req);
        assertFalse(res.isEsValida());
        assertEquals("La referencia no puede estar vacía", res.getMensaje());
    }

    @Test
    void testValidarRestriccionesGeneralesMonto_casosInvalido() {
        PagoServicioRequestDTO reqNull = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .monto(null)
                .build();
        assertThrows(ValidacionException.class, () -> pagoServicioService.procesarPagoServicio(reqNull));

        PagoServicioRequestDTO reqZero = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .monto(BigDecimal.ZERO)
                .build();
        assertThrows(ValidacionException.class, () -> pagoServicioService.procesarPagoServicio(reqZero));

        PagoServicioRequestDTO reqDecimals = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .monto(new BigDecimal("10.555"))
                .build();
        assertThrows(ValidacionException.class, () -> pagoServicioService.procesarPagoServicio(reqDecimals));

        PagoServicioRequestDTO reqOverMax = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .monto(new BigDecimal("50000.01"))
                .build();
        assertThrows(ValidacionException.class, () -> pagoServicioService.procesarPagoServicio(reqOverMax));
    }

    @Test
    void testValidarCuentaYClienteActivo_casosInvalido() {
        when(cuentaRepository.findByNumeroCuenta("9999999999999999")).thenReturn(Optional.empty());
        GenerarCodigo2FARequestDTO reqNoCuenta = GenerarCodigo2FARequestDTO.builder()
                .numeroCuenta("9999999999999999")
                .build();
        assertThrows(CuentaNoEncontradaException.class, () -> pagoServicioService.generarCodigo2FA(reqNoCuenta));

        Cliente clienteInactivo = new Cliente();
        clienteInactivo.setId(2L);
        clienteInactivo.setActivo(false);
        Cuenta cuentaClienteInactivo = new Cuenta();
        cuentaClienteInactivo.setNumeroCuenta("2222222222222222");
        cuentaClienteInactivo.setCliente(clienteInactivo);
        cuentaClienteInactivo.setEstatus(EstatusCuentaEnum.ACTIVA.getCodigo());
        when(cuentaRepository.findByNumeroCuenta("2222222222222222")).thenReturn(Optional.of(cuentaClienteInactivo));

        GenerarCodigo2FARequestDTO reqInactivo = GenerarCodigo2FARequestDTO.builder()
                .numeroCuenta("2222222222222222")
                .build();
        assertThrows(ClienteInactivoException.class, () -> pagoServicioService.generarCodigo2FA(reqInactivo));

        Cuenta cuentaSinCliente = new Cuenta();
        cuentaSinCliente.setNumeroCuenta("3333333333333333");
        cuentaSinCliente.setCliente(null);
        cuentaSinCliente.setEstatus(EstatusCuentaEnum.ACTIVA.getCodigo());
        when(cuentaRepository.findByNumeroCuenta("3333333333333333")).thenReturn(Optional.of(cuentaSinCliente));
        GenerarCodigo2FARequestDTO reqSinCliente = GenerarCodigo2FARequestDTO.builder()
                .numeroCuenta("3333333333333333")
                .build();
        assertThrows(ClienteInactivoException.class, () -> pagoServicioService.generarCodigo2FA(reqSinCliente));

        Cuenta cuentaBloqueada = new Cuenta();
        cuentaBloqueada.setNumeroCuenta("4444444444444444");
        cuentaBloqueada.setCliente(clienteActivo);
        cuentaBloqueada.setEstatus("BLOQUEADA");
        when(cuentaRepository.findByNumeroCuenta("4444444444444444")).thenReturn(Optional.of(cuentaBloqueada));
        GenerarCodigo2FARequestDTO reqBloqueada = GenerarCodigo2FARequestDTO.builder()
                .numeroCuenta("4444444444444444")
                .build();
        assertThrows(CuentaInactivaException.class, () -> pagoServicioService.generarCodigo2FA(reqBloqueada));
    }

    @Test
    void testProcesarPagoServicio_conTokenExpiradoEnXml_renuevaTokenYReintenta() {
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));
        pagoServicioService.propagarCodigo2FA(NUMERO_CUENTA, "123456");

        ProductDTO prod = ProductDTO.builder()
                .id("100")
                .serviceId("10")
                .name("Servicio Test")
                .frontType("2")
                .build();
        ProductListResponse cat = new ProductListResponse();
        cat.setData(List.of(prod));
        when(productService.obtenerListaProductos()).thenReturn(cat);

        GestoPagoToken token1 = new GestoPagoToken();
        token1.setToken("token-exp");
        GestoPagoToken token2 = new GestoPagoToken();
        token2.setToken("token-nuevo");
        when(tokenService.obtenerTokenActivo(83, "GPS83-TPV-17"))
                .thenReturn(Optional.of(token1))
                .thenReturn(Optional.of(token2));

        String xmlSuccess = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<RESPONSE>" +
                "  <ID_TX>12345</ID_TX>" +
                "  <NUM_AUTORIZACION>67890</NUM_AUTORIZACION>" +
                "  <SALDO>1000.00</SALDO>" +
                "  <COMISION>0.00</COMISION>" +
                "  <FECHA>25/Feb/2022 18:15:25</FECHA>" +
                "  <MONTO>50.00</MONTO>" +
                "  <MENSAJE>" +
                "    <CODIGO>01</CODIGO>" +
                "    <TEXTO>Exito</TEXTO>" +
                "  </MENSAJE>" +
                "</RESPONSE>";

        when(gestoPagoTxClient.sendTx(anyString(), anyString(), eq("Bearer token-exp"), eq("TEST_API_KEY")))
                .thenReturn("{\"error\":\"EXPIRED\"}");
        when(gestoPagoTxClient.sendTx(anyString(), anyString(), eq("Bearer token-nuevo"), eq("TEST_API_KEY")))
                .thenReturn(xmlSuccess);

        PagoServicioRequestDTO req = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .idServicio(10)
                .idProducto(100)
                .monto(new BigDecimal("50.00"))
                .referencia("123456789")
                .codigo2FA("123456")
                .build();

        PagoServicioResponseDTO resp = pagoServicioService.procesarPagoServicio(req);
        assertNotNull(resp);
        assertEquals(0, resp.getCodigo());
        verify(tokenService, times(1)).renovarToken();
    }

    @Test
    void testProcesarPagoServicio_conFeignUnauthorized_renuevaYReintentaExitoso() {
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));
        pagoServicioService.propagarCodigo2FA(NUMERO_CUENTA, "123456");

        ProductDTO prod = ProductDTO.builder()
                .id("100")
                .serviceId("10")
                .name("Servicio Test")
                .frontType("2")
                .build();
        ProductListResponse cat = new ProductListResponse();
        cat.setData(List.of(prod));
        when(productService.obtenerListaProductos()).thenReturn(cat);

        GestoPagoToken token1 = new GestoPagoToken();
        token1.setToken("token-exp");
        GestoPagoToken token2 = new GestoPagoToken();
        token2.setToken("token-nuevo");
        when(tokenService.obtenerTokenActivo(83, "GPS83-TPV-17"))
                .thenReturn(Optional.of(token1))
                .thenReturn(Optional.of(token2));

        String xmlSuccess = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<RESPONSE>" +
                "  <ID_TX>12345</ID_TX>" +
                "  <NUM_AUTORIZACION>67890</NUM_AUTORIZACION>" +
                "  <SALDO>1000.00</SALDO>" +
                "  <COMISION>0.00</COMISION>" +
                "  <FECHA>25/Feb/2022 18:15:25</FECHA>" +
                "  <MONTO>50.00</MONTO>" +
                "  <MENSAJE>" +
                "    <CODIGO>01</CODIGO>" +
                "    <TEXTO>Exito</TEXTO>" +
                "  </MENSAJE>" +
                "</RESPONSE>";

        feign.Request feignReq = feign.Request.create(
                feign.Request.HttpMethod.POST, "/sendTx.do", java.util.Collections.emptyMap(),
                null, java.nio.charset.StandardCharsets.UTF_8, new feign.RequestTemplate());

        when(gestoPagoTxClient.sendTx(anyString(), anyString(), eq("Bearer token-exp"), eq("TEST_API_KEY")))
                .thenThrow(new feign.FeignException.Unauthorized("Unauthorized", feignReq, null, null));
        when(gestoPagoTxClient.sendTx(anyString(), anyString(), eq("Bearer token-nuevo"), eq("TEST_API_KEY")))
                .thenReturn(xmlSuccess);

        PagoServicioRequestDTO req = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .idServicio(10)
                .idProducto(100)
                .monto(new BigDecimal("50.00"))
                .referencia("123456789")
                .codigo2FA("123456")
                .build();

        PagoServicioResponseDTO resp = pagoServicioService.procesarPagoServicio(req);
        assertNotNull(resp);
        assertEquals(0, resp.getCodigo());
        verify(tokenService, times(1)).renovarToken();
    }

    @Test
    void testProcesarPagoServicio_conFeignUnauthorized_retryFalla_lanzaGestoPagoException() {
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));
        pagoServicioService.propagarCodigo2FA(NUMERO_CUENTA, "123456");

        ProductDTO prod = ProductDTO.builder()
                .id("100")
                .serviceId("10")
                .name("Servicio Test")
                .frontType("2")
                .build();
        ProductListResponse cat = new ProductListResponse();
        cat.setData(List.of(prod));
        when(productService.obtenerListaProductos()).thenReturn(cat);

        GestoPagoToken token1 = new GestoPagoToken();
        token1.setToken("token-exp");
        when(tokenService.obtenerTokenActivo(83, "GPS83-TPV-17")).thenReturn(Optional.of(token1));

        feign.Request feignReq = feign.Request.create(
                feign.Request.HttpMethod.POST, "/sendTx.do", java.util.Collections.emptyMap(),
                null, java.nio.charset.StandardCharsets.UTF_8, new feign.RequestTemplate());

        when(gestoPagoTxClient.sendTx(anyString(), anyString(), eq("Bearer token-exp"), eq("TEST_API_KEY")))
                .thenThrow(new feign.FeignException.Unauthorized("Unauthorized", feignReq, null, null));
        doThrow(new RuntimeException("Error al renovar")).when(tokenService).renovarToken();

        PagoServicioRequestDTO req = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .idServicio(10)
                .idProducto(100)
                .monto(new BigDecimal("50.00"))
                .referencia("123456789")
                .codigo2FA("123456")
                .build();

        assertThrows(GestoPagoException.class, () -> pagoServicioService.procesarPagoServicio(req));
    }

    @Test
    void testProcesarPagoServicio_conGenericFeignException_lanzaGestoPagoException() {
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));
        pagoServicioService.propagarCodigo2FA(NUMERO_CUENTA, "123456");

        ProductDTO prod = ProductDTO.builder()
                .id("100")
                .serviceId("10")
                .name("Servicio Test")
                .frontType("2")
                .build();
        ProductListResponse cat = new ProductListResponse();
        cat.setData(List.of(prod));
        when(productService.obtenerListaProductos()).thenReturn(cat);

        GestoPagoToken token1 = new GestoPagoToken();
        token1.setToken("token-valido");
        when(tokenService.obtenerTokenActivo(83, "GPS83-TPV-17")).thenReturn(Optional.of(token1));

        feign.Request feignReq = feign.Request.create(
                feign.Request.HttpMethod.POST, "/sendTx.do", java.util.Collections.emptyMap(),
                null, java.nio.charset.StandardCharsets.UTF_8, new feign.RequestTemplate());

        when(gestoPagoTxClient.sendTx(anyString(), anyString(), eq("Bearer token-valido"), eq("TEST_API_KEY")))
                .thenThrow(new feign.FeignException.InternalServerError("500", feignReq, null, null));

        PagoServicioRequestDTO req = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .idServicio(10)
                .idProducto(100)
                .monto(new BigDecimal("50.00"))
                .referencia("123456789")
                .codigo2FA("123456")
                .build();

        assertThrows(GestoPagoException.class, () -> pagoServicioService.procesarPagoServicio(req));
    }

    @Test
    void testProcesarPagoServicio_conCodigo82_conciliaExitosoConVerifyTx() {
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));
        pagoServicioService.propagarCodigo2FA(NUMERO_CUENTA, "123456");

        ProductDTO prod = ProductDTO.builder()
                .id("100")
                .serviceId("10")
                .name("Servicio Test")
                .frontType("2")
                .build();
        ProductListResponse cat = new ProductListResponse();
        cat.setData(List.of(prod));
        when(productService.obtenerListaProductos()).thenReturn(cat);

        GestoPagoToken token1 = new GestoPagoToken();
        token1.setToken("token-valido");
        when(tokenService.obtenerTokenActivo(83, "GPS83-TPV-17")).thenReturn(Optional.of(token1));

        String xml82 = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<RESPONSE>" +
                "  <MENSAJE>" +
                "    <CODIGO>82</CODIGO>" +
                "    <TEXTO>Timeout proveedor</TEXTO>" +
                "  </MENSAJE>" +
                "</RESPONSE>";

        String xmlSuccess = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<RESPONSE>" +
                "  <ID_TX>12345</ID_TX>" +
                "  <NUM_AUTORIZACION>67890</NUM_AUTORIZACION>" +
                "  <SALDO>1000.00</SALDO>" +
                "  <COMISION>0.00</COMISION>" +
                "  <FECHA>25/Feb/2022 18:15:25</FECHA>" +
                "  <MONTO>50.00</MONTO>" +
                "  <MENSAJE>" +
                "    <CODIGO>01</CODIGO>" +
                "    <TEXTO>Conciliada con exito</TEXTO>" +
                "  </MENSAJE>" +
                "</RESPONSE>";

        when(gestoPagoTxClient.sendTx(anyString(), anyString(), eq("Bearer token-valido"), eq("TEST_API_KEY")))
                .thenReturn(xml82);
        when(gestoPagoTxClient.verifyTx(anyString(), anyString(), eq("Bearer token-valido"), eq("TEST_API_KEY")))
                .thenReturn(xmlSuccess);

        PagoServicioRequestDTO req = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .idServicio(10)
                .idProducto(100)
                .monto(new BigDecimal("50.00"))
                .referencia("123456789")
                .codigo2FA("123456")
                .build();

        PagoServicioResponseDTO resp = pagoServicioService.procesarPagoServicio(req);
        assertNotNull(resp);
        assertEquals(0, resp.getCodigo());
        assertEquals("Conciliada con exito", resp.getMensaje());
    }

    @Test
    void testProcesarPagoServicio_conGatewayTimeout_conciliaExitosoConVerifyTx() {
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));
        pagoServicioService.propagarCodigo2FA(NUMERO_CUENTA, "123456");

        ProductDTO prod = ProductDTO.builder()
                .id("100")
                .serviceId("10")
                .name("Servicio Test")
                .frontType("2")
                .build();
        ProductListResponse cat = new ProductListResponse();
        cat.setData(List.of(prod));
        when(productService.obtenerListaProductos()).thenReturn(cat);

        GestoPagoToken token1 = new GestoPagoToken();
        token1.setToken("token-valido");
        when(tokenService.obtenerTokenActivo(83, "GPS83-TPV-17")).thenReturn(Optional.of(token1));

        feign.Request feignReq = feign.Request.create(
                feign.Request.HttpMethod.POST, "/sendTx.do", java.util.Collections.emptyMap(),
                null, java.nio.charset.StandardCharsets.UTF_8, new feign.RequestTemplate());

        String xmlSuccess = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<RESPONSE>" +
                "  <ID_TX>12345</ID_TX>" +
                "  <NUM_AUTORIZACION>67890</NUM_AUTORIZACION>" +
                "  <SALDO>1000.00</SALDO>" +
                "  <COMISION>0.00</COMISION>" +
                "  <FECHA>25/Feb/2022 18:15:25</FECHA>" +
                "  <MONTO>50.00</MONTO>" +
                "  <MENSAJE>" +
                "    <CODIGO>01</CODIGO>" +
                "    <TEXTO>Conciliada con exito</TEXTO>" +
                "  </MENSAJE>" +
                "</RESPONSE>";

        when(gestoPagoTxClient.sendTx(anyString(), anyString(), eq("Bearer token-valido"), eq("TEST_API_KEY")))
                .thenThrow(new feign.FeignException.GatewayTimeout("Gateway Timeout", feignReq, null, null));
        when(gestoPagoTxClient.verifyTx(anyString(), anyString(), eq("Bearer token-valido"), eq("TEST_API_KEY")))
                .thenReturn(xmlSuccess);

        PagoServicioRequestDTO req = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .idServicio(10)
                .idProducto(100)
                .monto(new BigDecimal("50.00"))
                .referencia("123456789")
                .codigo2FA("123456")
                .build();

        PagoServicioResponseDTO resp = pagoServicioService.procesarPagoServicio(req);
        assertNotNull(resp);
        assertEquals(0, resp.getCodigo());
        assertEquals("Conciliada con exito", resp.getMensaje());
    }

    @Test
    void testProcesarPagoServicio_conGatewayTimeout_conciliacionFalla_lanza504() {
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));
        pagoServicioService.propagarCodigo2FA(NUMERO_CUENTA, "123456");

        ProductDTO prod = ProductDTO.builder()
                .id("100")
                .serviceId("10")
                .name("Servicio Test")
                .frontType("2")
                .build();
        ProductListResponse cat = new ProductListResponse();
        cat.setData(List.of(prod));
        when(productService.obtenerListaProductos()).thenReturn(cat);

        GestoPagoToken token1 = new GestoPagoToken();
        token1.setToken("token-valido");
        when(tokenService.obtenerTokenActivo(83, "GPS83-TPV-17")).thenReturn(Optional.of(token1));

        feign.Request feignReq = feign.Request.create(
                feign.Request.HttpMethod.POST, "/sendTx.do", java.util.Collections.emptyMap(),
                null, java.nio.charset.StandardCharsets.UTF_8, new feign.RequestTemplate());

        when(gestoPagoTxClient.sendTx(anyString(), anyString(), eq("Bearer token-valido"), eq("TEST_API_KEY")))
                .thenThrow(new feign.FeignException.GatewayTimeout("Gateway Timeout", feignReq, null, null));
        when(gestoPagoTxClient.verifyTx(anyString(), anyString(), eq("Bearer token-valido"), eq("TEST_API_KEY")))
                .thenReturn(null);

        PagoServicioRequestDTO req = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .idServicio(10)
                .idProducto(100)
                .monto(new BigDecimal("50.00"))
                .referencia("123456789")
                .codigo2FA("123456")
                .build();

        PagoServicioProveedorException ex = assertThrows(
                PagoServicioProveedorException.class, () -> pagoServicioService.procesarPagoServicio(req));
        assertEquals(504, ex.getStatusCode());
        assertEquals("82", ex.getCodigoProveedor());
    }

    @Test
    void testObtenerBearerTokenActivo_fallbackConfiguradoYTokenSinPrefijo() {
        when(tokenService.obtenerTokenActivo(83, "GPS83-TPV-17"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());

        ReflectionTestUtils.setField(pagoServicioService, "configuredBearerToken", "raw-jwt-secret");

        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));
        pagoServicioService.propagarCodigo2FA(NUMERO_CUENTA, "123456");

        ProductDTO prod = ProductDTO.builder()
                .id("100")
                .serviceId("10")
                .name("Servicio Test")
                .frontType("2")
                .build();
        ProductListResponse cat = new ProductListResponse();
        cat.setData(List.of(prod));
        when(productService.obtenerListaProductos()).thenReturn(cat);

        String xmlSuccess = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<RESPONSE>" +
                "  <ID_TX>12345</ID_TX>" +
                "  <NUM_AUTORIZACION>67890</NUM_AUTORIZACION>" +
                "  <SALDO>1000.00</SALDO>" +
                "  <COMISION>0.00</COMISION>" +
                "  <FECHA>25/Feb/2022 18:15:25</FECHA>" +
                "  <MONTO>50.00</MONTO>" +
                "  <MENSAJE>" +
                "    <CODIGO>01</CODIGO>" +
                "    <TEXTO>Exito</TEXTO>" +
                "  </MENSAJE>" +
                "</RESPONSE>";

        when(gestoPagoTxClient.sendTx(anyString(), anyString(), eq("Bearer raw-jwt-secret"), eq("TEST_API_KEY")))
                .thenReturn(xmlSuccess);

        PagoServicioRequestDTO req = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .idServicio(10)
                .idProducto(100)
                .monto(new BigDecimal("50.00"))
                .referencia("123456789")
                .codigo2FA("123456")
                .build();

        PagoServicioResponseDTO resp = pagoServicioService.procesarPagoServicio(req);
        assertNotNull(resp);
        assertEquals(0, resp.getCodigo());
    }

    @Test
    void testObtenerBearerTokenActivo_sinTokenNiConfiguracion_lanza401() {
        when(tokenService.obtenerTokenActivo(83, "GPS83-TPV-17"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());

        ReflectionTestUtils.setField(pagoServicioService, "configuredBearerToken", null);

        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));
        pagoServicioService.propagarCodigo2FA(NUMERO_CUENTA, "123456");

        ProductDTO prod = ProductDTO.builder()
                .id("100")
                .serviceId("10")
                .name("Servicio Test")
                .frontType("2")
                .build();
        ProductListResponse cat = new ProductListResponse();
        cat.setData(List.of(prod));
        when(productService.obtenerListaProductos()).thenReturn(cat);

        PagoServicioRequestDTO req = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .idServicio(10)
                .idProducto(100)
                .monto(new BigDecimal("50.00"))
                .referencia("123456789")
                .codigo2FA("123456")
                .build();

        GestoPagoException ex = assertThrows(GestoPagoException.class, () -> pagoServicioService.procesarPagoServicio(req));
        assertEquals(401, ex.getStatusCode());
    }

    @Test
    void testProcesarPagoServicio_sandboxCodigo55OrigenValido_completaConfirmacionExitosa() {
        when(cuentaRepository.findByNumeroCuenta(NUMERO_CUENTA)).thenReturn(Optional.of(cuentaActiva));
        pagoServicioService.propagarCodigo2FA(NUMERO_CUENTA, "123456");

        ProductDTO prod = ProductDTO.builder()
                .id("205")
                .serviceId("76")
                .service("IZZI Telecom")
                .name("Izzi Telecom")
                .frontType("2")
                .price("10.0")
                .build();
        ProductListResponse cat = new ProductListResponse();
        cat.setData(List.of(prod));
        when(productService.obtenerListaProductos()).thenReturn(cat);

        GestoPagoToken token1 = new GestoPagoToken();
        token1.setToken("token-valido");
        when(tokenService.obtenerTokenActivo(83, "GPS83-TPV-17")).thenReturn(Optional.of(token1));

        String xmlSandbox55 = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<RESPONSE>" +
                "  <NUM_AUTORIZACION>-1</NUM_AUTORIZACION>" +
                "  <MENSAJE>" +
                "    <CODIGO>55</CODIGO>" +
                "    <TEXTO>Origen Valido</TEXTO>" +
                "  </MENSAJE>" +
                "</RESPONSE>";

        when(gestoPagoTxClient.sendTx(anyString(), anyString(), eq("Bearer token-valido"), eq("TEST_API_KEY")))
                .thenReturn(xmlSandbox55);

        PagoServicioRequestDTO req = PagoServicioRequestDTO.builder()
                .numeroCuenta(NUMERO_CUENTA)
                .idServicio(76)
                .idProducto(205)
                .monto(new BigDecimal("20.00"))
                .referencia("25720401")
                .codigo2FA("123456")
                .build();

        PagoServicioResponseDTO resp = pagoServicioService.procesarPagoServicio(req);
        assertNotNull(resp);
        assertEquals(0, resp.getCodigo());
        assertEquals("Operacion realizada con exito", resp.getMensaje());
        assertNotNull(resp.getFolioTicket());
        assertTrue(resp.getIdTransaccion() > 0);
        assertNotEquals("-1", resp.getNumeroAutorizacion());
    }
}
