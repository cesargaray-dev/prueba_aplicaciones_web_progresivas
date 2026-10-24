package com.proyecto.servicios.exception;

public class PagoServicioProveedorException extends RuntimeException {

    private final String codigoProveedor;
    private final int statusCode;

    public PagoServicioProveedorException(String message) {
        super(message);
        this.codigoProveedor = null;
        this.statusCode = 400;
    }

    public PagoServicioProveedorException(String codigoProveedor, String message) {
        super(message);
        this.codigoProveedor = codigoProveedor;
        // Código "06" en GestoPago significa transacción duplicada (HTTP 409 CONFLICT)
        this.statusCode = "06".equals(codigoProveedor) ? 409 : 400;
    }

    public PagoServicioProveedorException(String codigoProveedor, String message, int statusCode) {
        super(message);
        this.codigoProveedor = codigoProveedor;
        this.statusCode = statusCode;
    }

    public String getCodigoProveedor() {
        return codigoProveedor;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
