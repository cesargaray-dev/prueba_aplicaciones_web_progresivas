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
@Schema(description = "Solicitud de inicio de sesión mediante credencial biométrica (huella digital)")
public class BiometriaLoginRequestDTO {

    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Size(max = 100, message = "El nombre de usuario no puede exceder 100 caracteres")
    @Schema(description = "Nombre de usuario vinculado al dispositivo", example = "e2ecesar1114")
    private String usuario;

    @NotBlank(message = "El token biométrico es obligatorio")
    @Size(min = 32, max = 256, message = "El token biométrico es inválido")
    @Schema(description = "Secreto criptográfico desbloqueado tras verificar la huella digital en el dispositivo")
    private String tokenBiometrico;
}
