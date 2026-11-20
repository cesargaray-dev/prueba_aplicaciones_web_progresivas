import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../models/payment_response.dart';
import '../models/product_item.dart';
import '../providers/auth_provider.dart';
import '../providers/payment_provider.dart';
import '../theme/app_theme.dart';
import '../utils/receipt_downloader.dart';
import '../utils/validation_utils.dart';

class ReceiptDialog extends StatefulWidget {
  final ProductItem product;
  final PaymentResponse receipt;
  final String referenciaORPhone;
  final VoidCallback? onViewHistory;

  const ReceiptDialog({
    super.key,
    required this.product,
    required this.receipt,
    required this.referenciaORPhone,
    this.onViewHistory,
  });

  @override
  State<ReceiptDialog> createState() => _ReceiptDialogState();
}

class _ReceiptDialogState extends State<ReceiptDialog> {
  final _emailController = TextEditingController();
  bool _showResend = false;
  bool _isResending = false;
  bool _isDownloadingPdf = false;
  bool? _correoEnviado;
  String? _mensajeCorreo;

  @override
  void initState() {
    super.initState();
    _correoEnviado = widget.receipt.correoEnviado;
    _mensajeCorreo = widget.receipt.mensajeCorreo;
    final userEmail = Provider.of<AuthProvider>(context, listen: false).session?.email ?? '';
    if (!ValidationUtils.esCorreoPlaceholder(userEmail)) {
      _emailController.text = userEmail;
    }
  }

  @override
  void dispose() {
    _emailController.dispose();
    super.dispose();
  }

  Future<void> _reenviar() async {
    final session = Provider.of<AuthProvider>(context, listen: false).session;
    if (session == null) return;
    final target = _emailController.text.trim();
    if (target.isNotEmpty &&
        (!ValidationUtils.esCorreoValido(target) || ValidationUtils.esCorreoPlaceholder(target))) {
      setState(() {
        _correoEnviado = false;
        _mensajeCorreo = 'Ingresa un correo electrónico real para enviar el comprobante.';
      });
      return;
    }

    setState(() => _isResending = true);
    try {
      final res = await Provider.of<PaymentProvider>(context, listen: false).reenviarComprobante(
        numeroCuenta: session.numeroCuenta,
        folioTicket: widget.receipt.folioTicket,
        correoDestino: target.isNotEmpty ? target : null,
      );
      if (!mounted) return;
      setState(() {
        _correoEnviado = res.enviado;
        _mensajeCorreo = res.mensaje ?? (res.enviado ? 'Comprobante reenviado.' : 'No se pudo enviar.');
        _isResending = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _correoEnviado = false;
        _mensajeCorreo = e.toString();
        _isResending = false;
      });
    }
  }

