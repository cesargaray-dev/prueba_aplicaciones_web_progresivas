package com.proyecto.servicios.model.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TotpVerificarRequestDTO {
    private String secret;

    @NotBlank(message = "El código de Google Authenticator es obligatorio")
    @Pattern(regexp = "^\\d{6}$", message = "El código de Google Authenticator debe tener 6 dígitos numéricos")
    private String codigo;
}
