package com.proyecto.servicios.model.auth;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Solicitud para abonar fondos a la cuenta bancaria del cliente autenticado.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud de abono o fondeo a la cuenta bancaria")
public class AbonoSaldoRequestDTO {

    @NotNull(message = "El monto a abonar es obligatorio")
    @DecimalMin(value = "1.00", message = "El monto mínimo de abono es $1.00 MXN")
    @DecimalMax(value = "50000.00", message = "El monto máximo por operación es $50,000.00 MXN")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT)
    private BigDecimal monto;

    @Size(max = 120, message = "El concepto no debe exceder 120 caracteres")
    private String concepto;
}
