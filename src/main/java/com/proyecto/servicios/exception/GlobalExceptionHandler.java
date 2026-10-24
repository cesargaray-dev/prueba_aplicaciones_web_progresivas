package com.proyecto.servicios.exception;

import com.proyecto.servicios.model.GenericResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

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
        log.error("Cuenta no encontrada", ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new GenericResponse(1, ex.getMessage()));
    }

    @ExceptionHandler(ClienteInactivoException.class)
    public ResponseEntity<GenericResponse> handleClienteInactivo(ClienteInactivoException ex) {
        log.error("Cliente inactivo", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new GenericResponse(1, ex.getMessage()));
    }

    @ExceptionHandler(CuentaInactivaException.class)
    public ResponseEntity<GenericResponse> handleCuentaInactiva(CuentaInactivaException ex) {
        log.error("Cuenta inactiva", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new GenericResponse(1, ex.getMessage()));
    }

    @ExceptionHandler(Autenticacion2FAException.class)
    public ResponseEntity<GenericResponse> handleAutenticacion2FA(Autenticacion2FAException ex) {
        log.error("Fallo de autenticación 2FA", ex);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new GenericResponse(1, ex.getMessage()));
    }

    @ExceptionHandler(SaldoInsuficienteException.class)
    public ResponseEntity<GenericResponse> handleSaldoInsuficiente(SaldoInsuficienteException ex) {
        log.error("Saldo insuficiente en cuenta", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new GenericResponse(1, ex.getMessage()));
    }

    @ExceptionHandler(PagoServicioProveedorException.class)
    public ResponseEntity<GenericResponse> handlePagoServicioProveedor(PagoServicioProveedorException ex) {
        log.error("Error devuelto por el proveedor de pago de servicios (código: {})", ex.getCodigoProveedor(), ex);
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status)
                .body(new GenericResponse(1, ex.getMessage()));
    }

    @ExceptionHandler(GestoPagoException.class)
    public ResponseEntity<GenericResponse> handleGestoPagoException(GestoPagoException ex) {
        log.error("Error de comunicación con GestoPago", ex);
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status)
                .body(new GenericResponse(1, ex.getMessage()));
    }

    @ExceptionHandler(ValidacionException.class)
    public ResponseEntity<GenericResponse> handleValidacion(ValidacionException ex) {
        log.error("Error de validación", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new GenericResponse(1, ex.getMessage()));
    }

    @ExceptionHandler(ClienteException.class)
    public ResponseEntity<GenericResponse> handleClienteException(ClienteException ex) {
        log.error("Error en módulo de clientes", ex);
        return ResponseEntity.status(ex.getStatusCode())
                .body(new GenericResponse(1, ex.getMessage()));
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
        genericResponse.setMensaje("Datos no validos" + listError);
        log.error("Error de validación DTO: {}", genericResponse.getMensaje());
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
        GenericResponse response = new GenericResponse(1, "Datos no validos" + listError);
        log.error("Error de validación de parámetros: {}", response.getMensaje());
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

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<GenericResponse> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        log.warn("Recurso no encontrado: {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new GenericResponse(1, "Recurso no encontrado: " + ex.getResourcePath()));
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
