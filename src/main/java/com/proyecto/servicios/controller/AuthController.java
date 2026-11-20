package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.GlobalExceptionHandler.ErrorResponse;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.auth.AbonoSaldoRequestDTO;
import com.proyecto.servicios.model.auth.ActualizarContactoRequestDTO;
import com.proyecto.servicios.model.auth.BiometriaEstadoResponseDTO;
import com.proyecto.servicios.model.auth.BiometriaLoginRequestDTO;
import com.proyecto.servicios.model.auth.BiometriaRegistroRequestDTO;
import com.proyecto.servicios.model.auth.CambiarContrasenaRequestDTO;
import com.proyecto.servicios.model.auth.LoginRequestDTO;
import com.proyecto.servicios.model.auth.LoginResponseDTO;
import com.proyecto.servicios.model.auth.LogoutRequestDTO;
import com.proyecto.servicios.model.auth.RegisterRequestDTO;
import com.proyecto.servicios.model.auth.UserProfileDTO;
import com.proyecto.servicios.service.AuthService;
import com.proyecto.servicios.service.BiometriaAuthService;
import com.proyecto.servicios.service.PortalCuentaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Validated
@Tag(name = "Autenticación", description = "Módulo de inicio y cierre de sesión de clientes y consulta de sesión")
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final PortalCuentaService portalCuentaService;
    private final BiometriaAuthService biometriaAuthService;

    public AuthController(AuthService authService) {
        this(authService, null, null);
    }

    public AuthController(AuthService authService, PortalCuentaService portalCuentaService) {
        this(authService, portalCuentaService, null);
    }

    @Autowired
    public AuthController(
            AuthService authService,
            PortalCuentaService portalCuentaService,
            BiometriaAuthService biometriaAuthService) {
        this.authService = authService;
        this.portalCuentaService = portalCuentaService;
        this.biometriaAuthService = biometriaAuthService;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Iniciar sesión de cliente",
            description = "Valida las credenciales de acceso contra login_clientes mediante BCrypt (con migración transparente desde SHA-256). Controla intentos fallidos, bloquea al alcanzar 5 intentos y devuelve información del cliente, cuenta y saldo."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Autenticación exitosa"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Credenciales inválidas, usuario inactivo o bloqueado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        log.info("REST request de login para usuario: {}", request.getUsuario());
        LoginResponseDTO resp = authService.login(request);
        if (portalCuentaService != null) {
            resp = portalCuentaService.enriquecerLoginConTelefono(resp);
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Cerrar sesión de cliente",
            description = "Invalida únicamente la sesión asociada al token Bearer presentado."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sesión cerrada exitosamente"),
            @ApiResponse(responseCode = "401", description = "Token ausente, inválido o expirado",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Intento de cerrar la sesión de otro usuario",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<GenericResponse> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) LogoutRequestDTO request) {
        String usuario = request != null ? request.getUsuario() : null;
        log.info("REST request de logout");
        authService.logout(authHeader, usuario);
        return ResponseEntity.ok(new GenericResponse(0, "Sesión cerrada exitosamente"));
    }

    @GetMapping("/me")
    @Operation(
            summary = "Consultar perfil y saldo del cliente autenticado",
            description = "Devuelve el perfil del usuario autenticado exclusivamente mediante el header Authorization (Bearer <token>)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Perfil recuperado exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autorizado o sesión expirada",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserProfileDTO> me(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return ResponseEntity.ok(authService.obtenerPerfilAutenticado(authHeader, null));
    }

    @PostMapping("/register")
    @Operation(
            summary = "Registrar nuevo cliente y cuenta digital",
            description = "Crea un nuevo cliente, su cuenta de 16 dígitos, saldo inicial de bienvenida y credenciales de acceso, iniciando sesión automáticamente."
    )
    public ResponseEntity<LoginResponseDTO> register(@Valid @RequestBody RegisterRequestDTO request) {
        log.info("REST request de auto-registro comercial para usuario: {}", request.getUsuario());
        if (portalCuentaService == null) {
            throw new IllegalStateException("El servicio de registro no está disponible en este contexto");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(portalCuentaService.registrarClientePortal(request));
    }

    @PutMapping({"/me/contacto", "/me/perfil"})
    @Operation(
            summary = "Actualizar datos personales, correo y teléfono móvil del cliente autenticado",
            description = "Actualiza nombre, apellidos, correo electrónico real para notificaciones Brevo y teléfono móvil mexicano de 10 dígitos.")
    public ResponseEntity<UserProfileDTO> actualizarContacto(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody ActualizarContactoRequestDTO request) {
        if (portalCuentaService == null) {
            throw new IllegalStateException("El servicio de perfil no está disponible en este contexto");
        }
        return ResponseEntity.ok(portalCuentaService.actualizarContacto(authHeader, null, request));
    }

    @PutMapping("/me/contrasena")
    @Operation(
            summary = "Cambiar la contraseña del cliente autenticado",
            description = "Valida la contraseña actual, guarda el nuevo hash BCrypt en login_clientes e invalida otras sesiones activas.")
    public ResponseEntity<GenericResponse> cambiarContrasena(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody CambiarContrasenaRequestDTO request) {
        if (portalCuentaService == null) {
            throw new IllegalStateException("El servicio de perfil no está disponible en este contexto");
        }
        portalCuentaService.cambiarContrasena(authHeader, null, request);
        return ResponseEntity.ok(new GenericResponse(0, "Contraseña actualizada exitosamente."));
    }

    @PostMapping("/me/abonar")
    @Operation(
            summary = "Abonar fondos a la cuenta autenticada",
            description = "Realiza un abono de fondos a la cuenta bancaria asociada a la sesión activa.")
    public ResponseEntity<UserProfileDTO> abonarSaldo(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody AbonoSaldoRequestDTO request) {
        if (portalCuentaService == null) {
            throw new IllegalStateException("El servicio de fondeo no está disponible en este contexto");
        }
        return ResponseEntity.ok(portalCuentaService.abonarSaldo(authHeader, null, request));
    }

    @PostMapping("/biometria/registrar")
    @Operation(
            summary = "Vincular acceso con huella digital al dispositivo móvil",
            description = "Almacena el hash SHA-256 del token biométrico en datos_biometricos para permitir inicio de sesión con huella.")
    public ResponseEntity<BiometriaEstadoResponseDTO> registrarBiometria(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody BiometriaRegistroRequestDTO request) {
        if (biometriaAuthService == null) {
            throw new IllegalStateException("El servicio biométrico no está disponible en este contexto");
        }
        return ResponseEntity.ok(biometriaAuthService.registrarHuella(authHeader, null, request));
    }

    @DeleteMapping("/biometria")
    @Operation(
            summary = "Desactivar y revocar acceso con huella digital",
            description = "Revoca el hash biométrico registrado para que el acceso requiera únicamente contraseña.")
    public ResponseEntity<BiometriaEstadoResponseDTO> desactivarBiometria(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (biometriaAuthService == null) {
            throw new IllegalStateException("El servicio biométrico no está disponible en este contexto");
        }
        return ResponseEntity.ok(biometriaAuthService.desactivarHuella(authHeader, null));
    }

    @GetMapping("/biometria/estado")
    @Operation(
            summary = "Consultar estado del acceso con huella digital",
            description = "Informa si la cuenta autenticada tiene el inicio biométrico habilitado.")
    public ResponseEntity<BiometriaEstadoResponseDTO> estadoBiometria(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (biometriaAuthService == null) {
            throw new IllegalStateException("El servicio biométrico no está disponible en este contexto");
        }
        return ResponseEntity.ok(biometriaAuthService.consultarEstado(authHeader, null));
    }

    @PostMapping("/login/biometrico")
    @Operation(
            summary = "Iniciar sesión con huella digital (biometría)",
            description = "Valida el token biométrico desbloqueado por la huella digital del dispositivo contra datos_biometricos.")
    public ResponseEntity<LoginResponseDTO> loginBiometrico(@Valid @RequestBody BiometriaLoginRequestDTO request) {
        log.info("REST request de login biométrico para usuario: {}", request.getUsuario());
        if (biometriaAuthService == null) {
            throw new IllegalStateException("El servicio biométrico no está disponible en este contexto");
        }
        LoginResponseDTO resp = biometriaAuthService.loginConHuella(request);
        if (portalCuentaService != null) {
            resp = portalCuentaService.enriquecerLoginConTelefono(resp);
        }
        return ResponseEntity.ok(resp);
    }
}
