package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.clientes.Cliente;
import com.proyecto.servicios.entity.clientes.Cuenta;
import com.proyecto.servicios.entity.gestopago.PagoServicio;
import com.proyecto.servicios.exception.AutenticacionException;
import com.proyecto.servicios.exception.ClienteNoEncontradoException;
import com.proyecto.servicios.exception.ValidacionException;
import com.proyecto.servicios.model.brevo.EmailEnvioResultadoDTO;
import com.proyecto.servicios.model.gestopago.GenerarCodigo2FAResponseDTO;
import com.proyecto.servicios.model.gestopago.PagoServicioRequestDTO;
import com.proyecto.servicios.model.gestopago.PagoServicioResponseDTO;
import com.proyecto.servicios.model.gestopago.ProductDTO;
import com.proyecto.servicios.model.gestopago.ProductListResponse;
import com.proyecto.servicios.model.gestopago.ReenviarComprobanteRequestDTO;
import com.proyecto.servicios.repositorys.clientes.CuentaRepository;
import com.proyecto.servicios.repositorys.gestopago.PagoServicioRepository;
import com.proyecto.servicios.service.ComprobanteEmailService;
import com.proyecto.servicios.service.GestoPagoProductService;
import com.proyecto.servicios.service.NotificacionPagoService;
import com.proyecto.servicios.util.TelefonoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Implementación del servicio coordinador de notificaciones 2FA, validaciones
 * previas de pago y reenvío de comprobantes digitales.
 */
@Service
@Slf4j
public class NotificacionPagoServiceImpl implements NotificacionPagoService {

    private final CuentaRepository cuentaRepository;
    private final PagoServicioRepository pagoServicioRepository;
    private final GestoPagoProductService productService;
    private final ComprobanteEmailService comprobanteEmailService;

    @Value("${app.security.expose-otp-in-response:false}")
    private boolean exposeOtpInResponse = false;

    public NotificacionPagoServiceImpl(
            CuentaRepository cuentaRepository,
            PagoServicioRepository pagoServicioRepository,
            GestoPagoProductService productService,
            ComprobanteEmailService comprobanteEmailService) {
        this.cuentaRepository = cuentaRepository;
        this.pagoServicioRepository = pagoServicioRepository;
        this.productService = productService;
        this.comprobanteEmailService = comprobanteEmailService;
    }

    @Override
    public GenerarCodigo2FAResponseDTO enriquecerYEnviar2FA(GenerarCodigo2FAResponseDTO response) {
        if (response == null) {
            return null;
        }
        String otpPlano = response.getCodigo2FA();
        long vigencia = response.getVigenciaSegundos() != null ? response.getVigenciaSegundos() : 300L;

        Optional<Cuenta> cuentaOpt = response.getNumeroCuenta() != null
                ? cuentaRepository.findByNumeroCuenta(response.getNumeroCuenta())
                : Optional.empty();

        if (cuentaOpt.isPresent() && cuentaOpt.get().getCliente() != null && otpPlano != null) {
            Cuenta cuenta = cuentaOpt.get();
            Cliente cliente = cuenta.getCliente();
            EmailEnvioResultadoDTO emailRes = comprobanteEmailService.enviarCodigo2FA(
                    cliente, cuenta, otpPlano, vigencia);
            if (emailRes != null) {
                response.setCorreoEnviado(emailRes.isEnviado());
                response.setCorreoDestino(emailRes.getCorreoDestino() != null
                        ? TelefonoUtil.enmascararCorreo(emailRes.getCorreoDestino())
                        : null);
                response.setMensajeCorreo(emailRes.getMensaje());
                response.setMessageId(emailRes.getMessageId());
            }
        } else {
            response.setCorreoEnviado(false);
            response.setMensajeCorreo("No se encontró un correo registrado para enviar el código 2FA.");
        }

        if (!exposeOtpInResponse) {
            response.setCodigo2FA(null);
        }
        return response;
    }

