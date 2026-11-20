package com.proyecto.servicios.model.brevo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resultado explícito del intento de envío de correo electrónico transaccional vía Brevo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmailEnvioResultadoDTO {

    private boolean enviado;
    private String correoDestino;
    private String messageId;
    private String mensaje;
    private Boolean correoPlaceholder;
}
