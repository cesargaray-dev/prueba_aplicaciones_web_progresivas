package com.proyecto.servicios.model.clientes;

import com.proyecto.servicios.validation.TelefonoMexicano;
import jakarta.validation.constraints.*;
import jakarta.validation.constraints.Min;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ClienteRequestDTO {

    // ───────────────────── DATOS PERSONALES ─────────────────────

    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s]+$", message = "El nombre no debe contener números ni caracteres especiales (@, #, etc.)")
    @Size(min = 3, max = 50, message = "El nombre debe tener entre 3 y 50 caracteres")
    private String nombre;

    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s]*$", message = "El segundo nombre no debe contener números ni caracteres especiales (@, #, etc.)")
    @Size(max = 50, message = "El segundo nombre no debe exceder 50 caracteres")
    private String segundoNombre;

    @NotBlank(message = "El apellido paterno es obligatorio")
    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s]+$", message = "El apellido paterno no debe contener números ni caracteres especiales (@, #, etc.)")
    @Size(min = 3, max = 50, message = "El apellido paterno debe tener entre 3 y 50 caracteres")
    private String apellidoPaterno;

    @NotBlank(message = "El apellido materno es obligatorio")
    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s]+$", message = "El apellido materno no debe contener números ni caracteres especiales (@, #, etc.)")
    @Size(min = 3, max = 50, message = "El apellido materno debe tener entre 3 y 50 caracteres")
    private String apellidoMaterno;

    @NotBlank(message = "La fecha de nacimiento es obligatoria")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "La fecha de nacimiento debe tener el formato estricto yyyy-MM-dd (ejemplo: 1995-05-10)")
    private String fechaNacimiento;

    @NotBlank(message = "La CURP es obligatoria")
    @Pattern(regexp = "^[A-Z]{4}\\d{6}[HM][A-Z]{5}[A-Z\\d]\\d$", message = "El formato de la CURP es inválido. Solo se aceptan letras mayúsculas y dígitos en el formato correcto")
    @Size(min = 18, max = 18, message = "La CURP debe contener exactamente 18 caracteres")
    private String curp;

    @NotBlank(message = "El RFC es obligatorio")
    @Pattern(regexp = "^[A-ZÑ&]{3,4}\\d{6}[A-Z\\d]{3}$", message = "El formato del RFC es inválido. Solo se aceptan letras mayúsculas y dígitos en el formato correcto")
    @Size(min = 12, max = 13, message = "El RFC debe contener 12 o 13 caracteres")
    private String rfc;

    // Catálogo: HOMBRE | MUJER
    @NotBlank(message = "El sexo es obligatorio")
    @Pattern(regexp = "^(HOMBRE|MUJER)$", message = "El sexo no es válido. Valores permitidos: HOMBRE, MUJER")
    private String sexo;

    // Catálogo desde BD: ID del catálogo con tipo 'NACIONALIDAD'
    @NotNull(message = "La nacionalidad es obligatoria (envíe el ID del catálogo de nacionalidades)")
    @Min(value = 1, message = "El ID de nacionalidad debe ser un número positivo")
    private Long nacionalidadId;

    // Catálogo: SOLTERO | CASADO | DIVORCIADO | VIUDO | UNION_LIBRE
    @NotBlank(message = "El estado civil es obligatorio")
    @Pattern(regexp = "^(SOLTERO|CASADO|DIVORCIADO|VIUDO|UNION_LIBRE)$", message = "El estado civil no es válido. Valores permitidos: SOLTERO, CASADO, DIVORCIADO, VIUDO, UNION_LIBRE")
    private String estadoCivil;

    // ───────────────────── DATOS DE CONTACTO ─────────────────────

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato del correo electrónico es inválido")
    @Size(max = 100, message = "El correo electrónico no debe exceder 100 caracteres")
    private String correo;

    @NotBlank(message = "El teléfono móvil es obligatorio")
    @TelefonoMexicano(message = "El teléfono móvil debe ser un número mexicano válido de 10 dígitos (sin prefijo 0 ni 1, ni secuencias/dígitos repetidos)")
    private String telefonoMovil;

    @TelefonoMexicano(optional = true, message = "El teléfono alternativo debe ser un número mexicano válido de 10 dígitos")
    private String telefonoAlternativo;

    // ───────────────────── DATOS LABORALES ─────────────────────

    @NotBlank(message = "La ocupación es obligatoria")
    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s]+$", message = "La ocupación no debe contener números ni caracteres especiales (@, #, etc.)")
    @Size(min = 3, max = 100, message = "La ocupación debe tener entre 3 y 100 caracteres")
    private String ocupacion;

    @NotBlank(message = "La empresa es obligatoria")
    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ0-9\\s\\.\\,\\-]+$", message = "La empresa no debe contener caracteres especiales (@, #, etc.)")
    @Size(min = 3, max = 150, message = "La empresa debe tener entre 3 y 150 caracteres")
    private String empresa;

    @NotNull(message = "El ingreso mensual es obligatorio")
    @DecimalMin(value = "0.01", message = "El ingreso mensual debe ser un número mayor a cero")
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.NUMBER_FLOAT)
    private BigDecimal ingresoMensual;

    // ───────────────────── DOMICILIO ─────────────────────

    @NotBlank(message = "La calle es obligatoria")
    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ0-9\\s\\.\\,\\-\\/]+$", message = "La calle no debe contener caracteres especiales (@, #, etc.)")
    @Size(min = 3, max = 200, message = "La calle debe tener entre 3 y 200 caracteres")
    private String calle;

    @NotBlank(message = "El número exterior es obligatorio")
    @Pattern(regexp = "^[A-Za-z0-9\\-]+$", message = "El número exterior solo debe contener letras, números o guiones")
    @Size(max = 10, message = "El número exterior no debe exceder 10 caracteres")
    private String numeroExterior;

    @Pattern(regexp = "^[A-Za-z0-9\\-\\s]*$", message = "El número interior solo debe contener letras, números o guiones")
    @Size(max = 20, message = "El número interior no debe exceder 20 caracteres")
    private String numeroInterior;

    @NotBlank(message = "La colonia es obligatoria")
    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ0-9\\s\\.\\,\\-]+$", message = "La colonia no debe contener caracteres especiales (@, #, etc.)")
    @Size(min = 3, max = 100, message = "La colonia debe tener entre 3 y 100 caracteres")
    private String colonia;

    @NotBlank(message = "El municipio es obligatorio")
    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s\\.]+$", message = "El municipio no debe contener números ni caracteres especiales (@, #, etc.)")
    @Size(min = 3, max = 100, message = "El municipio debe tener entre 3 y 100 caracteres")
    private String municipio;

    @NotBlank(message = "El estado es obligatorio")
    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s\\.]+$", message = "El estado no debe contener números ni caracteres especiales (@, #, etc.)")
    @Size(min = 2, max = 100, message = "El estado debe tener entre 2 y 100 caracteres")
    private String estado;

    @NotBlank(message = "El código postal es obligatorio")
    @Pattern(regexp = "^(0[1-9]|[1-9][0-9])[0-9]{3}$", message = "El código postal debe contener 5 dígitos numéricos válidos (rango oficial 01000 a 99999). No se aceptan '00000', letras ni caracteres especiales")
    private String codigoPostal;

    @NotBlank(message = "El país es obligatorio")
    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s]+$", message = "El país no debe contener números ni caracteres especiales (@, #, etc.)")
    @Size(min = 3, max = 60, message = "El país debe tener entre 3 y 60 caracteres")
    private String pais;

    // ───────────────────── LOGIN ─────────────────────

    @NotBlank(message = "El usuario es obligatorio")
    @Pattern(regexp = "^[A-Za-z0-9_\\.\\-]+$", message = "El usuario solo puede contener letras, números, puntos, guiones o guiones bajos. No se aceptan @, # ni espacios")
    @Size(min = 4, max = 50, message = "El usuario debe tener entre 4 y 50 caracteres")
    private String usuario;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 100, message = "La contraseña debe tener entre 8 y 100 caracteres")
    private String contrasena;

    // Simulación de dato biométrico (ej. Hash de MediaPipe)
    private String hashBiometrico;
}
