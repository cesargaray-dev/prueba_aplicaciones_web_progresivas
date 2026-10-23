package com.proyecto.servicios.service.clientes;

import com.proyecto.servicios.model.clientes.ClienteRequestDTO;
import com.proyecto.servicios.model.clientes.ClienteResponseDTO;
import com.proyecto.servicios.model.clientes.CuentaResponseDTO;

import java.time.LocalDate;
import java.util.List;

public interface ClienteService {

    ClienteResponseDTO registrarCliente(ClienteRequestDTO request);

    List<ClienteResponseDTO> obtenerTodosLosClientes();

    ClienteResponseDTO obtenerClientePorId(Long id);

    ClienteResponseDTO obtenerClientePorCurp(String curp);

    ClienteResponseDTO obtenerClientePorRfc(String rfc);

    ClienteResponseDTO obtenerClientePorCorreo(String correo);

    ClienteResponseDTO obtenerClientePorNumeroCuenta(String numeroCuenta);

    ClienteResponseDTO actualizarCliente(String curp, ClienteRequestDTO request);

    ClienteResponseDTO actualizarCompleto(ClienteRequestDTO request);

    ClienteResponseDTO actualizarParcial(com.proyecto.servicios.model.clientes.ClienteActualizacionParcialRequestDTO request);

    com.proyecto.servicios.model.clientes.ClienteResponse actualizarCompleto(com.proyecto.servicios.model.clientes.ClienteRegistroRequest request);

    void desactivarCliente(String curp);

    com.proyecto.servicios.model.GenericResponse desactivarCliente(com.proyecto.servicios.model.clientes.ClienteIdRequestDTO request);

    ClienteResponseDTO reactivarCliente(Long id);

    ClienteResponseDTO reactivarCliente(com.proyecto.servicios.model.clientes.ClienteIdRequestDTO request);

    ClienteResponseDTO buscarClienteDinamico(com.proyecto.servicios.model.clientes.ClienteBusquedaRequestDTO peticion);

    List<ClienteResponseDTO> buscarClientesDinamico(com.proyecto.servicios.model.clientes.ClienteBusquedaRequestDTO peticion);

    // Consultas específicas
    List<ClienteResponseDTO> obtenerClientesActivos();

    List<ClienteResponseDTO> obtenerClientesPorRangoFechas(LocalDate inicio, LocalDate fin);

    CuentaResponseDTO obtenerCuentaPorNumero(String numeroCuenta);

    List<CuentaResponseDTO> obtenerCuentasActivas();
}
