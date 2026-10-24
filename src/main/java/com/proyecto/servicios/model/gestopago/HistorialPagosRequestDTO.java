package com.proyecto.servicios.model.gestopago;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de entrada para consultar el historial de pagos y recargas de una cuenta bancaria.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Petición para consultar historial de pagos de servicios y recargas de una cuenta bancaria")
public class HistorialPagosRequestDTO {

    @NotBlank(message = "El número de cuenta es obligatorio")
    @Pattern(regexp = "^\\d{16}$", message = "El número de cuenta debe contener exactamente 16 dígitos numéricos")
    @Schema(description = "Número de cuenta bancaria de 16 dígitos", example = "8906887292154504")
    private String numeroCuenta;
}
