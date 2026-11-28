package com.proyecto.servicios.model.clientes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodigoPostalSepomexDTO {
    private String codigoPostal;
    private boolean valido;
    private String pais;
    private String codigoEstado;
    private String estado;
    private String municipio;
    private List<String> colonias;
    private String mensaje;
}
