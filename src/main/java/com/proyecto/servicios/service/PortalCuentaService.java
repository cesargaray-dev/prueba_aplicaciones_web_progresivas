package com.proyecto.servicios.service;

import com.proyecto.servicios.model.auth.AbonoSaldoRequestDTO;
import com.proyecto.servicios.model.auth.ActualizarContactoRequestDTO;
import com.proyecto.servicios.model.auth.CambiarContrasenaRequestDTO;
import com.proyecto.servicios.model.auth.DisponibilidadRegistroResponseDTO;
import com.proyecto.servicios.model.auth.EnviarCodigoRegistroRequestDTO;
import com.proyecto.servicios.model.auth.EnviarCodigoRegistroResponseDTO;
import com.proyecto.servicios.model.auth.LoginResponseDTO;
import com.proyecto.servicios.model.auth.RegisterRequestDTO;
import com.proyecto.servicios.model.auth.TotpEstadoResponseDTO;
import com.proyecto.servicios.model.auth.TotpSetupRequestDTO;
import com.proyecto.servicios.model.auth.TotpSetupResponseDTO;
import com.proyecto.servicios.model.auth.TotpVerificarRequestDTO;
import com.proyecto.servicios.model.auth.UserProfileDTO;

/**
 * Servicio de autoservicio comercial del portal de pagos:
 * auto-registro de clientes, actualización de contacto, fondeo de cuenta y enriquecimiento de sesión.
 */
public interface PortalCuentaService {

    /**
     * Envía un código temporal 2FA de 6 dígitos al correo electrónico para validar la creación de cuenta.
     */
    default EnviarCodigoRegistroResponseDTO enviarCodigoVerificacionRegistro(EnviarCodigoRegistroRequestDTO request) {
        throw new UnsupportedOperationException("Envío de código 2FA de registro no disponible en esta implementación");
    }

    /**
     * Valida el código 2FA enviado al correo y registra al nuevo cliente en el portal comercial.
     */
    default LoginResponseDTO registrarClientePortalConVerificacion(RegisterRequestDTO request) {
        return registrarClientePortal(request);
    }

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

    default DisponibilidadRegistroResponseDTO verificarDisponibilidadRegistro(
            String correo, String usuario, String curp, String rfc) {
        throw new UnsupportedOperationException("Verificación de disponibilidad no disponible en esta implementación");
    }

    default TotpSetupResponseDTO generarConfiguracionTotpRegistro(TotpSetupRequestDTO request) {
        throw new UnsupportedOperationException("Configuración de Google Authenticator no disponible en esta implementación");
    }

    default TotpEstadoResponseDTO consultarEstadoTotp(String authHeader) {
        throw new UnsupportedOperationException("Consulta TOTP no disponible en esta implementación");
    }

    default TotpSetupResponseDTO iniciarVinculacionTotp(String authHeader) {
        throw new UnsupportedOperationException("Vinculación TOTP no disponible en esta implementación");
    }

    default TotpEstadoResponseDTO activarTotp(String authHeader, TotpVerificarRequestDTO request) {
        throw new UnsupportedOperationException("Activación TOTP no disponible en esta implementación");
    }

    default TotpEstadoResponseDTO desactivarTotp(String authHeader, TotpVerificarRequestDTO request) {
        throw new UnsupportedOperationException("Desactivación TOTP no disponible en esta implementación");
    }
}
