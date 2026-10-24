package com.proyecto.servicios.model.gestopago;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.proyecto.servicios.model.GenericResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

/**
 * DTO de respuesta para la consulta del historial de pagos y recargas.
 * Extiende de GenericResponse cumpliendo el estándar arquitectónico del proyecto.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Respuesta estructurada de la consulta de historial de pagos de servicios")
public class HistorialPagosResponseDTO extends GenericResponse {

    @Schema(description = "Número de cuenta bancaria consultada", example = "8906887292154504")
    private String numeroCuenta;

    @Schema(description = "Total de transacciones registradas en el historial", example = "2")
    private Integer totalMovimientos;

    @Schema(description = "Lista detallada de pagos y recargas ordenados de forma descendente por fecha")
    private List<ItemHistorialPagoDTO> pagos;
}