    @Override
    public void validarAntesDePagar(PagoServicioRequestDTO request) {
        if (request == null) {
            throw new ValidacionException("La solicitud de pago es obligatoria.");
        }

        ProductDTO producto = buscarProductoEnCatalogo(request.getIdServicio(), request.getIdProducto());
        if (producto == null) {
            return;
        }

        int tipoFront = parseTipoFront(producto.getFrontType());
        if (tipoFront == 4 || tipoFront == 5) {
            throw new ValidacionException(
                    "El servicio seleccionado corresponde únicamente a consulta de saldo informativa y no admite pagos directos.");
        }

        java.math.BigDecimal precioCat = parsePrecio(producto.getPrice());
        boolean esPrecioFijo = tipoFront == 1 && precioCat.compareTo(java.math.BigDecimal.ZERO) > 0;
        if (request.getMonto() != null && !esPrecioFijo
                && request.getMonto().compareTo(new java.math.BigDecimal("10.00")) < 0) {
            throw new ValidacionException(
                    "El importe mínimo para pagos de servicios de monto abierto es de $10.00 MXN.");
        }

        boolean esRecarga = TelefonoUtil.esRecargaTelefonica(
                tipoFront, producto.getCategoryId(), producto.getService());

        if (esRecarga) {
            String ref = request.getReferencia() != null ? request.getReferencia().trim() : "";
            if (!TelefonoUtil.esTelefonoMexicanoValido(ref)) {
                throw new ValidacionException(TelefonoUtil.diagnosticarTelefonoInvalido(ref));
            }
            String tel = request.getTelefono() != null ? request.getTelefono().trim() : "";
            if (!tel.isEmpty() && !TelefonoUtil.esTelefonoMexicanoValido(tel)) {
                throw new ValidacionException(TelefonoUtil.diagnosticarTelefonoInvalido(tel));
            }
            if (tel.isEmpty()) {
                request.setTelefono(ref);
            }
        } else {
            String tel = request.getTelefono() != null ? request.getTelefono().trim() : "";
            if (!tel.isEmpty() && !"1111111111".equals(tel) && !TelefonoUtil.esTelefonoMexicanoValido(tel)) {
                throw new ValidacionException(TelefonoUtil.diagnosticarTelefonoInvalido(tel));
            }
        }
    }

    @Override
    public PagoServicioResponseDTO enriquecerRespuestaPago(PagoServicioResponseDTO response) {
        if (response == null) {
            return null;
        }
        EmailEnvioResultadoDTO resultado = comprobanteEmailService.consumirResultadoPorFolio(
                response.getFolioTicket());
        if (resultado != null) {
            response.setCorreoEnviado(resultado.isEnviado());
            response.setCorreoDestino(resultado.getCorreoDestino());
            response.setMensajeCorreo(resultado.getMensaje());
            response.setBrevoMessageId(resultado.getMessageId());
        }
        return response;
    }

    @Override
    public EmailEnvioResultadoDTO reenviarComprobante(ReenviarComprobanteRequestDTO request) {
        if (request == null) {
            throw new ValidacionException("Los datos de reenvío son obligatorios.");
        }

        PagoServicio pago = pagoServicioRepository.findByFolioTicket(request.getFolioTicket().trim())
                .orElseThrow(() -> new ValidacionException(
                        "No se encontró ninguna operación con el folio " + request.getFolioTicket()));

        if (!request.getNumeroCuenta().trim().equals(pago.getNumeroCuenta())) {
            throw new AutenticacionException("El folio solicitado no pertenece a la cuenta autenticada.");
        }

        Cuenta cuenta = cuentaRepository.findByNumeroCuenta(pago.getNumeroCuenta())
                .orElse(pago.getCuenta());
        Cliente cliente = cuenta != null ? cuenta.getCliente() : null;

        String override = request.getCorreoDestino() != null ? request.getCorreoDestino().trim() : null;
        if (override != null && !override.isBlank()) {
            if (!TelefonoUtil.esCorreoSintaxisValida(override) || TelefonoUtil.esCorreoPlaceholder(override)) {
                throw new ValidacionException(
                        "Ingresa un correo electrónico real y válido para recibir el comprobante.");
            }
        } else {
            String correoCliente = cliente != null ? cliente.getCorreo() : null;
            if (correoCliente == null || !TelefonoUtil.esCorreoSintaxisValida(correoCliente)
                    || TelefonoUtil.esCorreoPlaceholder(correoCliente)) {
                throw new ValidacionException(
                        "Tu cuenta no tiene un correo real configurado. Indícalo en el campo o actualízalo en Mi Perfil.");
            }
        }

        return comprobanteEmailService.enviarComprobantePagoConResultado(cuenta, cliente, pago, override);
    }

    @Override
    public PagoServicio obtenerPagoPorFolio(String folioTicket) {
        if (folioTicket == null || folioTicket.isBlank()) {
            throw new ValidacionException("El folio del comprobante es obligatorio.");
        }
        return pagoServicioRepository.findByFolioTicket(folioTicket.trim())
                .orElseThrow(() -> new ClienteNoEncontradoException(
                        "No se encontró ninguna operación con el folio " + folioTicket));
    }

