package com.proyecto.servicios.model.gestopago;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Respuesta de validación de referencia de pago o recarga")
public class ValidarReferenciaResponseDTO {

    @Schema(description = "Indica si la referencia cumple con el formato y reglas requeridas", example = "true")
    private boolean esValida;

    @Schema(description = "Mensaje explicativo del resultado de la validación", example = "Referencia válida")
    private String mensaje;

    @Schema(description = "Tipo de interfaz frontal (1=Telefonía/Recarga, 2=Servicios)", example = "2")
    private Integer tipoFront;

    @Schema(description = "Tipo de referencia (a=alfanumérico, b=código barras, c=contrato, bc=dual)", example = "c")
    private String tipoReferencia;

    @Schema(description = "Indica si el servicio requiere captura manual de monto", example = "false")
    private Boolean requiereMonto;

    @Schema(description = "Monto sugerido u oficial del catálogo si aplica", example = "20.00")
    private BigDecimal montoSugerido;

    @Schema(description = "Nombre del servicio asociado", example = "CFE")
    private String servicio;

    @Schema(description = "Nombre del producto asociado", example = "PAGO DE LUZ")
    private String producto;
}
