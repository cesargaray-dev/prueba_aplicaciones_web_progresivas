package com.proyecto.servicios.model.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TotpEstadoResponseDTO {
    private Boolean totpHabilitado;
    private String mensaje;
}
