package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.clientes.Cuenta;
import com.proyecto.servicios.entity.gestopago.PagoServicio;
import com.proyecto.servicios.model.brevo.EmailEnvioResultadoDTO;
import com.proyecto.servicios.model.gestopago.PagoServicioResponseDTO;

import java.math.BigDecimal;

/**
 * Servicio para la generación y envío de correos transaccionales vía Brevo:
 * comprobantes digitales de pago, códigos 2FA de seguridad y bienvenida.
 */
public interface ComprobanteEmailService {

    /**
     * Envía un comprobante bancario de pago al correo registrado del cliente.
     *
     * @param cuenta  Cuenta bancaria de la que se debitó el pago.
     * @param cliente Cliente titular asociado a la cuenta.
     * @param pago    Entidad del pago de servicio exitosamente procesado.
     */
    void enviarComprobantePago(Cuenta cuenta, Cliente cliente, PagoServicio pago);

    /**
     * Envía el comprobante de pago vía Brevo al correo indicado (o al correo registrado si es nulo)
     * y retorna el estado explícito del envío para reflejarlo en la respuesta hacia Web/Móvil.
     */
    default EmailEnvioResultadoDTO enviarComprobantePagoConResultado(
            Cuenta cuenta, Cliente cliente, PagoServicio pago, String correoDestinoOverride) {
        enviarComprobantePago(cuenta, cliente, pago);
        String target = (correoDestinoOverride != null && !correoDestinoOverride.isBlank())
                ? correoDestinoOverride.trim()
                : (cliente != null ? cliente.getCorreo() : null);
        return EmailEnvioResultadoDTO.builder()
                .enviado(target != null && !target.isBlank())
                .correoDestino(target)
                .mensaje("Comprobante procesado")
                .build();
    }

    /**
     * Recupera y limpia el último resultado de envío de comprobante registrado para un folio de ticket.
     */
    default EmailEnvioResultadoDTO consumirResultadoPorFolio(String folioTicket) {
        return null;
    }

    /**
     * Envía el código de verificación temporal 2FA de 6 dígitos al correo registrado del cliente.
     */
    default EmailEnvioResultadoDTO enviarCodigo2FA(
            Cliente cliente, Cuenta cuenta, String codigo2FA, long vigenciaSegundos) {
        return EmailEnvioResultadoDTO.builder()
                .enviado(false)
                .correoDestino(cliente != null ? cliente.getCorreo() : null)
                .mensaje("Envío de código 2FA por correo no habilitado")
                .build();
    }

    /**
     * Envía el correo electrónico de bienvenida tras el registro exitoso de una nueva cuenta.
     */
    default EmailEnvioResultadoDTO enviarCorreoBienvenida(
            Cliente cliente, Cuenta cuenta, String usuario, BigDecimal saldoInicial) {
        return EmailEnvioResultadoDTO.builder()
                .enviado(false)
                .correoDestino(cliente != null ? cliente.getCorreo() : null)
                .mensaje("Envío de correo de bienvenida no habilitado")
                .build();
    }

    /**
     * Envía un código temporal 2FA de 6 dígitos al correo proporcionado durante la creación de cuenta.
     */
    default EmailEnvioResultadoDTO enviarCodigoRegistro2FA(
            String correoDestino, String nombre, String codigo2FA, long vigenciaSegundos) {
        return EmailEnvioResultadoDTO.builder()
                .enviado(false)
                .correoDestino(correoDestino)
                .mensaje("Envío de código 2FA de registro por correo no habilitado")
                .build();
    }

    /**
     * Envía por correo vía Brevo el ticket oficial de un intento de operación (ej. rechazado -1 o pendiente).
     */
    default EmailEnvioResultadoDTO enviarTicketIntentoPago(
            Cuenta cuenta, Cliente cliente, PagoServicioResponseDTO ticket, String correoDestinoOverride) {
        String target = (correoDestinoOverride != null && !correoDestinoOverride.isBlank())
                ? correoDestinoOverride.trim()
                : (cliente != null ? cliente.getCorreo() : null);
        return EmailEnvioResultadoDTO.builder()
                .enviado(false)
                .correoDestino(target)
                .mensaje("Envío de ticket de intento por correo no habilitado")
                .build();
    }
}
