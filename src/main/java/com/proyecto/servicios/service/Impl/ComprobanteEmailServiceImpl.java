package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.BrevoEmailClient;
import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.clientes.Cuenta;
import com.proyecto.servicios.entity.gestopago.PagoServicio;
import com.proyecto.servicios.model.brevo.BrevoEmailRequestDTO;
import com.proyecto.servicios.model.brevo.BrevoEmailResponseDTO;
import com.proyecto.servicios.model.brevo.BrevoRecipientDTO;
import com.proyecto.servicios.model.brevo.BrevoSenderDTO;
import com.proyecto.servicios.service.ComprobanteEmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

/**
 * Implementación del servicio de envío de comprobantes bancarios vía Brevo (Sendinblue).
 * Cumple con todos los requerimientos:
 * 1. Número de Autorización
 * 2. Servicio
 * 3. Teléfono
 * 4. Referencia
 * 5. Monto
 * 6. Leyenda para aclaraciones
 * 7. Logo institucional
 * 8. Fecha y hora
 */
@Service
@Slf4j
public class ComprobanteEmailServiceImpl implements ComprobanteEmailService {

    private final BrevoEmailClient brevoEmailClient;

    @Value("${brevo.api.key}")
    private String apiKey;

    @Value("${brevo.sender.email}")
    private String senderEmail;

    @Value("${brevo.sender.name:Banco Digital - Pagos y Recargas}")
    private String senderName;

    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public ComprobanteEmailServiceImpl(BrevoEmailClient brevoEmailClient) {
        this.brevoEmailClient = brevoEmailClient;
    }

    @Override
    public void enviarComprobantePago(Cuenta cuenta, Cliente cliente, PagoServicio pago) {
        if (cliente == null || cliente.getCorreo() == null || cliente.getCorreo().isBlank()) {
            log.warn("No se pudo enviar el comprobante de pago: El cliente no tiene correo electrónico registrado.");
            return;
        }

        try {
            String destinatarioNombre = cliente.getNombre() != null
                    ? (cliente.getNombre() + (cliente.getApellidoPaterno() != null ? " " + cliente.getApellidoPaterno() : "")).trim()
                    : "Estimado(a) Cliente";

            String htmlBody = construirHtmlComprobante(destinatarioNombre, cuenta, pago);

            BrevoEmailRequestDTO request = BrevoEmailRequestDTO.builder()
                    .sender(BrevoSenderDTO.builder()
                            .name(senderName)
                            .email(senderEmail)
                            .build())
                    .to(Collections.singletonList(BrevoRecipientDTO.builder()
                            .name(destinatarioNombre)
                            .email(cliente.getCorreo().trim())
                            .build()))
                    .subject(String.format("Comprobante de Pago de Servicio - Autorización: %s", pago.getNumAutorizacion()))
                    .htmlContent(htmlBody)
                    .build();

            BrevoEmailResponseDTO response = brevoEmailClient.sendEmail(apiKey, request);
            log.info("Comprobante de pago enviado exitosamente vía Brevo al correo: {}. MessageId: {}",
                    cliente.getCorreo(), response != null ? response.getMessageId() : "OK");

        } catch (Exception e) {
            // Regla bancaria: Un fallo en el envío de correo de notificación NUNCA debe anular ni revertir
            // un cobro de servicio que ya fue debitado y autorizado por el proveedor.
            log.error("Aviso: No fue posible enviar el comprobante por correo vía Brevo para la cuenta {}. Motivo: {}",
                    cuenta.getNumeroCuenta(), e.getMessage(), e);
        }
    }

