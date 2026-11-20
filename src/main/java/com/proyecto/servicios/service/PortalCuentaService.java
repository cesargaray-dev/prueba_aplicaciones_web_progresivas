package com.proyecto.servicios.service;

import com.proyecto.servicios.model.auth.AbonoSaldoRequestDTO;
import com.proyecto.servicios.model.auth.ActualizarContactoRequestDTO;
import com.proyecto.servicios.model.auth.CambiarContrasenaRequestDTO;
import com.proyecto.servicios.model.auth.LoginResponseDTO;
import com.proyecto.servicios.model.auth.RegisterRequestDTO;
import com.proyecto.servicios.model.auth.UserProfileDTO;

/**
 * Servicio de autoservicio comercial del portal de pagos:
 * auto-registro de clientes, actualización de contacto, fondeo de cuenta y enriquecimiento de sesión.
 */
public interface PortalCuentaService {

    /**
     * Registra un nuevo cliente con su domicilio, cuenta digital de 16 dígitos, saldo inicial
     * y credenciales de acceso, iniciando sesión automáticamente y enviando correo de bienvenida.
     */
    LoginResponseDTO registrarClientePortal(RegisterRequestDTO request);

    /**
     * Actualiza el correo electrónico y teléfono móvil del cliente autenticado.
     */
    UserProfileDTO actualizarContacto(String authHeader, String usuarioParam, ActualizarContactoRequestDTO request);

    /**
     * Abona fondos a la cuenta bancaria del cliente autenticado (bloqueo pesimista).
     */
    UserProfileDTO abonarSaldo(String authHeader, String usuarioParam, AbonoSaldoRequestDTO request);

    /**
     * Completa el teléfono móvil en la respuesta de inicio de sesión cuando no viene poblado.
     */
    LoginResponseDTO enriquecerLoginConTelefono(LoginResponseDTO response);

    /**
     * Cambia la contraseña de acceso verificando la contraseña actual.
     */
    default void cambiarContrasena(
            String authHeader, String usuarioParam, CambiarContrasenaRequestDTO request) {
        throw new UnsupportedOperationException("Cambio de contraseña no disponible en esta implementación");
    }
}
