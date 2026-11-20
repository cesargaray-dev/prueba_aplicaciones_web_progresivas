package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.auth.AbonoSaldoRequestDTO;
import com.proyecto.servicios.model.auth.ActualizarContactoRequestDTO;
import com.proyecto.servicios.model.auth.BiometriaLoginRequestDTO;
import com.proyecto.servicios.model.auth.BiometriaRegistroRequestDTO;
import com.proyecto.servicios.model.auth.CambiarContrasenaRequestDTO;
import com.proyecto.servicios.model.auth.LoginRequestDTO;
import com.proyecto.servicios.model.auth.LoginResponseDTO;
import com.proyecto.servicios.model.auth.LogoutRequestDTO;
import com.proyecto.servicios.model.auth.RegisterRequestDTO;
import com.proyecto.servicios.model.auth.UserProfileDTO;
import com.proyecto.servicios.service.AuthService;
import com.proyecto.servicios.service.BiometriaAuthService;
import com.proyecto.servicios.service.PortalCuentaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private PortalCuentaService portalCuentaService;

    @Mock
    private BiometriaAuthService biometriaAuthService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(authService);
    }

    @Test
    void testLoginEndpoint() {
        LoginRequestDTO req = LoginRequestDTO.builder()
                .usuario("cesar123")
                .contrasena("Password@123")
                .build();

        LoginResponseDTO mockResp = LoginResponseDTO.builder()
                .token("tok123")
                .usuario("cesar123")
                .clienteId(1L)
                .saldo(new BigDecimal("500.00"))
                .build();

        when(authService.login(req)).thenReturn(mockResp);

        ResponseEntity<LoginResponseDTO> response = authController.login(req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("tok123", response.getBody().getToken());
        assertEquals("cesar123", response.getBody().getUsuario());
    }

    @Test
    void testLogoutEndpointConYSinBody() {
        LogoutRequestDTO req = LogoutRequestDTO.builder().usuario("cesar123").build();

        ResponseEntity<GenericResponse> response = authController.logout("Bearer tok123", req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getCodigo());
        assertEquals("Sesión cerrada exitosamente", response.getBody().getMensaje());
        verify(authService).logout("Bearer tok123", "cesar123");

        ResponseEntity<GenericResponse> responseSinBody = authController.logout("Bearer tok123", null);
        assertEquals(HttpStatus.OK, responseSinBody.getStatusCode());
        verify(authService).logout("Bearer tok123", null);
    }

    @Test
    void testMeEndpoint() {
        UserProfileDTO mockProfile = UserProfileDTO.builder()
                .usuario("cesar123")
                .clienteId(1L)
                .saldo(new BigDecimal("500.00"))
                .build();

        when(authService.obtenerPerfilAutenticado("Bearer tok123", null)).thenReturn(mockProfile);

        ResponseEntity<UserProfileDTO> response = authController.me("Bearer tok123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("cesar123", response.getBody().getUsuario());
    }

    @Test
    void testServiciosNoDisponiblesLanzanIllegalState() {
        AuthController soloAuth = new AuthController(authService, null, null);
        assertThrows(IllegalStateException.class, () -> soloAuth.register(new RegisterRequestDTO()));
        assertThrows(IllegalStateException.class, () -> soloAuth.actualizarContacto(
                "Bearer t", new ActualizarContactoRequestDTO("a@gmail.com", "5512345678")));
        assertThrows(IllegalStateException.class, () -> soloAuth.cambiarContrasena(
                "Bearer t", new CambiarContrasenaRequestDTO("Actual123", "Nueva1234")));
        assertThrows(IllegalStateException.class, () -> soloAuth.abonarSaldo(
                "Bearer t", new AbonoSaldoRequestDTO(BigDecimal.TEN, "Ab")));
        assertThrows(IllegalStateException.class, () -> soloAuth.registrarBiometria(
                "Bearer t", new BiometriaRegistroRequestDTO("a".repeat(32), "Dev")));
        assertThrows(IllegalStateException.class, () -> soloAuth.desactivarBiometria("Bearer t"));
        assertThrows(IllegalStateException.class, () -> soloAuth.estadoBiometria("Bearer t"));
        assertThrows(IllegalStateException.class, () -> soloAuth.loginBiometrico(
                new BiometriaLoginRequestDTO("u", "tok")));

        AuthController dosArgs = new AuthController(authService, portalCuentaService);
        when(biometriaAuthService.loginConHuella(any())).thenReturn(LoginResponseDTO.builder().usuario("u").build());
        AuthController completo = new AuthController(authService, portalCuentaService, biometriaAuthService);
        assertNotNull(dosArgs);
        assertNotNull(completo.loginBiometrico(new BiometriaLoginRequestDTO("u", "tok")));
    }
}
