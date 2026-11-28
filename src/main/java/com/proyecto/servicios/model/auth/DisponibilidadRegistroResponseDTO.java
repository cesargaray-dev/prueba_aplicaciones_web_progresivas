package com.proyecto.servicios.model.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DisponibilidadRegistroResponseDTO {
    private Boolean correoDisponible;
    private Boolean usuarioDisponible;
    private Boolean curpDisponible;
    private Boolean rfcDisponible;
    private Map<String, String> mensajes;
}
