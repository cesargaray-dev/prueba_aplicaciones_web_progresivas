package com.proyecto.servicios.controller;

import com.proyecto.servicios.entity.gestopago.PagoServicio;
import com.proyecto.servicios.exception.AutenticacionException;
import com.proyecto.servicios.exception.GlobalExceptionHandler.ErrorResponse;
import com.proyecto.servicios.model.brevo.EmailEnvioResultadoDTO;
import com.proyecto.servicios.model.gestopago.GenerarCodigo2FARequestDTO;
import com.proyecto.servicios.model.gestopago.GenerarCodigo2FAResponseDTO;
import com.proyecto.servicios.model.gestopago.HistorialPagosRequestDTO;
import com.proyecto.servicios.model.gestopago.HistorialPagosResponseDTO;
import com.proyecto.servicios.model.gestopago.PagoServicioRequestDTO;
import com.proyecto.servicios.model.gestopago.PagoServicioResponseDTO;
import com.proyecto.servicios.model.gestopago.ReenviarComprobanteRequestDTO;
import com.proyecto.servicios.model.gestopago.ValidarReferenciaRequestDTO;
import com.proyecto.servicios.model.gestopago.ValidarReferenciaResponseDTO;
import com.proyecto.servicios.security.AccountOwnershipValidator;
import com.proyecto.servicios.service.NotificacionPagoService;
import com.proyecto.servicios.service.PagoServicioService;
import com.proyecto.servicios.util.TelefonoUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final NotificacionPagoService notificacionPagoService;
    private final AccountOwnershipValidator ownershipValidator;

    public PagoServicioController(PagoServicioService pagoServicioService) {
        this(pagoServicioService, null, null);
    }

    public PagoServicioController(
            PagoServicioService pagoServicioService,
            NotificacionPagoService notificacionPagoService) {
        this(pagoServicioService, notificacionPagoService, null);
    }

    @Autowired
    public PagoServicioController(
            PagoServicioService pagoServicioService,
            NotificacionPagoService notificacionPagoService,
            AccountOwnershipValidator ownershipValidator) {
        this.pagoServicioService = pagoServicioService;
        this.notificacionPagoService = notificacionPagoService;
        this.ownershipValidator = ownershipValidator;
    }

    private void verificarPropiedadCuenta(String numeroCuenta) {
        if (ownershipValidator == null) {
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AutenticacionException("Sesión no autorizada.");
        }
        ownershipValidator.validarPropiedadCuenta(auth.getName(), numeroCuenta);
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
            @ApiResponse(responseCode = "403", description = "La cuenta no pertenece al usuario autenticado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cuenta bancaria no encontrada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GenerarCodigo2FAResponseDTO> generarCodigo2FA(
            @Valid @RequestBody GenerarCodigo2FARequestDTO request) {
        verificarPropiedadCuenta(request.getNumeroCuenta());
        log.info("REST request para generar código 2FA temporal para cuenta: {}",
                TelefonoUtil.enmascararCuenta(request.getNumeroCuenta()));
        GenerarCodigo2FAResponseDTO resp = pagoServicioService.generarCodigo2FA(request);
        if (notificacionPagoService != null) {
            resp = notificacionPagoService.enriquecerYEnviar2FA(resp);
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/validar-referencia")
    @Operation(
            summary = "Validar referencia y reglas de negocio para pago de servicio o recarga",
            description = "Valida el formato de la referencia (longitud, caracteres numéricos/alfanuméricos, código de barras, contrato) y dígito verificador (Módulo 10/11) según el catálogo de GestoPago."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Validación procesada exitosamente (indica en 'esValida' si cumple las reglas)"),
            @ApiResponse(responseCode = "400", description = "Parámetros obligatorios faltantes o con formato inválido",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ValidarReferenciaResponseDTO> validarReferencia(
            @Valid @RequestBody ValidarReferenciaRequestDTO request) {
        log.info("REST request para validar referencia de servicio idServicio: {}, referencia: {}",
                request.getIdServicio(), request.getReferencia());
        return ResponseEntity.ok(pagoServicioService.validarReferencia(request));
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
            @ApiResponse(responseCode = "403", description = "La cuenta no pertenece al usuario autenticado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cuenta bancaria no encontrada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Transacción duplicada reportada por el proveedor (CODIGO 06)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PagoServicioResponseDTO> pagarServicio(
            @Valid @RequestBody PagoServicioRequestDTO request) {
        verificarPropiedadCuenta(request.getNumeroCuenta());
        log.info("REST request para procesar pago de servicio con cuenta: {}",
                TelefonoUtil.enmascararCuenta(request.getNumeroCuenta()));
        if (notificacionPagoService != null) {
            notificacionPagoService.validarAntesDePagar(request);
        }
        PagoServicioResponseDTO resp = pagoServicioService.procesarPagoServicio(request);
        if (notificacionPagoService != null) {
            resp = notificacionPagoService.enriquecerRespuestaPago(resp);
        }
        return ResponseEntity.ok(resp);
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
            @ApiResponse(responseCode = "403", description = "La cuenta no pertenece al usuario autenticado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Cuenta bancaria no encontrada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<HistorialPagosResponseDTO> consultarHistorial(
            @Valid @RequestBody HistorialPagosRequestDTO request) {
        verificarPropiedadCuenta(request.getNumeroCuenta());
        log.info("REST request para consultar historial de pagos para cuenta: {}",
                TelefonoUtil.enmascararCuenta(request.getNumeroCuenta()));
        return ResponseEntity.ok(pagoServicioService.consultarHistorial(request));
    }

    @PostMapping("/comprobante/reenviar")
    @Operation(
            summary = "Reenviar comprobante digital por correo electrónico vía Brevo",
            description = "Reenvía el comprobante HTML de un pago exitoso al correo registrado de la cuenta o al correo alternativo indicado."
    )
    public ResponseEntity<EmailEnvioResultadoDTO> reenviarComprobante(
            @Valid @RequestBody ReenviarComprobanteRequestDTO request) {
        verificarPropiedadCuenta(request.getNumeroCuenta());
        log.info("REST request para reenviar comprobante folio={} de cuenta={}",
                request.getFolioTicket(), TelefonoUtil.enmascararCuenta(request.getNumeroCuenta()));
        if (notificacionPagoService == null) {
            throw new IllegalStateException("El servicio de notificaciones no está disponible en este contexto");
        }
        return ResponseEntity.ok(notificacionPagoService.reenviarComprobante(request));
    }

    @GetMapping("/comprobante/{folioTicket}/pdf")
    @Operation(
            summary = "Descargar comprobante oficial de pago en formato PDF",
            description = "Genera y descarga el comprobante oficial en formato PDF para el folio indicado, validando que pertenezca a la cuenta del usuario autenticado."
    )
    public ResponseEntity<byte[]> descargarComprobantePdf(@PathVariable("folioTicket") String folioTicket) {
        if (notificacionPagoService == null) {
            throw new IllegalStateException("El servicio de notificaciones no está disponible en este contexto");
        }
        PagoServicio pago = notificacionPagoService.obtenerPagoPorFolio(folioTicket);
        if (pago != null && pago.getNumeroCuenta() != null) {
            verificarPropiedadCuenta(pago.getNumeroCuenta());
        }
        byte[] pdfBytes = notificacionPagoService.generarComprobantePdf(folioTicket);
        String safeFolio = folioTicket != null ? folioTicket.replaceAll("[^A-Za-z0-9_-]", "_") : "ticket";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"comprobante-" + safeFolio + ".pdf\"")
                .body(pdfBytes);
    }
}
