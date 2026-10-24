package com.proyecto.servicios.service;

import com.proyecto.servicios.model.gestopago.GenerarCodigo2FARequestDTO;
import com.proyecto.servicios.model.gestopago.GenerarCodigo2FAResponseDTO;
import com.proyecto.servicios.model.gestopago.PagoServicioRequestDTO;
import com.proyecto.servicios.model.gestopago.PagoServicioResponseDTO;

/**
 * Servicio de negocio para la gestión y ejecución de pagos de servicios
 * bancarios a través de GestoPago (PuntoRed).
 */
public interface PagoServicioService {

    /**
     * Genera un código 2FA temporal de 6 dígitos para autorizar un pago de servicio.
     *
     * @param request DTO con el número de cuenta bancaria.
     * @return Respuesta con el código 2FA temporal y su vigencia.
     */
    GenerarCodigo2FAResponseDTO generarCodigo2FA(GenerarCodigo2FARequestDTO request);

    /**
     * Procesa el pago de un servicio validando cuenta, cliente activo, estatus de cuenta,
     * código 2FA temporal, saldo disponible y ejecutando la transacción cifrada en GestoPago.
     *
     * @param request Datos del pago de servicio a realizar.
     * @return Comprobante de la operación con el saldo restante y folios de autorización.
     */
    PagoServicioResponseDTO procesarPagoServicio(PagoServicioRequestDTO request);
}
