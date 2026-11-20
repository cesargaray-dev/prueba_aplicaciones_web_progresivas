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
@Schema(description = "Respuesta de inicio de sesión exitoso con datos de cliente y cuenta")
public class LoginResponseDTO {

    @Schema(description = "Token de autenticación de sesión", example = "e87f8728d844439c8789d7fa11c3494b")
    private String token;

    @Schema(description = "Nombre de usuario autenticado", example = "cesar123")
    private String usuario;

    @Schema(description = "Identificador del cliente", example = "1")
    private Long clienteId;

    @Schema(description = "Nombre completo del cliente", example = "César Iván Sánchez Torres")
    private String nombreCompleto;

    @Schema(description = "Correo electrónico del cliente", example = "cesar@correo.com")
    private String email;

    @Schema(description = "Teléfono móvil de 10 dígitos del cliente", example = "5512345678")
    private String telefonoMovil;

    @Schema(description = "Número de cuenta bancaria de 16 dígitos", example = "1234567890123456")
    private String numeroCuenta;

    @Schema(description = "Saldo disponible actual de la cuenta", example = "1500.00")
    private BigDecimal saldo;

    @Schema(description = "Fecha y hora del último acceso exitoso", example = "2026-11-14 00:40:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime ultimoAcceso;
}
