package com.proyecto.servicios.model.gestopago;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Payload JSON que se cifra en AES-256-CBC para enviarse en el campo 'signed'
 * al endpoint sendTx.do de GestoPago (PuntoRed).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GestoPagoTxPayload {

    private Integer idProducto;
    private Integer idServicio;
    private String telefono;
    private String horaLocal;
    private String referencia;
    private BigDecimal montoPago;
    private String upc;
    private String unidad;
}
