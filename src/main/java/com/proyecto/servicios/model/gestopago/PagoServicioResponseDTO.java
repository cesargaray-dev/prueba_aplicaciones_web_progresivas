package com.proyecto.servicios.model.gestopago;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.proyecto.servicios.model.GenericResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de respuesta para el pago de servicios con GestoPago.
 * Extiende de GenericResponse cumpliendo con el estándar del proyecto.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PagoServicioResponseDTO extends GenericResponse {

    private Long idTransaccion;
    private String numeroAutorizacion;
    private String folioTicket;
    private BigDecimal montoPagado;
    private BigDecimal comision;
    private BigDecimal saldoRestanteCuenta;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaHoraOperacion;

    private String concepto;
}
