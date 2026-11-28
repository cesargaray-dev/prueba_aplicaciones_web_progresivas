package com.proyecto.servicios.model.auth;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Perfil del cliente autenticado y estado de cuenta")
public class UserProfileDTO {

    @Schema(description = "Identificador del cliente", example = "1")
    private Long clienteId;

    @Schema(description = "Nombre de usuario", example = "cesar123")
    private String usuario;

    @Schema(description = "Primer nombre", example = "César")
    private String nombre;

    @Schema(description = "Segundo nombre", example = "Iván")
    private String segundoNombre;

    @Schema(description = "Apellido paterno", example = "Sánchez")
    private String apellidoPaterno;

    @Schema(description = "Apellido materno", example = "Torres")
    private String apellidoMaterno;

    @Schema(description = "Nombre completo concatenado", example = "César Iván Sánchez Torres")
    private String nombreCompleto;

    @Schema(description = "Correo electrónico", example = "cesar@correo.com")
    private String email;

    @Schema(description = "Teléfono móvil registrado", example = "5512345678")
    private String telefonoMovil;

    @Schema(description = "Teléfono alternativo", example = "5587654321")
    private String telefonoAlternativo;

    @Schema(description = "CURP oficial de 18 caracteres", example = "GAGC990515HDFRRL01")
    private String curp;

    @Schema(description = "RFC con homoclave", example = "GAGC990515HDF")
    private String rfc;

    @Schema(description = "Fecha de nacimiento (yyyy-MM-dd)", example = "1999-05-15")
    private String fechaNacimiento;

    @Schema(description = "Sexo registrado (HOMBRE o MUJER)", example = "HOMBRE")
    private String sexo;

    @Schema(description = "Nacionalidad", example = "MEXICANA")
    private String nacionalidad;

    @Schema(description = "Estado civil", example = "SOLTERO")
    private String estadoCivil;

    @Schema(description = "Ocupación o actividad económica", example = "EMPLEADO DEL SECTOR PRIVADO")
    private String ocupacion;

    @Schema(description = "Profesión del tabulador oficial", example = "INGENIERÍA EN SISTEMAS / SOFTWARE / TI")
    private String profesion;

    @Schema(description = "Empresa o lugar de trabajo", example = "TECNOLOGIA SA")
    private String empresa;

    @Schema(description = "Ingreso mensual estimado", example = "25000.00")
    private BigDecimal ingresoMensual;

    @Schema(description = "Calle del domicilio", example = "Av. Paseo de la Reforma")
    private String calle;

    @Schema(description = "Número exterior", example = "250")
    private String numeroExterior;

    @Schema(description = "Número interior", example = "4B")
    private String numeroInterior;

    @Schema(description = "Colonia", example = "Juárez")
    private String colonia;

    @Schema(description = "Municipio o Alcaldía", example = "Cuauhtémoc")
    private String municipio;

    @Schema(description = "Estado o Entidad Federativa", example = "Ciudad de México")
    private String estado;

    @Schema(description = "Código postal de 5 dígitos", example = "06600")
    private String codigoPostal;

    @Schema(description = "País", example = "México")
    private String pais;

    @Schema(description = "Número de cuenta bancaria activa (16 dígitos)", example = "1234567890123456")
    private String numeroCuenta;

    @Schema(description = "Saldo disponible actual", example = "1500.00")
    private BigDecimal saldo;

    @Schema(description = "Estatus activo del cliente", example = "true")
    private Boolean activo;

    @Schema(description = "Fecha y hora del último acceso", example = "2026-11-14 00:40:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime ultimoAcceso;

    @Schema(description = "Indica si el usuario tiene privilegios de administrador", example = "false")
    private Boolean esAdmin;

    @Schema(description = "Rol del usuario en la plataforma (ADMIN o CLIENTE)", example = "CLIENTE")
    private String rol;

    @Schema(description = "Indica si Google Authenticator (TOTP) está habilitado en la cuenta", example = "true")
    private Boolean totpHabilitado;
}
