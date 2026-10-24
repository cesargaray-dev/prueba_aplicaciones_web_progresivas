package com.proyecto.servicios.model.gestopago;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de entrada para solicitar un código 2FA temporal para pago de servicios.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerarCodigo2FARequestDTO {

    @NotBlank(message = "El número de cuenta es obligatorio")
    @Pattern(regexp = "^\\d{16}$", message = "El número de cuenta debe contener exactamente 16 dígitos numéricos")
    private String numeroCuenta;
}
