package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.clientes.Cuenta;
import com.proyecto.servicios.entity.clientes.LoginCliente;
import com.proyecto.servicios.entity.clientes.Saldo;
import com.proyecto.servicios.exception.AutenticacionException;
import com.proyecto.servicios.model.auth.LoginRequestDTO;
import com.proyecto.servicios.model.auth.LoginResponseDTO;
import com.proyecto.servicios.model.auth.UserProfileDTO;
import com.proyecto.servicios.repositorys.clientes.CuentaRepository;
import com.proyecto.servicios.repositorys.clientes.LoginClienteRepository;
import com.proyecto.servicios.repositorys.clientes.SaldoRepository;
import com.proyecto.servicios.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private LoginClienteRepository loginClienteRepository;

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private SaldoRepository saldoRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    private PasswordEncoder passwordEncoder;
    private AuthServiceImpl authService;

    private static final String USUARIO = "cesar123";
    private static final String PASSWORD_RAW = "Password@123";
    private static final String NUMERO_CUENTA = "1234567890123456";

    private Cliente cliente;
    private LoginCliente loginCliente;
    private Cuenta cuenta;
    private Saldo saldo;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthServiceImpl(
                loginClienteRepository, cuentaRepository, saldoRepository, redisTemplate, passwordEncoder, 300L);

        cliente = new Cliente();
        cliente.setId(10L);
        cliente.setNombre("César");
        cliente.setSegundoNombre("Iván");
        cliente.setApellidoPaterno("Sánchez");
        cliente.setApellidoMaterno("Torres");
        cliente.setCorreo("cesar@correo.com");
        cliente.setTelefonoMovil("5512345678");
        cliente.setActivo(true);

        cuenta = new Cuenta();
        cuenta.setId(20L);
        cuenta.setCliente(cliente);
        cuenta.setNumeroCuenta(NUMERO_CUENTA);
        cuenta.setEstatus("ACTIVA");

        saldo = new Saldo();
        saldo.setId(30L);
        saldo.setCuenta(cuenta);
        saldo.setSaldo(new BigDecimal("1500.00"));

        loginCliente = new LoginCliente();
        loginCliente.setId(1L);
        loginCliente.setCliente(cliente);
        loginCliente.setUsuario(USUARIO);
        loginCliente.setContrasenaHash(AuthServiceImpl.cifrarSHA256(PASSWORD_RAW));
        loginCliente.setActivo(true);
        loginCliente.setBloqueado(false);
        loginCliente.setIntentosFallidos(0);
        loginCliente.setFechaRegistro(LocalDateTime.now());
        loginCliente.setFechaActualizacion(LocalDateTime.now());
    }

    @Test
    void testLoginExitoso_migraSHA256aBCrypt() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(loginClienteRepository.findByUsuario(USUARIO)).thenReturn(Optional.of(loginCliente));
        when(cuentaRepository.findByClienteId(10L)).thenReturn(Optional.of(cuenta));
        when(saldoRepository.findByCuentaId(20L)).thenReturn(Optional.of(saldo));
        when(loginClienteRepository.save(any(LoginCliente.class))).thenAnswer(i -> i.getArgument(0));

        LoginRequestDTO request = LoginRequestDTO.builder()
                .usuario(USUARIO)
                .contrasena(PASSWORD_RAW)
                .build();

        LoginResponseDTO response = authService.login(request);

        assertNotNull(response);
        assertNotNull(response.getToken());
        assertEquals(USUARIO, response.getUsuario());
        assertEquals(10L, response.getClienteId());
        assertEquals("César Iván Sánchez Torres", response.getNombreCompleto());
        assertEquals("cesar@correo.com", response.getEmail());
        assertEquals(NUMERO_CUENTA, response.getNumeroCuenta());
        assertEquals(new BigDecimal("1500.00"), response.getSaldo());
        assertNotNull(response.getUltimoAcceso());

        assertTrue(loginCliente.getContrasenaHash().startsWith("$2"));
        assertTrue(passwordEncoder.matches(PASSWORD_RAW, loginCliente.getContrasenaHash()));
        assertEquals(0, loginCliente.getIntentosFallidos());
        verify(loginClienteRepository).save(loginCliente);

        // Segundo login ya usa el hash BCrypt migrado
        LoginResponseDTO secondLogin = authService.login(request);
        assertNotNull(secondLogin.getToken());
    }

    @Test
    void testLoginExitoso_conFalloRedisYCuentaNull() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("Redis down")).when(valueOperations).set(any(), any(), anyLong(), any());

        cliente.setSegundoNombre(null);
        cliente.setApellidoMaterno(null);
        when(loginClienteRepository.findByUsuario(USUARIO)).thenReturn(Optional.of(loginCliente));
        when(cuentaRepository.findByClienteId(10L)).thenReturn(Optional.empty());
        when(loginClienteRepository.save(any(LoginCliente.class))).thenAnswer(i -> i.getArgument(0));

        LoginRequestDTO request = LoginRequestDTO.builder()
                .usuario(USUARIO)
                .contrasena(PASSWORD_RAW)
                .build();

        LoginResponseDTO response = authService.login(request);
        assertNotNull(response);
        assertNull(response.getNumeroCuenta());
        assertEquals(BigDecimal.ZERO, response.getSaldo());
    }

    @Test
    void testLoginUsuarioNoExisteLanzaExcepcion() {
        when(loginClienteRepository.findByUsuario("desconocido")).thenReturn(Optional.empty());

        LoginRequestDTO request = LoginRequestDTO.builder()
                .usuario("desconocido")
                .contrasena(PASSWORD_RAW)
                .build();

        AutenticacionException ex = assertThrows(AutenticacionException.class, () -> authService.login(request));
        assertEquals("Credenciales inválidas", ex.getMessage());
    }

    @Test
    void testLoginUsuarioInactivoLanzaExcepcion() {
        loginCliente.setActivo(false);
        when(loginClienteRepository.findByUsuario(USUARIO)).thenReturn(Optional.of(loginCliente));

        LoginRequestDTO request = LoginRequestDTO.builder()
                .usuario(USUARIO)
                .contrasena(PASSWORD_RAW)
                .build();

        AutenticacionException ex = assertThrows(AutenticacionException.class, () -> authService.login(request));
        assertTrue(ex.getMessage().contains("inactivo"));
    }

    @Test
    void testLoginUsuarioBloqueadoLanzaExcepcion() {
        loginCliente.setBloqueado(true);
        when(loginClienteRepository.findByUsuario(USUARIO)).thenReturn(Optional.of(loginCliente));

        LoginRequestDTO request = LoginRequestDTO.builder()
                .usuario(USUARIO)
                .contrasena(PASSWORD_RAW)
                .build();

        AutenticacionException ex = assertThrows(AutenticacionException.class, () -> authService.login(request));
        assertTrue(ex.getMessage().contains("bloqueado"));
    }

    @Test
    void testLoginContrasenaIncorrectaIncrementaIntentosSinFiltrarConteo() {
        when(loginClienteRepository.findByUsuario(USUARIO)).thenReturn(Optional.of(loginCliente));
        when(loginClienteRepository.save(any(LoginCliente.class))).thenAnswer(i -> i.getArgument(0));

        LoginRequestDTO request = LoginRequestDTO.builder()
                .usuario(USUARIO)
                .contrasena("PasswordErroneo")
                .build();

        AutenticacionException ex = assertThrows(AutenticacionException.class, () -> authService.login(request));
        assertEquals("Credenciales inválidas", ex.getMessage());
        assertFalse(ex.getMessage().contains("/5"));
        assertEquals(1, loginCliente.getIntentosFallidos());
        assertFalse(loginCliente.getBloqueado());
        verify(loginClienteRepository).save(loginCliente);
    }

    @Test
    void testLoginBloqueoTras5IntentosFallidos() {
        loginCliente.setIntentosFallidos(4);
        when(loginClienteRepository.findByUsuario(USUARIO)).thenReturn(Optional.of(loginCliente));
        when(loginClienteRepository.save(any(LoginCliente.class))).thenAnswer(i -> i.getArgument(0));

        LoginRequestDTO request = LoginRequestDTO.builder()
                .usuario(USUARIO)
                .contrasena("PasswordErroneo")
                .build();

        AutenticacionException ex = assertThrows(AutenticacionException.class, () -> authService.login(request));
        assertTrue(ex.getMessage().contains("bloqueado tras alcanzar 5 intentos"));
        assertEquals(5, loginCliente.getIntentosFallidos());
        assertTrue(loginCliente.getBloqueado());
        verify(loginClienteRepository).save(loginCliente);
    }

    @Test
    void testLoginClienteInactivoLanzaExcepcion() {
        cliente.setActivo(false);
        when(loginClienteRepository.findByUsuario(USUARIO)).thenReturn(Optional.of(loginCliente));

        LoginRequestDTO request = LoginRequestDTO.builder()
                .usuario(USUARIO)
                .contrasena(PASSWORD_RAW)
                .build();

        AutenticacionException ex = assertThrows(AutenticacionException.class, () -> authService.login(request));
        assertTrue(ex.getMessage().contains("cliente asociado no se encuentra activo"));
    }

    @Test
    void testLoginClienteNuloLanzaExcepcion() {
        loginCliente.setCliente(null);
        when(loginClienteRepository.findByUsuario(USUARIO)).thenReturn(Optional.of(loginCliente));

        LoginRequestDTO request = LoginRequestDTO.builder()
                .usuario(USUARIO)
                .contrasena(PASSWORD_RAW)
                .build();

        AutenticacionException ex = assertThrows(AutenticacionException.class, () -> authService.login(request));
        assertTrue(ex.getMessage().contains("cliente asociado no se encuentra activo"));
    }

    @Test
    void testLoginRequestNuloLanzaExcepcion() {
        assertThrows(AutenticacionException.class, () -> authService.login(null));
        assertThrows(AutenticacionException.class, () -> authService.login(new LoginRequestDTO()));
    }

    @Test
    void testLogoutConTokenValido_revocaSoloEseTokenYValidaPropietario() {
        when(loginClienteRepository.findByUsuario(USUARIO)).thenReturn(Optional.of(loginCliente));
        when(cuentaRepository.findByClienteId(10L)).thenReturn(Optional.of(cuenta));
        when(saldoRepository.findByCuentaId(20L)).thenReturn(Optional.of(saldo));
        when(loginClienteRepository.save(any(LoginCliente.class))).thenAnswer(i -> i.getArgument(0));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        String tok1 = authService.login(new LoginRequestDTO(USUARIO, PASSWORD_RAW)).getToken();
        String tok2 = authService.login(new LoginRequestDTO(USUARIO, PASSWORD_RAW)).getToken();

        // Logout con otro usuario en body -> 403 AccessDeniedException
        assertThrows(AccessDeniedException.class, () -> authService.logout("Bearer " + tok1, "atacante"));
        assertTrue(authService.validarTokenYObtenerUsuario(tok1).isPresent());

        // Logout exitoso con propietario correcto -> revoca tok1, mantiene tok2
        assertDoesNotThrow(() -> authService.logout("Bearer " + tok1, USUARIO));
        verify(redisTemplate).delete("auth:session:token:" + tok1);
        verify(setOperations).remove("auth:session:user:" + USUARIO, tok1);

        assertTrue(authService.validarTokenYObtenerUsuario(tok1).isEmpty());
        assertTrue(authService.validarTokenYObtenerUsuario(tok2).isPresent());
    }

    @Test
    void testLogoutConFalloEnRedisYTokenInvalido() {
        when(loginClienteRepository.findByUsuario(USUARIO)).thenReturn(Optional.of(loginCliente));
        when(loginClienteRepository.save(any(LoginCliente.class))).thenAnswer(i -> i.getArgument(0));

        AuthServiceImpl localAuth = new AuthServiceImpl(
                loginClienteRepository, cuentaRepository, saldoRepository, redisTemplate, passwordEncoder, 300L);
        String tok = localAuth.crearSesionParaUsuario(USUARIO).getToken();

        doThrow(new RuntimeException("Redis err")).when(redisTemplate).delete(anyString());
        assertDoesNotThrow(() -> localAuth.logout("Bearer " + tok, null));

        assertThrows(AutenticacionException.class, () -> localAuth.logout(null, "usuarioSolo"));
        assertThrows(AutenticacionException.class, () -> localAuth.logout("Bearer ", null));
        assertThrows(AutenticacionException.class, () -> localAuth.logout("Bearer token-inexistente", null));
    }

    @Test
    void testObtenerPerfilAutenticadoIgnoraUsuarioParamYUsaSoloBearerToken() {
        when(loginClienteRepository.findByUsuario(USUARIO)).thenReturn(Optional.of(loginCliente));
        when(cuentaRepository.findByClienteId(10L)).thenReturn(Optional.of(cuenta));
        when(saldoRepository.findByCuentaId(20L)).thenReturn(Optional.of(saldo));
        when(loginClienteRepository.save(any(LoginCliente.class))).thenAnswer(i -> i.getArgument(0));

        String token = authService.login(new LoginRequestDTO(USUARIO, PASSWORD_RAW)).getToken();

        // Aunque se pase usuario="otro_usuario", el perfil corresponde estrictamente al token Bearer
        UserProfileDTO perfil = authService.obtenerPerfilAutenticado("Bearer " + token, "otro_usuario");

        assertNotNull(perfil);
        assertEquals(10L, perfil.getClienteId());
        assertEquals(USUARIO, perfil.getUsuario());
        assertEquals("César", perfil.getNombre());
        assertEquals("Iván", perfil.getSegundoNombre());
        assertEquals("Sánchez", perfil.getApellidoPaterno());
        assertEquals("Torres", perfil.getApellidoMaterno());
        assertEquals("César Iván Sánchez Torres", perfil.getNombreCompleto());
        assertEquals("cesar@correo.com", perfil.getEmail());
        assertEquals(NUMERO_CUENTA, perfil.getNumeroCuenta());
        assertEquals(new BigDecimal("1500.00"), perfil.getSaldo());
        assertTrue(perfil.getActivo());

        // Sin token Bearer (aunque se envíe ?usuario=cesar123) debe rechazar
        assertThrows(AutenticacionException.class, () -> authService.obtenerPerfilAutenticado(null, USUARIO));
    }

    @Test
    void testObtenerPerfilAutenticadoConTokenDesdeRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:session:token:tokenABC")).thenReturn(USUARIO);
        when(loginClienteRepository.findByUsuario(USUARIO)).thenReturn(Optional.of(loginCliente));
        when(cuentaRepository.findByClienteId(10L)).thenReturn(Optional.of(cuenta));
        when(saldoRepository.findByCuentaId(20L)).thenReturn(Optional.of(saldo));

        UserProfileDTO perfil = authService.obtenerPerfilAutenticado("Bearer tokenABC", null);

        assertNotNull(perfil);
        assertEquals(USUARIO, perfil.getUsuario());
        verify(redisTemplate).expire("auth:session:token:tokenABC", 300L, TimeUnit.SECONDS);
    }

    @Test
    void testObtenerPerfilAutenticado_conFalloRedisLanzaExcepcion() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis error"));

        assertThrows(AutenticacionException.class, () -> authService.obtenerPerfilAutenticado("Bearer tokenErr", null));
    }

    @Test
    void testObtenerPerfilSinSesionYEstadosInvalidosLanzaExcepcion() {
        AutenticacionException ex = assertThrows(AutenticacionException.class,
                () -> authService.obtenerPerfilAutenticado(null, null));
        assertTrue(ex.getMessage().contains("no autorizada o expirada"));

        when(loginClienteRepository.findByUsuario(USUARIO)).thenReturn(Optional.of(loginCliente));
        when(loginClienteRepository.save(any(LoginCliente.class))).thenAnswer(i -> i.getArgument(0));
        String tok = authService.crearSesionParaUsuario(USUARIO).getToken();

        loginCliente.setBloqueado(true);
        assertThrows(AutenticacionException.class, () -> authService.obtenerPerfilAutenticado("Bearer " + tok, null));

        loginCliente.setBloqueado(false);
        loginCliente.setActivo(false);
        assertThrows(AutenticacionException.class, () -> authService.obtenerPerfilAutenticado("Bearer " + tok, null));

        loginCliente.setActivo(true);
        cliente.setActivo(false);
        assertThrows(AutenticacionException.class, () -> authService.obtenerPerfilAutenticado("Bearer " + tok, null));

        when(loginClienteRepository.findByUsuario(USUARIO)).thenReturn(Optional.empty());
        assertThrows(AutenticacionException.class, () -> authService.obtenerPerfilAutenticado("Bearer " + tok, null));
    }

    @Test
    void testExpiracionTTL_evictionYInvalidarOtrasSesiones() throws Exception {
        AuthServiceImpl shortTtl = new AuthServiceImpl(
                loginClienteRepository, cuentaRepository, saldoRepository, redisTemplate, passwordEncoder, 1L);

        when(loginClienteRepository.findByUsuario(USUARIO)).thenReturn(Optional.of(loginCliente));
        when(loginClienteRepository.save(any(LoginCliente.class))).thenAnswer(i -> i.getArgument(0));

        String tok1 = shortTtl.crearSesionParaUsuario(USUARIO).getToken();
        String tok2 = shortTtl.crearSesionParaUsuario(USUARIO).getToken();

        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("auth:session:user:" + USUARIO)).thenReturn(Set.of(tok1, tok2));

        shortTtl.invalidarOtrasSesiones(USUARIO, "Bearer " + tok2);
        assertTrue(shortTtl.validarTokenYObtenerUsuario(tok1).isEmpty());
        assertTrue(shortTtl.validarTokenYObtenerUsuario(tok2).isPresent());

        Thread.sleep(1200L);
        assertTrue(shortTtl.validarTokenYObtenerUsuario(tok2).isEmpty());

        // Probar evictExpiredSessions con token expirado en memoria
        String tok3 = shortTtl.crearSesionParaUsuario(USUARIO).getToken();
        Thread.sleep(1200L);
        shortTtl.evictExpiredSessions();
        assertThrows(AutenticacionException.class, () -> shortTtl.logout("Bearer " + tok3, null));
    }

    @Test
    void testVerificarYCodificarContrasena_yDefaultInterfaceMethods() {
        String bcrypt = authService.codificarContrasena("Secreta2026!");
        assertTrue(authService.verificarContrasena("Secreta2026!", bcrypt));
        assertFalse(authService.verificarContrasena("Otra2026!", bcrypt));
        assertFalse(authService.verificarContrasena(null, bcrypt));
        assertFalse(authService.verificarContrasena("Secreta2026!", null));
        assertFalse(authService.verificarContrasena("Secreta2026!", "   "));

        AuthService stub = new AuthService() {
            @Override
            public LoginResponseDTO login(LoginRequestDTO request) { return null; }
            @Override
            public void logout(String token, String usuario) {}
            @Override
            public UserProfileDTO obtenerPerfilAutenticado(String token, String usuario) { return null; }
        };
        assertThrows(UnsupportedOperationException.class, () -> stub.crearSesionParaUsuario("u"));
        assertTrue(stub.validarTokenYObtenerUsuario("t").isEmpty());
        assertFalse(stub.verificarContrasena("a", "b"));
        assertEquals("a", stub.codificarContrasena("a"));
        assertDoesNotThrow(() -> stub.invalidarOtrasSesiones("u", "t"));
    }

    @Test
    void testCifrarSHA256Valido() {
        String hash1 = AuthServiceImpl.cifrarSHA256("password");
        String hash2 = AuthServiceImpl.cifrarSHA256("password");
        assertNotNull(hash1);
        assertEquals(64, hash1.length());
        assertEquals(hash1, hash2);
        assertNull(AuthServiceImpl.cifrarSHA256(null));
    }
}