    @Override
    public byte[] generarComprobantePdf(String folioTicket) {
        PagoServicio pago = obtenerPagoPorFolio(folioTicket);
        String folio = sanitizePdfText(pago.getFolioTicket());
        String auth = sanitizePdfText(pago.getNumAutorizacion());
        String servicio = sanitizePdfText(pago.getNombreServicio());
        String producto = sanitizePdfText(pago.getNombreProducto());
        String monto = pago.getMontoPagado() != null ? pago.getMontoPagado().toPlainString() : "0.00";
        String comision = pago.getComision() != null ? pago.getComision().toPlainString() : "0.00";
        String cuentaMask = sanitizePdfText(TelefonoUtil.enmascararCuenta(pago.getNumeroCuenta()));
        String destino = sanitizePdfText(
                (pago.getReferencia() != null && !pago.getReferencia().isBlank())
                        ? pago.getReferencia()
                        : pago.getTelefono());
        String fecha = pago.getFechaOperacion() != null ? pago.getFechaOperacion().toString() : "N/A";

        String streamContent = String.join("\n",
                "BT",
                "/F1 16 Tf",
                "50 760 Td",
                "(BANCO DIGITAL - COMPROBANTE OFICIAL DE PAGO) Tj",
                "/F1 11 Tf",
                "0 -28 Td",
                "(Estado de la Operacion: EXITOSA) Tj",
                "0 -20 Td",
                "(Folio de Ticket: " + folio + ") Tj",
                "0 -18 Td",
                "(Numero de Autorizacion: " + auth + ") Tj",
                "0 -18 Td",
                "(Proveedor / Servicio: " + servicio + ") Tj",
                "0 -18 Td",
                "(Producto: " + producto + ") Tj",
                "0 -18 Td",
                "(Referencia / Linea: " + destino + ") Tj",
                "0 -18 Td",
                "(Monto Pagado: $" + monto + " MXN) Tj",
                "0 -18 Td",
                "(Comision: $" + comision + " MXN) Tj",
                "0 -18 Td",
                "(Cuenta de Cargo: " + cuentaMask + ") Tj",
                "0 -18 Td",
                "(Fecha y Hora: " + fecha + ") Tj",
                "0 -26 Td",
                "(Conserve este comprobante oficial para cualquier aclaracion al 800-226-2600.) Tj",
                "ET");

        String pdf = "%PDF-1.4\n"
                + "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n"
                + "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n"
                + "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj\n"
                + "4 0 obj << /Length " + streamContent.length() + " >>\nstream\n"
                + streamContent + "\nendstream\nendobj\n"
                + "5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n"
                + "trailer << /Root 1 0 R /Size 6 >>\n"
                + "%%EOF\n";
        return pdf.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    private String sanitizePdfText(String value) {
        if (value == null || value.isBlank()) {
            return "N/A";
        }
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private ProductDTO buscarProductoEnCatalogo(Integer idServicio, Integer idProducto) {
        if (idServicio == null) {
            return null;
        }
        try {
            ProductListResponse catalogo = productService.obtenerListaProductos();
            if (catalogo == null || catalogo.getData() == null) {
                return null;
            }
            String servStr = String.valueOf(idServicio);
            if (idProducto != null) {
                String prodStr = String.valueOf(idProducto);
                Optional<ProductDTO> exacto = catalogo.getData().stream()
                        .filter(p -> servStr.equals(p.getServiceId()) && prodStr.equals(p.getId()))
                        .findFirst();
                if (exacto.isPresent()) {
                    return exacto.get();
                }
            }
            return catalogo.getData().stream()
                    .filter(p -> servStr.equals(p.getServiceId()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("No se pudo consultar catálogo para validación previa del pago: {}", e.getMessage());
            return null;
        }
    }

    private int parseTipoFront(String frontType) {
        if (frontType == null || frontType.isBlank()) {
            return 2;
        }
        try {
            return Integer.parseInt(frontType.trim());
        } catch (NumberFormatException e) {
            return 2;
        }
    }

    private java.math.BigDecimal parsePrecio(String rawPrice) {
        if (rawPrice == null || rawPrice.isBlank()) {
            return java.math.BigDecimal.ZERO;
        }
        try {
            return new java.math.BigDecimal(rawPrice.trim());
        } catch (NumberFormatException e) {
            return java.math.BigDecimal.ZERO;
        }
    }
}
