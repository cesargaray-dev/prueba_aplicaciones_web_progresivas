package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.BrevoEmailClient;
import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.clientes.Cuenta;
import com.proyecto.servicios.entity.gestopago.PagoServicio;
import com.proyecto.servicios.model.brevo.BrevoEmailRequestDTO;
import com.proyecto.servicios.model.brevo.BrevoEmailResponseDTO;
import com.proyecto.servicios.model.brevo.BrevoRecipientDTO;
import com.proyecto.servicios.model.brevo.BrevoSenderDTO;
import com.proyecto.servicios.model.brevo.EmailEnvioResultadoDTO;
import com.proyecto.servicios.model.gestopago.PagoServicioResponseDTO;
import com.proyecto.servicios.service.ComprobanteEmailService;
import com.proyecto.servicios.util.TelefonoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementación del servicio de notificaciones y comprobantes vía Brevo API v3.
 * Centraliza el envío de comprobantes de pago, códigos 2FA de autorización y correos de bienvenida.
 */
@Service
@Slf4j
public class ComprobanteEmailServiceImpl implements ComprobanteEmailService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final int MAX_RESULTADOS_CACHE = 500;

    private final BrevoEmailClient brevoEmailClient;
    private final Map<String, EmailEnvioResultadoDTO> resultadosPorFolio = new ConcurrentHashMap<>();

    @Value("${brevo.api.key:}")
    private String apiKey;

    @Value("${brevo.sender.email:}")
    private String senderEmail;

    @Value("${brevo.sender.name:Banco Digital - Pagos y Recargas}")
    private String senderName;

    public ComprobanteEmailServiceImpl(BrevoEmailClient brevoEmailClient) {
        this.brevoEmailClient = brevoEmailClient;
    }

    @Override
    public void enviarComprobantePago(Cuenta cuenta, Cliente cliente, PagoServicio pago) {
        enviarComprobantePagoConResultado(cuenta, cliente, pago, null);
    }

    @Override
    public EmailEnvioResultadoDTO enviarComprobantePagoConResultado(
            Cuenta cuenta, Cliente cliente, PagoServicio pago, String correoDestinoOverride) {

        String correoDestino = (correoDestinoOverride != null && !correoDestinoOverride.isBlank())
                ? correoDestinoOverride.trim()
                : (cliente != null ? cliente.getCorreo() : null);

        EmailEnvioResultadoDTO resultado;
        if (correoDestino == null || !TelefonoUtil.esCorreoSintaxisValida(correoDestino)) {
            log.warn("No se envió el comprobante: correo destino inválido o ausente para la cuenta {}",
                    cuenta != null ? enmascararCuenta(cuenta.getNumeroCuenta()) : "DESCONOCIDA");
            resultado = EmailEnvioResultadoDTO.builder()
                    .enviado(false)
                    .correoDestino(correoDestino)
                    .mensaje("No existe un correo electrónico válido configurado para enviar el comprobante.")
                    .build();
        } else {
            String nombreCliente = construirNombreCompleto(cliente);
            String folioAuth = pago != null && pago.getNumAutorizacion() != null ? pago.getNumAutorizacion() : "N/A";
            String asunto = "Comprobante de Pago de Servicio - Autorización #" + folioAuth;
            String htmlContent = construirHtmlComprobante(cuenta, nombreCliente, pago);

            resultado = enviarCorreoBrevo(correoDestino, nombreCliente, asunto, htmlContent, "comprobante de pago");
        }

        if (pago != null && pago.getFolioTicket() != null && !pago.getFolioTicket().isBlank()) {
            if (resultadosPorFolio.size() >= MAX_RESULTADOS_CACHE) {
                resultadosPorFolio.clear();
            }
            resultadosPorFolio.put(pago.getFolioTicket(), resultado);
        }
        return resultado;
    }

    @Override
    public EmailEnvioResultadoDTO consumirResultadoPorFolio(String folioTicket) {
        if (folioTicket == null || folioTicket.isBlank()) {
            return null;
        }
        return resultadosPorFolio.remove(folioTicket);
    }

    @Override
    public EmailEnvioResultadoDTO enviarCodigo2FA(
            Cliente cliente, Cuenta cuenta, String codigo2FA, long vigenciaSegundos) {

        String correoDestino = cliente != null ? cliente.getCorreo() : null;
        if (correoDestino == null || !TelefonoUtil.esCorreoSintaxisValida(correoDestino)) {
            return EmailEnvioResultadoDTO.builder()
                    .enviado(false)
                    .correoDestino(correoDestino)
                    .mensaje("No hay un correo electrónico válido registrado en la cuenta.")
                    .build();
        }

        String nombreCliente = construirNombreCompleto(cliente);
        String cuentaMask = cuenta != null ? enmascararCuenta(cuenta.getNumeroCuenta()) : "****";
        String asunto = "Código de Seguridad 2FA (" + codigo2FA + ") - Banco Digital";
        String htmlContent = construirHtml2FA(nombreCliente, cuentaMask, codigo2FA, vigenciaSegundos);

        return enviarCorreoBrevo(correoDestino, nombreCliente, asunto, htmlContent, "código 2FA");
    }

    @Override
    public EmailEnvioResultadoDTO enviarCorreoBienvenida(
            Cliente cliente, Cuenta cuenta, String usuario, BigDecimal saldoInicial) {

        String correoDestino = cliente != null ? cliente.getCorreo() : null;
        if (correoDestino == null || !TelefonoUtil.esCorreoSintaxisValida(correoDestino)) {
            return EmailEnvioResultadoDTO.builder()
                    .enviado(false)
                    .correoDestino(correoDestino)
                    .mensaje("Correo inválido para bienvenida.")
                    .build();
        }

        String nombreCliente = construirNombreCompleto(cliente);
        String cuentaMask = cuenta != null ? enmascararCuenta(cuenta.getNumeroCuenta()) : "****";
        String asunto = "¡Bienvenido a Banco Digital! Tu cuenta de pagos está activa";
        String htmlContent = construirHtmlBienvenida(nombreCliente, usuario, cuentaMask, saldoInicial);

        return enviarCorreoBrevo(correoDestino, nombreCliente, asunto, htmlContent, "bienvenida");
    }

    @Override
    public EmailEnvioResultadoDTO enviarCodigoRegistro2FA(
            String correoDestino, String nombre, String codigo2FA, long vigenciaSegundos) {
        if (correoDestino == null || !TelefonoUtil.esCorreoSintaxisValida(correoDestino)) {
            return EmailEnvioResultadoDTO.builder()
                    .enviado(false)
                    .correoDestino(correoDestino)
                    .mensaje("El formato del correo electrónico no es válido.")
                    .build();
        }
        String nombreDestinatario = (nombre != null && !nombre.isBlank()) ? nombre.trim() : "Futuro(a) Cliente";
        String asunto = "Código de Verificación de Cuenta (" + codigo2FA + ") - Banco Digital";
        String htmlContent = construirHtmlCodigoRegistro(nombreDestinatario, codigo2FA, vigenciaSegundos);

        return enviarCorreoBrevo(correoDestino.trim(), nombreDestinatario, asunto, htmlContent, "código 2FA de registro");
    }

    @Override
    public EmailEnvioResultadoDTO enviarTicketIntentoPago(
            Cuenta cuenta, Cliente cliente, PagoServicioResponseDTO ticket, String correoDestinoOverride) {
        String correoDestino = (correoDestinoOverride != null && !correoDestinoOverride.isBlank())
                ? correoDestinoOverride.trim()
                : (cliente != null ? cliente.getCorreo() : null);

        EmailEnvioResultadoDTO resultado;
        if (correoDestino == null || !TelefonoUtil.esCorreoSintaxisValida(correoDestino)) {
            resultado = EmailEnvioResultadoDTO.builder()
                    .enviado(false)
                    .correoDestino(correoDestino)
                    .mensaje("No existe un correo electrónico válido configurado para enviar el ticket.")
                    .build();
        } else {
            String nombreCliente = construirNombreCompleto(cliente);
            String folio = (ticket != null && ticket.getFolioTicket() != null) ? ticket.getFolioTicket() : "N/A";
            String prod = (ticket != null && ticket.getProducto() != null) ? ticket.getProducto() : "Operación";
            String asunto = "Ticket de Operación (" + prod + ") - Folio " + folio;
            String htmlContent = construirHtmlTicketIntento(cuenta, nombreCliente, ticket);
            resultado = enviarCorreoBrevo(correoDestino, nombreCliente, asunto, htmlContent, "ticket de operación");
        }

        if (ticket != null && ticket.getFolioTicket() != null && !ticket.getFolioTicket().isBlank()) {
            if (resultadosPorFolio.size() >= MAX_RESULTADOS_CACHE) {
                resultadosPorFolio.clear();
            }
            resultadosPorFolio.put(ticket.getFolioTicket(), resultado);
        }
        return resultado;
    }

    private EmailEnvioResultadoDTO enviarCorreoBrevo(
            String destinatario,
            String nombreDestinatario,
            String asunto,
            String htmlContent,
            String tipoOperacion) {

        boolean isPlaceholder = TelefonoUtil.esCorreoPlaceholder(destinatario);
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("No se envió {} vía Brevo a {}: BREVO_API_KEY no configurada.",
                    tipoOperacion, TelefonoUtil.enmascararCorreo(destinatario));
            return EmailEnvioResultadoDTO.builder()
                    .enviado(false)
                    .correoDestino(destinatario)
                    .correoPlaceholder(isPlaceholder)
                    .mensaje("Servicio SMTP Brevo sin clave configurada en este entorno.")
                    .build();
        }

        try {
            BrevoEmailRequestDTO request = BrevoEmailRequestDTO.builder()
                    .sender(BrevoSenderDTO.builder()
                            .name(senderName)
                            .email(senderEmail)
                            .build())
                    .to(List.of(BrevoRecipientDTO.builder()
                            .name(nombreDestinatario)
                            .email(destinatario)
                            .build()))
                    .subject(asunto)
                    .htmlContent(htmlContent)
                    .build();

            BrevoEmailResponseDTO response = brevoEmailClient.sendEmail(apiKey, request);
            String messageId = response != null ? response.getMessageId() : "OK";

            log.info("{} enviado exitosamente vía Brevo a {}. MessageId: {}",
                    tipoOperacion, destinatario, messageId);

            String mensajeResultado = isPlaceholder
                    ? "Despachado vía Brevo, pero el correo '" + destinatario + "' pertenece a un dominio de ejemplo sin buzón real. Actualízalo en Mi Perfil."
                    : "Correo enviado exitosamente a " + destinatario + ".";

            return EmailEnvioResultadoDTO.builder()
                    .enviado(!isPlaceholder)
                    .correoDestino(destinatario)
                    .correoPlaceholder(isPlaceholder)
                    .messageId(messageId)
                    .mensaje(mensajeResultado)
                    .build();

        } catch (Exception e) {
            log.error("No fue posible enviar {} vía Brevo a {}: {}",
                    tipoOperacion, TelefonoUtil.enmascararCorreo(destinatario), e.getMessage());
            return EmailEnvioResultadoDTO.builder()
                    .enviado(false)
                    .correoDestino(destinatario)
                    .correoPlaceholder(isPlaceholder)
                    .mensaje("No se pudo entregar el correo en este momento (" + e.getMessage() + ").")
                    .build();
        }
    }

    private String construirNombreCompleto(Cliente cliente) {
        if (cliente == null) {
            return "Estimado(a) Cliente";
        }
        StringBuilder sb = new StringBuilder();
        if (cliente.getNombre() != null && !cliente.getNombre().isBlank()) {
            sb.append(cliente.getNombre().trim());
        }
        if (cliente.getApellidoPaterno() != null && !cliente.getApellidoPaterno().isBlank()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(cliente.getApellidoPaterno().trim());
        }
        return sb.isEmpty() ? "Estimado(a) Cliente" : sb.toString();
    }

    private String enmascararCuenta(String cuenta) {
        if (cuenta == null || cuenta.isBlank()) {
            return "****";
        }
        if (cuenta.length() < 4) {
            return cuenta;
        }
        return "**** **** **** " + cuenta.substring(cuenta.length() - 4);
    }

    private String esc(String val) {
        return val == null ? "N/A" : HtmlUtils.htmlEscape(val);
    }

    private String construirHtmlCodigoRegistro(String nombreCliente, String codigo2FA, long vigenciaSegundos) {
        long minutos = Math.max(1, vigenciaSegundos / 60);
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head><meta charset="UTF-8"><title>Verificación de Correo - Registro</title></head>
            <body style="margin:0;padding:0;background-color:#f4f6f9;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#1e293b;">
              <div style="width:100%%;padding:30px 12px;">
                <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:14px;overflow:hidden;border:1px solid #e2e8f0;box-shadow:0 4px 16px rgba(15,23,42,0.08);">
                  <div style="background:linear-gradient(135deg,#0f172a 0%%,#059669 100%%);padding:26px;text-align:center;color:#ffffff;">
                    <div style="font-size:20px;font-weight:800;letter-spacing:1px;">&#127974; BANCO DIGITAL</div>
                    <div style="font-size:12px;color:#d1fae5;margin-top:4px;">Verificación de Correo Electrónico (2FA)</div>
                  </div>
                  <div style="padding:28px;">
                    <p style="font-size:15px;margin-top:0;">Hola <strong>%s</strong>,</p>
                    <p style="font-size:14px;color:#475569;line-height:1.5;">
                      Ingresa el siguiente código de verificación de 6 dígitos para confirmar tu correo electrónico y completar la apertura de tu cuenta en <strong>Banco Digital</strong>:
                    </p>
                    <div style="background:#f8fafc;border:2px dashed #059669;border-radius:12px;padding:22px;text-align:center;margin:22px 0;">
                      <div style="font-size:11px;text-transform:uppercase;letter-spacing:1.2px;color:#64748b;font-weight:700;">Código de Verificación de Registro</div>
                      <div style="font-size:36px;font-weight:900;letter-spacing:10px;color:#0f172a;font-family:monospace;margin-top:6px;">%s</div>
                      <div style="font-size:12px;color:#047857;font-weight:600;margin-top:8px;">Vigencia: %d minuto(s) &bull; Un solo uso</div>
                    </div>
                    <div style="background:#fef3c7;border-left:4px solid #d97706;padding:12px 14px;border-radius:0 8px 8px 0;font-size:12px;color:#78350f;line-height:1.5;">
                      <strong>Seguridad:</strong> Nunca compartas este código con nadie. Si no solicitaste crear una cuenta en Banco Digital, puedes ignorar este mensaje.
                    </div>
                  </div>
                </div>
              </div>
            </body>
            </html>
            """.formatted(esc(nombreCliente), esc(codigo2FA), minutos);
    }

    private String construirHtml2FA(String nombreCliente, String cuentaEnmascarada, String codigo2FA, long vigenciaSegundos) {
        long minutos = Math.max(1, vigenciaSegundos / 60);
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head><meta charset="UTF-8"><title>Código de Seguridad 2FA</title></head>
            <body style="margin:0;padding:0;background-color:#f4f6f9;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#1e293b;">
              <div style="width:100%%;padding:30px 12px;">
                <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:14px;overflow:hidden;border:1px solid #e2e8f0;box-shadow:0 4px 16px rgba(15,23,42,0.08);">
                  <div style="background:linear-gradient(135deg,#0f172a 0%%,#1e3a8a 100%%);padding:26px;text-align:center;color:#ffffff;">
                    <div style="font-size:20px;font-weight:800;letter-spacing:1px;">&#128274; BANCO DIGITAL</div>
                    <div style="font-size:12px;color:#93c5fd;margin-top:4px;">Verificación en Dos Pasos (2FA)</div>
                  </div>
                  <div style="padding:28px;">
                    <p style="font-size:15px;margin-top:0;">Hola <strong>%s</strong>,</p>
                    <p style="font-size:14px;color:#475569;line-height:1.5;">
                      Utiliza el siguiente código temporal de seguridad para autorizar tu pago con cargo a la cuenta <strong>%s</strong>:
                    </p>
                    <div style="background:#f8fafc;border:2px dashed #1e3a8a;border-radius:12px;padding:22px;text-align:center;margin:22px 0;">
                      <div style="font-size:11px;text-transform:uppercase;letter-spacing:1.2px;color:#64748b;font-weight:700;">Código de Autorización 2FA</div>
                      <div style="font-size:36px;font-weight:900;letter-spacing:10px;color:#0f172a;font-family:monospace;margin-top:6px;">%s</div>
                      <div style="font-size:12px;color:#b45309;font-weight:600;margin-top:8px;">Vigencia: %d minuto(s) &bull; Un solo uso</div>
                    </div>
                    <div style="background:#fef3c7;border-left:4px solid #d97706;padding:12px 14px;border-radius:0 8px 8px 0;font-size:12px;color:#78350f;line-height:1.5;">
                      <strong>Seguridad:</strong> Banco Digital nunca te pedirá este código por llamada telefónica o chat. Si no iniciaste esta operación, ignora este mensaje.
                    </div>
                  </div>
                </div>
              </div>
            </body>
            </html>
            """.formatted(esc(nombreCliente), esc(cuentaEnmascarada), esc(codigo2FA), minutos);
    }

    private String construirHtmlBienvenida(String nombreCliente, String usuario, String cuentaEnmascarada, BigDecimal saldoInicial) {
        BigDecimal saldo = saldoInicial != null ? saldoInicial : BigDecimal.ZERO;
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head><meta charset="UTF-8"><title>Bienvenido a Banco Digital</title></head>
            <body style="margin:0;padding:0;background-color:#f4f6f9;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#1e293b;">
              <div style="width:100%%;padding:30px 12px;">
                <div style="max-width:580px;margin:0 auto;background:#ffffff;border-radius:14px;overflow:hidden;border:1px solid #e2e8f0;">
                  <div style="background:linear-gradient(135deg,#0f172a 0%%,#1d4ed8 100%%);padding:28px;text-align:center;color:#ffffff;">
                    <div style="font-size:22px;font-weight:800;">&#127974; BANCO DIGITAL</div>
                    <div style="font-size:13px;color:#bfdbfe;margin-top:4px;">Tu plataforma segura de Pagos y Recargas GestoPago</div>
                  </div>
                  <div style="padding:28px;">
                    <p style="font-size:15px;margin-top:0;">¡Hola <strong>%s</strong>!</p>
                    <p style="font-size:14px;color:#475569;line-height:1.6;">
                      Tu cuenta digital ha sido creada exitosamente. Ya puedes iniciar sesión con las mismas credenciales tanto en nuestra aplicación Web como en nuestra aplicación Móvil.
                    </p>
                    <table style="width:100%%;border-collapse:collapse;margin:18px 0;background:#f8fafc;border-radius:10px;">
                      <tr><td style="padding:10px 14px;color:#64748b;font-size:13px;">Usuario:</td><td style="padding:10px 14px;font-weight:700;text-align:right;">%s</td></tr>
                      <tr><td style="padding:10px 14px;color:#64748b;font-size:13px;">Cuenta Asignada:</td><td style="padding:10px 14px;font-weight:700;font-family:monospace;text-align:right;">%s</td></tr>
                      <tr><td style="padding:10px 14px;color:#64748b;font-size:13px;">Saldo Disponible:</td><td style="padding:10px 14px;font-weight:800;color:#059669;text-align:right;">$%s MXN</td></tr>
                    </table>
                    <p style="font-size:12px;color:#64748b;margin-bottom:0;">
                      Todas tus operaciones estarán protegidas con verificación 2FA en este correo electrónico y recibirás automáticamente tu comprobante oficial en cada pago.
                    </p>
                  </div>
                </div>
              </div>
            </body>
            </html>
            """.formatted(esc(nombreCliente), esc(usuario), esc(cuentaEnmascarada), saldo.toPlainString());
    }

    private String construirHtmlComprobante(Cuenta cuenta, String nombreCliente, PagoServicio pago) {
        String cuentaEnmascarada = enmascararCuenta(cuenta != null ? cuenta.getNumeroCuenta() : null);
        LocalDateTime fechaOp = (pago != null && pago.getFechaOperacion() != null)
                ? pago.getFechaOperacion() : LocalDateTime.now();
        String fechaFormateada = fechaOp.format(FORMATO_FECHA);

        String referencia = (pago != null && pago.getReferencia() != null && !pago.getReferencia().isBlank())
                ? pago.getReferencia() : "No aplica (Recarga de tiempo aire)";
        String telefono = (pago != null && pago.getTelefono() != null && !pago.getTelefono().isBlank() && !"1111111111".equals(pago.getTelefono()))
                ? pago.getTelefono() : "No aplica (Servicio con recibo)";
        BigDecimal monto = (pago != null && pago.getMontoPagado() != null) ? pago.getMontoPagado() : BigDecimal.ZERO;
        BigDecimal comision = (pago != null && pago.getComision() != null) ? pago.getComision() : BigDecimal.ZERO;
        BigDecimal saldoRestante = (pago != null && pago.getSaldoRestante() != null) ? pago.getSaldoRestante() : BigDecimal.ZERO;
        String idTxGestoPago = (pago != null && pago.getIdTxGestopago() != null)
                ? String.valueOf(pago.getIdTxGestopago()) : "N/A";
        String upc = (pago != null && pago.getUpc() != null && !pago.getUpc().isBlank())
                ? pago.getUpc() : "N/A";
        String numAuth = (pago != null && pago.getNumAutorizacion() != null) ? pago.getNumAutorizacion() : "N/A";
        String folio = (pago != null && pago.getFolioTicket() != null) ? pago.getFolioTicket() : "N/A";
        String servicio = (pago != null && pago.getNombreServicio() != null) ? pago.getNombreServicio() : "N/A";
        String producto = (pago != null && pago.getNombreProducto() != null) ? pago.getNombreProducto() : "N/A";

        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
              <meta charset="UTF-8">
              <title>Comprobante de Pago - Banco Digital</title>
              <style>
                body { margin: 0; padding: 0; background-color: #f4f6f9; font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #1e293b; }
                .wrapper { width: 100%%; padding: 30px 10px; }
                .container { max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 15px rgba(0,0,0,0.08); border: 1px solid #e2e8f0; }
                .header { background: linear-gradient(135deg, #0f172a 0%%, #1e3a8a 100%%); padding: 28px; text-align: center; color: #ffffff; }
                .logo-text { font-size: 24px; font-weight: 800; letter-spacing: 1px; margin-bottom: 4px; }
                .header-sub { font-size: 13px; color: #93c5fd; font-weight: 500; text-transform: uppercase; letter-spacing: 1.5px; }
                .status-badge { display: inline-block; background-color: #10b981; color: #ffffff; padding: 6px 16px; border-radius: 20px; font-size: 12px; font-weight: 700; margin-top: 14px; }
                .content { padding: 30px; }
                .amount-box { background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; padding: 20px; text-align: center; margin-bottom: 25px; }
                .amount-label { font-size: 12px; color: #64748b; text-transform: uppercase; font-weight: 600; letter-spacing: 1px; }
                .amount-value { font-size: 36px; font-weight: 800; color: #0f172a; margin: 6px 0; }
                .table-receipt { width: 100%%; border-collapse: collapse; margin-bottom: 24px; }
                .table-receipt td { padding: 12px 6px; border-bottom: 1px solid #f1f5f9; font-size: 14px; }
                .table-receipt tr:last-child td { border-bottom: none; }
                .label { color: #64748b; font-weight: 500; text-align: left; }
                .value { color: #0f172a; font-weight: 600; text-align: right; }
                .highlight-val { color: #1e40af; font-family: monospace; font-size: 15px; }
                .leyenda-box { background-color: #fffbeb; border-left: 4px solid #f59e0b; padding: 14px 16px; border-radius: 0 8px 8px 0; font-size: 12px; color: #78350f; line-height: 1.5; margin-top: 20px; }
                .leyenda-title { font-weight: 700; margin-bottom: 4px; color: #92400e; }
                .footer { background-color: #f8fafc; padding: 20px 30px; text-align: center; font-size: 11px; color: #94a3b8; border-top: 1px solid #e2e8f0; line-height: 1.5; }
              </style>
            </head>
            <body>
              <div class="wrapper">
                <div class="container">
                  <div class="header">
                    <div class="logo-text">&#127974; BANCO DIGITAL</div>
                    <div class="header-sub">Comprobante Oficial de Operación</div>
                    <div class="status-badge">&#10004; PAGO EXITOSO</div>
                  </div>
                  <div class="content">
                    <div class="amount-box">
                      <div class="amount-label">Monto Total Pagado</div>
                      <div class="amount-value">$%s <span style="font-size:16px; color:#64748b;">MXN</span></div>
                    </div>
                    <p style="font-size: 14px; margin-bottom: 18px;">Estimado(a) <strong>%s</strong>, tu pago ha sido procesado y registrado con éxito con el proveedor del servicio.</p>
                    <table class="table-receipt">
                      <tr><td class="label">ID Transacción GestoPago (ID_TX):</td><td class="value highlight-val">%s</td></tr>
                      <tr><td class="label">Número de Autorización GestoPago:</td><td class="value highlight-val">%s</td></tr>
                      <tr><td class="label">Folio UPC GestoPago:</td><td class="value highlight-val">%s</td></tr>
                      <tr><td class="label">Folio de Ticket:</td><td class="value highlight-val">%s</td></tr>
                      <tr><td class="label">Servicio:</td><td class="value">%s</td></tr>
                      <tr><td class="label">Producto:</td><td class="value">%s</td></tr>
                      <tr><td class="label">Teléfono:</td><td class="value">%s</td></tr>
                      <tr><td class="label">Referencia de Pago:</td><td class="value">%s</td></tr>
                      <tr><td class="label">Fecha y Hora:</td><td class="value">%s</td></tr>
                      <tr><td class="label">Comisión:</td><td class="value">$%s MXN</td></tr>
                      <tr><td class="label">Cuenta de Cargo:</td><td class="value">%s</td></tr>
                      <tr><td class="label">Saldo Restante Disponible:</td><td class="value" style="color: #059669;">$%s MXN</td></tr>
                    </table>
                    <div class="leyenda-box">
                      <div class="leyenda-title">&#9888;&#65039; LEYENDA OFICIAL Y ACLARACIONES</div>
                      Conserve este comprobante digital como constancia oficial de pago. Para cualquier duda, aclaración, reporte de saldo o reclamación relacionada con esta transacción o la aplicación del servicio por parte del proveedor, comuníquese a nuestra <strong>Línea de Aclaraciones al 800-226-2600</strong> (sin costo, las 24 horas) o envíe un correo a <strong>aclaraciones@bancodigital.com</strong> dentro de los 30 días naturales posteriores a la fecha de operación. Favor de mencionar el <strong>Número de Autorización: %s</strong> y su <strong>Folio de Ticket: %s</strong>.
                    </div>
                  </div>
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
                esc(nombreCliente),
                esc(idTxGestoPago),
                esc(numAuth),
                esc(upc),
                esc(folio),
                esc(servicio),
                esc(producto),
                esc(telefono),
                esc(referencia),
                esc(fechaFormateada),
                comision.toPlainString(),
                esc(cuentaEnmascarada),
                saldoRestante.toPlainString(),
                esc(numAuth),
                esc(folio)
        );
    }

    private String construirHtmlTicketIntento(Cuenta cuenta, String nombreCliente, PagoServicioResponseDTO t) {
        String cuentaEnmascarada = enmascararCuenta(
                cuenta != null ? cuenta.getNumeroCuenta() : (t != null ? t.getNumeroCuenta() : null));
        LocalDateTime fechaOp = (t != null && t.getFechaHoraOperacion() != null)
                ? t.getFechaHoraOperacion() : LocalDateTime.now();
        String fechaFormateada = fechaOp.format(FORMATO_FECHA);

        BigDecimal monto = (t != null && t.getMontoPagado() != null) ? t.getMontoPagado() : BigDecimal.ZERO;
        BigDecimal saldoIntacto = (t != null && t.getSaldoRestanteCuenta() != null)
                ? t.getSaldoRestanteCuenta() : BigDecimal.ZERO;
        String producto = (t != null && t.getProducto() != null && !t.getProducto().isBlank())
                ? t.getProducto() : "Operación GestoPago";
        String resultadoTxt = (t != null && "PENDIENTE".equalsIgnoreCase(t.getEstadoOperacion()))
                ? "Operación pendiente de confirmación"
                : "Operación rechazada";
        String motivo = (t != null && t.getMotivoProveedor() != null && !t.getMotivoProveedor().isBlank())
                ? t.getMotivoProveedor() : "Origen no valido";
        String numGestoPago = (t != null && t.getNumeroAutorizacion() != null && !t.getNumeroAutorizacion().isBlank())
                ? t.getNumeroAutorizacion() : "-1";
        String upc = (t != null && t.getUpc() != null && !t.getUpc().isBlank()) ? t.getUpc() : "N/A";
        String firma = (t != null && t.getFirmaDigital() != null && !t.getFirmaDigital().isBlank())
                ? t.getFirmaDigital() : "N/A";
        String folio = (t != null && t.getFolioTicket() != null && !t.getFolioTicket().isBlank())
                ? t.getFolioTicket() : "N/A";
        String destino = (t != null && t.getReferenciaDestino() != null && !t.getReferenciaDestino().isBlank())
                ? t.getReferenciaDestino() : "N/A";
        String leyenda = (t != null && t.getLeyenda() != null && !t.getLeyenda().isBlank())
                ? t.getLeyenda()
                : com.proyecto.servicios.util.TotpAuthenticatorUtil.construirLeyendaTicket(
                        "RECHAZADA", null, numGestoPago, folio);

        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
              <meta charset="UTF-8">
              <title>Ticket de Operación - Banco Digital</title>
            </head>
            <body style="margin:0;padding:0;background-color:#f4f6f9;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#1e293b;">
              <div style="width:100%%;padding:30px 10px;">
                <div style="max-width:600px;margin:0 auto;background:#ffffff;border-radius:14px;overflow:hidden;border:1px solid #e2e8f0;box-shadow:0 4px 15px rgba(0,0,0,0.08);">
                  <div style="background:linear-gradient(135deg,#0f172a 0%%,#881337 100%%);padding:26px;text-align:center;color:#ffffff;">
                    <div style="font-size:22px;font-weight:800;letter-spacing:1px;">&#127974; BANCO DIGITAL</div>
                    <div style="font-size:12px;color:#fecdd3;margin-top:4px;text-transform:uppercase;letter-spacing:1.2px;">Ticket Digital de Operación</div>
                  </div>
                  <div style="padding:28px;">
                    <div style="background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;padding:18px;text-align:center;margin-bottom:22px;">
                      <div style="font-size:11px;color:#64748b;text-transform:uppercase;font-weight:700;letter-spacing:1px;">Importe Solicitado (Sin Cargo Aplicado)</div>
                      <div style="font-size:32px;font-weight:800;color:#0f172a;margin:6px 0;">$%s <span style="font-size:15px;color:#64748b;">MXN</span></div>
                    </div>
                    <p style="font-size:14px;margin-bottom:18px;">Estimado(a) <strong>%s</strong>, te compartimos el ticket oficial del intento de operación procesado ante GestoPago:</p>
                    <table style="width:100%%;border-collapse:collapse;margin-bottom:22px;">
                      <tr><td style="padding:10px 6px;border-bottom:1px solid #f1f5f9;color:#64748b;font-size:13px;">Operación / Producto:</td><td style="padding:10px 6px;border-bottom:1px solid #f1f5f9;font-weight:700;text-align:right;font-size:14px;">%s</td></tr>
                      <tr><td style="padding:10px 6px;border-bottom:1px solid #f1f5f9;color:#64748b;font-size:13px;">Resultado:</td><td style="padding:10px 6px;border-bottom:1px solid #f1f5f9;font-weight:800;color:#be123c;text-align:right;font-size:14px;">%s</td></tr>
                      <tr><td style="padding:10px 6px;border-bottom:1px solid #f1f5f9;color:#64748b;font-size:13px;">Motivo GestoPago:</td><td style="padding:10px 6px;border-bottom:1px solid #f1f5f9;font-weight:700;font-family:monospace;text-align:right;font-size:14px;">%s</td></tr>
                      <tr><td style="padding:10px 6px;border-bottom:1px solid #f1f5f9;color:#64748b;font-size:13px;">Número GestoPago:</td><td style="padding:10px 6px;border-bottom:1px solid #f1f5f9;font-weight:800;font-family:monospace;text-align:right;font-size:14px;">%s</td></tr>
                      <tr><td style="padding:10px 6px;border-bottom:1px solid #f1f5f9;color:#64748b;font-size:13px;">Folio UPC GestoPago:</td><td style="padding:10px 6px;border-bottom:1px solid #f1f5f9;font-family:monospace;text-align:right;font-size:13px;">%s</td></tr>
                      <tr><td style="padding:10px 6px;border-bottom:1px solid #f1f5f9;color:#64748b;font-size:13px;">Firma AES-256:</td><td style="padding:10px 6px;border-bottom:1px solid #f1f5f9;font-family:monospace;text-align:right;font-size:12px;">%s</td></tr>
                      <tr><td style="padding:10px 6px;border-bottom:1px solid #f1f5f9;color:#64748b;font-size:13px;">Folio de Ticket:</td><td style="padding:10px 6px;border-bottom:1px solid #f1f5f9;font-weight:700;font-family:monospace;color:#1e40af;text-align:right;font-size:13px;">%s</td></tr>
                      <tr><td style="padding:10px 6px;border-bottom:1px solid #f1f5f9;color:#64748b;font-size:13px;">Línea / Referencia:</td><td style="padding:10px 6px;border-bottom:1px solid #f1f5f9;font-family:monospace;text-align:right;font-size:13px;">%s</td></tr>
                      <tr><td style="padding:10px 6px;border-bottom:1px solid #f1f5f9;color:#64748b;font-size:13px;">Fecha y Hora:</td><td style="padding:10px 6px;border-bottom:1px solid #f1f5f9;text-align:right;font-size:13px;">%s</td></tr>
                      <tr><td style="padding:10px 6px;border-bottom:1px solid #f1f5f9;color:#64748b;font-size:13px;">Cuenta Asociada:</td><td style="padding:10px 6px;border-bottom:1px solid #f1f5f9;font-family:monospace;text-align:right;font-size:13px;">%s</td></tr>
                      <tr><td style="padding:10px 6px;color:#64748b;font-size:13px;">Saldo Intacto Disponible:</td><td style="padding:10px 6px;font-weight:800;color:#059669;text-align:right;font-size:14px;">$%s MXN</td></tr>
                    </table>
                    <div style="background:#fffbeb;border-left:4px solid #f59e0b;padding:14px 16px;border-radius:0 8px 8px 0;font-size:12px;color:#78350f;line-height:1.5;">
                      <div style="font-weight:700;margin-bottom:4px;color:#92400e;">&#9888;&#65039; LEYENDA OFICIAL Y ACLARACIONES</div>
                      %s
                    </div>
                  </div>
                </div>
              </div>
            </body>
            </html>
            """.formatted(
                monto.toPlainString(),
                esc(nombreCliente),
                esc(producto),
                esc(resultadoTxt),
                esc(motivo),
                esc(numGestoPago),
                esc(upc),
                esc(firma),
                esc(folio),
                esc(destino),
                esc(fechaFormateada),
                esc(cuentaEnmascarada),
                saldoIntacto.toPlainString(),
                esc(leyenda)
        );
    }
}
