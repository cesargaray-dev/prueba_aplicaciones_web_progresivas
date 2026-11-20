package com.proyecto.servicios.model.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminActualizarUsuarioRequestDTO {
    private String nombre;
    private String segundoNombre;
    private String apellidoPaterno;
    private String apellidoMaterno;
    private String correo;
    private String telefonoMovil;
    private String usuario;
    private String nuevaContrasena;
    private BigDecimal saldo;
    private Boolean activo;
    private Boolean bloqueado;
}
