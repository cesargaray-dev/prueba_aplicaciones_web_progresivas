package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.clientes.Cuenta;
import com.proyecto.servicios.entity.gestopago.PagoServicio;
import com.proyecto.servicios.exception.AutenticacionException;
import com.proyecto.servicios.exception.ValidacionException;
import com.proyecto.servicios.model.brevo.EmailEnvioResultadoDTO;
import com.proyecto.servicios.model.gestopago.GenerarCodigo2FAResponseDTO;
import com.proyecto.servicios.model.gestopago.PagoServicioRequestDTO;
import com.proyecto.servicios.model.gestopago.PagoServicioResponseDTO;
import com.proyecto.servicios.model.gestopago.ProductDTO;
import com.proyecto.servicios.model.gestopago.ProductListResponse;
import com.proyecto.servicios.model.gestopago.ReenviarComprobanteRequestDTO;
import com.proyecto.servicios.repositorys.clientes.CuentaRepository;
import com.proyecto.servicios.repositorys.gestopago.PagoServicioRepository;
import com.proyecto.servicios.service.ComprobanteEmailService;
import com.proyecto.servicios.service.GestoPagoProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificacionPagoServiceImplTest {

    @Mock
    private CuentaRepository cuentaRepository;
    @Mock
    private PagoServicioRepository pagoServicioRepository;
    @Mock
    private GestoPagoProductService productService;
    @Mock
    private ComprobanteEmailService comprobanteEmailService;

    private NotificacionPagoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificacionPagoServiceImpl(
                cuentaRepository,
                pagoServicioRepository,
                productService,
                comprobanteEmailService
        );
    }

    @Test
    void enriquecerYEnviar2FA_enviaCorreoYEnmascaraDestino() {
        Cliente cliente = new Cliente();
        cliente.setCorreo("cesar@gmail.com");
        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("5225253189113705");
        cuenta.setCliente(cliente);

        when(cuentaRepository.findByNumeroCuenta("5225253189113705")).thenReturn(Optional.of(cuenta));
        when(comprobanteEmailService.enviarCodigo2FA(cliente, cuenta, "482910", 300L))
                .thenReturn(EmailEnvioResultadoDTO.builder()
                        .enviado(true)
                        .correoDestino("cesar@gmail.com")
                        .messageId("msg-2fa-1")
                        .mensaje("Enviado correctamente")
                        .build());

        GenerarCodigo2FAResponseDTO input = GenerarCodigo2FAResponseDTO.builder()
                .numeroCuenta("5225253189113705")
                .codigo2FA("482910")
                .vigenciaSegundos(300L)
                .build();

        GenerarCodigo2FAResponseDTO res = service.enriquecerYEnviar2FA(input);

        assertTrue(res.getCorreoEnviado());
        assertEquals("ces***@gmail.com", res.getCorreoDestino());
        assertEquals("msg-2fa-1", res.getMessageId());
        assertNull(res.getCodigo2FA());

        ReflectionTestUtils.setField(service, "exposeOtpInResponse", true);
        GenerarCodigo2FAResponseDTO expuesto = service.enriquecerYEnviar2FA(
                GenerarCodigo2FAResponseDTO.builder()
                        .numeroCuenta("5225253189113705")
                        .codigo2FA("482910")
                        .vigenciaSegundos(300L)
                        .build()
        );
        assertEquals("482910", expuesto.getCodigo2FA());
    }

    @Test
    void validarAntesDePagar_rechazaTelefonosFalsosYProductosConsulta() {
        ProductDTO telcel = ProductDTO.builder()
                .serviceId("133").id("582").frontType("1").categoryId("2").service("Telcel").build();
        ProductDTO consulta = ProductDTO.builder()
                .serviceId("999").id("9991").frontType("4").service("Consulta CFE").build();
        ProductDTO amazon = ProductDTO.builder()
                .serviceId("200").id("2001").frontType("1").categoryId("10").service("Amazon Cash").build();

        ProductListResponse catalogo = new ProductListResponse();
        catalogo.setData(List.of(telcel, consulta, amazon));
        when(productService.obtenerListaProductos()).thenReturn(catalogo);

        PagoServicioRequestDTO recargaFalsa = PagoServicioRequestDTO.builder()
                .numeroCuenta("5225253189113705")
                .idServicio(133).idProducto(582)
                .referencia("1111111111")
                .monto(new BigDecimal("100.00")).codigo2FA("123456").build();
        assertThrows(ValidacionException.class, () -> service.validarAntesDePagar(recargaFalsa));

        PagoServicioRequestDTO recargaValida = PagoServicioRequestDTO.builder()
                .numeroCuenta("5225253189113705")
                .idServicio(133).idProducto(582)
                .referencia("5512345678")
                .monto(new BigDecimal("100.00")).codigo2FA("123456").build();
        service.validarAntesDePagar(recargaValida);
        assertEquals("5512345678", recargaValida.getTelefono());

        PagoServicioRequestDTO soloConsulta = PagoServicioRequestDTO.builder()
                .numeroCuenta("5225253189113705")
                .idServicio(999).idProducto(9991)
                .referencia("123456789").monto(BigDecimal.ONE).codigo2FA("123456").build();
        assertThrows(ValidacionException.class, () -> service.validarAntesDePagar(soloConsulta));

        PagoServicioRequestDTO giftcard = PagoServicioRequestDTO.builder()
                .numeroCuenta("5225253189113705")
                .idServicio(200).idProducto(2001)
                .referencia("AMZ99887766").monto(new BigDecimal("200.00")).codigo2FA("123456").build();
        assertDoesNotThrow(() -> service.validarAntesDePagar(giftcard));
    }

    @Test
    void enriquecerRespuestaPago_y_reenviarComprobante_funcionan() {
        when(comprobanteEmailService.consumirResultadoPorFolio("FOL-100"))
                .thenReturn(EmailEnvioResultadoDTO.builder()
                        .enviado(true)
                        .correoDestino("cesar@gmail.com")
                        .messageId("msg-99")
                        .mensaje("Enviado")
                        .build());

        PagoServicioResponseDTO pagoResp = PagoServicioResponseDTO.builder()
                .folioTicket("FOL-100").build();
        service.enriquecerRespuestaPago(pagoResp);
        assertTrue(pagoResp.getCorreoEnviado());
        assertEquals("msg-99", pagoResp.getBrevoMessageId());

        Cliente cliente = new Cliente();
        cliente.setCorreo("cesar@gmail.com");
        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("5225253189113705");
        cuenta.setCliente(cliente);
        PagoServicio pagoEntity = new PagoServicio();
        pagoEntity.setFolioTicket("FOL-100");
        pagoEntity.setNumeroCuenta("5225253189113705");
        pagoEntity.setCuenta(cuenta);

        when(pagoServicioRepository.findByFolioTicket("FOL-100")).thenReturn(Optional.of(pagoEntity));
        when(cuentaRepository.findByNumeroCuenta("5225253189113705")).thenReturn(Optional.of(cuenta));
        when(comprobanteEmailService.enviarComprobantePagoConResultado(
                eq(cuenta), eq(cliente), eq(pagoEntity), eq("otro@gmail.com")))
                .thenReturn(EmailEnvioResultadoDTO.builder().enviado(true).correoDestino("otro@gmail.com").build());

        EmailEnvioResultadoDTO reenviado = service.reenviarComprobante(
                new ReenviarComprobanteRequestDTO("5225253189113705", "FOL-100", "otro@gmail.com"));
        assertTrue(reenviado.isEnviado());

        assertThrows(AutenticacionException.class, () -> service.reenviarComprobante(
                new ReenviarComprobanteRequestDTO("1111222233334444", "FOL-100", null)));
        assertThrows(ValidacionException.class, () -> service.reenviarComprobante(
                new ReenviarComprobanteRequestDTO("5225253189113705", "FOL-100", "fake@ejemplo.com")));
    }

    @Test
    void casosBorde_2fa_validacionTelefoniaYReenvioSinCorreoReal() {
        assertNull(service.enriquecerYEnviar2FA(null));
        assertNull(service.enriquecerRespuestaPago(null));
        assertThrows(ValidacionException.class, () -> service.validarAntesDePagar(null));
        assertThrows(ValidacionException.class, () -> service.reenviarComprobante(null));

        when(cuentaRepository.findByNumeroCuenta("0000")).thenReturn(Optional.empty());
        GenerarCodigo2FAResponseDTO sinCuenta = service.enriquecerYEnviar2FA(
                GenerarCodigo2FAResponseDTO.builder().numeroCuenta("0000").codigo2FA("123456").build());
        assertFalse(sinCuenta.getCorreoEnviado());

        ProductDTO prodCfe = new ProductDTO("99", "CFE Recibo", "CFE", "80", "0", "b", "2", "f", "5");
        ProductDTO prodTel = new ProductDTO("10", "Telcel 100", "Telcel", "20", "100", "a", "not-num", "f", "1");
        ProductDTO prodTelNum = new ProductDTO("10", "Telcel 100", "Telcel", "20", "100", "a", "1", "f", "1");
        ProductListResponse cat = new ProductListResponse();
        cat.setData(List.of(prodCfe, prodTel));

        // Sin idServicio -> early return
        service.validarAntesDePagar(PagoServicioRequestDTO.builder().build());

        // Fallback por servicio sin coincidencia exacta de idProducto + frontType no numérico -> 2
        when(productService.obtenerListaProductos()).thenReturn(cat);
        PagoServicioRequestDTO servTelInvalido = PagoServicioRequestDTO.builder()
                .idServicio(20).idProducto(999).referencia("0180020300").telefono("1111111111").build();
        // 1111111111 se permite como placeholder en servicios no-recarga
        assertDoesNotThrow(() -> service.validarAntesDePagar(servTelInvalido));

        PagoServicioRequestDTO servTelMalo = PagoServicioRequestDTO.builder()
                .idServicio(80).idProducto(99).referencia("0180020300").telefono("0512345678").build();
        assertThrows(ValidacionException.class, () -> service.validarAntesDePagar(servTelMalo));

        // Recarga con referencia válida pero request.telefono inválido
        cat.setData(List.of(prodTelNum));
        PagoServicioRequestDTO recargaTelMalo = PagoServicioRequestDTO.builder()
                .idServicio(20).idProducto(10).referencia("5512345678").telefono("1234567890").build();
        assertThrows(ValidacionException.class, () -> service.validarAntesDePagar(recargaTelMalo));

        // Catalogo lanza excepcion -> no bloquea validacion
        when(productService.obtenerListaProductos()).thenThrow(new RuntimeException("Cache error"));
        assertDoesNotThrow(() -> service.validarAntesDePagar(recargaTelMalo));

        // Reenvio: folio inexistente y cliente sin correo real
        when(pagoServicioRepository.findByFolioTicket("NO-EXISTE")).thenReturn(Optional.empty());
        assertThrows(ValidacionException.class, () -> service.reenviarComprobante(
                new ReenviarComprobanteRequestDTO("5225253189113705", "NO-EXISTE", null)));

        Cliente sinCorreo = new Cliente();
        sinCorreo.setCorreo("demo@ejemplo.com");
        Cuenta c = new Cuenta();
        c.setNumeroCuenta("5225253189113705");
        c.setCliente(sinCorreo);
        PagoServicio pago = new PagoServicio();
        pago.setFolioTicket("FOL-PLACE");
        pago.setNumeroCuenta("5225253189113705");
        pago.setCuenta(c);
        when(pagoServicioRepository.findByFolioTicket("FOL-PLACE")).thenReturn(Optional.of(pago));
        when(cuentaRepository.findByNumeroCuenta("5225253189113705")).thenReturn(Optional.of(c));
        assertThrows(ValidacionException.class, () -> service.reenviarComprobante(
                new ReenviarComprobanteRequestDTO("5225253189113705", "FOL-PLACE", null)));

        // Comprobante PDF
        assertThrows(ValidacionException.class, () -> service.obtenerPagoPorFolio("   "));
        pago.setMontoPagado(new BigDecimal("150.00"));
        pago.setComision(BigDecimal.ZERO);
        pago.setNombreServicio("CFE");
        pago.setNombreProducto("Recibo CFE");
        pago.setNumAutorizacion("AUTH-777");
        pago.setReferencia("0102030405");
        byte[] pdfBytes = service.generarComprobantePdf("FOL-PLACE");
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 400);
        assertTrue(new String(pdfBytes).startsWith("%PDF-1.4"));
    }
}
