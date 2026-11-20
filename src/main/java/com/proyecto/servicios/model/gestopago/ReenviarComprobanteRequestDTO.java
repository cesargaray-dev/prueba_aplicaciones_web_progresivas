package com.proyecto.servicios.model.gestopago;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Solicitud para reenviar por correo electrónico el comprobante oficial de una operación.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReenviarComprobanteRequestDTO {

    @NotBlank(message = "El número de cuenta es obligatorio")
    @Pattern(regexp = "^\\d{16}$", message = "El número de cuenta debe contener 16 dígitos numéricos")
    private String numeroCuenta;

    @NotBlank(message = "El folio de ticket es obligatorio")
    private String folioTicket;

    @Email(message = "El correo de destino no tiene un formato válido")
    private String correoDestino;
}
