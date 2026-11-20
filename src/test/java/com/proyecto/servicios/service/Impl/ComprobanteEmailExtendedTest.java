package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.BrevoEmailClient;
import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.clientes.Cuenta;
import com.proyecto.servicios.entity.gestopago.PagoServicio;
import com.proyecto.servicios.model.brevo.BrevoEmailRequestDTO;
import com.proyecto.servicios.model.brevo.BrevoEmailResponseDTO;
import com.proyecto.servicios.model.brevo.EmailEnvioResultadoDTO;
import com.proyecto.servicios.service.ComprobanteEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComprobanteEmailExtendedTest {

    @Mock
    private BrevoEmailClient brevoEmailClient;

    private ComprobanteEmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        emailService = new ComprobanteEmailServiceImpl(brevoEmailClient);
        ReflectionTestUtils.setField(emailService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(emailService, "senderEmail", "sender@banco.com");
        ReflectionTestUtils.setField(emailService, "senderName", "Banco Digital");
    }

    @Test
    void enviarCodigo2FA_yBienvenida_entreganCorreoRealYDetectanPlaceholder() {
        Cliente cliente = new Cliente();
        cliente.setNombre("César");
        cliente.setApellidoPaterno("Garay");
        cliente.setCorreo("cesar@gmail.com");

        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("5225253189113705");

        when(brevoEmailClient.sendEmail(eq("test-api-key"), any(BrevoEmailRequestDTO.class)))
                .thenReturn(new BrevoEmailResponseDTO("<msg-ok@brevo>"));

        EmailEnvioResultadoDTO r2fa = emailService.enviarCodigo2FA(cliente, cuenta, "654321", 300L);
        assertTrue(r2fa.isEnviado());
        assertEquals("<msg-ok@brevo>", r2fa.getMessageId());

        EmailEnvioResultadoDTO rBienvenida = emailService.enviarCorreoBienvenida(
                cliente, cuenta, "cesargaray", new BigDecimal("200.00"));
        assertTrue(rBienvenida.isEnviado());

        cliente.setCorreo("cesar123.demo@ejemplo.com");
        EmailEnvioResultadoDTO rPlaceholder = emailService.enviarCodigo2FA(cliente, cuenta, "654321", 300L);
        assertFalse(rPlaceholder.isEnviado());
        assertTrue(Boolean.TRUE.equals(rPlaceholder.getCorreoPlaceholder()));
        assertTrue(rPlaceholder.getMensaje().contains("ejemplo"));
    }

    @Test
    void enviarComprobanteConResultado_consumeFolioYEscapaHtml() {
        Cliente cliente = new Cliente();
        cliente.setNombre("<script>alert(1)</script>");
        cliente.setCorreo("cliente@gmail.com");
        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("5225253189113705");
        PagoServicio pago = new PagoServicio();
        pago.setFolioTicket("FOL-XSS-1");
        pago.setNumAutorizacion("AUTH-1");
        pago.setMontoPagado(new BigDecimal("50.00"));
        pago.setSaldoRestante(new BigDecimal("150.00"));

        when(brevoEmailClient.sendEmail(eq("test-api-key"), any(BrevoEmailRequestDTO.class)))
                .thenReturn(new BrevoEmailResponseDTO("<msg-receipt>"));

        EmailEnvioResultadoDTO res = emailService.enviarComprobantePagoConResultado(cuenta, cliente, pago, null);
        assertTrue(res.isEnviado());

        ArgumentCaptor<BrevoEmailRequestDTO> captor = ArgumentCaptor.forClass(BrevoEmailRequestDTO.class);
        verify(brevoEmailClient).sendEmail(eq("test-api-key"), captor.capture());
        assertFalse(captor.getValue().getHtmlContent().contains("<script>"));
        assertTrue(captor.getValue().getHtmlContent().contains("&lt;script&gt;"));

        EmailEnvioResultadoDTO consumido = emailService.consumirResultadoPorFolio("FOL-XSS-1");
        assertNotNull(consumido);
        assertEquals("<msg-receipt>", consumido.getMessageId());
        assertNull(emailService.consumirResultadoPorFolio("FOL-XSS-1"));
    }

    @Test
    void enviarCorreo_manejaExcepcionBrevoYMetodosDefaultDeInterfaz() {
        when(brevoEmailClient.sendEmail(eq("test-api-key"), any(BrevoEmailRequestDTO.class)))
                .thenThrow(new RuntimeException("Brevo down"));

        Cliente cliente = new Cliente();
        cliente.setCorreo("cliente@gmail.com");
        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("5225253189113705");

        EmailEnvioResultadoDTO fallo = emailService.enviarCodigo2FA(cliente, cuenta, "112233", 120L);
        assertFalse(fallo.isEnviado());
        assertTrue(fallo.getMensaje().contains("Brevo down"));

        ComprobanteEmailService stubDefault = (c, cl, p) -> { };
        PagoServicio pago = new PagoServicio();
        EmailEnvioResultadoDTO defComprobante = stubDefault.enviarComprobantePagoConResultado(
                cuenta, cliente, pago, "alt@gmail.com");
        assertTrue(defComprobante.isEnviado());
        assertNull(stubDefault.consumirResultadoPorFolio("FOL"));
        assertFalse(stubDefault.enviarCodigo2FA(cliente, cuenta, "111222", 60L).isEnviado());
        assertFalse(stubDefault.enviarCorreoBienvenida(cliente, cuenta, "u", BigDecimal.TEN).isEnviado());
    }

    @Test
    void coberturaCompleta_apiKeyVacia_cuentaCortaYBienvenidaFallida() {
        Cliente cliente = new Cliente();
        cliente.setNombre("Ana");
        cliente.setCorreo("ana.real@gmail.com");
        Cuenta cuentaCorta = new Cuenta();
        cuentaCorta.setNumeroCuenta("12");

        // Bienvenida con placeholder email y con excepción de Brevo
        cliente.setCorreo("ana@ejemplo.com");
        assertFalse(emailService.enviarCorreoBienvenida(cliente, cuentaCorta, "ana", BigDecimal.ONE).isEnviado());

        cliente.setCorreo("ana.real@gmail.com");
        when(brevoEmailClient.sendEmail(eq("test-api-key"), any(BrevoEmailRequestDTO.class)))
                .thenThrow(new RuntimeException("SMTP timeout"));
        assertFalse(emailService.enviarCorreoBienvenida(cliente, cuentaCorta, "ana", BigDecimal.ONE).isEnviado());

        // Sin API key configurada
        ReflectionTestUtils.setField(emailService, "apiKey", "");
        EmailEnvioResultadoDTO sinKey2fa = emailService.enviarCodigo2FA(cliente, cuentaCorta, "999888", 300L);
        assertFalse(sinKey2fa.isEnviado());
        EmailEnvioResultadoDTO sinKeyBienv = emailService.enviarCorreoBienvenida(
                cliente, cuentaCorta, "ana", BigDecimal.TEN);
        assertFalse(sinKeyBienv.isEnviado());

        PagoServicio pago = new PagoServicio();
        pago.setFolioTicket("FOL-SHORT");
        pago.setMontoPagado(new BigDecimal("25.00"));
        EmailEnvioResultadoDTO sinKeyRecibo = emailService.enviarComprobantePagoConResultado(
                cuentaCorta, cliente, pago, "alt@gmail.com");
        assertFalse(sinKeyRecibo.isEnviado());
    }
}
