package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.BrevoEmailClient;
import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.clientes.Cuenta;
import com.proyecto.servicios.entity.gestopago.PagoServicio;
import com.proyecto.servicios.model.brevo.BrevoEmailRequestDTO;
import com.proyecto.servicios.model.brevo.BrevoEmailResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComprobanteEmailServiceImplTest {

    @Mock
    private BrevoEmailClient brevoEmailClient;

    private ComprobanteEmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        emailService = new ComprobanteEmailServiceImpl(brevoEmailClient);
        ReflectionTestUtils.setField(emailService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(emailService, "senderEmail", "notificaciones@banco.com");
        ReflectionTestUtils.setField(emailService, "senderName", "Banco Digital");
    }

    @Test
    void enviarComprobantePago_clienteConCorreo_enviaEmailConHtmlFormateado() {
        Cliente cliente = new Cliente();
        cliente.setNombre("Cesar");
        cliente.setApellidoPaterno("Garay");
        cliente.setCorreo("cesar@example.com");

        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("1234567890123456");

        PagoServicio pago = new PagoServicio();
        pago.setNumAutorizacion("100072555");
        pago.setFolioTicket("FT-20261024-TEST");
        pago.setNombreServicio("IZZI Telecom");
        pago.setNombreProducto("Izzi Telecom");
        pago.setReferencia("25720399");
        pago.setTelefono("");
        pago.setMontoPagado(new BigDecimal("20.00"));
        pago.setComision(new BigDecimal("7.00"));
        pago.setSaldoRestante(new BigDecimal("60.00"));
        pago.setFechaOperacion(LocalDateTime.now());

        BrevoEmailResponseDTO brevoResponse = new BrevoEmailResponseDTO();
        brevoResponse.setMessageId("<test-msg-id@brevo.com>");
        when(brevoEmailClient.sendEmail(eq("test-api-key"), any(BrevoEmailRequestDTO.class)))
                .thenReturn(brevoResponse);

        assertDoesNotThrow(() -> emailService.enviarComprobantePago(cuenta, cliente, pago));

        ArgumentCaptor<BrevoEmailRequestDTO> captor = ArgumentCaptor.forClass(BrevoEmailRequestDTO.class);
        verify(brevoEmailClient, times(1)).sendEmail(eq("test-api-key"), captor.capture());

        BrevoEmailRequestDTO sent = captor.getValue();
        assertEquals("cesar@example.com", sent.getTo().get(0).getEmail());
        assertTrue(sent.getSubject().contains("100072555"));
        assertTrue(sent.getHtmlContent().contains("100072555"));
        assertTrue(sent.getHtmlContent().contains("IZZI Telecom"));
        assertTrue(sent.getHtmlContent().contains("3456"));
    }

    @Test
    void enviarComprobantePago_datosOpcionalesNulosYTelefono1111111111() {
        Cliente cliente = new Cliente();
        cliente.setNombre(null);
        cliente.setApellidoPaterno(null);
        cliente.setCorreo("cliente.anonimo@example.com");

        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("123"); // Cuenta corta (< 4 caracteres)

        PagoServicio pago = new PagoServicio();
        pago.setNumAutorizacion(null);
        pago.setFolioTicket(null);
        pago.setNombreServicio(null);
        pago.setNombreProducto(null);
        pago.setReferencia(null);
        pago.setTelefono("1111111111"); // Teléfono ficticio para recibos
        pago.setMontoPagado(null);
        pago.setComision(null);
        pago.setSaldoRestante(null);
        pago.setFechaOperacion(null);

        when(brevoEmailClient.sendEmail(eq("test-api-key"), any(BrevoEmailRequestDTO.class)))
                .thenReturn(null); // response nulo

        assertDoesNotThrow(() -> emailService.enviarComprobantePago(cuenta, cliente, pago));

        ArgumentCaptor<BrevoEmailRequestDTO> captor = ArgumentCaptor.forClass(BrevoEmailRequestDTO.class);
        verify(brevoEmailClient, times(1)).sendEmail(eq("test-api-key"), captor.capture());

        BrevoEmailRequestDTO sent = captor.getValue();
        assertTrue(sent.getHtmlContent().contains("Estimado(a) Cliente"));
        assertTrue(sent.getHtmlContent().contains("No aplica (Servicio con recibo)"));
        assertTrue(sent.getHtmlContent().contains("No aplica (Recarga de tiempo aire)"));
        assertTrue(sent.getHtmlContent().contains("123")); // Cuenta completa
    }

    @Test
    void enviarComprobantePago_clienteNulo_noEnviaEmail() {
        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("1234567890123456");
        PagoServicio pago = new PagoServicio();

        emailService.enviarComprobantePago(cuenta, null, pago);
        verifyNoInteractions(brevoEmailClient);
    }

    @Test
    void enviarComprobantePago_clienteSinCorreo_noEnviaEmail() {
        Cliente cliente = new Cliente();
        cliente.setCorreo(null);

        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("1234567890123456");

        PagoServicio pago = new PagoServicio();

        emailService.enviarComprobantePago(cuenta, cliente, pago);
        verifyNoInteractions(brevoEmailClient);
    }

    @Test
    void enviarComprobantePago_clienteConCorreoBlanco_noEnviaEmail() {
        Cliente cliente = new Cliente();
        cliente.setCorreo("   ");

        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("1234567890123456");

        PagoServicio pago = new PagoServicio();

        emailService.enviarComprobantePago(cuenta, cliente, pago);
        verifyNoInteractions(brevoEmailClient);
    }

    @Test
    void enviarComprobantePago_errorEnBrevo_noPropagaExcepcion() {
        Cliente cliente = new Cliente();
        cliente.setCorreo("cesar@example.com");

        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("1234567890123456");

        PagoServicio pago = new PagoServicio();
        pago.setNumAutorizacion("100072555");
        pago.setFechaOperacion(LocalDateTime.now());

        when(brevoEmailClient.sendEmail(any(), any()))
                .thenThrow(new RuntimeException("Error en servidor Brevo"));

        assertDoesNotThrow(() -> emailService.enviarComprobantePago(cuenta, cliente, pago));
    }
}
