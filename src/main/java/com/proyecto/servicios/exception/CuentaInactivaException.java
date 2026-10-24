package com.proyecto.servicios.exception;

public class CuentaInactivaException extends RuntimeException {

    public CuentaInactivaException(String message) {
        super(message);
    }

    public CuentaInactivaException(String message, Throwable cause) {
        super(message, cause);
    }
}
