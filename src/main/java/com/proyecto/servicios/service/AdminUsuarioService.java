package com.proyecto.servicios.service;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.admin.AdminActualizarUsuarioRequestDTO;
import com.proyecto.servicios.model.admin.AdminUsuarioDTO;
import com.proyecto.servicios.model.auth.RegisterRequestDTO;

import java.util.List;

public interface AdminUsuarioService {
    List<AdminUsuarioDTO> listarTodosLosUsuarios(String adminAutenticado);
    AdminUsuarioDTO obtenerUsuarioPorId(String adminAutenticado, Long clienteId);
    AdminUsuarioDTO crearUsuarioComoAdmin(String adminAutenticado, RegisterRequestDTO request);
    AdminUsuarioDTO actualizarUsuarioComoAdmin(String adminAutenticado, Long clienteId, AdminActualizarUsuarioRequestDTO request);
    GenericResponse eliminarUsuarioComoAdmin(String adminAutenticado, Long clienteId);
    void asegurarUsuarioAdminInicial();
}
