package com.proyecto.servicios.model.gestopago;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO de entrada para realizar recargas o pagos de servicios mediante GestoPago (PuntoRed).
 * <p>
 * Los campos {@code telefono} y {@code referencia} son opcionales a nivel de estructura,
 * pero al menos uno es obligatorio según el {@code tipoFront} del producto:
 * <ul>
 *   <li>{@code tipoFront = 1} (Recargas): {@code telefono} obligatorio (10 dígitos), {@code referencia} se envía vacío.</li>
 *   <li>{@code tipoFront = 2} (Pago de servicios con recibo): {@code referencia} obligatoria, {@code telefono} se fija en {@code "1111111111"}.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagoServicioRequestDTO {

    @NotBlank(message = "El número de cuenta es obligatorio")
    @Pattern(regexp = "^\\d{10}$", message = "El número de cuenta debe contener exactamente 10 dígitos numéricos")
    private String numeroCuenta;

    @NotNull(message = "El idServicio es obligatorio")
    @Positive(message = "El idServicio debe ser un número entero positivo")
    private Integer idServicio;

    @NotNull(message = "El idProducto es obligatorio")
    @Positive(message = "El idProducto debe ser un número entero positivo")
    private Integer idProducto;

    @NotNull(message = "El monto a pagar es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto a pagar debe ser mayor a cero (mínimo 0.01)")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT)
    private BigDecimal monto;

    /**
     * Opcional en el DTO general; obligatorio cuando el producto es Recarga (tipoFront = 1).
     */
    @Pattern(regexp = "^(#?\\d{10})?$", message = "El teléfono debe contener exactamente 10 dígitos numéricos")
    private String telefono;

    /**
     * Opcional en el DTO general; obligatorio cuando el producto es Pago de Servicio con recibo (tipoFront = 2).
     */
    @Size(max = 100, message = "La referencia debe tener entre 1 y 100 caracteres")
    @Pattern(regexp = "^([A-Za-z0-9\\s\\-_]{1,100})?$", message = "La referencia solo puede contener caracteres alfanuméricos (entre 1 y 100 caracteres)")
    private String referencia;

    @NotBlank(message = "El código 2FA es obligatorio para autorizar el pago")
    @Pattern(regexp = "^\\d{6}$", message = "El código 2FA debe contener exactamente 6 dígitos numéricos")
    private String codigo2FA;
}
