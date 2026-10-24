package com.proyecto.servicios.exception;

public class Autenticacion2FAException extends RuntimeException {

    public Autenticacion2FAException(String message) {
        super(message);
    }

    public Autenticacion2FAException(String message, Throwable cause) {
        super(message, cause);
    }
}
