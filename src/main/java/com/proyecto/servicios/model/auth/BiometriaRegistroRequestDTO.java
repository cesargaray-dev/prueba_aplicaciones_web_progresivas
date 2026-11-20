package com.proyecto.servicios.model.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud para vincular el acceso biométrico (huella digital) del dispositivo a la cuenta autenticada")
public class BiometriaRegistroRequestDTO {

    @NotBlank(message = "El token biométrico del dispositivo es obligatorio")
    @Size(min = 32, max = 256, message = "El token biométrico debe tener entre 32 y 256 caracteres")
    @Schema(description = "Secreto criptográfico generado por el dispositivo móvil y protegido con biometría", example = "a1b2c3d4e5f6...64chars")
    private String tokenBiometrico;

    @Size(max = 120, message = "El nombre del dispositivo no puede exceder 120 caracteres")
    @Schema(description = "Identificador opcional del dispositivo móvil", example = "Android - Pixel")
    private String dispositivo;
}
