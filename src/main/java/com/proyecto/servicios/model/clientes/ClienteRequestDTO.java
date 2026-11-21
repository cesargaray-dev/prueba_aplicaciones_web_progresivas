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

    @Pattern(regexp = "^$|^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s]{2,50}$", message = "El apellido materno debe tener entre 2 y 50 letras")
    private String apellidoMaterno;

    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "La fecha de nacimiento debe tener el formato estricto yyyy-MM-dd (ejemplo: 1995-05-10)")
    private String fechaNacimiento;

    @Pattern(regexp = "^$|^[A-Z]{4}\\d{6}[HM][A-Z]{5}[A-Z\\d]\\d$", message = "El formato de la CURP es inválido. Solo se aceptan letras mayúsculas y dígitos en el formato correcto")
    @Size(max = 18, message = "La CURP debe contener máximo 18 caracteres")
    private String curp;

    @Pattern(regexp = "^$|^[A-ZÑ&]{3,4}\\d{6}[A-Z\\d]{3}$", message = "El formato del RFC es inválido. Solo se aceptan letras mayúsculas y dígitos en el formato correcto")
    @Size(max = 13, message = "El RFC debe contener máximo 13 caracteres")
    private String rfc;

    // Catálogo: HOMBRE | MUJER
    @Pattern(regexp = "^$|^(HOMBRE|MUJER)$", message = "El sexo no es válido. Valores permitidos: HOMBRE, MUJER")
    private String sexo;

    // Catálogo desde BD: ID del catálogo con tipo 'NACIONALIDAD'
    @Min(value = 1, message = "El ID de nacionalidad debe ser un número positivo")
    private Long nacionalidadId;

    // Catálogo: SOLTERO | CASADO | DIVORCIADO | VIUDO | UNION_LIBRE
    @Pattern(regexp = "^$|^(SOLTERO|CASADO|DIVORCIADO|VIUDO|UNION_LIBRE)$", message = "El estado civil no es válido. Valores permitidos: SOLTERO, CASADO, DIVORCIADO, VIUDO, UNION_LIBRE")
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

    @Pattern(regexp = "^$|^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s]{3,100}$", message = "La ocupación debe tener entre 3 y 100 letras sin números ni caracteres especiales")
    private String ocupacion;

    @Pattern(regexp = "^$|^[A-Za-zÁÉÍÓÚáéíóúÑñ0-9\\s\\.\\,\\-]{3,150}$", message = "La empresa debe tener entre 3 y 150 caracteres válidos")
    private String empresa;

    @DecimalMin(value = "0.01", message = "El ingreso mensual debe ser un número mayor a cero")
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.NUMBER_FLOAT)
    private BigDecimal ingresoMensual;

    // ───────────────────── DOMICILIO ─────────────────────

    @Pattern(regexp = "^$|^[A-Za-zÁÉÍÓÚáéíóúÑñ0-9\\s\\.\\,\\-\\/]{3,200}$", message = "La calle debe tener entre 3 y 200 caracteres válidos")
    private String calle;

    @Pattern(regexp = "^$|^[A-Za-z0-9\\-]{1,10}$", message = "El número exterior solo debe contener letras, números o guiones (máximo 10 caracteres)")
    private String numeroExterior;

    @Pattern(regexp = "^[A-Za-z0-9\\-\\s]*$", message = "El número interior solo debe contener letras, números o guiones")
    @Size(max = 20, message = "El número interior no debe exceder 20 caracteres")
    private String numeroInterior;

    @Pattern(regexp = "^$|^[A-Za-zÁÉÍÓÚáéíóúÑñ0-9\\s\\.\\,\\-]{3,100}$", message = "La colonia debe tener entre 3 y 100 caracteres válidos")
    private String colonia;

    @Pattern(regexp = "^$|^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s\\.]{3,100}$", message = "El municipio debe tener entre 3 y 100 letras válidas")
    private String municipio;

    @Pattern(regexp = "^$|^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s\\.]{2,100}$", message = "El estado debe tener entre 2 y 100 letras sin números ni caracteres especiales")
    private String estado;

    @Pattern(regexp = "^$|^(0[1-9]|[1-9][0-9])[0-9]{3}$", message = "El código postal debe contener 5 dígitos numéricos válidos (rango oficial 01000 a 99999). No se aceptan '00000', letras ni caracteres especiales")
    private String codigoPostal;

    @Pattern(regexp = "^$|^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s]{3,60}$", message = "El país debe tener entre 3 y 60 letras")
    private String pais;

    // ───────────────────── LOGIN ─────────────────────

    @Pattern(regexp = "^\\s*$|^[A-Za-z0-9_\\.\\-]{4,50}$", message = "El usuario debe tener entre 4 y 50 caracteres (letras, números, puntos o guiones)")
    private String usuario;

    @Pattern(regexp = "^\\s*$|^.{8,100}$", message = "La contraseña debe tener entre 8 y 100 caracteres")
    private String contrasena;

    // Simulación de dato biométrico (ej. Hash de MediaPipe)
    private String hashBiometrico;
}
