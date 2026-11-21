package com.proyecto.servicios.service.clientes.impl;

import com.proyecto.servicios.entity.clientes.*;
import com.proyecto.servicios.enums.EstatusCuentaEnum;
import com.proyecto.servicios.exception.ClienteNoEncontradoException;
import com.proyecto.servicios.exception.CorreoDuplicadoException;
import com.proyecto.servicios.exception.CuentaNoEncontradaException;
import com.proyecto.servicios.exception.CurpDuplicadaException;
import com.proyecto.servicios.exception.RfcDuplicadoException;
import com.proyecto.servicios.exception.ValidacionException;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.clientes.*;
import com.proyecto.servicios.repositorys.clientes.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteServiceImplTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private LoginClienteRepository loginRepository;

    @Mock
    private DatoBiometricoRepository biometricoRepository;

    @Mock
    private CatalogoRepository catalogoRepository;

    private ClienteServiceImpl clienteService;

    @BeforeEach
    void setUp() {
        clienteService = new ClienteServiceImpl(
                clienteRepository,
                cuentaRepository,
                loginRepository,
                biometricoRepository,
                catalogoRepository
        );
    }

    private Cliente crearClienteMock(Long id, String curp, String rfc, String correo) {
        Cliente c = new Cliente();
        c.setId(id);
        c.setCurp(curp);
        c.setRfc(rfc);
        c.setNombre("Carlos");
        c.setSegundoNombre("Eduardo");
        c.setApellidoPaterno("Sanchez");
        c.setApellidoMaterno("Ramirez");
        c.setCorreo(correo);
        c.setTelefonoMovil("5512345678");
        c.setTelefonoAlternativo("5587654321");
        c.setSexo("M");
        c.setNacionalidad("MEXICANA");
        c.setEstadoCivil("SOLTERO");
        c.setOcupacion("Ingeniero");
        c.setEmpresa("Tech Corp");
        c.setIngresoMensual(new BigDecimal("25000.00"));
        c.setActivo(true);
        c.setFechaNacimiento(LocalDate.of(1995, 5, 20));
        c.setFechaRegistro(LocalDateTime.of(2023, 1, 15, 10, 0));

        Cuenta cuenta = new Cuenta();
        cuenta.setId(100L);
        cuenta.setNumeroCuenta("5225253189113705");
        cuenta.setEstatus(EstatusCuentaEnum.ACTIVA.getCodigo());
        cuenta.setFechaApertura(LocalDateTime.of(2023, 1, 15, 10, 0));
        cuenta.setCliente(c);

        Saldo saldo = new Saldo();
        saldo.setSaldo(new BigDecimal("1000.00"));
        saldo.setCuenta(cuenta);
        cuenta.setSaldo(saldo);

        c.setCuenta(cuenta);

        Domicilio d = new Domicilio();
        d.setCalle("Av Principal");
        d.setNumeroExterior("123");
        d.setNumeroInterior("4B");
        d.setColonia("Centro");
        d.setMunicipio("Cuauhtemoc");
        d.setEstado("CIUDAD DE MEXICO");
        d.setCodigoPostal("06000");
        d.setPais("MEXICO");
        c.setDomicilio(d);

        LoginCliente login = new LoginCliente();
        login.setId(200L);
        login.setUsuario("csanchez");
        login.setContrasenaHash("hash123");
        login.setActivo(true);
        login.setCliente(c);
        c.setLoginCliente(login);

        return c;
    }

    private Catalogo crearCatalogoMock(Long id, String tipo, String clave, String descripcion) {
        Catalogo cat = new Catalogo();
        cat.setId(id);
        cat.setTipo(tipo);
        cat.setClave(clave);
        cat.setDescripcion(descripcion);
        cat.setActivo(true);
        return cat;
    }

    // ----------------------------------------------------
    // 1. REGISTRAR CLIENTE
    // ----------------------------------------------------

    @Test
    void registrarCliente_exitosoConLoginYBiometrico() {
        ClienteRequestDTO req = new ClienteRequestDTO();
        req.setNombre("Ana");
        req.setSegundoNombre("Maria");
        req.setApellidoPaterno("Lopez");
        req.setApellidoMaterno("Gomez");
        req.setFechaNacimiento("1992-08-10");
        req.setCurp("LOGA920810MDFR01");
        req.setRfc("LOGA920810ABC");
        req.setSexo("F");
        req.setNacionalidadId(1L);
        req.setEstadoCivil("CASADA");
        req.setCorreo("ana.lopez@example.com");
        req.setTelefonoMovil("5511223344");
        req.setTelefonoAlternativo("5599887766");
        req.setOcupacion("Doctora");
        req.setEmpresa("Hospital Central");
        req.setIngresoMensual(new BigDecimal("40000.00"));
        req.setCalle("Calle Roble");
        req.setNumeroExterior("456");
        req.setNumeroInterior("2");
        req.setColonia("Del Valle");
        req.setMunicipio("Benito Juarez");
        req.setEstado("CIUDAD DE MEXICO");
        req.setCodigoPostal("03100");
        req.setPais("MEXICO");
        req.setUsuario("analopez");
        req.setContrasena("Password123!");
        req.setHashBiometrico("biometric_face_hash_xyz");

        Catalogo nac = crearCatalogoMock(1L, "NACIONALIDAD", "MEX", "MEXICANA");
        when(catalogoRepository.findById(1L)).thenReturn(Optional.of(nac));
        when(clienteRepository.existsByCurp(req.getCurp())).thenReturn(false);
        when(clienteRepository.existsByRfc(req.getRfc())).thenReturn(false);
        when(clienteRepository.existsByCorreo(req.getCorreo())).thenReturn(false);
        when(catalogoRepository.existsByTipoAndDescripcionIgnoreCaseAndActivoTrue("ESTADO", "CIUDAD DE MEXICO")).thenReturn(true);
        // Primero simula duplicado y luego único para cubrir rama de bucle do-while
        when(cuentaRepository.existsByNumeroCuenta(anyString())).thenReturn(true, false);
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> {
            Cliente saved = i.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        ClienteResponseDTO response = clienteService.registrarCliente(req);

        assertNotNull(response);
        assertEquals("Ana", response.getNombre());
        assertEquals(0, response.getCodigo());
        verify(biometricoRepository, times(1)).save(any(DatoBiometrico.class));
        verify(clienteRepository, atLeastOnce()).save(any(Cliente.class));
    }

    @Test
    void registrarCliente_exitosoSinLoginYSinBiometrico() {
        ClienteRequestDTO req = new ClienteRequestDTO();
        req.setNombre("Pedro");
        req.setApellidoPaterno("Perez");
        req.setFechaNacimiento("1990-01-01");
        req.setCurp("PERP900101HDFR01");
        req.setRfc("PERP900101XYZ");
        req.setNacionalidadId(1L);
        req.setCorreo("pedro@example.com");
        req.setEstado("JALISCO");
        req.setUsuario("   "); // Blanco
        req.setContrasena("   "); // Blanco
        req.setHashBiometrico("   "); // Blanco

        Catalogo nac = crearCatalogoMock(1L, "NACIONALIDAD", "MEX", "MEXICANA");
        when(catalogoRepository.findById(1L)).thenReturn(Optional.of(nac));
        when(clienteRepository.existsByCurp(req.getCurp())).thenReturn(false);
        when(clienteRepository.existsByRfc(req.getRfc())).thenReturn(false);
        when(clienteRepository.existsByCorreo(req.getCorreo())).thenReturn(false);
        when(catalogoRepository.existsByTipoAndDescripcionIgnoreCaseAndActivoTrue("ESTADO", "JALISCO")).thenReturn(false);
        when(catalogoRepository.existsByTipoAndClaveIgnoreCaseAndActivoTrue("ESTADO", "JALISCO")).thenReturn(true);
        when(cuentaRepository.existsByNumeroCuenta(anyString())).thenReturn(false);
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> {
            Cliente saved = i.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        ClienteResponseDTO response = clienteService.registrarCliente(req);

        assertNotNull(response);
        assertEquals("Pedro", response.getNombre());
        verify(biometricoRepository, never()).save(any(DatoBiometrico.class));
    }

    @Test
    void registrarCliente_fechaFutura_lanzaValidacionException() {
        ClienteRequestDTO req = new ClienteRequestDTO();
        req.setFechaNacimiento("2099-01-01");

        ValidacionException ex = assertThrows(ValidacionException.class, () -> clienteService.registrarCliente(req));
        assertTrue(ex.getMessage().contains("fecha futura"));
    }

    @Test
    void registrarCliente_menorDeEdad_lanzaValidacionException() {
        ClienteRequestDTO req = new ClienteRequestDTO();
        String fechaMenor = LocalDate.now().minusYears(16).toString();
        req.setFechaNacimiento(fechaMenor);

        ValidacionException ex = assertThrows(ValidacionException.class, () -> clienteService.registrarCliente(req));
        assertTrue(ex.getMessage().contains("mayor de edad"));
    }

    @Test
    void registrarCliente_fechaInvalidaFormato_lanzaValidacionException() {
        ClienteRequestDTO req = new ClienteRequestDTO();
        req.setFechaNacimiento("2024-02-30");

        assertThrows(ValidacionException.class, () -> clienteService.registrarCliente(req));
    }

    @Test
    void registrarCliente_nacionalidadInexistente_lanzaValidacionException() {
        ClienteRequestDTO req = new ClienteRequestDTO();
        req.setFechaNacimiento("1990-01-01");
        req.setNacionalidadId(99L);

        when(catalogoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ValidacionException.class, () -> clienteService.registrarCliente(req));
    }

    @Test
    void registrarCliente_conCurpDuplicada_lanzaCurpDuplicadaException() {
        ClienteRequestDTO req = new ClienteRequestDTO();
        req.setFechaNacimiento("1990-01-01");
        req.setNacionalidadId(1L);
        req.setCurp("CURP123456HDFR00");

        Catalogo nac = crearCatalogoMock(1L, "NACIONALIDAD", "MEX", "MEXICANA");
        when(catalogoRepository.findById(1L)).thenReturn(Optional.of(nac));
        when(clienteRepository.existsByCurp("CURP123456HDFR00")).thenReturn(true);

        assertThrows(CurpDuplicadaException.class, () -> clienteService.registrarCliente(req));
    }

    @Test
    void registrarCliente_conRfcDuplicado_lanzaRfcDuplicadoException() {
        ClienteRequestDTO req = new ClienteRequestDTO();
        req.setFechaNacimiento("1990-01-01");
        req.setNacionalidadId(1L);
        req.setCurp("CURP123456HDFR00");
        req.setRfc("RFC123456789");

        Catalogo nac = crearCatalogoMock(1L, "NACIONALIDAD", "MEX", "MEXICANA");
        when(catalogoRepository.findById(1L)).thenReturn(Optional.of(nac));
        when(clienteRepository.existsByCurp(req.getCurp())).thenReturn(false);
        when(clienteRepository.existsByRfc(req.getRfc())).thenReturn(true);

        assertThrows(RfcDuplicadoException.class, () -> clienteService.registrarCliente(req));
    }

    @Test
    void registrarCliente_conCorreoDuplicado_lanzaCorreoDuplicadoException() {
        ClienteRequestDTO req = new ClienteRequestDTO();
        req.setFechaNacimiento("1990-01-01");
        req.setNacionalidadId(1L);
        req.setCurp("CURP123456HDFR00");
        req.setRfc("RFC123456789");
        req.setCorreo("carlos@test.com");

        Catalogo nac = crearCatalogoMock(1L, "NACIONALIDAD", "MEX", "MEXICANA");
        when(catalogoRepository.findById(1L)).thenReturn(Optional.of(nac));
        when(clienteRepository.existsByCurp(req.getCurp())).thenReturn(false);
        when(clienteRepository.existsByRfc(req.getRfc())).thenReturn(false);
        when(clienteRepository.existsByCorreo(req.getCorreo())).thenReturn(true);

        assertThrows(CorreoDuplicadoException.class, () -> clienteService.registrarCliente(req));
    }

    @Test
    void registrarCliente_conEstadoInvalido_lanzaValidacionException() {
        ClienteRequestDTO req = new ClienteRequestDTO();
        req.setFechaNacimiento("1990-01-01");
        req.setNacionalidadId(1L);
        req.setCurp("CURP123456HDFR00");
        req.setRfc("RFC123456789");
        req.setCorreo("carlos@test.com");
        req.setEstado("ESTADO_INEXISTENTE");

        Catalogo nac = crearCatalogoMock(1L, "NACIONALIDAD", "MEX", "MEXICANA");
        when(catalogoRepository.findById(1L)).thenReturn(Optional.of(nac));
        when(clienteRepository.existsByCurp(req.getCurp())).thenReturn(false);
        when(clienteRepository.existsByRfc(req.getRfc())).thenReturn(false);
        when(clienteRepository.existsByCorreo(req.getCorreo())).thenReturn(false);
        when(catalogoRepository.existsByTipoAndDescripcionIgnoreCaseAndActivoTrue("ESTADO", "ESTADO_INEXISTENTE")).thenReturn(false);
        when(catalogoRepository.existsByTipoAndClaveIgnoreCaseAndActivoTrue("ESTADO", "ESTADO_INEXISTENTE")).thenReturn(false);

        assertThrows(ValidacionException.class, () -> clienteService.registrarCliente(req));
    }

    // ----------------------------------------------------
    // 2. CONSULTAS
    // ----------------------------------------------------

    @Test
    void obtenerTodosLosClientes_retornaListaMapeada() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findAll()).thenReturn(List.of(c));

        List<ClienteResponseDTO> result = clienteService.obtenerTodosLosClientes();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Carlos", result.get(0).getNombre());
    }

    @Test
    void obtenerClientePorId_cuandoExiste_retornaDTO() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(c));

        ClienteResponseDTO result = clienteService.obtenerClientePorId(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("CURP123456HDFR00", result.getCurp());
    }

    @Test
    void obtenerClientePorId_cuandoNoExiste_lanzaClienteNoEncontradoException() {
        when(clienteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ClienteNoEncontradoException.class,
                () -> clienteService.obtenerClientePorId(99L));
    }

    @Test
    void obtenerClientePorCurp_cuandoExiste_retornaDTO() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findByCurp("CURP123456HDFR00")).thenReturn(Optional.of(c));

        ClienteResponseDTO result = clienteService.obtenerClientePorCurp("CURP123456HDFR00");

        assertNotNull(result);
        assertEquals("CURP123456HDFR00", result.getCurp());
    }

    @Test
    void obtenerClientePorCurp_cuandoNoExiste_lanzaClienteNoEncontradoException() {
        when(clienteRepository.findByCurp("CURP_INEXISTENTE")).thenReturn(Optional.empty());

        assertThrows(ClienteNoEncontradoException.class,
                () -> clienteService.obtenerClientePorCurp("CURP_INEXISTENTE"));
    }

    @Test
    void obtenerClientePorRfc_cuandoExiste_retornaDTO() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findByRfc("RFC123456789")).thenReturn(Optional.of(c));

        ClienteResponseDTO result = clienteService.obtenerClientePorRfc("RFC123456789");

        assertNotNull(result);
        assertEquals("RFC123456789", result.getRfc());
    }

    @Test
    void obtenerClientePorRfc_cuandoNoExiste_lanzaClienteNoEncontradoException() {
        when(clienteRepository.findByRfc("RFC_INEXISTENTE")).thenReturn(Optional.empty());

        assertThrows(ClienteNoEncontradoException.class,
                () -> clienteService.obtenerClientePorRfc("RFC_INEXISTENTE"));
    }

    @Test
    void obtenerClientePorCorreo_cuandoExiste_retornaDTO() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findByCorreo("carlos@test.com")).thenReturn(Optional.of(c));

        ClienteResponseDTO result = clienteService.obtenerClientePorCorreo("carlos@test.com");

        assertNotNull(result);
        assertEquals("carlos@test.com", result.getCorreo());
    }

    @Test
    void obtenerClientePorCorreo_cuandoNoExiste_lanzaClienteNoEncontradoException() {
        when(clienteRepository.findByCorreo("noexiste@test.com")).thenReturn(Optional.empty());

        assertThrows(ClienteNoEncontradoException.class,
                () -> clienteService.obtenerClientePorCorreo("noexiste@test.com"));
    }

    @Test
    void obtenerClientePorNumeroCuenta_cuandoExiste_retornaCliente() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        Cuenta cuenta = c.getCuenta();
        when(cuentaRepository.findByNumeroCuenta("5225253189113705")).thenReturn(Optional.of(cuenta));

        ClienteResponseDTO result = clienteService.obtenerClientePorNumeroCuenta("5225253189113705");

        assertNotNull(result);
        assertEquals("Carlos", result.getNombre());
    }

    @Test
    void obtenerClientePorNumeroCuenta_cuandoNoExiste_lanzaCuentaNoEncontradaException() {
        when(cuentaRepository.findByNumeroCuenta("9999999999999999")).thenReturn(Optional.empty());

        assertThrows(CuentaNoEncontradaException.class,
                () -> clienteService.obtenerClientePorNumeroCuenta("9999999999999999"));
    }

    // ----------------------------------------------------
    // 3. ACTUALIZAR CLIENTE
    // ----------------------------------------------------

    @Test
    void actualizarCliente_exitosoConCambioCorreo() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findByCurp("CURP123456HDFR00")).thenReturn(Optional.of(c));

        ClienteRequestDTO req = new ClienteRequestDTO();
        req.setNombre("Carlos Actualizado");
        req.setSegundoNombre("Edu");
        req.setApellidoPaterno("Sanchez");
        req.setApellidoMaterno("Ramirez");
        req.setFechaNacimiento("1995-05-20");
        req.setSexo("M");
        req.setNacionalidadId(1L);
        req.setEstadoCivil("CASADO");
        req.setOcupacion("Gerente");
        req.setEmpresa("Big Corp");
        req.setIngresoMensual(new BigDecimal("35000.00"));
        req.setCorreo("carlos.nuevo@test.com");
        req.setTelefonoMovil("5599887766");
        req.setTelefonoAlternativo("5522334455");
        req.setEstado("CIUDAD DE MEXICO");
        req.setCalle("Av Reforma");
        req.setNumeroExterior("100");
        req.setCodigoPostal("06500");

        Catalogo nac = crearCatalogoMock(1L, "NACIONALIDAD", "MEX", "MEXICANA");
        when(catalogoRepository.findById(1L)).thenReturn(Optional.of(nac));
        when(clienteRepository.existsByCorreo("carlos.nuevo@test.com")).thenReturn(false);
        when(catalogoRepository.existsByTipoAndDescripcionIgnoreCaseAndActivoTrue("ESTADO", "CIUDAD DE MEXICO")).thenReturn(true);
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ClienteResponseDTO res = clienteService.actualizarCliente("CURP123456HDFR00", req);

        assertNotNull(res);
        assertEquals("Carlos Actualizado", res.getNombre());
        assertEquals(0, res.getCodigo());
        assertEquals("Datos actualizados correctamente", res.getMensaje());
    }

    @Test
    void actualizarCliente_conEstadoNuloYDomicilioNulo() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        c.setDomicilio(null);
        when(clienteRepository.findByCurp("CURP123456HDFR00")).thenReturn(Optional.of(c));

        ClienteRequestDTO req = new ClienteRequestDTO();
        req.setNacionalidadId(1L);
        req.setCorreo("carlos@test.com");
        req.setEstado(null);

        Catalogo nac = crearCatalogoMock(1L, "NACIONALIDAD", "MEX", "MEXICANA");
        when(catalogoRepository.findById(1L)).thenReturn(Optional.of(nac));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ClienteResponseDTO res = clienteService.actualizarCliente("CURP123456HDFR00", req);
        assertNotNull(res);
        assertNull(res.getCalle());
    }

    @Test
    void actualizarCliente_nacionalidadInexistente_lanzaValidacionException() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findByCurp("CURP123456HDFR00")).thenReturn(Optional.of(c));

        ClienteRequestDTO req = new ClienteRequestDTO();
        req.setNacionalidadId(999L);
        when(catalogoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ValidacionException.class, () -> clienteService.actualizarCliente("CURP123456HDFR00", req));
    }

    @Test
    void actualizarCliente_correoDuplicado_lanzaCorreoDuplicadoException() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findByCurp("CURP123456HDFR00")).thenReturn(Optional.of(c));

        ClienteRequestDTO req = new ClienteRequestDTO();
        req.setNacionalidadId(1L);
        req.setCorreo("otro@test.com");

        Catalogo nac = crearCatalogoMock(1L, "NACIONALIDAD", "MEX", "MEXICANA");
        when(catalogoRepository.findById(1L)).thenReturn(Optional.of(nac));
        when(clienteRepository.existsByCorreo("otro@test.com")).thenReturn(true);

        assertThrows(CorreoDuplicadoException.class, () -> clienteService.actualizarCliente("CURP123456HDFR00", req));
    }

    @Test
    void actualizarCliente_estadoInvalido_lanzaValidacionException() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findByCurp("CURP123456HDFR00")).thenReturn(Optional.of(c));

        ClienteRequestDTO req = new ClienteRequestDTO();
        req.setNacionalidadId(1L);
        req.setCorreo("carlos@test.com");
        req.setEstado("ESTADO_FALSO");

        Catalogo nac = crearCatalogoMock(1L, "NACIONALIDAD", "MEX", "MEXICANA");
        when(catalogoRepository.findById(1L)).thenReturn(Optional.of(nac));
        when(catalogoRepository.existsByTipoAndDescripcionIgnoreCaseAndActivoTrue("ESTADO", "ESTADO_FALSO")).thenReturn(false);
        when(catalogoRepository.existsByTipoAndClaveIgnoreCaseAndActivoTrue("ESTADO", "ESTADO_FALSO")).thenReturn(false);

        assertThrows(ValidacionException.class, () -> clienteService.actualizarCliente("CURP123456HDFR00", req));
    }

    // ----------------------------------------------------
    // 4. ACTUALIZAR COMPLETO
    // ----------------------------------------------------

    @Test
    void actualizarCompleto_porId_exitoso() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(c));
        when(clienteRepository.findByCurp("CURP123456HDFR00")).thenReturn(Optional.of(c));

        ClienteRequestDTO req = new ClienteRequestDTO();
        req.setId(1L);
        req.setNombre("Carlos Editado");
        req.setNacionalidadId(1L);
        req.setCorreo("carlos@test.com");

        Catalogo nac = crearCatalogoMock(1L, "NACIONALIDAD", "MEX", "MEXICANA");
        when(catalogoRepository.findById(1L)).thenReturn(Optional.of(nac));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ClienteResponseDTO resp = clienteService.actualizarCompleto(req);
        assertNotNull(resp);
        assertEquals("Carlos Editado", resp.getNombre());
    }

    @Test
    void actualizarCompleto_porCurp_exitoso() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findByCurp("CURP123456HDFR00")).thenReturn(Optional.of(c));

        ClienteRequestDTO req = new ClienteRequestDTO();
        req.setCurp("CURP123456HDFR00");
        req.setNombre("Carlos Editado 2");
        req.setNacionalidadId(1L);
        req.setCorreo("carlos@test.com");

        Catalogo nac = crearCatalogoMock(1L, "NACIONALIDAD", "MEX", "MEXICANA");
        when(catalogoRepository.findById(1L)).thenReturn(Optional.of(nac));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ClienteResponseDTO resp = clienteService.actualizarCompleto(req);
        assertNotNull(resp);
        assertEquals("Carlos Editado 2", resp.getNombre());
    }

    @Test
    void actualizarCompleto_porRfc_exitoso() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findByRfc("RFC123456789")).thenReturn(Optional.of(c));
        when(clienteRepository.findByCurp("CURP123456HDFR00")).thenReturn(Optional.of(c));

        ClienteRequestDTO req = new ClienteRequestDTO();
        req.setRfc("RFC123456789");
        req.setNombre("Carlos Editado 3");
        req.setNacionalidadId(1L);
        req.setCorreo("carlos@test.com");

        Catalogo nac = crearCatalogoMock(1L, "NACIONALIDAD", "MEX", "MEXICANA");
        when(catalogoRepository.findById(1L)).thenReturn(Optional.of(nac));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ClienteResponseDTO resp = clienteService.actualizarCompleto(req);
        assertNotNull(resp);
        assertEquals("Carlos Editado 3", resp.getNombre());
    }

    @Test
    void actualizarCompleto_sinCriterios_lanzaValidacionException() {
        ClienteRequestDTO req = new ClienteRequestDTO();
        assertThrows(ValidacionException.class, () -> clienteService.actualizarCompleto(req));
    }

    @Test
    void actualizarCompleto_sobrecargadoRegistroRequest_exitoso() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(c));
        when(clienteRepository.findByCurp("CURP123456HDFR00")).thenReturn(Optional.of(c));

        ClienteRegistroRequest regReq = new ClienteRegistroRequest();
        regReq.setId(1L);
        regReq.setNombre("Carlos Sobrecargado");
        regReq.setNacionalidadId(1L);
        regReq.setCorreo("carlos@test.com");

        Catalogo nac = crearCatalogoMock(1L, "NACIONALIDAD", "MEX", "MEXICANA");
        when(catalogoRepository.findById(1L)).thenReturn(Optional.of(nac));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ClienteResponse resp = clienteService.actualizarCompleto(regReq);
        assertNotNull(resp);
        assertEquals("Carlos Sobrecargado", resp.getNombre());
    }

    // ----------------------------------------------------
    // 5. ACTUALIZAR PARCIAL
    // ----------------------------------------------------

    @Test
    void actualizarParcial_porId_exitoso() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(c));

        ClienteActualizacionParcialRequestDTO req = new ClienteActualizacionParcialRequestDTO();
        req.setId(1L);
        req.setNombre("NuevoNombre");
        req.setSegundoNombre("NuevoSegundo");
        req.setApellidoPaterno("NuevoPaterno");
        req.setApellidoMaterno("NuevoMaterno");
        req.setFechaNacimiento("1993-04-15");
        req.setSexo("F");
        req.setNacionalidadId(1L);
        req.setEstadoCivil("CASADO");
        req.setCorreo("nuevo.email@test.com");
        req.setTelefonoMovil("5500112233");
        req.setTelefonoAlternativo("5544332211");
        req.setOcupacion("Director");
        req.setEmpresa("Enterprise Inc");
        req.setIngresoMensual(new BigDecimal("60000.00"));
        req.setEstado("CIUDAD DE MEXICO");
        req.setCalle("Av Insurgentes");
        req.setNumeroExterior("500");
        req.setNumeroInterior("101");
        req.setColonia("Juarez");
        req.setMunicipio("Cuauhtemoc");
        req.setCodigoPostal("06600");
        req.setPais("MEXICO");

        Catalogo nac = crearCatalogoMock(1L, "NACIONALIDAD", "MEX", "MEXICANA");
        when(catalogoRepository.findById(1L)).thenReturn(Optional.of(nac));
        when(clienteRepository.existsByCorreo("nuevo.email@test.com")).thenReturn(false);
        when(catalogoRepository.existsByTipoAndDescripcionIgnoreCaseAndActivoTrue("ESTADO", "CIUDAD DE MEXICO")).thenReturn(true);
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ClienteResponseDTO resp = clienteService.actualizarParcial(req);
        assertNotNull(resp);
        assertEquals("NuevoNombre", resp.getNombre());
        assertEquals(0, resp.getCodigo());
        assertEquals("Datos actualizados parcialmente de forma correcta", resp.getMensaje());
    }

    @Test
    void actualizarParcial_porCurp_exitoso() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findByCurp("CURP123456HDFR00")).thenReturn(Optional.of(c));

        ClienteActualizacionParcialRequestDTO req = new ClienteActualizacionParcialRequestDTO();
        req.setCurp("CURP123456HDFR00");
        req.setNombre("ParcialCurp");
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ClienteResponseDTO resp = clienteService.actualizarParcial(req);
        assertNotNull(resp);
        assertEquals("ParcialCurp", resp.getNombre());
    }

    @Test
    void actualizarParcial_conDomicilioNuloYCorreoSinCambio() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        c.setDomicilio(null);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(c));

        ClienteActualizacionParcialRequestDTO req = new ClienteActualizacionParcialRequestDTO();
        req.setId(1L);
        req.setCorreo("carlos@test.com");
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ClienteResponseDTO res = clienteService.actualizarParcial(req);
        assertNotNull(res);
    }

    @Test
    void actualizarParcial_sinIdNiCurp_lanzaValidacionException() {
        ClienteActualizacionParcialRequestDTO req = new ClienteActualizacionParcialRequestDTO();
        assertThrows(ValidacionException.class, () -> clienteService.actualizarParcial(req));
    }

    @Test
    void actualizarParcial_fechaFutura_lanzaValidacionException() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(c));

        ClienteActualizacionParcialRequestDTO req = new ClienteActualizacionParcialRequestDTO();
        req.setId(1L);
        req.setFechaNacimiento("2099-01-01");

        assertThrows(ValidacionException.class, () -> clienteService.actualizarParcial(req));
    }

    @Test
    void actualizarParcial_menorDeEdad_lanzaValidacionException() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(c));

        ClienteActualizacionParcialRequestDTO req = new ClienteActualizacionParcialRequestDTO();
        req.setId(1L);
        req.setFechaNacimiento(LocalDate.now().minusYears(15).toString());

        assertThrows(ValidacionException.class, () -> clienteService.actualizarParcial(req));
    }

    @Test
    void actualizarParcial_nacionalidadInexistente_lanzaValidacionException() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(c));

        ClienteActualizacionParcialRequestDTO req = new ClienteActualizacionParcialRequestDTO();
        req.setId(1L);
        req.setNacionalidadId(99L);
        when(catalogoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ValidacionException.class, () -> clienteService.actualizarParcial(req));
    }

    @Test
    void actualizarParcial_correoDuplicado_lanzaCorreoDuplicadoException() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(c));

        ClienteActualizacionParcialRequestDTO req = new ClienteActualizacionParcialRequestDTO();
        req.setId(1L);
        req.setCorreo("otro@test.com");
        when(clienteRepository.existsByCorreo("otro@test.com")).thenReturn(true);

        assertThrows(CorreoDuplicadoException.class, () -> clienteService.actualizarParcial(req));
    }

    @Test
    void actualizarParcial_estadoInvalido_lanzaValidacionException() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(c));

        ClienteActualizacionParcialRequestDTO req = new ClienteActualizacionParcialRequestDTO();
        req.setId(1L);
        req.setEstado("INVALIDO");
        when(catalogoRepository.existsByTipoAndDescripcionIgnoreCaseAndActivoTrue("ESTADO", "INVALIDO")).thenReturn(false);
        when(catalogoRepository.existsByTipoAndClaveIgnoreCaseAndActivoTrue("ESTADO", "INVALIDO")).thenReturn(false);

        assertThrows(ValidacionException.class, () -> clienteService.actualizarParcial(req));
    }

    // ----------------------------------------------------
    // 6. DESACTIVAR Y REACTIVAR
    // ----------------------------------------------------

    @Test
    void desactivarCliente_cuandoExiste_cambiaEstatusAInactivo() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(c));
        when(clienteRepository.findByCurp("CURP123456HDFR00")).thenReturn(Optional.of(c));

        ClienteIdRequestDTO req = new ClienteIdRequestDTO();
        req.setId(1L);

        GenericResponse resp = clienteService.desactivarCliente(req);

        assertEquals(0, resp.getCodigo());
        assertFalse(c.getActivo());
        assertEquals(EstatusCuentaEnum.INACTIVA.getCodigo(), c.getCuenta().getEstatus());
        assertFalse(c.getLoginCliente().getActivo());
        verify(clienteRepository, times(1)).save(c);
    }

    @Test
    void desactivarCliente_conCuentaNulaYLoginNulo() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        c.setCuenta(null);
        c.setLoginCliente(null);
        when(clienteRepository.findByCurp("CURP123456HDFR00")).thenReturn(Optional.of(c));

        ClienteIdRequestDTO req = new ClienteIdRequestDTO();
        req.setCurp("CURP123456HDFR00");

        GenericResponse resp = clienteService.desactivarCliente(req);
        assertEquals(0, resp.getCodigo());
        assertFalse(c.getActivo());
    }

    @Test
    void desactivarCliente_porCurpRequest_cambiaEstatusAInactivo() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findByCurp("CURP123456HDFR00")).thenReturn(Optional.of(c));

        ClienteIdRequestDTO req = new ClienteIdRequestDTO();
        req.setCurp("CURP123456HDFR00");

        GenericResponse resp = clienteService.desactivarCliente(req);

        assertEquals(0, resp.getCodigo());
        assertFalse(c.getActivo());
    }

    @Test
    void desactivarCliente_sinIdNiCurp_lanzaValidacionException() {
        ClienteIdRequestDTO req = new ClienteIdRequestDTO();
        assertThrows(ValidacionException.class, () -> clienteService.desactivarCliente(req));
    }

    @Test
    void reactivarCliente_cuandoEstaInactivo_cambiaEstatusAActivo() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        c.setActivo(false);
        c.getCuenta().setEstatus(EstatusCuentaEnum.INACTIVA.getCodigo());
        c.getLoginCliente().setActivo(false);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(c));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ClienteResponseDTO resp = clienteService.reactivarCliente(1L);

        assertNotNull(resp);
        assertTrue(c.getActivo());
        assertEquals(EstatusCuentaEnum.ACTIVA.getCodigo(), c.getCuenta().getEstatus());
        assertTrue(c.getLoginCliente().getActivo());
        verify(clienteRepository, times(1)).save(c);
    }

    @Test
    void reactivarCliente_conCuentaNulaYLoginNulo() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        c.setActivo(false);
        c.setCuenta(null);
        c.setLoginCliente(null);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(c));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ClienteResponseDTO res = clienteService.reactivarCliente(1L);
        assertNotNull(res);
        assertTrue(c.getActivo());
    }

    @Test
    void reactivarCliente_cuandoYaEstaActivo_lanzaValidacionException() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        c.setActivo(true);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(c));

        assertThrows(ValidacionException.class, () -> clienteService.reactivarCliente(1L));
    }

    @Test
    void reactivarCliente_requestPorId_exitoso() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        c.setActivo(false);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(c));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ClienteIdRequestDTO req = new ClienteIdRequestDTO();
        req.setId(1L);

        ClienteResponseDTO resp = clienteService.reactivarCliente(req);
        assertNotNull(resp);
        assertTrue(c.getActivo());
    }

    @Test
    void reactivarCliente_requestPorCurp_exitoso() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        c.setActivo(false);
        when(clienteRepository.findByCurp("CURP123456HDFR00")).thenReturn(Optional.of(c));
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(c));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ClienteIdRequestDTO req = new ClienteIdRequestDTO();
        req.setCurp("CURP123456HDFR00");

        ClienteResponseDTO resp = clienteService.reactivarCliente(req);
        assertNotNull(resp);
        assertTrue(c.getActivo());
    }

    @Test
    void reactivarCliente_requestSinIdNiCurp_lanzaValidacionException() {
        ClienteIdRequestDTO req = new ClienteIdRequestDTO();
        assertThrows(ValidacionException.class, () -> clienteService.reactivarCliente(req));
    }

    // ----------------------------------------------------
    // 7. BUSQUEDA DINAMICA Y LISTADOS
    // ----------------------------------------------------

    @Test
    void buscarClienteDinamico_conCriterio_retornaPrimerCliente() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        ClienteBusquedaRequestDTO req = new ClienteBusquedaRequestDTO();
        req.setCurp("CURP123456HDFR00");

        when(clienteRepository.findAll(any(Specification.class))).thenReturn(List.of(c));

        ClienteResponseDTO res = clienteService.buscarClienteDinamico(req);
        assertNotNull(res);
        assertEquals("Carlos", res.getNombre());
    }

    @Test
    void buscarClientesDinamico_sinCriterios_lanzaValidacionException() {
        ClienteBusquedaRequestDTO req = new ClienteBusquedaRequestDTO();
        assertThrows(ValidacionException.class, () -> clienteService.buscarClientesDinamico(req));
    }

    @Test
    void buscarClientesDinamico_sinResultados_lanzaClienteNoEncontradoException() {
        ClienteBusquedaRequestDTO req = new ClienteBusquedaRequestDTO();
        req.setNombre("Desconocido");

        when(clienteRepository.findAll(any(Specification.class))).thenReturn(Collections.emptyList());

        assertThrows(ClienteNoEncontradoException.class, () -> clienteService.buscarClientesDinamico(req));
    }

    @Test
    void buscarClientesDinamico_conCriteriosIndividuales() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findAll(any(Specification.class))).thenReturn(List.of(c));

        ClienteBusquedaRequestDTO reqId = new ClienteBusquedaRequestDTO();
        reqId.setId(1L);
        assertEquals(1, clienteService.buscarClientesDinamico(reqId).size());

        ClienteBusquedaRequestDTO reqRfc = new ClienteBusquedaRequestDTO();
        reqRfc.setRfc("RFC123456789");
        assertEquals(1, clienteService.buscarClientesDinamico(reqRfc).size());

        ClienteBusquedaRequestDTO reqCorreo = new ClienteBusquedaRequestDTO();
        reqCorreo.setCorreo("carlos@test.com");
        assertEquals(1, clienteService.buscarClientesDinamico(reqCorreo).size());

        ClienteBusquedaRequestDTO reqCuenta = new ClienteBusquedaRequestDTO();
        reqCuenta.setNumeroCuenta("5225253189113705");
        assertEquals(1, clienteService.buscarClientesDinamico(reqCuenta).size());

        ClienteBusquedaRequestDTO reqAp = new ClienteBusquedaRequestDTO();
        reqAp.setApellidoPaterno("Sanchez");
        assertEquals(1, clienteService.buscarClientesDinamico(reqAp).size());

        ClienteBusquedaRequestDTO reqActivo = new ClienteBusquedaRequestDTO();
        reqActivo.setActivo(true);
        assertEquals(1, clienteService.buscarClientesDinamico(reqActivo).size());
    }

    @Test
    void obtenerClientesActivos_retornaLista() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(clienteRepository.findByActivoTrueOrderByFechaRegistroAsc()).thenReturn(List.of(c));

        List<ClienteResponseDTO> list = clienteService.obtenerClientesActivos();
        assertNotNull(list);
        assertEquals(1, list.size());
    }

    @Test
    void obtenerClientesPorRangoFechas_rangoValido_retornaLista() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        LocalDate inicio = LocalDate.of(2023, 1, 1);
        LocalDate fin = LocalDate.of(2023, 12, 31);
        when(clienteRepository.findByFechaRegistroBetweenOrderByFechaRegistroAsc(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(c));

        List<ClienteResponseDTO> list = clienteService.obtenerClientesPorRangoFechas(inicio, fin);
        assertNotNull(list);
        assertEquals(1, list.size());
    }

    @Test
    void obtenerClientesPorRangoFechas_inicioPosteriorAFin_lanzaValidacionException() {
        LocalDate inicio = LocalDate.of(2024, 1, 1);
        LocalDate fin = LocalDate.of(2023, 1, 1);

        assertThrows(ValidacionException.class, () -> clienteService.obtenerClientesPorRangoFechas(inicio, fin));
    }

    @Test
    void obtenerCuentaPorNumero_cuandoExisteConSaldoYCliente_retornaDTO() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(cuentaRepository.findByNumeroCuenta("5225253189113705")).thenReturn(Optional.of(c.getCuenta()));

        CuentaResponseDTO dto = clienteService.obtenerCuentaPorNumero("5225253189113705");
        assertNotNull(dto);
        assertEquals(new BigDecimal("1000.00"), dto.getSaldo());
        assertEquals("Carlos Sanchez", dto.getNombreCliente());
    }

    @Test
    void obtenerCuentaPorNumero_cuandoExisteSinSaldoYSinCliente_retornaDTOConSaldoCero() {
        Cuenta cuenta = new Cuenta();
        cuenta.setNumeroCuenta("1234567890123456");
        cuenta.setEstatus(EstatusCuentaEnum.ACTIVA.getCodigo());
        cuenta.setSaldo(null);
        cuenta.setCliente(null);
        when(cuentaRepository.findByNumeroCuenta("1234567890123456")).thenReturn(Optional.of(cuenta));

        CuentaResponseDTO dto = clienteService.obtenerCuentaPorNumero("1234567890123456");
        assertNotNull(dto);
        assertEquals(BigDecimal.ZERO, dto.getSaldo());
        assertNull(dto.getNombreCliente());
    }

    @Test
    void obtenerCuentaPorNumero_cuandoNoExiste_lanzaCuentaNoEncontradaException() {
        when(cuentaRepository.findByNumeroCuenta("0000000000000000")).thenReturn(Optional.empty());

        assertThrows(CuentaNoEncontradaException.class, () -> clienteService.obtenerCuentaPorNumero("0000000000000000"));
    }

    @Test
    void obtenerCuentasActivas_retornaLista() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        when(cuentaRepository.findByEstatusOrderByFechaAperturaAsc(EstatusCuentaEnum.ACTIVA.getCodigo()))
                .thenReturn(List.of(c.getCuenta()));

        List<CuentaResponseDTO> list = clienteService.obtenerCuentasActivas();
        assertNotNull(list);
        assertEquals(1, list.size());
    }

    @Test
    void registrarCliente_minimalSinKyc_aplicaValoresPorDefectoYGuarda() {
        ClienteRequestDTO req = new ClienteRequestDTO();
        req.setNombre("Carlos");
        req.setApellidoPaterno("Garay");
        req.setCorreo("carlos.simple@gmail.com");
        req.setTelefonoMovil("5512345678");

        when(clienteRepository.existsByCorreo("carlos.simple@gmail.com")).thenReturn(false);
        when(clienteRepository.existsByCurp(anyString())).thenReturn(false);
        when(cuentaRepository.existsByNumeroCuenta(anyString())).thenReturn(false);
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> {
            Cliente saved = i.getArgument(0);
            assertEquals("", saved.getApellidoMaterno());
            assertEquals(LocalDate.of(1995, 1, 1), saved.getFechaNacimiento());
            assertEquals("HOMBRE", saved.getSexo());
            assertEquals("MEXICANA", saved.getNacionalidad());
            assertEquals("SOLTERO", saved.getEstadoCivil());
            assertNotNull(saved.getCurp());
            assertEquals(18, saved.getCurp().length());
            assertNotNull(saved.getRfc());
            saved.setId(50L);
            return saved;
        });

        ClienteResponseDTO res = clienteService.registrarCliente(req);
        assertNotNull(res);
        assertEquals(50L, res.getId());
        assertEquals("Carlos", res.getNombre());
        assertEquals("CIUDAD DE MEXICO", res.getEstado());
        assertEquals("06000", res.getCodigoPostal());
        assertEquals(0, res.getCodigo());
    }
}
