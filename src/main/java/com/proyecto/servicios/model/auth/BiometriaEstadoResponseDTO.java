package com.proyecto.servicios.model.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Estado del acceso biométrico (huella digital) para la cuenta del cliente")
public class BiometriaEstadoResponseDTO {

    @Schema(description = "Indica si el inicio de sesión con huella digital está activo", example = "true")
    private boolean habilitada;

    @Schema(description = "Usuario asociado", example = "e2ecesar1114")
    private String usuario;

    @Schema(description = "Tipo de biometría configurada", example = "HUELLA")
    private String tipo;

    @Schema(description = "Mensaje descriptivo de la operación")
    private String mensaje;
}
