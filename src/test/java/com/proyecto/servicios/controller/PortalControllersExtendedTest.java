package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.auth.AbonoSaldoRequestDTO;
import com.proyecto.servicios.model.auth.ActualizarContactoRequestDTO;
import com.proyecto.servicios.model.auth.BiometriaEstadoResponseDTO;
import com.proyecto.servicios.model.auth.BiometriaLoginRequestDTO;
import com.proyecto.servicios.model.auth.BiometriaRegistroRequestDTO;
import com.proyecto.servicios.model.auth.CambiarContrasenaRequestDTO;
import com.proyecto.servicios.model.auth.LoginRequestDTO;
import com.proyecto.servicios.model.auth.LoginResponseDTO;
import com.proyecto.servicios.model.auth.RegisterRequestDTO;
import com.proyecto.servicios.model.auth.UserProfileDTO;
import com.proyecto.servicios.model.brevo.EmailEnvioResultadoDTO;
import com.proyecto.servicios.model.gestopago.GenerarCodigo2FARequestDTO;
import com.proyecto.servicios.model.gestopago.GenerarCodigo2FAResponseDTO;
import com.proyecto.servicios.model.gestopago.PagoServicioRequestDTO;
import com.proyecto.servicios.model.gestopago.PagoServicioResponseDTO;
import com.proyecto.servicios.model.gestopago.ReenviarComprobanteRequestDTO;
import com.proyecto.servicios.service.AuthService;
import com.proyecto.servicios.service.BiometriaAuthService;
import com.proyecto.servicios.service.NotificacionPagoService;
import com.proyecto.servicios.service.PagoServicioService;
import com.proyecto.servicios.service.PortalCuentaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortalControllersExtendedTest {

    @Mock
    private AuthService authService;
    @Mock
    private PortalCuentaService portalCuentaService;
    @Mock
    private BiometriaAuthService biometriaAuthService;
    @Mock
    private PagoServicioService pagoServicioService;
    @Mock
    private NotificacionPagoService notificacionPagoService;

    @Test
    void authController_nuevosEndpointsYLoginEnriquecido() {
        AuthController controller = new AuthController(authService, portalCuentaService, biometriaAuthService);

        LoginResponseDTO rawLogin = LoginResponseDTO.builder().usuario("cesar").build();
        LoginResponseDTO enrichedLogin = LoginResponseDTO.builder()
                .usuario("cesar").telefonoMovil("5512345678").build();

        when(authService.login(any())).thenReturn(rawLogin);
        when(portalCuentaService.enriquecerLoginConTelefono(rawLogin)).thenReturn(enrichedLogin);
        ResponseEntity<LoginResponseDTO> loginRes = controller.login(new LoginRequestDTO("cesar", "pass"));
        assertEquals("5512345678", loginRes.getBody().getTelefonoMovil());

        when(portalCuentaService.registrarClientePortal(any())).thenReturn(enrichedLogin);
        ResponseEntity<LoginResponseDTO> regRes = controller.register(new RegisterRequestDTO());
        assertEquals(HttpStatus.CREATED, regRes.getStatusCode());

        UserProfileDTO perfil = UserProfileDTO.builder()
                .email("nuevo@gmail.com").telefonoMovil("5512345678").saldo(new BigDecimal("500.00")).build();
        when(portalCuentaService.actualizarContacto("Bearer t", null, new ActualizarContactoRequestDTO("nuevo@gmail.com", "5512345678")))
                .thenReturn(perfil);
        assertEquals("nuevo@gmail.com", controller.actualizarContacto(
                "Bearer t", new ActualizarContactoRequestDTO("nuevo@gmail.com", "5512345678")).getBody().getEmail());

        CambiarContrasenaRequestDTO passReq = new CambiarContrasenaRequestDTO("Actual123", "Nueva2026");
        ResponseEntity<GenericResponse> passRes = controller.cambiarContrasena("Bearer t", passReq);
        assertEquals(HttpStatus.OK, passRes.getStatusCode());
        verify(portalCuentaService).cambiarContrasena("Bearer t", null, passReq);

        when(portalCuentaService.abonarSaldo("Bearer t", null, new AbonoSaldoRequestDTO(new BigDecimal("100.00"), "Dep")))
                .thenReturn(perfil);
        assertEquals(new BigDecimal("500.00"), controller.abonarSaldo(
                "Bearer t", new AbonoSaldoRequestDTO(new BigDecimal("100.00"), "Dep")).getBody().getSaldo());

        BiometriaEstadoResponseDTO estadoOn = BiometriaEstadoResponseDTO.builder()
                .habilitada(true).usuario("cesar").tipo("HUELLA").build();
        when(biometriaAuthService.registrarHuella(eq("Bearer t"), isNull(), any())).thenReturn(estadoOn);
        assertTrue(controller.registrarBiometria(
                "Bearer t", new BiometriaRegistroRequestDTO("a".repeat(40), "Pixel")).getBody().isHabilitada());

        when(biometriaAuthService.consultarEstado("Bearer t", null)).thenReturn(estadoOn);
        assertTrue(controller.estadoBiometria("Bearer t").getBody().isHabilitada());

        when(biometriaAuthService.desactivarHuella("Bearer t", null))
                .thenReturn(BiometriaEstadoResponseDTO.builder().habilitada(false).build());
        assertFalse(controller.desactivarBiometria("Bearer t").getBody().isHabilitada());

        when(biometriaAuthService.loginConHuella(any())).thenReturn(rawLogin);
        assertEquals("5512345678", controller.loginBiometrico(
                new BiometriaLoginRequestDTO("cesar", "a".repeat(40))).getBody().getTelefonoMovil());
    }

    @Test
    void pagoServicioController_flujo2FAPagoYReenvioEnriquecidos() {
        PagoServicioController controller = new PagoServicioController(pagoServicioService, notificacionPagoService);

        GenerarCodigo2FAResponseDTO otpRaw = GenerarCodigo2FAResponseDTO.builder().codigo2FA("123456").build();
        GenerarCodigo2FAResponseDTO otpEnriched = GenerarCodigo2FAResponseDTO.builder()
                .codigo2FA("123456").correoEnviado(true).correoDestino("ces***@gmail.com").build();
        when(pagoServicioService.generarCodigo2FA(any())).thenReturn(otpRaw);
        when(notificacionPagoService.enriquecerYEnviar2FA(otpRaw)).thenReturn(otpEnriched);

        GenerarCodigo2FAResponseDTO otpRes = controller.generarCodigo2FA(new GenerarCodigo2FARequestDTO("5225253189113705")).getBody();
        assertTrue(otpRes.getCorreoEnviado());

        PagoServicioResponseDTO pagoRaw = PagoServicioResponseDTO.builder().folioTicket("FOL-1").build();
        PagoServicioResponseDTO pagoEnriched = PagoServicioResponseDTO.builder()
                .folioTicket("FOL-1").correoEnviado(true).correoDestino("cesar@gmail.com").build();
        when(pagoServicioService.procesarPagoServicio(any())).thenReturn(pagoRaw);
        when(notificacionPagoService.enriquecerRespuestaPago(pagoRaw)).thenReturn(pagoEnriched);

        PagoServicioRequestDTO payReq = PagoServicioRequestDTO.builder().numeroCuenta("5225253189113705").build();
        PagoServicioResponseDTO payRes = controller.pagarServicio(payReq).getBody();
        verify(notificacionPagoService).validarAntesDePagar(payReq);
        assertTrue(payRes.getCorreoEnviado());

        EmailEnvioResultadoDTO reenvio = EmailEnvioResultadoDTO.builder().enviado(true).build();
        when(notificacionPagoService.reenviarComprobante(any())).thenReturn(reenvio);
        assertTrue(controller.reenviarComprobante(
                new ReenviarComprobanteRequestDTO("5225253189113705", "FOL-1", null)).getBody().isEnviado());
    }
}
