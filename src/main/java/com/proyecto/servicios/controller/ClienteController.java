package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.GlobalExceptionHandler.ErrorResponse;
import com.proyecto.servicios.model.clientes.ClienteRequestDTO;
import com.proyecto.servicios.model.clientes.ClienteResponseDTO;
import com.proyecto.servicios.service.clientes.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/clientes")
@Validated
@Tag(name = "Clientes", description = "Operaciones de gestión de clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @PostMapping
    @Operation(summary = "Registrar cliente", description = "Crea un nuevo cliente en el sistema con cuenta bancaria inicial de $100.00")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Cliente registrado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos o formato incorrecto en el JSON",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "CURP, RFC o correo ya registrado",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClienteResponseDTO> registrarCliente(@Valid @RequestBody ClienteRequestDTO request) {
        return new ResponseEntity<>(clienteService.registrarCliente(request), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Obtener todos los clientes", description = "Devuelve la lista completa de clientes registrados")
    @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente")
    public ResponseEntity<List<ClienteResponseDTO>> obtenerTodos() {
        return ResponseEntity.ok(clienteService.obtenerTodosLosClientes());
    }


    @PutMapping("/actualizar")
    @Operation(summary = "Actualizar cliente", description = "Actualiza los datos de un cliente enviando todos sus datos en JSON. La CURP o ID viene incluida en el cuerpo.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cliente actualizado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos o formato incorrecto en el JSON",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Cliente no encontrado",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClienteResponseDTO> actualizarCliente(
            @Valid @RequestBody ClienteRequestDTO request) {
        return ResponseEntity.ok(clienteService.actualizarCompleto(request));
    }

    @PatchMapping("/actualizar")
    @Operation(summary = "Actualizar cliente parcialmente", description = "Actualiza solo los campos enviados en el JSON. Se identifica al cliente por ID o CURP. CURP y RFC no se modifican.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cliente actualizado parcialmente"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos o formato incorrecto en el JSON",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Cliente no encontrado",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Correo ya registrado en otro cliente",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClienteResponseDTO> actualizarParcial(
            @Valid @RequestBody com.proyecto.servicios.model.clientes.ClienteActualizacionParcialRequestDTO request) {
        return ResponseEntity.ok(clienteService.actualizarParcial(request));
    }

    @PatchMapping("/desactivar")
    @Operation(summary = "Desactivar cliente", description = "Desactiva un cliente (baja lógica) recibiendo un JSON con su ID o CURP")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cliente desactivado exitosamente"),
        @ApiResponse(responseCode = "404", description = "Cliente no encontrado",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Formato de CURP o ID inválido",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<com.proyecto.servicios.model.GenericResponse> desactivarCliente(
            @Valid @RequestBody com.proyecto.servicios.model.clientes.ClienteIdRequestDTO peticion) {
        return ResponseEntity.ok(clienteService.desactivarCliente(peticion));
    }

    @PatchMapping("/reactivar")
    @Operation(summary = "Reactivar cliente", description = "Reactiva un cliente previamente desactivado recibiendo un JSON con su ID o CURP")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cliente reactivado exitosamente"),
        @ApiResponse(responseCode = "404", description = "Cliente no encontrado",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "El cliente ya se encuentra activo o parámetros inválidos",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClienteResponseDTO> reactivarCliente(
            @Valid @RequestBody com.proyecto.servicios.model.clientes.ClienteIdRequestDTO peticion) {
        return ResponseEntity.ok(clienteService.reactivarCliente(peticion));
    }

    @PostMapping("/buscar")
    @Operation(summary = "Buscar cliente dinámicamente", description = "Permite buscar un cliente combinando filtros: curp, rfc, correo, numeroCuenta, nombre, activo")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Cliente encontrado"),
        @ApiResponse(responseCode = "404", description = "No existe un cliente con los datos proporcionados",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "400", description = "Filtros inválidos o sin criterios",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ClienteResponseDTO> buscarCliente(
            @Valid @RequestBody com.proyecto.servicios.model.clientes.ClienteBusquedaRequestDTO peticion) {
        return ResponseEntity.ok(clienteService.buscarClienteDinamico(peticion));
    }

    @GetMapping("/activos")
    @Operation(summary = "Obtener clientes activos", description = "Devuelve únicamente los clientes con estado activo")
    @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente")
    public ResponseEntity<List<ClienteResponseDTO>> obtenerActivos() {
        return ResponseEntity.ok(clienteService.obtenerClientesActivos());
    }

    @PostMapping("/rango-fechas")
    @Operation(summary = "Obtener clientes por rango de fechas de registro",
            description = "Filtra clientes enviando un JSON con fecha de inicio y fin. Formato: yyyy-MM-dd")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
        @ApiResponse(responseCode = "400", description = "Formato de fecha inválido o parámetros faltantes",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<ClienteResponseDTO>> obtenerPorRangoFechas(
            @Valid @RequestBody com.proyecto.servicios.model.clientes.ClienteRangoFechasRequestDTO peticion) {
        return ResponseEntity.ok(clienteService.obtenerClientesPorRangoFechas(peticion.getInicio(), peticion.getFin()));
    }
}
