package com.proyecto.servicios.service;

import com.proyecto.servicios.model.auth.BiometriaEstadoResponseDTO;
import com.proyecto.servicios.model.auth.BiometriaLoginRequestDTO;
import com.proyecto.servicios.model.auth.BiometriaRegistroRequestDTO;
import com.proyecto.servicios.model.auth.LoginResponseDTO;

/**
 * Servicio para administrar y autenticar el acceso biométrico (huella digital)
 * almacenado mediante hash SHA-256 en la tabla datos_biometricos.
 */
public interface BiometriaAuthService {

    /**
     * Vincula o renueva el token biométrico (hash SHA-256) para el cliente autenticado.
     */
    BiometriaEstadoResponseDTO registrarHuella(
            String authHeader, String usuarioParam, BiometriaRegistroRequestDTO request);

    /**
     * Desactiva y revoca el acceso biométrico del cliente autenticado.
     */
    BiometriaEstadoResponseDTO desactivarHuella(String authHeader, String usuarioParam);

    /**
     * Consulta si el usuario tiene acceso con huella digital activo.
     */
    BiometriaEstadoResponseDTO consultarEstado(String authHeader, String usuarioParam);

    /**
     * Autentica al usuario verificando el token biométrico contra el hash registrado en BD.
     */
    LoginResponseDTO loginConHuella(BiometriaLoginRequestDTO request);
}
