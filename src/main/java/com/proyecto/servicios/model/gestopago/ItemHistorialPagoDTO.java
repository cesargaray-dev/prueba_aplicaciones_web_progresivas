package com.proyecto.servicios.model.gestopago;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Detalle de una transacción en el historial de pagos de servicios y recargas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Detalle individual de un pago de servicio o recarga en el historial")
public class ItemHistorialPagoDTO {

    @Schema(description = "Identificador único interno del pago", example = "1")
    private Long idPago;

    @Schema(description = "Folio único de ticket", example = "TK20261023190105123")
    private String folioTicket;

    @Schema(description = "Número de autorización emitido por el proveedor", example = "391028")
    private String numeroAutorizacion;

    @Schema(description = "ID de transacción del proveedor (GestoPago)", example = "7829104")
    private Long idTxGestopago;

    @Schema(description = "Identificador del servicio", example = "76")
    private Integer idServicio;

    @Schema(description = "Nombre del servicio", example = "TELCEL")
    private String servicio;

    @Schema(description = "Identificador del producto", example = "205")
    private Integer idProducto;

    @Schema(description = "Nombre del producto", example = "PAQUETE SIN LIMITE 20")
    private String producto;

    @Schema(description = "Número de teléfono recargado (si aplica)", example = "5512345678")
    private String telefono;

    @Schema(description = "Número de referencia de servicio pagado (si aplica)", example = "25720381")
    private String referencia;

    @Schema(description = "Monto pagado", example = "20.00")
    private BigDecimal monto;

    @Schema(description = "Comisión cobrada", example = "0.00")
    private BigDecimal comision;

    @Schema(description = "Saldo disponible previo a la transacción", example = "100.00")
    private BigDecimal saldoAnterior;

    @Schema(description = "Saldo disponible posterior a la transacción", example = "80.00")
    private BigDecimal saldoRestante;

    @Schema(description = "Concepto bancario de la transacción", example = "RECARGA TELCEL...")
    private String concepto;

    @Schema(description = "Fecha y hora exacta de la operación", example = "2026-10-23 19:01:05")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaOperacion;
}
