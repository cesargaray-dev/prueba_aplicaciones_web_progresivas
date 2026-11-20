package com.proyecto.servicios.model.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud de cierre de sesión")
public class LogoutRequestDTO {

    @Schema(description = "Nombre de usuario (opcional)", example = "cesar123")
    private String usuario;
}
