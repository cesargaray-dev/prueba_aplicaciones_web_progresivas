package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.controller.AdminUsuarioController;
import com.proyecto.servicios.entity.clientes.*;
import com.proyecto.servicios.entity.gestopago.PagoServicio;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.admin.AdminActualizarUsuarioRequestDTO;
import com.proyecto.servicios.model.admin.AdminUsuarioDTO;
import com.proyecto.servicios.model.auth.LoginResponseDTO;
import com.proyecto.servicios.model.auth.RegisterRequestDTO;
import com.proyecto.servicios.repositorys.clientes.*;
import com.proyecto.servicios.repositorys.gestopago.PagoServicioRepository;
import com.proyecto.servicios.security.AccountOwnershipValidator;
import com.proyecto.servicios.service.AuthService;
import com.proyecto.servicios.service.PortalCuentaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUsuarioServiceImplTest {

    @Mock
    private ClienteRepository clienteRepo;
    @Mock
    private LoginClienteRepository loginRepo;
    @Mock
    private CuentaRepository cuentaRepo;
    @Mock
    private SaldoRepository saldoRepo;
    @Mock
    private DatoBiometricoRepository bioRepo;
    @Mock
    private PagoServicioRepository pagoRepo;
    @Mock
    private PortalCuentaService portalService;
    @Mock
    private AuthService authService;
    @Mock
    private AccountOwnershipValidator ownershipValidator;

    private AdminUsuarioServiceImpl service;
    private AdminUsuarioController controller;

    @BeforeEach
    void setUp() {
        service = new AdminUsuarioServiceImpl(
                clienteRepo,
                loginRepo,
                cuentaRepo,
                saldoRepo,
                bioRepo,
                pagoRepo,
                portalService,
                authService,
                ownershipValidator,
                new BCryptPasswordEncoder()
        );
        controller = new AdminUsuarioController(service);
    }

    private Cliente buildSampleCliente(Long id, String username) {
        Cliente c = new Cliente();
        c.setId(id);
        c.setNombre("CARLOS");
        c.setApellidoPaterno("GARAY");
        c.setApellidoMaterno("GARCIA");
        c.setCorreo("carlos@gmail.com");
        c.setTelefonoMovil("5512345678");
        c.setActivo(true);

        Cuenta cta = new Cuenta();
        cta.setId(10L + id);
        cta.setCliente(c);
        cta.setNumeroCuenta("5225253189113705");
        cta.setEstatus("ACTIVA");

        Saldo s = new Saldo();
        s.setCuenta(cta);
        s.setSaldo(new BigDecimal("250.00"));
        cta.setSaldo(s);
        c.setCuenta(cta);

        LoginCliente lg = new LoginCliente();
        lg.setCliente(c);
        lg.setUsuario(username);
        lg.setContrasenaHash("hash");
        lg.setActivo(true);
        lg.setBloqueado(false);
        lg.setIntentosFallidos(0);
        c.setLoginCliente(lg);
        return c;
    }

    @Test
    void asegurarUsuarioAdminInicialCreaOCorrigeAdmin() {
        when(loginRepo.findByUsuario("admin")).thenReturn(Optional.empty());
        when(clienteRepo.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));

        service.onReady();
        verify(clienteRepo, times(1)).save(any(Cliente.class));

        Cliente existingAdmin = buildSampleCliente(1L, "admin");
        existingAdmin.setActivo(false);
        existingAdmin.getLoginCliente().setActivo(false);
        existingAdmin.getLoginCliente().setBloqueado(true);
        when(loginRepo.findByUsuario("admin")).thenReturn(Optional.of(existingAdmin.getLoginCliente()));

        service.asegurarUsuarioAdminInicial();
        assertTrue(existingAdmin.getActivo());
        assertTrue(existingAdmin.getLoginCliente().getActivo());
        assertFalse(existingAdmin.getLoginCliente().getBloqueado());
    }

    @Test
    void listarUsaConsultaNativaRapidaCuandoRetornaFilas() {
        Object[] row = new Object[]{
                1L,
                "admin",
                "ADMINISTRADOR",
                "GENERAL",
                "BANCO",
                "DIGITAL",
                java.sql.Date.valueOf("1990-01-01"),
                "HOMBRE",
                "ADMG900101HDFRSL09",
                "ADMG900101ADM",
                "admin@bancodigital.com.mx",
                "5523456789",
                "4000000000000001",
                "ACTIVA",
                new BigDecimal("10000.00"),
                Boolean.TRUE,
                Boolean.FALSE,
                0,
                Boolean.TRUE,
                java.sql.Timestamp.valueOf("2026-11-20 12:00:00"),
                java.sql.Timestamp.valueOf("2026-11-01 10:00:00")
        };
        when(clienteRepo.findAllResumenAdminRaw()).thenReturn(List.<Object[]>of(row));

        List<AdminUsuarioDTO> result = service.listarTodosLosUsuarios("admin");
        assertEquals(1, result.size());
        assertEquals("admin", result.get(0).getUsuario());
        assertTrue(result.get(0).getEsAdmin());
        assertTrue(result.get(0).getBiometriaActiva());
        assertEquals(new BigDecimal("10000.00"), result.get(0).getSaldo());
        verify(clienteRepo, never()).findAll(any(Sort.class));
    }

    @Test
    void listarCrearActualizarYEliminarUsuariosDesdeController() {
        Cliente user = buildSampleCliente(2L, "carlos98");
        when(clienteRepo.findAll(any(Sort.class))).thenReturn(List.of(user));
        when(clienteRepo.findById(2L)).thenReturn(Optional.of(user));
        when(clienteRepo.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));
        when(saldoRepo.findByCuentaId(anyLong())).thenReturn(Optional.of(user.getCuenta().getSaldo()));

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("admin", "tok");

        ResponseEntity<List<AdminUsuarioDTO>> listResp = controller.listarTodos(auth);
        assertEquals(1, listResp.getBody().size());
        assertEquals("carlos98", listResp.getBody().get(0).getUsuario());

        ResponseEntity<AdminUsuarioDTO> getResp = controller.obtenerPorId(2L, auth);
        assertEquals(2L, getResp.getBody().getClienteId());

        AdminActualizarUsuarioRequestDTO upd = AdminActualizarUsuarioRequestDTO.builder()
                .nombre("CARLOS EDITADO")
                .segundoNombre("ENRIQUE")
                .apellidoPaterno("GARAY")
                .apellidoMaterno("GARCIA")
                .correo("carlos.nuevo@gmail.com")
                .telefonoMovil("5523456789")
                .usuario("carlos_new")
                .nuevaContrasena("Nueva2026#")
                .saldo(new BigDecimal("850.00"))
                .activo(false)
                .bloqueado(false)
                .build();

        ResponseEntity<AdminUsuarioDTO> updResp = controller.actualizarUsuario(2L, upd, auth);
        assertEquals("carlos_new", updResp.getBody().getUsuario());
        assertEquals(new BigDecimal("850.00"), updResp.getBody().getSaldo());
        assertFalse(updResp.getBody().getActivo());

        RegisterRequestDTO reg = new RegisterRequestDTO();
        reg.setUsuario("nuevo99");
        when(portalService.registrarClientePortal(any())).thenReturn(
                LoginResponseDTO.builder().clienteId(2L).usuario("nuevo99").token("tmp").build()
        );
        ResponseEntity<AdminUsuarioDTO> createResp = controller.crearUsuario(reg, auth);
        assertNotNull(createResp.getBody());

        when(bioRepo.findByClienteId(2L)).thenReturn(List.of(new DatoBiometrico()));
        when(pagoRepo.findByCuentaId(anyLong())).thenReturn(List.of(new PagoServicio()));

        ResponseEntity<GenericResponse> delResp = controller.eliminarUsuario(2L, auth);
        assertEquals(0, delResp.getBody().getCodigo());
        verify(clienteRepo).delete(user);
    }
}
