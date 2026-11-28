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
    @Size(min = 2, max = 40, message = "El nombre debe tener entre 2 y 40 caracteres")
    private String nombre;

    @Pattern(regexp = "^$|^(?=.*[A-Za-zÁÉÍÓÚÜáéíóúüÑñ])[A-Za-zÁÉÍÓÚÜáéíóúüÑñ\\s]{2,40}$", message = "El segundo nombre debe tener entre 2 y 40 letras")
    @Size(max = 40, message = "El segundo nombre no debe exceder 40 caracteres")
    private String segundoNombre;

    @NotBlank(message = "El apellido paterno es obligatorio")
    @Pattern(regexp = "^(?=.*[A-Za-zÁÉÍÓÚÜáéíóúüÑñ])[A-Za-zÁÉÍÓÚÜáéíóúüÑñ\\s]+$", message = "El apellido paterno solo debe contener letras")
    @Size(min = 3, max = 40, message = "El apellido paterno debe tener entre 3 y 40 caracteres")
    private String apellidoPaterno;

    @Pattern(regexp = "^$|^(?=.*[A-Za-zÁÉÍÓÚÜáéíóúüÑñ])[A-Za-zÁÉÍÓÚÜáéíóúüÑñ\\s]{3,40}$", message = "El apellido materno debe tener entre 3 y 40 letras")
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

    @Pattern(regexp = "^$|^[A-Z]{4}\\d{6}[HM][A-Z]{5}[A-Z\\d]\\d$", message = "El formato de la CURP es inválido (18 caracteres oficiales)")
    private String curp;

    @Pattern(regexp = "^$|^[A-ZÑ&]{3,4}\\d{6}[A-Z\\d]{3}$", message = "El formato del RFC es inválido (12 o 13 caracteres con homoclave)")
    private String rfc;

    @Pattern(regexp = "^$|^(HOMBRE|MUJER)$", message = "Valores permitidos para sexo: HOMBRE, MUJER")
    private String sexo;

    @Size(max = 60, message = "La nacionalidad no debe exceder 60 caracteres")
    private String nacionalidad;

    @Pattern(regexp = "^$|^(SOLTERO|CASADO|DIVORCIADO|VIUDO|UNION_LIBRE)$", message = "Estado civil inválido")
    private String estadoCivil;

    @TelefonoMexicano(optional = true, message = "El teléfono alternativo debe ser un número mexicano válido de 10 dígitos")
    private String telefonoAlternativo;

    @Size(max = 100, message = "La ocupación no debe exceder 100 caracteres")
    private String ocupacion;

    @Size(max = 150, message = "La empresa no debe exceder 150 caracteres")
    private String empresa;

    @DecimalMin(value = "0.00", message = "El ingreso mensual no puede ser negativo")
    private java.math.BigDecimal ingresoMensual;

    @Size(max = 200, message = "La calle no debe exceder 200 caracteres")
    private String calle;

    @Size(max = 20, message = "El número exterior no debe exceder 20 caracteres")
    private String numeroExterior;

    @Size(max = 20, message = "El número interior no debe exceder 20 caracteres")
    private String numeroInterior;

    @Size(max = 100, message = "La colonia no debe exceder 100 caracteres")
    private String colonia;

    @Size(max = 100, message = "El municipio no debe exceder 100 caracteres")
    private String municipio;

    @Size(max = 100, message = "El estado no debe exceder 100 caracteres")
    private String estado;

    @Pattern(
            regexp = "^$|^(0[1-9]|1[0-6]|[2-8][0-9]|9[0-8])[0-9]{3}$|^99([0-8][0-9]{2}|9[0-8][0-9]|99[0-8])$",
            message = "El código postal no corresponde a un rango SEPOMEX válido en México"
    )
    private String codigoPostal;

    @Size(max = 60, message = "El país no debe exceder 60 caracteres")
    private String pais;

    @Pattern(regexp = "^$|^\\d{6}$", message = "El código de verificación 2FA debe tener 6 dígitos numéricos")
    private String codigoVerificacion;
}
