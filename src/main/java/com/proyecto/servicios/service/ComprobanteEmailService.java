package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.clientes.Cuenta;
import com.proyecto.servicios.entity.gestopago.PagoServicio;

/**
 * Servicio para la generación y envío de comprobantes bancarios digitales de pago vía correo electrónico.
 */
public interface ComprobanteEmailService {

    /**
     * Envía un correo con diseño bancario profesional conteniendo todos los datos requeridos
     * de la transacción: autorización, servicio, teléfono, referencia, monto, fecha, logo y leyenda de aclaraciones.
     *
     * @param cuenta  Cuenta bancaria de la que se debitó el pago.
     * @param cliente Cliente titular asociado a la cuenta.
     * @param pago    Entidad del pago de servicio exitosamente procesado.
     */
    void enviarComprobantePago(Cuenta cuenta, Cliente cliente, PagoServicio pago);
}
