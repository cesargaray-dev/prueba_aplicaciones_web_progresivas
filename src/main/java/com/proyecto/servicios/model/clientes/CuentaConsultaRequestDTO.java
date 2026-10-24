package com.proyecto.servicios.model.clientes;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "Petición para consultar cuenta o saldo sin usar PathVariable")
public class CuentaConsultaRequestDTO {

    @NotBlank(message = "El número de cuenta es obligatorio")
    @Pattern(regexp = "^\\d{16}$", message = "El número de cuenta debe tener exactamente 16 dígitos numéricos")
    @Schema(description = "Número de cuenta de 16 dígitos", example = "1234567890123456")
    private String numeroCuenta;
}
