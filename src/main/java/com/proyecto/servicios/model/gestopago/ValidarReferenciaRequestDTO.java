package com.proyecto.servicios.model.gestopago;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Petición para validar formato y reglas de referencia de un servicio")
public class ValidarReferenciaRequestDTO {

    @NotNull(message = "El idServicio es obligatorio")
    @Schema(description = "Identificador del servicio en GestoPago", example = "76")
    private Integer idServicio;

    @Schema(description = "Identificador del producto en GestoPago", example = "205")
    private Integer idProducto;

    @NotBlank(message = "La referencia es obligatoria")
    @Schema(description = "Referencia ingresada por el usuario (número de teléfono, contrato o código de barras)", example = "5512345678")
    private String referencia;

    @Schema(description = "Monto a pagar opcional para pre-validación", example = "20.00")
    private BigDecimal monto;
}
