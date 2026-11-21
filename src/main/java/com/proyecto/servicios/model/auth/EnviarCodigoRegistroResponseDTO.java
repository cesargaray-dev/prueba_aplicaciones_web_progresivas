package com.proyecto.servicios.model.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta tras enviar el código temporal de verificación 2FA para creación de cuenta.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resultado del envío del código 2FA al correo para registro de cuenta")
public class EnviarCodigoRegistroResponseDTO {

    private int codigo;
    private String mensaje;
    private boolean correoEnviado;
    private String correoDestino;
    private long expiraEnSegundos;
}
