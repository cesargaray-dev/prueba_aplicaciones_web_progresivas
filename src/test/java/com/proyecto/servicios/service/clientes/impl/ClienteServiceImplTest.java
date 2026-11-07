package com.proyecto.servicios.service.clientes.impl;

import com.proyecto.servicios.entity.clientes.*;
import com.proyecto.servicios.enums.EstatusCuentaEnum;
import com.proyecto.servicios.exception.ClienteNoEncontradoException;
import com.proyecto.servicios.exception.CuentaNoEncontradaException;
import com.proyecto.servicios.exception.CurpDuplicadaException;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.clientes.ClienteIdRequestDTO;
import com.proyecto.servicios.model.clientes.ClienteRequestDTO;
import com.proyecto.servicios.model.clientes.ClienteResponseDTO;
import com.proyecto.servicios.repositorys.clientes.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
        c.setApellidoPaterno("Sanchez");
        c.setApellidoMaterno("Ramirez");
        c.setCorreo(correo);
        c.setTelefonoMovil("5512345678");
        c.setActivo(true);
        c.setFechaNacimiento(LocalDate.of(1995, 5, 20));

        Cuenta cuenta = new Cuenta();
        cuenta.setId(100L);
        cuenta.setNumeroCuenta("5225253189113705");
        cuenta.setEstatus(EstatusCuentaEnum.ACTIVA.getCodigo());
        cuenta.setCliente(c);

        Saldo saldo = new Saldo();
        saldo.setSaldo(new BigDecimal("1000.00"));
        saldo.setCuenta(cuenta);
        cuenta.setSaldo(saldo);

        c.setCuenta(cuenta);

        Domicilio d = new Domicilio();
        d.setCalle("Av Principal");
        d.setNumeroExterior("123");
        d.setCodigoPostal("06000");
        c.setDomicilio(d);

        return c;
    }

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
        verify(clienteRepository, times(1)).save(c);
    }

    @Test
    void reactivarCliente_cuandoEstaInactivo_cambiaEstatusAActivo() {
        Cliente c = crearClienteMock(1L, "CURP123456HDFR00", "RFC123456789", "carlos@test.com");
        c.setActivo(false);
        c.getCuenta().setEstatus(EstatusCuentaEnum.INACTIVA.getCodigo());
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(c));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        ClienteResponseDTO resp = clienteService.reactivarCliente(1L);

        assertNotNull(resp);
        assertTrue(c.getActivo());
        assertEquals(EstatusCuentaEnum.ACTIVA.getCodigo(), c.getCuenta().getEstatus());
        verify(clienteRepository, times(1)).save(c);
    }

    @Test
    void registrarCliente_conCurpDuplicada_lanzaCurpDuplicadaException() {
        ClienteRequestDTO req = new ClienteRequestDTO();
        req.setFechaNacimiento("1990-01-01");
        req.setNacionalidadId(1L);
        req.setCurp("CURP123456HDFR00");

        Catalogo nacionalidad = new Catalogo();
        nacionalidad.setId(1L);
        nacionalidad.setTipo("NACIONALIDAD");
        nacionalidad.setActivo(true);
        nacionalidad.setDescripcion("MEXICANA");
        when(catalogoRepository.findById(1L)).thenReturn(Optional.of(nacionalidad));

        when(clienteRepository.existsByCurp("CURP123456HDFR00")).thenReturn(true);

        assertThrows(CurpDuplicadaException.class, () -> clienteService.registrarCliente(req));
    }
}
