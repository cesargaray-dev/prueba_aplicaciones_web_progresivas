package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.GlobalExceptionHandler.ErrorResponse;
import com.proyecto.servicios.model.gestopago.GenerarCodigo2FARequestDTO;
import com.proyecto.servicios.model.gestopago.GenerarCodigo2FAResponseDTO;
import com.proyecto.servicios.model.gestopago.HistorialPagosRequestDTO;
import com.proyecto.servicios.model.gestopago.HistorialPagosResponseDTO;
import com.proyecto.servicios.model.gestopago.PagoServicioRequestDTO;
import com.proyecto.servicios.model.gestopago.PagoServicioResponseDTO;
import com.proyecto.servicios.service.PagoServicioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pagos")
@Validated
@Tag(name = "Pagos de Servicios", description = "Módulo bancario de pago de servicios con GestoPago (PuntoRed)")
@Slf4j
public class PagoServicioController {

    private final PagoServicioService pagoServicioService;

    public PagoServicioController(PagoServicioService pagoServicioService) {
        this.pagoServicioService = pagoServicioService;
    }

    @PostMapping("/2fa/generar")
    @Operation(
            summary = "Generar código 2FA temporal para pago de servicios",
            description = "Genera y propaga un código temporal de 6 dígitos (OTP) de un solo uso para autorizar un pago de servicio desde la cuenta indicada."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Código 2FA temporal generado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, cliente inactivo o cuenta inactiva",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cuenta bancaria no encontrada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GenerarCodigo2FAResponseDTO> generarCodigo2FA(
            @Valid @RequestBody GenerarCodigo2FARequestDTO request) {
        log.info("REST request para generar código 2FA temporal para cuenta: {}", request.getNumeroCuenta());
        return ResponseEntity.ok(pagoServicioService.generarCodigo2FA(request));
    }

    @PostMapping("/servicios")
    @Operation(
            summary = "Realizar pago de servicio con GestoPago",
            description = "Ejecuta el pago de un servicio previa validación de existencia de cuenta, cliente activo, cuenta activa, código 2FA de 6 dígitos y saldo suficiente. " +
                    "Solo descuenta el saldo de la cuenta bancaria cuando GestoPago confirma CODIGO=01."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pago de servicio aplicado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, saldo insuficiente, cliente/cuenta inactiva o rechazo del proveedor",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Código 2FA inválido, expirado o no generado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cuenta bancaria no encontrada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Transacción duplicada reportada por el proveedor (CODIGO 06)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PagoServicioResponseDTO> pagarServicio(
            @Valid @RequestBody PagoServicioRequestDTO request) {
        log.info("REST request para procesar pago de servicio con cuenta: {}", request.getNumeroCuenta());
        return ResponseEntity.ok(pagoServicioService.procesarPagoServicio(request));
    }

    @PostMapping("/historial")
    @Operation(
            summary = "Consultar historial de pagos de servicios y recargas",
            description = "Consulta todos los pagos de servicios y recargas efectuadas con una cuenta bancaria mediante JSON @RequestBody, ordenados cronológicamente del más reciente al más antiguo."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Historial consultado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Número de cuenta inválido (debe contener 16 dígitos numéricos)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cuenta bancaria no encontrada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<HistorialPagosResponseDTO> consultarHistorial(
            @Valid @RequestBody HistorialPagosRequestDTO request) {
        log.info("REST request para consultar historial de pagos para cuenta: {}", request.getNumeroCuenta());
        return ResponseEntity.ok(pagoServicioService.consultarHistorial(request));
    }
}
