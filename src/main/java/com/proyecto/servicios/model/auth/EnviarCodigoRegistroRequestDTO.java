package com.proyecto.servicios.model.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Solicitud para enviar un código temporal 2FA (OTP de 6 dígitos) al correo
 * electrónico antes de crear una nueva cuenta de usuario.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud de envío de código 2FA al correo para validar creación de cuenta")
public class EnviarCodigoRegistroRequestDTO {

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato del correo electrónico es inválido")
    @Size(max = 100, message = "El correo electrónico no debe exceder 100 caracteres")
    private String correo;

    @Size(max = 100, message = "El nombre no debe exceder 100 caracteres")
    private String nombre;

    @Size(max = 50, message = "El usuario no debe exceder 50 caracteres")
    private String usuario;

    @Size(max = 18, message = "La CURP no debe exceder 18 caracteres")
    private String curp;

    @Size(max = 13, message = "El RFC no debe exceder 13 caracteres")
    private String rfc;

    public EnviarCodigoRegistroRequestDTO(String correo, String nombre, String usuario) {
        this.correo = correo;
        this.nombre = nombre;
        this.usuario = usuario;
    }
}
