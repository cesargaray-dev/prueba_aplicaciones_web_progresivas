package com.proyecto.servicios.model.clientes;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Peticion para actualizacion PARCIAL (PATCH) de un cliente.
 * Solo se identifican al cliente por ID o CURP y se actualizan unicamente los campos enviados.
 * Los campos que no se envien (null) conservan su valor actual.
 * CURP y RFC no se pueden modificar.
 */
@Data
@Schema(description = "Actualizacion parcial de cliente: solo se modifican los campos enviados")
public class ClienteActualizacionParcialRequestDTO {

    // ───────────────────── IDENTIFICACION ─────────────────────

    @Positive(message = "El ID del cliente debe ser un número positivo")
    @Schema(description = "ID del cliente (opcional si se envía CURP)", example = "1")
    private Long id;

    @Pattern(regexp = "^$|^[A-Z]{4}\\d{6}[HM][A-Z]{5}[A-Z\\d]\\d$", message = "El formato de la CURP es inválido")
    @Schema(description = "CURP del cliente (opcional si se envía ID). Solo identifica, no se modifica", example = "JUAN800101HDFRRN01")
    private String curp;

    // ───────────────────── DATOS PERSONALES ─────────────────────

    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s]+$", message = "El nombre no debe contener números ni caracteres especiales (@, #, etc.)")
    @Size(min = 3, max = 50, message = "El nombre debe tener entre 3 y 50 caracteres")
    private String nombre;

    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s]*$", message = "El segundo nombre no debe contener números ni caracteres especiales (@, #, etc.)")
    @Size(max = 50, message = "El segundo nombre no debe exceder 50 caracteres")
    private String segundoNombre;

    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s]+$", message = "El apellido paterno no debe contener números ni caracteres especiales (@, #, etc.)")
    @Size(min = 3, max = 50, message = "El apellido paterno debe tener entre 3 y 50 caracteres")
    private String apellidoPaterno;

    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s]+$", message = "El apellido materno no debe contener números ni caracteres especiales (@, #, etc.)")
    @Size(min = 3, max = 50, message = "El apellido materno debe tener entre 3 y 50 caracteres")
    private String apellidoMaterno;

    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "La fecha de nacimiento debe tener el formato estricto yyyy-MM-dd (ejemplo: 1995-05-10)")
    private String fechaNacimiento;

    @Pattern(regexp = "^(HOMBRE|MUJER)$", message = "El sexo no es válido. Valores permitidos: HOMBRE, MUJER")
    private String sexo;

    @Min(value = 1, message = "El ID de nacionalidad debe ser un número positivo")
    private Long nacionalidadId;

    @Pattern(regexp = "^(SOLTERO|CASADO|DIVORCIADO|VIUDO|UNION_LIBRE)$", message = "El estado civil no es válido. Valores permitidos: SOLTERO, CASADO, DIVORCIADO, VIUDO, UNION_LIBRE")
    private String estadoCivil;

    // ───────────────────── DATOS DE CONTACTO ─────────────────────

    @Email(message = "El formato del correo electrónico es inválido")
    @Size(max = 100, message = "El correo electrónico no debe exceder 100 caracteres")
    private String correo;

    @Pattern(regexp = "^\\d{10}$", message = "El teléfono móvil debe contener exactamente 10 dígitos. No se aceptan letras ni caracteres especiales")
    private String telefonoMovil;

    @Pattern(regexp = "^(\\d{10})?$", message = "El teléfono alternativo debe contener exactamente 10 dígitos. No se aceptan letras ni caracteres especiales")
    private String telefonoAlternativo;

    // ───────────────────── DATOS LABORALES ─────────────────────

    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s]+$", message = "La ocupación no debe contener números ni caracteres especiales (@, #, etc.)")
    @Size(min = 3, max = 100, message = "La ocupación debe tener entre 3 y 100 caracteres")
    private String ocupacion;

    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ0-9\\s\\.\\,\\-]+$", message = "La empresa no debe contener caracteres especiales (@, #, etc.)")
    @Size(min = 3, max = 150, message = "La empresa debe tener entre 3 y 150 caracteres")
    private String empresa;

    @DecimalMin(value = "0.01", message = "El ingreso mensual debe ser un número mayor a cero")
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.NUMBER_FLOAT)
    private BigDecimal ingresoMensual;

    // ───────────────────── DOMICILIO ─────────────────────

    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ0-9\\s\\.\\,\\-\\/]+$", message = "La calle no debe contener caracteres especiales (@, #, etc.)")
    @Size(min = 3, max = 200, message = "La calle debe tener entre 3 y 200 caracteres")
    private String calle;

    @Pattern(regexp = "^[A-Za-z0-9\\-]+$", message = "El número exterior solo debe contener letras, números o guiones")
    @Size(max = 10, message = "El número exterior no debe exceder 10 caracteres")
    private String numeroExterior;

    @Pattern(regexp = "^[A-Za-z0-9\\-\\s]*$", message = "El número interior solo debe contener letras, números o guiones")
    @Size(max = 20, message = "El número interior no debe exceder 20 caracteres")
    private String numeroInterior;

    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ0-9\\s\\.\\,\\-]+$", message = "La colonia no debe contener caracteres especiales (@, #, etc.)")
    @Size(min = 3, max = 100, message = "La colonia debe tener entre 3 y 100 caracteres")
    private String colonia;

    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s\\.]+$", message = "El municipio no debe contener números ni caracteres especiales (@, #, etc.)")
    @Size(min = 3, max = 100, message = "El municipio debe tener entre 3 y 100 caracteres")
    private String municipio;

    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s\\.]+$", message = "El estado no debe contener números ni caracteres especiales (@, #, etc.)")
    @Size(min = 2, max = 100, message = "El estado debe tener entre 2 y 100 caracteres")
    private String estado;

    @Pattern(regexp = "^(0[1-9]|[1-9][0-9])[0-9]{3}$", message = "El código postal debe contener 5 dígitos numéricos válidos (rango oficial 01000 a 99999). No se aceptan '00000', letras ni caracteres especiales")
    private String codigoPostal;

    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ\\s]+$", message = "El país no debe contener números ni caracteres especiales (@, #, etc.)")
    @Size(min = 3, max = 60, message = "El país debe tener entre 3 y 60 caracteres")
    private String pais;
}
