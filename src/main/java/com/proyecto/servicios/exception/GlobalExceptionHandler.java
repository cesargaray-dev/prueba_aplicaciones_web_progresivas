package com.proyecto.servicios.exception;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.util.TelefonoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("\\b\\d{12,19}\\b");

    private String sanitizarMensaje(String mensaje) {
        if (mensaje == null) {
            return null;
        }
        Matcher matcher = ACCOUNT_NUMBER_PATTERN.matcher(mensaje);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(TelefonoUtil.enmascararCuenta(matcher.group())));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    @ExceptionHandler(ClienteNoEncontradoException.class)
    public ResponseEntity<GenericResponse> handleClienteNoEncontrado(ClienteNoEncontradoException ex) {
        log.error("Cliente no encontrado", ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new GenericResponse(1, ex.getMessage()));
    }

    @ExceptionHandler(CurpDuplicadaException.class)
    public ResponseEntity<GenericResponse> handleCurpDuplicada(CurpDuplicadaException ex) {
        log.error("CURP duplicada", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new GenericResponse(1, ex.getMessage()));
    }

    @ExceptionHandler(RfcDuplicadoException.class)
    public ResponseEntity<GenericResponse> handleRfcDuplicado(RfcDuplicadoException ex) {
        log.error("RFC duplicado", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new GenericResponse(1, ex.getMessage()));
    }

    @ExceptionHandler(CorreoDuplicadoException.class)
    public ResponseEntity<GenericResponse> handleCorreoDuplicado(CorreoDuplicadoException ex) {
        log.error("Correo duplicado", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new GenericResponse(1, ex.getMessage()));
    }

    @ExceptionHandler(ClienteYaRegistradoException.class)
    public ResponseEntity<GenericResponse> handleClienteYaRegistrado(ClienteYaRegistradoException ex) {
        log.error("Cliente ya registrado", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new GenericResponse(1, ex.getMessage()));
    }

    @ExceptionHandler(CuentaNoEncontradaException.class)
    public ResponseEntity<GenericResponse> handleCuentaNoEncontrada(CuentaNoEncontradaException ex) {
        log.error("Cuenta no encontrada: {}", sanitizarMensaje(ex.getMessage()));
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new GenericResponse(1, sanitizarMensaje(ex.getMessage())));
    }

    @ExceptionHandler(ClienteInactivoException.class)
    public ResponseEntity<GenericResponse> handleClienteInactivo(ClienteInactivoException ex) {
        log.error("Cliente inactivo: {}", sanitizarMensaje(ex.getMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new GenericResponse(1, sanitizarMensaje(ex.getMessage())));
    }

    @ExceptionHandler(CuentaInactivaException.class)
    public ResponseEntity<GenericResponse> handleCuentaInactiva(CuentaInactivaException ex) {
        log.error("Cuenta inactiva: {}", sanitizarMensaje(ex.getMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new GenericResponse(1, sanitizarMensaje(ex.getMessage())));
    }

    @ExceptionHandler(Autenticacion2FAException.class)
    public ResponseEntity<GenericResponse> handleAutenticacion2FA(Autenticacion2FAException ex) {
        log.error("Fallo de autenticación 2FA: {}", sanitizarMensaje(ex.getMessage()));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new GenericResponse(1, sanitizarMensaje(ex.getMessage())));
    }

    @ExceptionHandler(AutenticacionException.class)
    public ResponseEntity<GenericResponse> handleAutenticacion(AutenticacionException ex) {
        log.error("Fallo de autenticación de cliente");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new GenericResponse(1, sanitizarMensaje(ex.getMessage())));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<GenericResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Acceso denegado: {}", sanitizarMensaje(ex.getMessage()));
        String msg = ex.getMessage() != null && !ex.getMessage().isBlank()
                ? sanitizarMensaje(ex.getMessage())
                : "Acceso denegado. No tienes permisos para acceder a este recurso.";
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new GenericResponse(1, msg));
    }

    @ExceptionHandler(SaldoInsuficienteException.class)
    public ResponseEntity<GenericResponse> handleSaldoInsuficiente(SaldoInsuficienteException ex) {
        log.error("Saldo insuficiente en cuenta: {}", sanitizarMensaje(ex.getMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new GenericResponse(1, sanitizarMensaje(ex.getMessage())));
    }

    @ExceptionHandler(PagoServicioProveedorException.class)
    public ResponseEntity<GenericResponse> handlePagoServicioProveedor(PagoServicioProveedorException ex) {
        log.error("Error devuelto por el proveedor de pago de servicios (código: {})", ex.getCodigoProveedor());
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        if (ex.getDetalleTicket() != null) {
            var ticket = ex.getDetalleTicket();
            ticket.setCodigo(ticket.getCodigo() != null ? ticket.getCodigo() : 1);
            ticket.setMensaje(sanitizarMensaje(ex.getMessage()));
            if (ticket.getMensajeOperacion() != null) {
                ticket.setMensajeOperacion(sanitizarMensaje(ticket.getMensajeOperacion()));
            }
            return ResponseEntity.status(status).body(ticket);
        }
        return ResponseEntity.status(status)
                .body(new GenericResponse(1, sanitizarMensaje(ex.getMessage())));
    }

    @ExceptionHandler(GestoPagoException.class)
    public ResponseEntity<GenericResponse> handleGestoPagoException(GestoPagoException ex) {
        log.error("Error de comunicación con GestoPago");
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status)
                .body(new GenericResponse(1, sanitizarMensaje(ex.getMessage())));
    }

    @ExceptionHandler(ValidacionException.class)
    public ResponseEntity<GenericResponse> handleValidacion(ValidacionException ex) {
        log.error("Error de validación: {}", sanitizarMensaje(ex.getMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new GenericResponse(1, sanitizarMensaje(ex.getMessage())));
    }

    @ExceptionHandler(ClienteException.class)
    public ResponseEntity<GenericResponse> handleClienteException(ClienteException ex) {
        log.error("Error en módulo de clientes: {}", sanitizarMensaje(ex.getMessage()));
        return ResponseEntity.status(ex.getStatusCode())
                .body(new GenericResponse(1, sanitizarMensaje(ex.getMessage())));
    }

    /**
     * En caso de que alguna validacion no se cumpla para algun DTO se tira MethodArgumentNotValidException
     * este metodo la cacha y regresa los mensajes de error para el cliente, con los message de cada campo.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GenericResponse> dtoValidation(MethodArgumentNotValidException ex) {
        GenericResponse genericResponse = new GenericResponse();
        Map<String, String> listError = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            listError.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        genericResponse.setCodigo(1);
        String detalle = listError.values().stream()
                .filter(m -> m != null && !m.isBlank())
                .distinct()
                .reduce((a, b) -> a + " · " + b)
                .orElse("Verifica los campos ingresados.");
        genericResponse.setMensaje(detalle);
        log.error("Error de validación DTO: {}", detalle);
        return new ResponseEntity<>(genericResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<GenericResponse> handleConstraintViolation(jakarta.validation.ConstraintViolationException ex) {
        Map<String, String> listError = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String path = violation.getPropertyPath().toString();
            String campo = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            listError.put(campo, violation.getMessage());
        });
        String detalle = listError.values().stream()
                .filter(m -> m != null && !m.isBlank())
                .distinct()
                .reduce((a, b) -> a + " · " + b)
                .orElse("Parámetros inválidos.");
        GenericResponse response = new GenericResponse(1, detalle);
        log.error("Error de validación de parámetros: {}", detalle);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<GenericResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        String mensaje = "El parámetro '" + ex.getParameterName() + "' es requerido y no fue proporcionado.";
        log.error("Parámetro requerido faltante: {}", ex.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new GenericResponse(1, mensaje));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<GenericResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String tipoEsperado = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "desconocido";
        String mensaje = "El parámetro '" + ex.getName() + "' tiene un formato inválido. Se esperaba un valor de tipo: " + tipoEsperado + ".";
        log.error("Error de tipo en parámetro: {}", ex.getName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new GenericResponse(1, mensaje));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<GenericResponse> handleHttpMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        log.error("Error de formato en JSON", ex);

        String mensajeEspecifico = "Error en el formato del JSON. Verifique que los tipos de datos sean correctos.";

        Throwable causa = ex.getCause();
        if (causa instanceof com.fasterxml.jackson.databind.exc.MismatchedInputException mismatch) {
            String campo = mismatch.getPath().stream()
                    .map(ref -> ref.getFieldName())
                    .filter(f -> f != null)
                    .reduce((a, b) -> a + "." + b)
                    .orElse(null);

            String tipoCampo = mismatch.getTargetType() != null ? mismatch.getTargetType().getSimpleName() : "desconocido";

            if (campo != null) {
                if (tipoCampo.equals("BigDecimal") || tipoCampo.equals("Double") || tipoCampo.equals("Float")) {
                    mensajeEspecifico = "El campo '" + campo + "' debe ser un número decimal sin comillas (ej: " + campo + ": 1234.56). No envíe el valor entre comillas.";
                } else if (tipoCampo.equals("Long") || tipoCampo.equals("Integer")) {
                    mensajeEspecifico = "El campo '" + campo + "' debe ser un número entero sin comillas (ej: " + campo + ": 15). No envíe el valor entre comillas.";
                } else {
                    mensajeEspecifico = "El campo '" + campo + "' tiene un tipo de dato incorrecto. Se esperaba: " + tipoCampo + ".";
                }
            }
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new GenericResponse(1, mensajeEspecifico));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<GenericResponse> handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
        log.error("Violación de integridad de datos en BD", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new GenericResponse(1, "Conflicto de integridad: el registro ya existe o viola una restricción del sistema"));
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<GenericResponse> handleMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException ex) {
        log.warn("Método HTTP no soportado: {}", ex.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new GenericResponse(1, "Método HTTP '" + ex.getMethod() + "' no permitido para este endpoint."));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<GenericResponse> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        log.warn("Recurso no encontrado");
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new GenericResponse(1, "Recurso no encontrado."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse> handleGeneral(Exception ex) {
        log.error("Error inesperado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new GenericResponse(2, "Ha ocurrido un error inesperado al procesar la información"));
    }

    /**
     * Clase auxiliar para compatibilidad con esquemas Swagger / OpenAPI
     */
    public static class ErrorResponse extends GenericResponse {
        public ErrorResponse() {
            super();
        }

        public ErrorResponse(int status, String mensaje) {
            super(status, mensaje);
        }
    }
}
