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

    @Schema(description = "Número de cuenta bancaria activa (16 dígitos)", example = "1234567890123456")
    private String numeroCuenta;

    @Schema(description = "Saldo disponible actual", example = "1500.00")
    private BigDecimal saldo;

    @Schema(description = "Estatus activo del cliente", example = "true")
    private Boolean activo;

    @Schema(description = "Fecha y hora del último acceso", example = "2026-11-14 00:40:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime ultimoAcceso;
}
