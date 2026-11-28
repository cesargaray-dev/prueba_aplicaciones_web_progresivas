package com.proyecto.servicios.controller;

import com.proyecto.servicios.exception.GlobalExceptionHandler.ErrorResponse;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.auth.AbonoSaldoRequestDTO;
import com.proyecto.servicios.model.auth.ActualizarContactoRequestDTO;
import com.proyecto.servicios.model.auth.BiometriaEstadoResponseDTO;
import com.proyecto.servicios.model.auth.BiometriaLoginRequestDTO;
import com.proyecto.servicios.model.auth.BiometriaRegistroRequestDTO;
import com.proyecto.servicios.model.auth.CambiarContrasenaRequestDTO;
import com.proyecto.servicios.model.auth.DisponibilidadRegistroResponseDTO;
import com.proyecto.servicios.model.auth.EnviarCodigoRegistroRequestDTO;
import com.proyecto.servicios.model.auth.EnviarCodigoRegistroResponseDTO;
import com.proyecto.servicios.model.auth.LoginRequestDTO;
import com.proyecto.servicios.model.auth.LoginResponseDTO;
import com.proyecto.servicios.model.auth.LogoutRequestDTO;
import com.proyecto.servicios.model.auth.RegisterRequestDTO;
import com.proyecto.servicios.model.auth.TotpEstadoResponseDTO;
import com.proyecto.servicios.model.auth.TotpSetupRequestDTO;
import com.proyecto.servicios.model.auth.TotpSetupResponseDTO;
import com.proyecto.servicios.model.auth.TotpVerificarRequestDTO;
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

    @GetMapping("/session-status")
    @Operation(
            summary = "Verificar vigencia de la sesión sin reiniciar inactividad",
            description = "Comprueba si el token Bearer sigue activo o fue desplazado por un inicio de sesión en otro dispositivo."
    )
    public ResponseEntity<java.util.Map<String, Object>> sessionStatus(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        boolean activa = authService.verificarSesionActivaSinRefrescar(authHeader);
        return ResponseEntity.ok(java.util.Map.of("activa", activa));
    }

    @PostMapping("/keep-alive")
    @Operation(
            summary = "Mantener viva la sesión ante actividad del usuario",
            description = "Extiende el TTL deslizante de inactividad en el servidor cuando el usuario interactúa activamente (incluyendo dentro de ventanas modales de compra)."
    )
    public ResponseEntity<java.util.Map<String, Object>> keepAlive(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        boolean activa = authService.validarTokenYObtenerUsuario(authHeader).isPresent();
        return ResponseEntity.ok(java.util.Map.of("activa", activa));
    }

    @GetMapping("/register/disponibilidad")
    @Operation(
            summary = "Verificar disponibilidad inmediata de correo, usuario, CURP y RFC",
            description = "Permite avisar inmediatamente al usuario durante el registro por pasos si el correo electrónico, usuario, CURP o RFC ya están registrados."
    )
    public ResponseEntity<DisponibilidadRegistroResponseDTO> verificarDisponibilidadRegistro(
            @RequestParam(value = "correo", required = false) String correo,
            @RequestParam(value = "usuario", required = false) String usuario,
            @RequestParam(value = "curp", required = false) String curp,
            @RequestParam(value = "rfc", required = false) String rfc) {
        if (portalCuentaService == null) {
            throw new IllegalStateException("El servicio de registro no está disponible en este contexto");
        }
        return ResponseEntity.ok(portalCuentaService.verificarDisponibilidadRegistro(correo, usuario, curp, rfc));
    }

    @PostMapping("/register/totp")
    @Operation(
            summary = "Generar clave secreta y código QR de Google Authenticator para registro",
            description = "Valida que el correo y usuario estén disponibles y genera una clave Base32 + QR compatible con Google Authenticator (TOTP de 6 dígitos cada 30 segundos)."
    )
    public ResponseEntity<TotpSetupResponseDTO> configurarTotpRegistro(
            @RequestBody TotpSetupRequestDTO request) {
        if (portalCuentaService == null) {
            throw new IllegalStateException("El servicio de registro no está disponible en este contexto");
        }
        return ResponseEntity.ok(portalCuentaService.generarConfiguracionTotpRegistro(request));
    }

    @GetMapping("/me/totp/estado")
    @Operation(summary = "Consultar si la cuenta autenticada tiene activo Google Authenticator")
    public ResponseEntity<TotpEstadoResponseDTO> estadoTotp(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (portalCuentaService == null) {
            throw new IllegalStateException("El servicio de perfil no está disponible en este contexto");
        }
        return ResponseEntity.ok(portalCuentaService.consultarEstadoTotp(authHeader));
    }

    @PostMapping("/me/totp/iniciar")
    @Operation(summary = "Generar código QR y clave Base32 para vincular Google Authenticator en una cuenta existente")
    public ResponseEntity<TotpSetupResponseDTO> iniciarTotp(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (portalCuentaService == null) {
            throw new IllegalStateException("El servicio de perfil no está disponible en este contexto");
        }
        return ResponseEntity.ok(portalCuentaService.iniciarVinculacionTotp(authHeader));
    }

    @PostMapping("/me/totp/activar")
    @Operation(summary = "Confirmar y activar Google Authenticator validando el código actual de 6 dígitos")
    public ResponseEntity<TotpEstadoResponseDTO> activarTotp(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody TotpVerificarRequestDTO request) {
        if (portalCuentaService == null) {
            throw new IllegalStateException("El servicio de perfil no está disponible en este contexto");
        }
        return ResponseEntity.ok(portalCuentaService.activarTotp(authHeader, request));
    }

    @PostMapping("/me/totp/desactivar")
    @Operation(summary = "Desactivar Google Authenticator verificando el código actual")
    public ResponseEntity<TotpEstadoResponseDTO> desactivarTotp(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) TotpVerificarRequestDTO request) {
        if (portalCuentaService == null) {
            throw new IllegalStateException("El servicio de perfil no está disponible en este contexto");
        }
        return ResponseEntity.ok(portalCuentaService.desactivarTotp(authHeader, request));
    }

    @PostMapping("/register/enviar-codigo")
    @Operation(
            summary = "Enviar código 2FA al correo antes de crear la cuenta",
            description = "Valida que el correo sea real y disponible, genera un código temporal de 6 dígitos (5 min) y lo envía vía Brevo para confirmar el correo del nuevo usuario."
    )
    public ResponseEntity<EnviarCodigoRegistroResponseDTO> enviarCodigoRegistro(
            @Valid @RequestBody EnviarCodigoRegistroRequestDTO request) {
        if (portalCuentaService == null) {
            throw new IllegalStateException("El servicio de registro no está disponible en este contexto");
        }
        return ResponseEntity.ok(portalCuentaService.enviarCodigoVerificacionRegistro(request));
    }

    @PostMapping("/register")
    @Operation(
            summary = "Registrar nuevo cliente y cuenta digital",
            description = "Valida el código 2FA enviado al correo, crea el nuevo cliente, su cuenta de 16 dígitos, saldo inicial de bienvenida y credenciales de acceso, iniciando sesión automáticamente."
    )
    public ResponseEntity<LoginResponseDTO> register(@Valid @RequestBody RegisterRequestDTO request) {
        log.info("REST request de auto-registro comercial para usuario: {}", request.getUsuario());
        if (portalCuentaService == null) {
            throw new IllegalStateException("El servicio de registro no está disponible en este contexto");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(portalCuentaService.registrarClientePortalConVerificacion(request));
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
