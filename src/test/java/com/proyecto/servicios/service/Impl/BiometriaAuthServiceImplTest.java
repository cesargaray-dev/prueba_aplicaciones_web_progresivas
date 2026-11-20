package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.clientes.Cuenta;
import com.proyecto.servicios.entity.clientes.DatoBiometrico;
import com.proyecto.servicios.entity.clientes.LoginCliente;
import com.proyecto.servicios.entity.clientes.Saldo;
import com.proyecto.servicios.exception.AutenticacionException;
import com.proyecto.servicios.exception.ValidacionException;
import com.proyecto.servicios.model.auth.BiometriaEstadoResponseDTO;
import com.proyecto.servicios.model.auth.BiometriaLoginRequestDTO;
import com.proyecto.servicios.model.auth.BiometriaRegistroRequestDTO;
import com.proyecto.servicios.model.auth.LoginResponseDTO;
import com.proyecto.servicios.model.auth.UserProfileDTO;
import com.proyecto.servicios.repositorys.clientes.ClienteRepository;
import com.proyecto.servicios.repositorys.clientes.CuentaRepository;
import com.proyecto.servicios.repositorys.clientes.DatoBiometricoRepository;
import com.proyecto.servicios.repositorys.clientes.LoginClienteRepository;
import com.proyecto.servicios.repositorys.clientes.SaldoRepository;
import com.proyecto.servicios.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BiometriaAuthServiceImplTest {

    private static final String TOKEN_VAL = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0";

    @Mock
    private AuthService authService;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private LoginClienteRepository loginClienteRepository;
    @Mock
    private DatoBiometricoRepository datoBiometricoRepository;
    @Mock
    private CuentaRepository cuentaRepository;
    @Mock
    private SaldoRepository saldoRepository;

    private BiometriaAuthServiceImpl biometriaService;

    @BeforeEach
    void setUp() {
        biometriaService = new BiometriaAuthServiceImpl(
                authService,
                clienteRepository,
                loginClienteRepository,
                datoBiometricoRepository
        );
    }

    @Test
    void registrarDesactivarYConsultarEstadoHuella_flujoCompleto() {
        UserProfileDTO perfil = UserProfileDTO.builder()
                .clienteId(10L)
                .usuario("cesar123")
                .build();
        Cliente cliente = new Cliente();
        cliente.setId(10L);
        cliente.setActivo(true);

        when(authService.obtenerPerfilAutenticado("Bearer tok", null)).thenReturn(perfil);
        when(clienteRepository.findById(10L)).thenReturn(Optional.of(cliente));
        when(datoBiometricoRepository.findByClienteIdAndTipo(10L, "HUELLA")).thenReturn(Optional.empty());
        when(datoBiometricoRepository.save(any(DatoBiometrico.class))).thenAnswer(inv -> inv.getArgument(0));

        // Desactivar cuando aún no existe registro
        BiometriaEstadoResponseDTO desactivarVacio = biometriaService.desactivarHuella("Bearer tok", null);
        assertFalse(desactivarVacio.isHabilitada());

        BiometriaEstadoResponseDTO reg = biometriaService.registrarHuella(
                "Bearer tok", null, new BiometriaRegistroRequestDTO(TOKEN_VAL, "Android Pixel"));

        assertTrue(reg.isHabilitada());
        assertEquals("HUELLA", reg.getTipo());
        assertEquals("cesar123", reg.getUsuario());

        ArgumentCaptor<DatoBiometrico> captor = ArgumentCaptor.forClass(DatoBiometrico.class);
        verify(datoBiometricoRepository).save(captor.capture());
        DatoBiometrico guardado = captor.getValue();
        assertEquals(AuthServiceImpl.cifrarSHA256(TOKEN_VAL), guardado.getHashBiometrico());
        assertTrue(guardado.getActivo());

        when(datoBiometricoRepository.findByClienteIdAndTipo(10L, "HUELLA")).thenReturn(Optional.of(guardado));
        BiometriaEstadoResponseDTO estadoActivo = biometriaService.consultarEstado("Bearer tok", null);
        assertTrue(estadoActivo.isHabilitada());

        // Re-registrar sobre registro existente
        biometriaService.registrarHuella("Bearer tok", null, new BiometriaRegistroRequestDTO(TOKEN_VAL, "Android"));

        BiometriaEstadoResponseDTO revocado = biometriaService.desactivarHuella("Bearer tok", null);
        assertFalse(revocado.isHabilitada());
        assertFalse(guardado.getActivo());
        assertEquals("REVOCADO", guardado.getHashBiometrico());

        BiometriaEstadoResponseDTO estadoInactivo = biometriaService.consultarEstado("Bearer tok", null);
        assertFalse(estadoInactivo.isHabilitada());
    }

    @Test
    void registrarHuella_rechazaTokenCortoOClienteInexistente() {
        assertThrows(ValidacionException.class, () ->
                biometriaService.registrarHuella("Bearer tok", null, null));
        assertThrows(ValidacionException.class, () ->
                biometriaService.registrarHuella("Bearer tok", null, new BiometriaRegistroRequestDTO("corto", null)));

        when(authService.obtenerPerfilAutenticado("Bearer tok", null))
                .thenReturn(UserProfileDTO.builder().clienteId(99L).usuario("cesar").build());
        when(clienteRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ValidacionException.class, () ->
                biometriaService.registrarHuella("Bearer tok", null, new BiometriaRegistroRequestDTO(TOKEN_VAL, null)));
    }

    @Test
    void loginConHuella_validoEInvalidosYBloqueoPor5Intentos() {
        Cliente cliente = new Cliente();
        cliente.setId(10L);
        cliente.setActivo(true);
        LoginCliente login = new LoginCliente();
        login.setUsuario("cesar123");
        login.setActivo(true);
        login.setBloqueado(false);
        login.setIntentosFallidos(0);
        login.setCliente(cliente);

        DatoBiometrico dato = new DatoBiometrico();
        dato.setCliente(cliente);
        dato.setTipo("HUELLA");
        dato.setActivo(true);
        dato.setHashBiometrico(AuthServiceImpl.cifrarSHA256(TOKEN_VAL));

        when(loginClienteRepository.findByUsuario("cesar123")).thenReturn(Optional.of(login));
        when(datoBiometricoRepository.findByClienteIdAndTipo(10L, "HUELLA")).thenReturn(Optional.of(dato));
        when(authService.crearSesionParaUsuario("cesar123")).thenReturn(
                LoginResponseDTO.builder().token("session-bio-1").usuario("cesar123").saldo(BigDecimal.TEN).build());

        LoginResponseDTO resp = biometriaService.loginConHuella(new BiometriaLoginRequestDTO("cesar123", TOKEN_VAL));
        assertEquals("session-bio-1", resp.getToken());

        // 4 intentos biométricos fallidos incrementan contador sin bloquear aún
        for (int i = 1; i <= 4; i++) {
            assertThrows(AutenticacionException.class, () ->
                    biometriaService.loginConHuella(new BiometriaLoginRequestDTO("cesar123", "otro_token_completamente_distinto_123456")));
            assertEquals(i, login.getIntentosFallidos());
            assertFalse(login.getBloqueado());
        }

        // 5to intento fallido bloquea la cuenta
        AutenticacionException bloqueoEx = assertThrows(AutenticacionException.class, () ->
                biometriaService.loginConHuella(new BiometriaLoginRequestDTO("cesar123", "otro_token_completamente_distinto_123456")));
        assertTrue(bloqueoEx.getMessage().contains("bloqueado"));
        assertEquals(5, login.getIntentosFallidos());
        assertTrue(login.getBloqueado());
        verify(loginClienteRepository, times(5)).save(login);

        // Reset para probar otras ramas
        login.setBloqueado(false);
        login.setIntentosFallidos(0);

        // Hash revocado
        dato.setHashBiometrico("REVOCADO");
        assertThrows(AutenticacionException.class, () ->
                biometriaService.loginConHuella(new BiometriaLoginRequestDTO("cesar123", TOKEN_VAL)));
        dato.setHashBiometrico(AuthServiceImpl.cifrarSHA256(TOKEN_VAL));

        // Credenciales incompletas
        assertThrows(AutenticacionException.class, () -> biometriaService.loginConHuella(null));
        assertThrows(AutenticacionException.class, () ->
                biometriaService.loginConHuella(new BiometriaLoginRequestDTO("", TOKEN_VAL)));
        assertThrows(AutenticacionException.class, () ->
                biometriaService.loginConHuella(new BiometriaLoginRequestDTO("cesar123", "  ")));

        // Bloqueado / inactivo / cliente inactivo / sin huella activa
        login.setBloqueado(true);
        assertThrows(AutenticacionException.class, () ->
                biometriaService.loginConHuella(new BiometriaLoginRequestDTO("cesar123", TOKEN_VAL)));
        login.setBloqueado(false);
        login.setActivo(false);
        assertThrows(AutenticacionException.class, () ->
                biometriaService.loginConHuella(new BiometriaLoginRequestDTO("cesar123", TOKEN_VAL)));
        login.setActivo(true);
        cliente.setActivo(false);
        assertThrows(AutenticacionException.class, () ->
                biometriaService.loginConHuella(new BiometriaLoginRequestDTO("cesar123", TOKEN_VAL)));
        cliente.setActivo(true);
        dato.setActivo(false);
        assertThrows(AutenticacionException.class, () ->
                biometriaService.loginConHuella(new BiometriaLoginRequestDTO("cesar123", TOKEN_VAL)));
    }

    @Test
    void authServiceImpl_crearSesionParaUsuario_cubreExitoYExcepciones() {
        AuthServiceImpl authImpl = new AuthServiceImpl(
                loginClienteRepository, cuentaRepository, saldoRepository, null);

        Cliente c = new Cliente();
        c.setId(20L);
        c.setNombre("César");
        c.setApellidoPaterno("Garay");
        c.setCorreo("cesar@gmail.com");
        c.setTelefonoMovil("5512345678");
        c.setActivo(true);

        Cuenta cta = new Cuenta();
        cta.setId(30L);
        cta.setNumeroCuenta("5225253189113705");
        cta.setCliente(c);

        Saldo saldo = new Saldo();
        saldo.setCuenta(cta);
        saldo.setSaldo(new BigDecimal("450.00"));

        LoginCliente login = new LoginCliente();
        login.setUsuario("cesar_bio");
        login.setActivo(true);
        login.setBloqueado(false);
        login.setCliente(c);

        when(loginClienteRepository.findByUsuario("cesar_bio")).thenReturn(Optional.of(login));
        when(cuentaRepository.findByClienteId(20L)).thenReturn(Optional.of(cta));
        when(saldoRepository.findByCuentaId(30L)).thenReturn(Optional.of(saldo));

        LoginResponseDTO sesion = authImpl.crearSesionParaUsuario("cesar_bio");
        assertNotNull(sesion.getToken());
        assertEquals("cesar_bio", sesion.getUsuario());
        assertEquals("5512345678", sesion.getTelefonoMovil());
        assertEquals(new BigDecimal("450.00"), sesion.getSaldo());

        assertThrows(AutenticacionException.class, () -> authImpl.crearSesionParaUsuario("  "));

        login.setBloqueado(true);
        assertThrows(AutenticacionException.class, () -> authImpl.crearSesionParaUsuario("cesar_bio"));
        login.setBloqueado(false);
        login.setActivo(false);
        assertThrows(AutenticacionException.class, () -> authImpl.crearSesionParaUsuario("cesar_bio"));
        login.setActivo(true);
        c.setActivo(false);
        assertThrows(AutenticacionException.class, () -> authImpl.crearSesionParaUsuario("cesar_bio"));
    }
}
