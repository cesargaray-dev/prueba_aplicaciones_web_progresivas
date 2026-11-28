package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.admin.AdminActualizarUsuarioRequestDTO;
import com.proyecto.servicios.model.admin.AdminUsuarioDTO;
import com.proyecto.servicios.model.auth.AbonoSaldoRequestDTO;
import com.proyecto.servicios.model.auth.RegisterRequestDTO;
import com.proyecto.servicios.service.AdminUsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/usuarios")
@Tag(name = "Administración de Usuarios", description = "Gestión total de usuarios exclusiva para rol ADMIN")
public class AdminUsuarioController {

    private final AdminUsuarioService adminUsuarioService;

    public AdminUsuarioController(AdminUsuarioService adminUsuarioService) {
        this.adminUsuarioService = adminUsuarioService;
    }

    @GetMapping
    @Operation(summary = "Listar todos los usuarios registrados")
    public ResponseEntity<List<AdminUsuarioDTO>> listarTodos(Authentication auth) {
        String admin = auth != null ? auth.getName() : null;
        return ResponseEntity.ok(adminUsuarioService.listarTodosLosUsuarios(admin));
    }

    @GetMapping("/{clienteId}")
    @Operation(summary = "Obtener detalle de un usuario por ID")
    public ResponseEntity<AdminUsuarioDTO> obtenerPorId(
            @PathVariable Long clienteId,
            Authentication auth) {
        String admin = auth != null ? auth.getName() : null;
        return ResponseEntity.ok(adminUsuarioService.obtenerUsuarioPorId(admin, clienteId));
    }

    @PostMapping
    @Operation(summary = "Crear un nuevo usuario desde el panel de administración")
    public ResponseEntity<AdminUsuarioDTO> crearUsuario(
            @Valid @RequestBody RegisterRequestDTO request,
            Authentication auth) {
        String admin = auth != null ? auth.getName() : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminUsuarioService.crearUsuarioComoAdmin(admin, request));
    }

    @PutMapping("/{clienteId}")
    @Operation(summary = "Editar datos, saldo, contraseña o estado de un usuario")
    public ResponseEntity<AdminUsuarioDTO> actualizarUsuario(
            @PathVariable Long clienteId,
            @Valid @RequestBody AdminActualizarUsuarioRequestDTO request,
            Authentication auth) {
        String admin = auth != null ? auth.getName() : null;
        return ResponseEntity.ok(adminUsuarioService.actualizarUsuarioComoAdmin(admin, clienteId, request));
    }

    @PostMapping("/{clienteId}/abonar")
    @Operation(summary = "Ingresar dinero (abonar saldo) a la cuenta de un usuario como administrador")
    public ResponseEntity<AdminUsuarioDTO> abonarSaldoUsuario(
            @PathVariable Long clienteId,
            @Valid @RequestBody AbonoSaldoRequestDTO request,
            Authentication auth) {
        String admin = auth != null ? auth.getName() : null;
        return ResponseEntity.ok(adminUsuarioService.abonarSaldoComoAdmin(admin, clienteId, request));
    }

    @DeleteMapping("/{clienteId}")
    @Operation(summary = "Eliminar permanentemente un usuario y sus registros asociados")
    public ResponseEntity<GenericResponse> eliminarUsuario(
            @PathVariable Long clienteId,
            Authentication auth) {
        String admin = auth != null ? auth.getName() : null;
        return ResponseEntity.ok(adminUsuarioService.eliminarUsuarioComoAdmin(admin, clienteId));
    }

    @DeleteMapping
    @Operation(summary = "Eliminar permanentemente todos los usuarios clientes (conservando @admin)")
    public ResponseEntity<GenericResponse> eliminarTodosLosUsuarios(Authentication auth) {
        String admin = auth != null ? auth.getName() : null;
        return ResponseEntity.ok(adminUsuarioService.eliminarTodosLosUsuariosComoAdmin(admin));
    }
}
