package com.proyecto.servicios.model.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminUsuarioDTO {
    private Long clienteId;
    private String usuario;
    private String rol;
    private Boolean esAdmin;
    private String nombre;
    private String segundoNombre;
    private String apellidoPaterno;
    private String apellidoMaterno;
    private String nombreCompleto;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate fechaNacimiento;

    private String sexo;
    private String nacionalidad;
    private String estadoCivil;
    private String curp;
    private String rfc;
    private String correo;
    private String telefonoMovil;
    private String numeroCuenta;
    private String estatusCuenta;
    private BigDecimal saldo;
    private Boolean activo;
    private Boolean bloqueado;
    private Integer intentosFallidos;
    private Boolean biometriaActiva;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime ultimoAcceso;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fechaRegistro;
}
