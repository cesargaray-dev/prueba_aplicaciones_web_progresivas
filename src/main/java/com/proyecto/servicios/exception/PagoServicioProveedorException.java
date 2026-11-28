package com.proyecto.servicios.exception;

import com.proyecto.servicios.model.gestopago.PagoServicioResponseDTO;

public class PagoServicioProveedorException extends RuntimeException {

    private final String codigoProveedor;
    private final int statusCode;
    private final PagoServicioResponseDTO detalleTicket;

    public PagoServicioProveedorException(String message) {
        super(message);
        this.codigoProveedor = null;
        this.statusCode = 400;
        this.detalleTicket = null;
    }

    public PagoServicioProveedorException(String codigoProveedor, String message) {
        super(message);
        this.codigoProveedor = codigoProveedor;
        // Código "06" en GestoPago significa transacción duplicada (HTTP 409 CONFLICT)
        this.statusCode = "06".equals(codigoProveedor) ? 409 : 400;
        this.detalleTicket = null;
    }

    public PagoServicioProveedorException(String codigoProveedor, String message, int statusCode) {
        super(message);
        this.codigoProveedor = codigoProveedor;
        this.statusCode = statusCode;
        this.detalleTicket = null;
    }

    public PagoServicioProveedorException(
            String codigoProveedor,
            String message,
            int statusCode,
            PagoServicioResponseDTO detalleTicket) {
        super(message);
        this.codigoProveedor = codigoProveedor;
        this.statusCode = statusCode;
        this.detalleTicket = detalleTicket;
    }

    public String getCodigoProveedor() {
        return codigoProveedor;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public PagoServicioResponseDTO getDetalleTicket() {
        return detalleTicket;
    }
}
