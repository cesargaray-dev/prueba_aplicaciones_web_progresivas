package com.proyecto.servicios.model.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TotpSetupResponseDTO {
    private String secret;
    private String secretFormateado;
    private String otpauthUri;
    private String qrPngBase64;
    private String emisor;
    private String cuenta;
    private Integer digitos;
    private Integer periodoSegundos;
}
