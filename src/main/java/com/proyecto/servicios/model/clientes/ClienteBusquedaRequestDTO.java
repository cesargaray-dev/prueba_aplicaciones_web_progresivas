package com.proyecto.servicios.model.clientes;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ClienteBusquedaRequestDTO {

    @Pattern(regexp = "^$|^[A-Z]{4}\\d{6}[HM][A-Z]{5}[A-Z\\d]\\d$", message = "El formato de la CURP es inválido. Solo se aceptan 18 caracteres alfanuméricos en mayúscula")
    private String curp;

    @Pattern(regexp = "^$|^[A-Z&Ñ]{3,4}\\d{6}[A-Z\\d]{3}$", message = "El formato del RFC es inválido. Debe tener 12 o 13 caracteres alfanuméricos en mayúscula")
    private String rfc;

    @Pattern(regexp = "^$|^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$", message = "El formato del correo es inválido")
    private String correo;

    @Pattern(regexp = "^$|^\\d{16}$", message = "El número de cuenta debe tener exactamente 16 dígitos numéricos")
    private String numeroCuenta;

    private Long id;

    private String nombre;

    private String apellidoPaterno;

    private Boolean activo;
}
