package com.proyecto.servicios.model.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud de inicio de sesión de cliente")
public class LoginRequestDTO {

    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Schema(description = "Nombre de usuario registrado", example = "cesar123")
    private String usuario;

    @NotBlank(message = "La contraseña es obligatoria")
    @Schema(description = "Contraseña en texto claro", example = "Password@123")
    private String contrasena;
}
