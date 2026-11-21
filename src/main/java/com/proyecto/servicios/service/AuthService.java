package com.proyecto.servicios.service;

import com.proyecto.servicios.model.auth.LoginRequestDTO;
import com.proyecto.servicios.model.auth.LoginResponseDTO;
import com.proyecto.servicios.model.auth.UserProfileDTO;

import java.util.Optional;

public interface AuthService {

    /**
     * Inicia sesión validando credenciales contra la base de datos (login_clientes).
     * Controla intentos fallidos (bloqueo al alcanzar 5 intentos), valida estatus activo
     * y genera token de sesión con detalles de cliente, cuenta y saldo.
     *
     * @param request Datos de acceso (usuario y contraseña).
     * @return Respuesta con token y perfil del usuario autenticado.
     */
    LoginResponseDTO login(LoginRequestDTO request);

    /**
     * Cierra la sesión activa invalidando el token y limpiando el contexto.
     *
     * @param token   Token de sesión Bearer.
     * @param usuario Nombre de usuario (opcional para verificación de propiedad).
     */
    void logout(String token, String usuario);

    /**
     * Obtiene el perfil y saldo del cliente autenticado a partir de su token de sesión Bearer.
     *
     * @param token   Token de sesión Bearer.
     * @param usuario Parámetro legado ignorado por seguridad.
     * @return Perfil completo y balance del cliente.
     */
    UserProfileDTO obtenerPerfilAutenticado(String token, String usuario);

    /**
     * Genera una sesión autenticada para un usuario validado previamente por un factor alterno
     * (por ejemplo, verificación biométrica de huella digital).
     *
     * @param usuario Nombre de usuario validado.
     * @return Respuesta con token de sesión, perfil, cuenta y saldo.
     */
    default LoginResponseDTO crearSesionParaUsuario(String usuario) {
        throw new UnsupportedOperationException("Creación directa de sesión no soportada en esta implementación");
    }

    /**
     * Valida un token Bearer y devuelve el nombre de usuario autenticado si la sesión está vigente.
     */
    default Optional<String> validarTokenYObtenerUsuario(String token) {
        return Optional.empty();
    }

    /**
     * Verifica una contraseña en texto plano contra un hash almacenado (BCrypt o legado SHA-256).
     */
    default boolean verificarContrasena(String rawPassword, String storedHash) {
        return false;
    }

    /**
     * Codifica una contraseña usando BCrypt.
     */
    default String codificarContrasena(String rawPassword) {
        return rawPassword;
    }

    /**
     * Invalida todas las sesiones activas del usuario, conservando opcionalmente el token actual.
     */
    default void invalidarOtrasSesiones(String usuario, String tokenAConservar) {
        // Sin operación por defecto
    }

    /**
     * Indica si el token fue revocado porque la cuenta inició sesión en otro dispositivo.
     */
    default boolean fueDesplazadoPorOtroDispositivo(String token) {
        return false;
    }

    /**
     * Verifica si el token sigue activo sin extender su tiempo de expiración por inactividad.
     * Lanza AutenticacionException si la sesión expiró o fue cerrada desde otro dispositivo.
     */
    default boolean verificarSesionActivaSinRefrescar(String token) {
        return validarTokenYObtenerUsuario(token).isPresent();
    }
}
