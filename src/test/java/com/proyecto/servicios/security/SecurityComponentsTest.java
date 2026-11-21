package com.proyecto.servicios.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.config.PasswordEncoderConfig;
import com.proyecto.servicios.config.SecurityConfig;
import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.clientes.Cuenta;
import com.proyecto.servicios.entity.clientes.LoginCliente;
import com.proyecto.servicios.exception.AutenticacionException;
import com.proyecto.servicios.repositorys.clientes.CuentaRepository;
import com.proyecto.servicios.repositorys.clientes.LoginClienteRepository;
import com.proyecto.servicios.service.AuthService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityComponentsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void accountOwnershipValidatorValidaTodosLosCasos() {
        LoginClienteRepository loginRepo = mock(LoginClienteRepository.class);
        CuentaRepository cuentaRepo = mock(CuentaRepository.class);
        AccountOwnershipValidator validator = new AccountOwnershipValidator(loginRepo, cuentaRepo);

        assertThrows(AutenticacionException.class, () -> validator.validarPropiedadCuenta(null, "1234"));
        assertThrows(AutenticacionException.class, () -> validator.validarPropiedadCuenta("   ", "1234"));
        assertThrows(AccessDeniedException.class, () -> validator.validarPropiedadCuenta("cesar", null));
        assertThrows(AccessDeniedException.class, () -> validator.validarPropiedadCuenta("cesar", "  "));

        when(loginRepo.findByUsuario("noexiste")).thenReturn(Optional.empty());
        assertThrows(AutenticacionException.class, () -> validator.validarPropiedadCuenta("noexiste", "1234"));

        LoginCliente sinCliente = new LoginCliente();
        when(loginRepo.findByUsuario("sincliente")).thenReturn(Optional.of(sinCliente));
        assertThrows(AccessDeniedException.class, () -> validator.validarPropiedadCuenta("sincliente", "1234"));

        Cliente cliente = new Cliente();
        cliente.setId(10L);
        LoginCliente conCliente = new LoginCliente();
        conCliente.setCliente(cliente);
        when(loginRepo.findByUsuario("cesar")).thenReturn(Optional.of(conCliente));

        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("5915019417993769");
        when(cuentaRepo.findByClienteId(10L)).thenReturn(Optional.of(cuenta));

        assertThrows(AccessDeniedException.class, () -> validator.validarPropiedadCuenta("cesar", "9999999999999999"));
        assertDoesNotThrow(() -> validator.validarPropiedadCuenta("cesar", "5915019417993769"));
    }

    @Test
    void bearerTokenAuthenticationFilterAutenticaYContinuaCadena() throws Exception {
        AuthService authService = mock(AuthService.class);
        BearerTokenAuthenticationFilter filter = new BearerTokenAuthenticationFilter(authService);
        FilterChain chain = mock(FilterChain.class);

        // Sin header
        MockHttpServletRequest req1 = new MockHttpServletRequest();
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        filter.doFilter(req1, res1, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        // Header no Bearer
        MockHttpServletRequest req2 = new MockHttpServletRequest();
        req2.addHeader("Authorization", "Basic abc");
        filter.doFilter(req2, res1, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        // Header Bearer válido
        when(authService.validarTokenYObtenerUsuario("tok-123")).thenReturn(Optional.of("cesar"));
        MockHttpServletRequest req3 = new MockHttpServletRequest();
        req3.addHeader("Authorization", "Bearer tok-123");
        filter.doFilter(req3, res1, chain);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("cesar", SecurityContextHolder.getContext().getAuthentication().getPrincipal());

        // Header Bearer desplazado por otro dispositivo -> guarda atributo de error para EntryPoint
        SecurityContextHolder.clearContext();
        when(authService.validarTokenYObtenerUsuario("tok-displaced")).thenReturn(Optional.empty());
        when(authService.fueDesplazadoPorOtroDispositivo("tok-displaced")).thenReturn(true);
        MockHttpServletRequest req4 = new MockHttpServletRequest();
        req4.addHeader("Authorization", "Bearer tok-displaced");
        filter.doFilter(req4, res1, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(req4.getAttribute(BearerTokenAuthenticationFilter.ATTR_AUTH_ERROR_MESSAGE));

        // session-status omite el filtro para no extender el TTL deslizante
        MockHttpServletRequest reqStatus = new MockHttpServletRequest("GET", "/api/v1/auth/session-status");
        assertTrue(filter.shouldNotFilter(reqStatus));
    }

    @Test
    void restHandlersYConfigsFuncionanCorrectamente() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        RestAccessDeniedHandler deniedHandler = new RestAccessDeniedHandler(mapper);
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resDenied = new MockHttpServletResponse();
        deniedHandler.handle(req, resDenied, new AccessDeniedException("Forbidden"));
        assertEquals(403, resDenied.getStatus());
        assertTrue(resDenied.getContentAsString().contains("Acceso denegado"));

        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(mapper);
        MockHttpServletResponse resUnauth = new MockHttpServletResponse();
        entryPoint.commence(req, resUnauth, new BadCredentialsException("No token"));
        assertEquals(401, resUnauth.getStatus());
        assertTrue(resUnauth.getContentAsString().contains("No autorizado"));

        MockHttpServletRequest reqDisplaced = new MockHttpServletRequest();
        reqDisplaced.setAttribute(
                BearerTokenAuthenticationFilter.ATTR_AUTH_ERROR_MESSAGE,
                "Se inició sesión con tu cuenta desde otro dispositivo.");
        MockHttpServletResponse resDisplaced = new MockHttpServletResponse();
        entryPoint.commence(reqDisplaced, resDisplaced, new BadCredentialsException("Displaced"));
        assertEquals(401, resDisplaced.getStatus());
        assertTrue(resDisplaced.getContentAsString().contains("otro dispositivo"));

        PasswordEncoderConfig encoderConfig = new PasswordEncoderConfig();
        PasswordEncoder encoder = encoderConfig.passwordEncoder();
        String hash = encoder.encode("Venta2026#");
        assertTrue(encoder.matches("Venta2026#", hash));

        SecurityConfig securityConfig = new SecurityConfig();
        BearerTokenAuthenticationFilter filter = new BearerTokenAuthenticationFilter(mock(AuthService.class));
        FilterRegistrationBean<BearerTokenAuthenticationFilter> reg =
                securityConfig.disableBearerFilterServletRegistration(filter);
        assertFalse(reg.isEnabled());
    }
}