    private String construirHtmlComprobante(String nombreCliente, Cuenta cuenta, PagoServicio pago) {
        String numCuenta = cuenta.getNumeroCuenta();
        String cuentaEnmascarada = numCuenta.length() >= 4
                ? "**** **** **** " + numCuenta.substring(numCuenta.length() - 4)
                : numCuenta;

        String fechaFormateada = pago.getFechaOperacion() != null
                ? pago.getFechaOperacion().format(FORMATO_FECHA_HORA)
                : "N/A";

        String telefono = (pago.getTelefono() != null && !pago.getTelefono().isBlank() && !pago.getTelefono().contains("1111111111"))
                ? pago.getTelefono()
                : "No aplica (Servicio con recibo)";

        String referencia = (pago.getReferencia() != null && !pago.getReferencia().isBlank())
                ? pago.getReferencia()
                : "No aplica (Recarga de tiempo aire)";

        BigDecimal monto = pago.getMontoPagado() != null ? pago.getMontoPagado() : BigDecimal.ZERO;
        BigDecimal comision = pago.getComision() != null ? pago.getComision() : BigDecimal.ZERO;
        BigDecimal saldoRestante = pago.getSaldoRestante() != null ? pago.getSaldoRestante() : BigDecimal.ZERO;

        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Comprobante de Pago Bancario</title>
              <style>
                body { margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f1f5f9; color: #1e293b; }
                .wrapper { width: 100%%; background-color: #f1f5f9; padding: 30px 10px; }
                .container { max-width: 620px; margin: 0 auto; background: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 10px 25px rgba(0,0,0,0.06); }
                .header { background: linear-gradient(135deg, #0f172a 0%%, #1e3a8a 100%%); padding: 36px 30px; text-align: center; color: #ffffff; }
                .logo-box { display: inline-flex; align-items: center; justify-content: center; margin-bottom: 12px; }
                .logo-text { font-size: 24px; font-weight: 800; letter-spacing: 1px; color: #ffffff; margin: 0; }
                .logo-subtext { font-size: 11px; text-transform: uppercase; letter-spacing: 2px; color: #93c5fd; margin-top: 2px; }
                .badge { display: inline-block; background-color: #10b981; color: #ffffff; font-size: 12px; font-weight: 700; padding: 6px 14px; border-radius: 20px; text-transform: uppercase; letter-spacing: 0.5px; margin-top: 15px; }
                .amount-hero { text-align: center; padding: 28px 20px 20px; background-color: #f8fafc; border-bottom: 1px solid #e2e8f0; }
                .amount-label { font-size: 13px; text-transform: uppercase; letter-spacing: 1px; color: #64748b; font-weight: 600; }
                .amount-val { font-size: 38px; font-weight: 800; color: #0f172a; margin: 6px 0 0; }
                .amount-currency { font-size: 20px; font-weight: 600; color: #2563eb; }
                .content { padding: 28px 30px; }
                .table-receipt { width: 100%%; border-collapse: collapse; margin-top: 10px; }
                .table-receipt td { padding: 12px 6px; font-size: 14px; border-bottom: 1px solid #f1f5f9; }
                .table-receipt td.label { color: #64748b; font-weight: 500; width: 42%%; }
                .table-receipt td.value { color: #0f172a; font-weight: 700; text-align: right; }
                .highlight-val { color: #1e3a8a !important; font-family: monospace; font-size: 15px !important; }
                .leyenda-box { margin-top: 26px; padding: 18px 20px; background-color: #eff6ff; border-left: 4px solid #3b82f6; border-radius: 8px; font-size: 13px; line-height: 1.6; color: #1e3a8a; }
                .leyenda-title { font-weight: 700; margin-bottom: 6px; display: flex; align-items: center; }
                .footer { padding: 24px 30px; text-align: center; font-size: 12px; color: #94a3b8; background-color: #f8fafc; border-top: 1px solid #e2e8f0; line-height: 1.6; }
              </style>
            </head>
            <body>
              <div class="wrapper">
                <div class="container">
                  <!-- Header con Logo Bancario -->
                  <div class="header">
                    <div class="logo-box">
                      <svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="#60a5fa" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="vertical-align: middle; margin-right: 8px;">
                        <path d="M3 21h18"/>
                        <path d="M3 10h18"/>
                        <path d="M5 6l7-3 7 3"/>
                        <path d="M4 10v11"/>
                        <path d="M20 10v11"/>
                        <path d="M8 14v4"/>
                        <path d="M12 14v4"/>
                        <path d="M16 14v4"/>
                      </svg>
                      <span class="logo-text">BANCO DIGITAL</span>
                    </div>
                    <div class="logo-subtext">Servicios Financieros & Pagos en Línea</div>
                    <div><span class="badge">&#10003; Pago Exitoso y Autorizado</span></div>
                  </div>

                  <!-- Monto Destacado -->
                  <div class="amount-hero">
                    <div class="amount-label">Monto Total Debitado</div>
                    <div class="amount-val">$%s <span class="amount-currency">MXN</span></div>
                  </div>

                  <!-- Contenido y Datos requeridos -->
                  <div class="content">
                    <p style="margin: 0 0 16px; font-size: 15px; color: #334155;">Hola <strong>%s</strong>, tu pago ha sido procesado y registrado con éxito con el proveedor del servicio.</p>

                    <table class="table-receipt">
                      <tr>
                        <td class="label">Número de Autorización:</td>
                        <td class="value highlight-val">%s</td>
                      </tr>
                      <tr>
                        <td class="label">Folio de Ticket:</td>
                        <td class="value highlight-val">%s</td>
                      </tr>
                      <tr>
                        <td class="label">Servicio:</td>
                        <td class="value">%s</td>
                      </tr>
                      <tr>
                        <td class="label">Producto:</td>
                        <td class="value">%s</td>
                      </tr>
                      <tr>
                        <td class="label">Teléfono:</td>
                        <td class="value">%s</td>
                      </tr>
                      <tr>
                        <td class="label">Referencia de Pago:</td>
                        <td class="value">%s</td>
                      </tr>
                      <tr>
                        <td class="label">Fecha y Hora:</td>
                        <td class="value">%s</td>
                      </tr>
                      <tr>
                        <td class="label">Comisión:</td>
                        <td class="value">$%s MXN</td>
                      </tr>
                      <tr>
                        <td class="label">Cuenta de Cargo:</td>
                        <td class="value">%s</td>
                      </tr>
                      <tr>
                        <td class="label">Saldo Restante Disponible:</td>
                        <td class="value" style="color: #059669;">$%s MXN</td>
                      </tr>
                    </table>

                    <!-- Leyenda para Aclaraciones solicitada por el profesor -->
                    <div class="leyenda-box">
                      <div class="leyenda-title">&#9888;&#65039; LEYENDA OFICIAL Y ACLARACIONES</div>
                      Conserve este comprobante digital como constancia oficial de pago. Para cualquier duda, aclaración, reporte de saldo o reclamación relacionada con esta transacción o la aplicación del servicio por parte del proveedor, comuníquese a nuestra <strong>Línea de Aclaraciones al 800-226-2600</strong> (sin costo, las 24 horas) o envíe un correo a <strong>aclaraciones@bancodigital.com</strong> dentro de los 30 días naturales posteriores a la fecha de operación. Favor de mencionar el <strong>Número de Autorización: %s</strong> y su <strong>Folio de Ticket: %s</strong>.
                    </div>
                  </div>

                  <!-- Pie de página -->
                  <div class="footer">
                    Este es un comprobante digital generado automáticamente por Banco Digital S.A. de C.V.<br>
                    Por favor no responda a este mensaje. Consulta nuestro Aviso de Privacidad en www.bancodigital.com.
                  </div>
                </div>
              </div>
            </body>
            </html>
            """.formatted(
                monto.toPlainString(),
                nombreCliente,
                pago.getNumAutorizacion() != null ? pago.getNumAutorizacion() : "N/A",
                pago.getFolioTicket() != null ? pago.getFolioTicket() : "N/A",
                pago.getNombreServicio() != null ? pago.getNombreServicio() : "N/A",
                pago.getNombreProducto() != null ? pago.getNombreProducto() : "N/A",
                telefono,
                referencia,
                fechaFormateada,
                comision.toPlainString(),
                cuentaEnmascarada,
                saldoRestante.toPlainString(),
                pago.getNumAutorizacion() != null ? pago.getNumAutorizacion() : "N/A",
                pago.getFolioTicket() != null ? pago.getFolioTicket() : "N/A"
        );
    }
}
