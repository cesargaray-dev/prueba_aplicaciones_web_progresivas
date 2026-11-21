package com.proyecto.servicios.model.auth;

import com.proyecto.servicios.validation.TelefonoMexicano;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Solicitud de auto-registro comercial de nuevo cliente con apertura automática de cuenta digital.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud de registro de cliente y creación de credenciales de acceso")
public class RegisterRequestDTO {

    @NotBlank(message = "El nombre es obligatorio")
    @Pattern(regexp = "^(?=.*[A-Za-zÁÉÍÓÚÜáéíóúüÑñ])[A-Za-zÁÉÍÓÚÜáéíóúüÑñ\\s]+$", message = "El nombre solo debe contener letras")
    @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres")
    private String nombre;

    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚÜáéíóúüÑñ\\s]*$", message = "El segundo nombre solo debe contener letras")
    @Size(max = 50, message = "El segundo nombre no debe exceder 50 caracteres")
    private String segundoNombre;

    @NotBlank(message = "El apellido paterno es obligatorio")
    @Pattern(regexp = "^(?=.*[A-Za-zÁÉÍÓÚÜáéíóúüÑñ])[A-Za-zÁÉÍÓÚÜáéíóúüÑñ\\s]+$", message = "El apellido paterno solo debe contener letras")
    @Size(min = 2, max = 50, message = "El apellido paterno debe tener entre 2 y 50 caracteres")
    private String apellidoPaterno;

    @Pattern(regexp = "^$|^(?=.*[A-Za-zÁÉÍÓÚÜáéíóúüÑñ])[A-Za-zÁÉÍÓÚÜáéíóúüÑñ\\s]{2,50}$", message = "El apellido materno debe tener entre 2 y 50 letras")
    private String apellidoMaterno;

    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "La fecha de nacimiento debe tener formato yyyy-MM-dd")
    private String fechaNacimiento;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato del correo electrónico es inválido")
    @Size(max = 100, message = "El correo electrónico no debe exceder 100 caracteres")
    private String correo;

    @NotBlank(message = "El teléfono móvil es obligatorio")
    @TelefonoMexicano
    private String telefonoMovil;

    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Pattern(regexp = "^[A-Za-z0-9_.\\-]+$", message = "El usuario solo puede contener letras, números, puntos o guiones")
    @Size(min = 4, max = 50, message = "El usuario debe tener entre 4 y 50 caracteres")
    private String usuario;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 100, message = "La contraseña debe tener al menos 8 caracteres")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,100}$",
            message = "La contraseña debe incluir al menos una mayúscula, una minúscula y un número"
    )
    private String contrasena;

    @Pattern(regexp = "^$|^[A-Z]{4}\\d{6}[HM][A-Z]{5}[A-Z\\d]\\d$", message = "El formato de la CURP es inválido")
    private String curp;

    @Pattern(regexp = "^$|^[A-ZÑ&]{3,4}\\d{6}[A-Z\\d]{3}$", message = "El formato del RFC es inválido")
    private String rfc;

    @Pattern(regexp = "^(HOMBRE|MUJER)$", message = "Valores permitidos para sexo: HOMBRE, MUJER")
    private String sexo;

    @Pattern(regexp = "^$|^\\d{6}$", message = "El código de verificación 2FA debe tener 6 dígitos numéricos")
    private String codigoVerificacion;
}