  Future<void> _downloadPdf() async {
    final folio = widget.receipt.folioTicket.trim();
    if (folio.isEmpty) return;

    setState(() => _isDownloadingPdf = true);
    try {
      final bytes = await Provider.of<PaymentProvider>(context, listen: false)
          .downloadReceiptPdf(folio);
      final saved = await savePdfReceipt(bytes, 'comprobante_$folio.pdf');
      if (!mounted) return;
      setState(() => _isDownloadingPdf = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('PDF descargado: $saved'),
          backgroundColor: AppTheme.accentEmerald,
          behavior: SnackBarBehavior.floating,
        ),
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => _isDownloadingPdf = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('No se pudo descargar el PDF: $e'),
          backgroundColor: AppTheme.errorRed,
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final currencyFormatter = NumberFormat.currency(locale: 'es_MX', symbol: '\$');
    final r = widget.receipt;
    final isEmailOk = _correoEnviado == true;

    final Color statusColor;
    final IconData statusIcon;
    final String statusBadgeText;
    final String titleText;
    if (r.isSuccess) {
      statusColor = AppTheme.accentEmerald;
      statusIcon = Icons.check_circle_rounded;
      statusBadgeText = 'EXITOSA';
      titleText = '¡Operación Exitosa!';
    } else if (r.isPending) {
      statusColor = AppTheme.warningAmber;
      statusIcon = Icons.schedule_rounded;
      statusBadgeText = 'PENDIENTE / NO CONFIRMADA';
      titleText = 'Operación Pendiente de Confirmación';
    } else {
      statusColor = AppTheme.errorRed;
      statusIcon = Icons.cancel_rounded;
      statusBadgeText = 'RECHAZADA';
      titleText = 'Operación Rechazada';
    }

    final displayMsg = (r.mensajeOperacion != null && r.mensajeOperacion!.isNotEmpty)
        ? r.mensajeOperacion!
        : r.mensaje;

    return Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      insetPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 460),
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 68,
                height: 68,
                decoration: BoxDecoration(
                  color: statusColor.withValues(alpha: 0.12),
                  shape: BoxShape.circle,
                ),
                child: Icon(statusIcon, color: statusColor, size: 46),
              ),
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: statusColor.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(14),
                ),
                child: Text(
                  statusBadgeText,
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.bold,
                    color: statusColor,
                    letterSpacing: 0.6,
                  ),
                ),
              ),
              const SizedBox(height: 10),
              Text(
                titleText,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.bold,
                  color: AppTheme.textDark,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                displayMsg,
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 13, color: AppTheme.textMuted),
              ),
              const SizedBox(height: 16),

              if (r.isRejected)
                Container(
                  width: double.infinity,
                  margin: const EdgeInsets.only(bottom: 14),
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppTheme.errorRed.withValues(alpha: 0.08),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: AppTheme.errorRed.withValues(alpha: 0.35)),
                  ),
                  child: const Row(
                    children: [
                      Icon(Icons.shield_outlined, color: AppTheme.errorRed, size: 18),
                      SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'No se realizó ningún cargo a tu cuenta.',
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: AppTheme.errorRed,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),

              if (r.isPending)
                Container(
                  width: double.infinity,
                  margin: const EdgeInsets.only(bottom: 14),
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppTheme.warningAmber.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: AppTheme.warningAmber.withValues(alpha: 0.4)),
                  ),
                  child: const Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Icon(Icons.info_outline_rounded, color: AppTheme.warningAmber, size: 18),
                      SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'No reintentes el pago inmediatamente. Verifica el estado final en Historial de Pagos.',
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: Color(0xFF92400E),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),

              if (r.isSuccess) ...[
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 20),
                  decoration: BoxDecoration(
                    color: AppTheme.backgroundSlate,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: AppTheme.borderSlate),
                  ),
                  child: Column(
                    children: [
                      Text(
                        '${widget.product.service} - ${widget.product.name}',
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          fontWeight: FontWeight.w600,
                          color: AppTheme.textMuted,
                          fontSize: 13,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        '${currencyFormatter.format(r.montoPagado)} MXN',
                        style: const TextStyle(
                          fontSize: 28,
                          fontWeight: FontWeight.bold,
                          color: AppTheme.primaryNavy,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 18),
              ] else ...[
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
                  decoration: BoxDecoration(
                    color: AppTheme.backgroundSlate,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: AppTheme.borderSlate),
                  ),
                  child: Text(
                    '${widget.product.service} - ${widget.product.name}',
                    textAlign: TextAlign.center,
                    style: const TextStyle(
                      fontWeight: FontWeight.w600,
                      color: AppTheme.textDark,
                      fontSize: 13,
                    ),
                  ),
                ),
                const SizedBox(height: 14),
              ],

              if (r.isSuccess && (r.idTransaccion != null || r.numeroAutorizacion.isNotEmpty)) ...[
                Container(
                  width: double.infinity,
                  margin: const EdgeInsets.only(bottom: 14),
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    gradient: const LinearGradient(
                      colors: [Color(0xFF040914), Color(0xFF0C1E4A), Color(0xFF1E3A8A)],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(
                      color: const Color(0xFFFDE68A).withValues(alpha: 0.4),
                    ),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text(
                            '✓ COMPRA VERIFICADA GESTOPAGO',
                            style: TextStyle(
                              fontSize: 10,
                              fontWeight: FontWeight.w900,
                              color: Color(0xFFFDE68A),
                              letterSpacing: 0.9,
                            ),
                          ),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                            decoration: BoxDecoration(
                              color: const Color(0xFF10B981).withValues(alpha: 0.2),
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Text(
                              'COD ${r.codigoProveedor ?? '01'}',
                              style: const TextStyle(
                                fontSize: 10,
                                fontWeight: FontWeight.bold,
                                fontFamily: 'monospace',
                                color: Color(0xFF6EE7B7),
                              ),
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      if (r.idTransaccion != null)
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            const Text(
                              'ID TX GestoPago:',
                              style: TextStyle(fontSize: 12, color: Colors.white70),
                            ),
                            Text(
                              '#${r.idTransaccion}',
                              style: const TextStyle(
                                fontSize: 14,
                                fontWeight: FontWeight.w900,
                                fontFamily: 'monospace',
                                color: Color(0xFFFDE68A),
                              ),
                            ),
                          ],
                        ),
                      if (r.numeroAutorizacion.isNotEmpty) ...[
                        const SizedBox(height: 6),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            const Text(
                              'No. Autorización:',
                              style: TextStyle(fontSize: 12, color: Colors.white70),
                            ),
                            Flexible(
                              child: Text(
                                r.numeroAutorizacion,
                                textAlign: TextAlign.end,
                                style: const TextStyle(
                                  fontSize: 14,
                                  fontWeight: FontWeight.w900,
                                  fontFamily: 'monospace',
                                  color: Colors.white,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ],
                      if (r.upc != null && r.upc!.isNotEmpty) ...[
                        const SizedBox(height: 6),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            const Text(
                              'Folio UPC:',
                              style: TextStyle(fontSize: 11.5, color: Colors.white70),
                            ),
                            Flexible(
                              child: Text(
                                r.upc!,
                                textAlign: TextAlign.end,
                                style: const TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w700,
                                  fontFamily: 'monospace',
                                  color: Color(0xFF93C5FD),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ],
                  ),
                ),
              ],

              if (r.idTransaccion != null) ...[
                _buildDetailRow('ID TX GestoPago', '#${r.idTransaccion}', isMonospace: true, isBold: true),
                Divider(height: 14, color: AppTheme.borderCol(context)),
              ],
              if (r.folioTicket.isNotEmpty) ...[
                _buildDetailRow('Folio Ticket', r.folioTicket, isMonospace: true),
                Divider(height: 14, color: AppTheme.borderCol(context)),
              ],
              if (r.numeroAutorizacion.isNotEmpty) ...[
                _buildDetailRow('No. Autorización GestoPago', r.numeroAutorizacion, isMonospace: true),
                Divider(height: 14, color: AppTheme.borderCol(context)),
              ],
              if (r.upc != null && r.upc!.isNotEmpty) ...[
                _buildDetailRow('Folio UPC', r.upc!, isMonospace: true),
                const Divider(height: 14, color: AppTheme.borderSlate),
              ],
              if (r.codigoProveedor != null && r.codigoProveedor!.isNotEmpty) ...[
                _buildDetailRow('Código Proveedor', r.codigoProveedor!, isMonospace: true),
                const Divider(height: 14, color: AppTheme.borderSlate),
              ],
              _buildDetailRow(
                widget.product.isRecarga ? 'Teléfono Celular' : 'Referencia',
                widget.referenciaORPhone,
              ),
              if (r.fechaHoraOperacion.isNotEmpty) ...[
                const Divider(height: 14, color: AppTheme.borderSlate),
                _buildDetailRow('Fecha Operación', r.fechaHoraOperacion),
              ],
              if (r.isSuccess) ...[
                const Divider(height: 14, color: AppTheme.borderSlate),
                _buildDetailRow('Comisión Bancaria', currencyFormatter.format(r.comision)),
                const Divider(height: 14, color: AppTheme.borderSlate),
                _buildDetailRow(
                  'Saldo Restante',
                  '${currencyFormatter.format(r.saldoRestanteCuenta)} MXN',
                  isBold: true,
                  valueColor: AppTheme.accentEmerald,
                ),
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: (isEmailOk ? AppTheme.secondaryBlue : AppTheme.warningAmber)
                        .withValues(alpha: 0.08),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                      color: (isEmailOk ? AppTheme.secondaryBlue : AppTheme.warningAmber)
                          .withValues(alpha: 0.35),
                    ),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Row(
                        children: [
                          Icon(
                            isEmailOk ? Icons.mark_email_read_outlined : Icons.mail_outline_rounded,
                            size: 20,
                            color: isEmailOk ? AppTheme.secondaryBlue : const Color(0xFFB45309),
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              _mensajeCorreo ??
                                  (isEmailOk
                                      ? 'Comprobante enviado por correo electrónico vía Brevo.'
                                      : 'No se pudo confirmar la entrega por correo. Puedes reenviarlo aquí.'),
                              style: const TextStyle(fontSize: 12, color: AppTheme.textDark),
                            ),
                          ),
                          TextButton(
                            onPressed: () => setState(() => _showResend = !_showResend),
                            child: Text(_showResend ? 'Ocultar' : 'Reenviar'),
                          ),
                        ],
                      ),
                      if (_showResend) ...[
                        const SizedBox(height: 8),
                        Row(
                          children: [
                            Expanded(
                              child: TextField(
                                controller: _emailController,
                                keyboardType: TextInputType.emailAddress,
                                decoration: const InputDecoration(
                                  isDense: true,
                                  hintText: 'tucorreo@gmail.com',
                                ),
                              ),
                            ),
                            const SizedBox(width: 8),
                            ElevatedButton(
                              onPressed: _isResending ? null : _reenviar,
                              child: _isResending
                                  ? const SizedBox(
                                      width: 14,
                                      height: 14,
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2,
                                        color: Colors.white,
                                      ),
                                    )
                                  : const Text('Enviar'),
                            ),
                          ],
                        ),
                      ],
                    ],
                  ),
                ),
                if (r.folioTicket.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      onPressed: _isDownloadingPdf ? null : _downloadPdf,
                      icon: _isDownloadingPdf
                          ? const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.picture_as_pdf_rounded, size: 18),
                      label: Text(_isDownloadingPdf ? 'Descargando PDF...' : 'Descargar PDF'),
                    ),
                  ),
                ],
              ],

              const SizedBox(height: 18),

              Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: () {
                        final text = r.isRejected
                            ? 'Operación Rechazada - ${widget.product.name}\n'
                                'Código: ${r.codigoProveedor ?? 'N/A'}\n'
                                'Detalle: $displayMsg'
                            : (r.isPending
                                ? 'Operación Pendiente - ${widget.product.name}\n'
                                    'Folio: ${r.folioTicket}\n'
                                    'UPC: ${r.upc ?? 'N/A'}'
                                : 'Comprobante GestoPago - ${widget.product.name}\n'
                                    'ID TX GestoPago: ${r.idTransaccion ?? 'N/A'}\n'
                                    'Autorización GestoPago: ${r.numeroAutorizacion}\n'
                                    'Folio UPC: ${r.upc ?? 'N/A'}\n'
                                    'Folio Ticket: ${r.folioTicket}\n'
                                    'Monto: ${currencyFormatter.format(r.montoPagado)}');
                        Clipboard.setData(ClipboardData(text: text));
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('Detalle copiado al portapapeles'),
                            behavior: SnackBarBehavior.floating,
                          ),
                        );
                      },
                      icon: const Icon(Icons.copy_rounded, size: 18),
                      label: Text(
                        r.isRejected
                            ? 'Copiar Detalle'
                            : (r.isPending ? 'Copiar Folio / UPC' : 'Copiar Folio'),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: ElevatedButton(
                      onPressed: () => Navigator.of(context).pop(),
                      style: ElevatedButton.styleFrom(backgroundColor: AppTheme.primaryNavy),
                      child: Text(
                        r.isRejected ? 'Cerrar' : (r.isPending ? 'Entendido' : 'Finalizar'),
                      ),
                    ),
                  ),
                ],
              ),
              if ((r.isSuccess || r.isPending) && widget.onViewHistory != null) ...[
                const SizedBox(height: 8),
                TextButton.icon(
                  onPressed: () {
                    Navigator.of(context).pop();
                    widget.onViewHistory!();
                  },
                  icon: const Icon(Icons.receipt_long_rounded, size: 17),
                  label: Text(
                    r.isPending ? 'Consultar Historial' : 'Ver en Historial de Pagos',
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildDetailRow(
    String label,
    String value, {
    bool isBold = false,
    bool isMonospace = false,
    Color? valueColor,
  }) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label, style: TextStyle(fontSize: 13, color: AppTheme.textSecondary(context))),
        const SizedBox(width: 12),
        Flexible(
          child: Text(
            value,
            textAlign: TextAlign.end,
            style: TextStyle(
              fontSize: 13,
              fontWeight: isBold ? FontWeight.bold : FontWeight.w600,
              color: valueColor ?? AppTheme.textPrimary(context),
              fontFamily: isMonospace ? 'monospace' : null,
            ),
          ),
        ),
      ],
    );
  }
}
