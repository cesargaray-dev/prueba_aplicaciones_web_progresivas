package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.clientes.Cuenta;
import com.proyecto.servicios.entity.clientes.LoginCliente;
import com.proyecto.servicios.entity.clientes.Saldo;
import com.proyecto.servicios.exception.AutenticacionException;
import com.proyecto.servicios.exception.CorreoDuplicadoException;
import com.proyecto.servicios.exception.CuentaNoEncontradaException;
import com.proyecto.servicios.exception.CurpDuplicadaException;
import com.proyecto.servicios.exception.RfcDuplicadoException;
import com.proyecto.servicios.exception.ValidacionException;
import com.proyecto.servicios.model.auth.AbonoSaldoRequestDTO;
import com.proyecto.servicios.model.auth.ActualizarContactoRequestDTO;
import com.proyecto.servicios.model.auth.CambiarContrasenaRequestDTO;
import com.proyecto.servicios.model.auth.LoginRequestDTO;
import com.proyecto.servicios.model.auth.LoginResponseDTO;
import com.proyecto.servicios.model.auth.RegisterRequestDTO;
import com.proyecto.servicios.model.auth.UserProfileDTO;
import com.proyecto.servicios.model.brevo.EmailEnvioResultadoDTO;
import com.proyecto.servicios.repositorys.clientes.ClienteRepository;
import com.proyecto.servicios.repositorys.clientes.CuentaRepository;
import com.proyecto.servicios.repositorys.clientes.LoginClienteRepository;
import com.proyecto.servicios.repositorys.clientes.SaldoRepository;
import com.proyecto.servicios.service.AuthService;
import com.proyecto.servicios.service.ComprobanteEmailService;
import com.proyecto.servicios.service.PortalCuentaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortalCuentaServiceImplTest {

    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private LoginClienteRepository loginClienteRepository;
    @Mock
    private CuentaRepository cuentaRepository;
    @Mock
    private SaldoRepository saldoRepository;
    @Mock
    private AuthService authService;
    @Mock
    private ComprobanteEmailService comprobanteEmailService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private PasswordEncoder passwordEncoder;
    private PortalCuentaServiceImpl service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        service = new PortalCuentaServiceImpl(
                clienteRepository,
                loginClienteRepository,
                cuentaRepository,
                saldoRepository,
                authService,
                comprobanteEmailService,
                null,
                passwordEncoder
        );
    }

    @Test
    void registrarClientePortal_exito_creaCuentaConBCryptYEnviaBienvenida() {
        RegisterRequestDTO req = crearRegistroValido();

        when(loginClienteRepository.findByUsuario("nuevo_user")).thenReturn(Optional.empty());
        when(clienteRepository.existsByCorreo("nuevo.cliente@gmail.com")).thenReturn(false);
        when(clienteRepository.existsByCurp(any())).thenReturn(false);
        when(clienteRepository.existsByRfc(any())).thenReturn(false);
        when(cuentaRepository.existsByNumeroCuenta(any())).thenReturn(false);
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            c.setId(99L);
            assertTrue(c.getLoginCliente().getContrasenaHash().startsWith("$2"));
            assertTrue(passwordEncoder.matches("Segura2026", c.getLoginCliente().getContrasenaHash()));
            return c;
        });

        LoginResponseDTO loginMock = LoginResponseDTO.builder()
                .token("tok-123")
                .usuario("nuevo_user")
                .clienteId(99L)
                .email("nuevo.cliente@gmail.com")
                .numeroCuenta("5123456789012345")
                .saldo(new BigDecimal("200.00"))
                .build();
        when(authService.login(any(LoginRequestDTO.class))).thenReturn(loginMock);
        when(comprobanteEmailService.enviarCorreoBienvenida(any(), any(), eq("nuevo_user"), any()))
                .thenReturn(EmailEnvioResultadoDTO.builder().enviado(true).build());

        LoginResponseDTO resp = service.registrarClientePortal(req);

        assertNotNull(resp);
        assertEquals("tok-123", resp.getToken());
        assertEquals("5512345678", resp.getTelefonoMovil());
        verify(clienteRepository).save(any(Cliente.class));
        verify(comprobanteEmailService).enviarCorreoBienvenida(any(), any(), eq("nuevo_user"), eq(new BigDecimal("200.00")));
    }

    @Test
    void registrarClientePortal_conTransactionManager() {
        PortalCuentaServiceImpl txService = new PortalCuentaServiceImpl(
                clienteRepository, loginClienteRepository, cuentaRepository, saldoRepository,
                authService, comprobanteEmailService, transactionManager);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(loginClienteRepository.findByUsuario("nuevo_user")).thenReturn(Optional.empty());
        when(clienteRepository.existsByCorreo("nuevo.cliente@gmail.com")).thenReturn(false);
        when(clienteRepository.existsByCurp(any())).thenReturn(false);
        when(clienteRepository.existsByRfc(any())).thenReturn(false);
        when(cuentaRepository.existsByNumeroCuenta(any())).thenReturn(false);
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            c.setId(88L);
            return c;
        });
        when(authService.login(any())).thenReturn(LoginResponseDTO.builder().usuario("nuevo_user").clienteId(88L).build());

        LoginResponseDTO resp = txService.registrarClientePortal(crearRegistroValido());
        assertNotNull(resp);
        verify(transactionManager).commit(any());
    }

    @Test
    void registrarClientePortal_rechazaValidacionesYDuplicados() {
        RegisterRequestDTO telInvalido = crearRegistroValido();
        telInvalido.setTelefonoMovil("1111111111");
        assertThrows(ValidacionException.class, () -> service.registrarClientePortal(telInvalido));

        RegisterRequestDTO correoEjemplo = crearRegistroValido();
        correoEjemplo.setCorreo("demo@ejemplo.com");
        assertThrows(ValidacionException.class, () -> service.registrarClientePortal(correoEjemplo));

        RegisterRequestDTO menorEdad = crearRegistroValido();
        menorEdad.setFechaNacimiento("2020-01-01");
        assertThrows(ValidacionException.class, () -> service.registrarClientePortal(menorEdad));

        RegisterRequestDTO usuarioDuplicado = crearRegistroValido();
        when(loginClienteRepository.findByUsuario("nuevo_user")).thenReturn(Optional.of(new LoginCliente()));
        assertThrows(ValidacionException.class, () -> service.registrarClientePortal(usuarioDuplicado));

        RegisterRequestDTO correoDuplicado = crearRegistroValido();
        when(loginClienteRepository.findByUsuario("nuevo_user")).thenReturn(Optional.empty());
        when(clienteRepository.existsByCorreo("nuevo.cliente@gmail.com")).thenReturn(true);
        assertThrows(CorreoDuplicadoException.class, () -> service.registrarClientePortal(correoDuplicado));
    }

    @Test
    void registrarClientePortal_rechazaCurpORfcDuplicado() {
        RegisterRequestDTO reqCurp = crearRegistroValido();
        reqCurp.setCurp("GAGC990101HDFRRN01");
        when(loginClienteRepository.findByUsuario("nuevo_user")).thenReturn(Optional.empty());
        when(clienteRepository.existsByCorreo("nuevo.cliente@gmail.com")).thenReturn(false);
        when(clienteRepository.existsByCurp("GAGC990101HDFRRN01")).thenReturn(true);
        assertThrows(CurpDuplicadaException.class, () -> service.registrarClientePortal(reqCurp));

        RegisterRequestDTO reqRfc = crearRegistroValido();
        reqRfc.setRfc("GAGC990101ABC");
        when(clienteRepository.existsByCurp(any())).thenReturn(false);
        when(clienteRepository.existsByRfc("GAGC990101ABC")).thenReturn(true);
        assertThrows(RfcDuplicadoException.class, () -> service.registrarClientePortal(reqRfc));
    }

    @Test
    void actualizarContacto_actualizaCorreoYTelefonoCorrectamente() {
        UserProfileDTO perfil = UserProfileDTO.builder()
                .clienteId(10L)
                .usuario("cesar")
                .email("old@gmail.com")
                .numeroCuenta("5225253189113705")
                .build();
        Cliente cliente = new Cliente();
        cliente.setId(10L);
        cliente.setCorreo("old@gmail.com");
        cliente.setTelefonoMovil("5511112222");

        when(authService.obtenerPerfilAutenticado("Bearer t", null)).thenReturn(perfil);
        when(clienteRepository.findById(10L)).thenReturn(Optional.of(cliente));
        when(clienteRepository.existsByCorreo("nuevo@gmail.com")).thenReturn(false);

        UserProfileDTO actualizado = service.actualizarContacto(
                "Bearer t",
                null,
                new ActualizarContactoRequestDTO("nuevo@gmail.com", "5587654321")
        );

        assertEquals("nuevo@gmail.com", actualizado.getEmail());
        assertEquals("5587654321", actualizado.getTelefonoMovil());
        verify(clienteRepository).save(cliente);
    }

    @Test
    void abonarSaldo_incrementaSaldoConBloqueoPesimista() {
        UserProfileDTO perfil = UserProfileDTO.builder()
                .clienteId(10L)
                .numeroCuenta("5225253189113705")
                .saldo(new BigDecimal("100.00"))
                .build();
        Cliente cliente = new Cliente();
        cliente.setTelefonoMovil("5512345678");
        Cuenta cuenta = new Cuenta();
        cuenta.setId(5L);
        cuenta.setNumeroCuenta("5225253189113705");
        cuenta.setCliente(cliente);
        Saldo saldo = new Saldo();
        saldo.setCuenta(cuenta);
        saldo.setSaldo(new BigDecimal("100.00"));

        when(authService.obtenerPerfilAutenticado("Bearer t", null)).thenReturn(perfil);
        when(cuentaRepository.findByNumeroCuentaForUpdate("5225253189113705")).thenReturn(Optional.of(cuenta));
        when(saldoRepository.findByCuentaId(5L)).thenReturn(Optional.of(saldo));

        UserProfileDTO res = service.abonarSaldo(
                "Bearer t",
                null,
                new AbonoSaldoRequestDTO(new BigDecimal("250.50"), "Recarga demo")
        );

        assertEquals(new BigDecimal("350.50"), res.getSaldo());
        assertEquals("5512345678", res.getTelefonoMovil());
        verify(saldoRepository).save(saldo);

        // Concepto blanco y cliente null
        cuenta.setCliente(null);
        UserProfileDTO res2 = service.abonarSaldo(
                "Bearer t", null, new AbonoSaldoRequestDTO(new BigDecimal("10.00"), "   "));
        assertEquals(new BigDecimal("360.50"), res2.getSaldo());
        assertEquals("Abono digital de fondos", saldo.getConcepto());
    }

    @Test
    void abonarSaldo_montoInvalidoOCuentaInexistente_lanzaExcepcion() {
        assertThrows(ValidacionException.class, () ->
                service.abonarSaldo("Bearer t", null, null));
        assertThrows(ValidacionException.class, () ->
                service.abonarSaldo("Bearer t", null, new AbonoSaldoRequestDTO(BigDecimal.ZERO, "Invalid")));

        UserProfileDTO perfil = UserProfileDTO.builder().numeroCuenta("9999999999999999").build();
        when(authService.obtenerPerfilAutenticado("Bearer t", null)).thenReturn(perfil);
        when(cuentaRepository.findByNumeroCuentaForUpdate("9999999999999999")).thenReturn(Optional.empty());
        assertThrows(CuentaNoEncontradaException.class, () ->
                service.abonarSaldo("Bearer t", null, new AbonoSaldoRequestDTO(new BigDecimal("50.00"), "Test")));

        Cuenta cuenta = new Cuenta();
        cuenta.setId(77L);
        when(cuentaRepository.findByNumeroCuentaForUpdate("9999999999999999")).thenReturn(Optional.of(cuenta));
        when(saldoRepository.findByCuentaId(77L)).thenReturn(Optional.empty());
        assertThrows(CuentaNoEncontradaException.class, () ->
                service.abonarSaldo("Bearer t", null, new AbonoSaldoRequestDTO(new BigDecimal("50.00"), "Test")));
    }

    @Test
    void enriquecerLoginConTelefono_completaSiFalta() {
        assertNull(service.enriquecerLoginConTelefono(null));
        LoginResponseDTO conTel = LoginResponseDTO.builder().telefonoMovil("5500001111").build();
        assertSame(conTel, service.enriquecerLoginConTelefono(conTel));

        Cliente cliente = new Cliente();
        cliente.setTelefonoMovil("5598765432");
        when(clienteRepository.findById(7L)).thenReturn(Optional.of(cliente));

        LoginResponseDTO resp = LoginResponseDTO.builder().clienteId(7L).build();
        service.enriquecerLoginConTelefono(resp);
        assertEquals("5598765432", resp.getTelefonoMovil());
    }

    @Test
    void registrarClientePortal_minimalSinKyc_aplicaValoresPorDefecto() {
        RegisterRequestDTO minReq = new RegisterRequestDTO();
        minReq.setNombre("Carlos");
        minReq.setApellidoPaterno("Garay");
        minReq.setCorreo("carlos.min@gmail.com");
        minReq.setTelefonoMovil("5512345678");
        minReq.setUsuario("carlos_min");
        minReq.setContrasena("Segura2026");

        when(loginClienteRepository.findByUsuario("carlos_min")).thenReturn(Optional.empty());
        when(clienteRepository.existsByCorreo("carlos.min@gmail.com")).thenReturn(false);
        when(clienteRepository.existsByCurp(any())).thenReturn(false);
        when(clienteRepository.existsByRfc(any())).thenReturn(false);
        when(cuentaRepository.existsByNumeroCuenta(anyString())).thenReturn(false);
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            assertEquals("", c.getApellidoMaterno());
            assertEquals(java.time.LocalDate.of(1995, 1, 1), c.getFechaNacimiento());
            assertEquals("HOMBRE", c.getSexo());
            assertNotNull(c.getCurp());
            assertNotNull(c.getRfc());
            c.setId(105L);
            return c;
        });
        when(authService.login(any())).thenReturn(
                LoginResponseDTO.builder().clienteId(105L).usuario("carlos_min").token("jwt-min").build()
        );

        LoginResponseDTO resp = service.registrarClientePortal(minReq);
        assertNotNull(resp);
        assertEquals("carlos_min", resp.getUsuario());
    }

    @Test
    void validarEntradasYFallosRegistroYContactoYCambioContrasena() {
        assertThrows(ValidacionException.class, () -> service.registrarClientePortal(null));
        assertThrows(ValidacionException.class, () -> service.actualizarContacto("Bearer t", null, null));

        RegisterRequestDTO req = crearRegistroValido();
        req.setCorreo("correo-malo");
        assertThrows(ValidacionException.class, () -> service.registrarClientePortal(req));

        req.setCorreo("nuevo.cliente@gmail.com");
        req.setFechaNacimiento("fecha-invalida");
        assertThrows(ValidacionException.class, () -> service.registrarClientePortal(req));

        req.setFechaNacimiento("2099-01-01");
        assertThrows(ValidacionException.class, () -> service.registrarClientePortal(req));

        req.setFechaNacimiento("2018-01-01");
        assertThrows(ValidacionException.class, () -> service.registrarClientePortal(req));

        // Registro con CURP/RFC explícitos, nombres cortos y falla controlada en correo de bienvenida
        RegisterRequestDTO reqExplicito = crearRegistroValido();
        reqExplicito.setCurp("GAGC990515HDFRRL01");
        reqExplicito.setRfc("GAGC990515HDF");
        reqExplicito.setNombre("Bo");
        reqExplicito.setSegundoNombre("Luis");
        reqExplicito.setApellidoPaterno("Aa");
        reqExplicito.setApellidoMaterno("Eo");
        when(loginClienteRepository.findByUsuario("nuevo_user")).thenReturn(Optional.empty());
        when(clienteRepository.existsByCorreo("nuevo.cliente@gmail.com")).thenReturn(false);
        when(clienteRepository.existsByCurp("GAGC990515HDFRRL01")).thenReturn(false);
        when(clienteRepository.existsByRfc("GAGC990515HDF")).thenReturn(false);
        when(cuentaRepository.existsByNumeroCuenta(anyString())).thenReturn(false);
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            c.setId(55L);
            return c;
        });
        when(authService.login(any(LoginRequestDTO.class))).thenReturn(LoginResponseDTO.builder()
                .usuario("nuevo_user").clienteId(55L).build());
        when(comprobanteEmailService.enviarCorreoBienvenida(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Brevo temporary error"));

        LoginResponseDTO regRes = service.registrarClientePortal(reqExplicito);
        assertEquals("5512345678", regRes.getTelefonoMovil());

        // actualizarContacto con errores de validacion y duplicado
        when(authService.obtenerPerfilAutenticado("Bearer t", null))
                .thenReturn(UserProfileDTO.builder().clienteId(10L).numeroCuenta("5225").build());
        assertThrows(ValidacionException.class, () -> service.actualizarContacto(
                "Bearer t", null, new ActualizarContactoRequestDTO("ok@gmail.com", "1111111111")));
        assertThrows(ValidacionException.class, () -> service.actualizarContacto(
                "Bearer t", null, new ActualizarContactoRequestDTO("mal-correo", "5512345678")));
        assertThrows(ValidacionException.class, () -> service.actualizarContacto(
                "Bearer t", null, new ActualizarContactoRequestDTO("demo@ejemplo.com", "5512345678")));

        Cliente existente = new Cliente();
        existente.setId(10L);
        existente.setCorreo("actual@gmail.com");
        when(clienteRepository.findById(10L)).thenReturn(Optional.of(existente));
        when(clienteRepository.existsByCorreo("ocupado@gmail.com")).thenReturn(true);
        assertThrows(CorreoDuplicadoException.class, () -> service.actualizarContacto(
                "Bearer t", null, new ActualizarContactoRequestDTO("ocupado@gmail.com", "5512345678")));

        // Actualización de nombre y apellidos + validaciones de longitud
        ActualizarContactoRequestDTO conNombre = ActualizarContactoRequestDTO.builder()
                .correo("actual@gmail.com")
                .telefonoMovil("5512345678")
                .nombre("César")
                .segundoNombre("Enrique")
                .apellidoPaterno("Garay")
                .apellidoMaterno("García")
                .build();
        UserProfileDTO perfilNombre = service.actualizarContacto("Bearer t", null, conNombre);
        assertEquals("César Enrique Garay García", perfilNombre.getNombreCompleto());
        assertEquals("Enrique", perfilNombre.getSegundoNombre());

        ActualizarContactoRequestDTO segVacio = ActualizarContactoRequestDTO.builder()
                .correo("actual@gmail.com").telefonoMovil("5512345678").segundoNombre("  ").build();
        assertNull(service.actualizarContacto("Bearer t", null, segVacio).getSegundoNombre());

        ActualizarContactoRequestDTO nombreCorto = ActualizarContactoRequestDTO.builder()
                .correo("actual@gmail.com").telefonoMovil("5512345678").nombre("A").build();
        assertThrows(ValidacionException.class, () -> service.actualizarContacto("Bearer t", null, nombreCorto));
        ActualizarContactoRequestDTO apPatCorto = ActualizarContactoRequestDTO.builder()
                .correo("actual@gmail.com").telefonoMovil("5512345678").apellidoPaterno("G").build();
        assertThrows(ValidacionException.class, () -> service.actualizarContacto("Bearer t", null, apPatCorto));
        ActualizarContactoRequestDTO apMatCorto = ActualizarContactoRequestDTO.builder()
                .correo("actual@gmail.com").telefonoMovil("5512345678").apellidoMaterno("G").build();
        assertThrows(ValidacionException.class, () -> service.actualizarContacto("Bearer t", null, apMatCorto));

        // Cambio de contraseña (legado SHA-256 -> BCrypt + invalidarOtrasSesiones)
        when(authService.obtenerPerfilAutenticado("Bearer t", null))
                .thenReturn(UserProfileDTO.builder().clienteId(10L).usuario("cesar123").build());
        LoginCliente lc = new LoginCliente();
        lc.setUsuario("cesar123");
        lc.setContrasenaHash(AuthServiceImpl.cifrarSHA256("Actual2026"));
        when(loginClienteRepository.findByUsuario("cesar123")).thenReturn(Optional.of(lc));

        assertThrows(ValidacionException.class, () ->
                service.cambiarContrasena("Bearer t", null, null));
        assertThrows(ValidacionException.class, () ->
                service.cambiarContrasena("Bearer t", null, new CambiarContrasenaRequestDTO("Actual2026", "debil")));
        assertThrows(AutenticacionException.class, () ->
                service.cambiarContrasena("Bearer t", null, new CambiarContrasenaRequestDTO("MalPass1", "Nueva2026")));
        assertThrows(ValidacionException.class, () ->
                service.cambiarContrasena("Bearer t", null, new CambiarContrasenaRequestDTO("Actual2026", "Actual2026")));

        service.cambiarContrasena("Bearer t", null, new CambiarContrasenaRequestDTO("Actual2026", "Nueva2026"));
        assertTrue(lc.getContrasenaHash().startsWith("$2"));
        assertTrue(passwordEncoder.matches("Nueva2026", lc.getContrasenaHash()));
        verify(loginClienteRepository).save(lc);
        verify(authService).invalidarOtrasSesiones("cesar123", "t");

        // Reintento sobre BCrypt guardado rechazando reutilización
        assertThrows(ValidacionException.class, () ->
                service.cambiarContrasena("Bearer t", null, new CambiarContrasenaRequestDTO("Nueva2026", "Nueva2026")));

        PortalCuentaService stub = new PortalCuentaService() {
            @Override
            public LoginResponseDTO registrarClientePortal(RegisterRequestDTO r) { return null; }
            @Override
            public UserProfileDTO actualizarContacto(String a, String u, ActualizarContactoRequestDTO r) { return null; }
            @Override
            public UserProfileDTO abonarSaldo(String a, String u, AbonoSaldoRequestDTO r) { return null; }
            @Override
            public LoginResponseDTO enriquecerLoginConTelefono(LoginResponseDTO r) { return null; }
        };
        assertThrows(UnsupportedOperationException.class, () -> stub.cambiarContrasena("Bearer t", null, null));
    }

    private RegisterRequestDTO crearRegistroValido() {
        return RegisterRequestDTO.builder()
                .nombre("César")
                .apellidoPaterno("Garay")
                .apellidoMaterno("García")
                .fechaNacimiento("1999-05-15")
                .correo("nuevo.cliente@gmail.com")
                .telefonoMovil("5512345678")
                .usuario("nuevo_user")
                .contrasena("Segura2026")
                .sexo("HOMBRE")
                .build();
    }
}
