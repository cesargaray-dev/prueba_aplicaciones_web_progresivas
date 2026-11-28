package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.gestopago.PagoServicio;
import com.proyecto.servicios.model.brevo.EmailEnvioResultadoDTO;
import com.proyecto.servicios.model.gestopago.GenerarCodigo2FAResponseDTO;
import com.proyecto.servicios.model.gestopago.PagoServicioRequestDTO;
import com.proyecto.servicios.model.gestopago.PagoServicioResponseDTO;
import com.proyecto.servicios.model.gestopago.ReenviarComprobanteRequestDTO;

/**
 * Servicio coordinador de seguridad operativa de pagos, envío de códigos 2FA por correo,
 * validación estricta de telefonía previa al cobro y reenvío de comprobantes Brevo.
 */
public interface NotificacionPagoService {

    /**
     * Envía el código 2FA recién generado al correo electrónico del titular y agrega
     * los metadatos reales de entrega a la respuesta.
     */
    GenerarCodigo2FAResponseDTO enriquecerYEnviar2FA(GenerarCodigo2FAResponseDTO response);

    /**
     * Valida reglas comerciales previas al pago (bloqueo de productos de consulta y
     * validación estricta de teléfonos mexicanos en recargas celulares).
     */
    void validarAntesDePagar(PagoServicioRequestDTO request);

    /**
     * Agrega al comprobante retornado el estado real del envío vía Brevo para ese folio.
     */
    PagoServicioResponseDTO enriquecerRespuestaPago(PagoServicioResponseDTO response);

    /**
     * Reenvía por correo electrónico vía Brevo el comprobante de un pago existente.
     */
    EmailEnvioResultadoDTO reenviarComprobante(ReenviarComprobanteRequestDTO request);

    /**
     * Busca un pago registrado por su folio de ticket o lanza excepción 404 si no existe.
     */
    default PagoServicio obtenerPagoPorFolio(String folioTicket) {
        return null;
    }

    /**
     * Obtiene el número de cuenta propietario de un folio (tanto de pagos exitosos como de tickets de intento).
     */
    default String obtenerCuentaPropietariaPorFolio(String folioTicket) {
        PagoServicio p = obtenerPagoPorFolio(folioTicket);
        return p != null ? p.getNumeroCuenta() : null;
    }

    /**
     * Genera los bytes de un documento PDF oficial (%PDF-1.4) para el folio indicado.
     */
    default byte[] generarComprobantePdf(String folioTicket) {
        return new byte[0];
    }
}
