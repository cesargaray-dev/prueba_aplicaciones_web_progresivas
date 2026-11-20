package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.clientes.CuentaConsultaRequestDTO;
import com.proyecto.servicios.model.clientes.CuentaResponseDTO;
import com.proyecto.servicios.service.clientes.ClienteService;
import com.proyecto.servicios.util.TelefonoUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cuentas")
@Tag(name = "Cuentas", description = "Operaciones de consulta de cuentas bancarias")
public class CuentaController {

    private final ClienteService clienteService;

    public CuentaController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @PostMapping("/consultar")
    @Operation(summary = "Obtener cuenta por número", description = "Consulta los detalles de una cuenta bancaria mediante JSON con @RequestBody")
    public ResponseEntity<CuentaResponseDTO> obtenerCuenta(@Valid @RequestBody CuentaConsultaRequestDTO request) {
        return ResponseEntity.ok(clienteService.obtenerCuentaPorNumero(request.getNumeroCuenta()));
    }

    @GetMapping("/activas")
    @Operation(summary = "Obtener cuentas activas", description = "Devuelve el listado de todas las cuentas bancarias con estatus activo")
    public ResponseEntity<List<CuentaResponseDTO>> obtenerCuentasActivas() {
        return ResponseEntity.ok(clienteService.obtenerCuentasActivas());
    }

    @PostMapping("/saldo")
    @Operation(summary = "Consultar saldo", description = "Consulta el saldo disponible de una cuenta bancaria mediante JSON con @RequestBody")
    public ResponseEntity<CuentaResponseDTO> consultarSaldo(@Valid @RequestBody CuentaConsultaRequestDTO request) {
        CuentaResponseDTO cuenta = clienteService.obtenerCuentaPorNumero(request.getNumeroCuenta());
        cuenta.setMensaje("El saldo disponible de la cuenta " + TelefonoUtil.enmascararCuenta(request.getNumeroCuenta()) + " es: $" + cuenta.getSaldo());
        return ResponseEntity.ok(cuenta);
    }
}
