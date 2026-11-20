import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../models/history_item.dart';
import '../providers/auth_provider.dart';
import '../providers/payment_provider.dart';
import '../theme/app_theme.dart';
import '../utils/receipt_downloader.dart';
import '../utils/validation_utils.dart';
import 'brand_avatar.dart';

class HistoryReceiptSheet extends StatefulWidget {
  final HistoryItem item;

  const HistoryReceiptSheet({super.key, required this.item});

  @override
  State<HistoryReceiptSheet> createState() => _HistoryReceiptSheetState();
}

class _HistoryReceiptSheetState extends State<HistoryReceiptSheet> {
  final _emailController = TextEditingController();
  bool _sending = false;
  bool _downloadingPdf = false;
  bool? _sentOk;
  String? _sentMsg;

  @override
  void initState() {
    super.initState();
    final email = Provider.of<AuthProvider>(context, listen: false).session?.email ?? '';
    if (!ValidationUtils.esCorreoPlaceholder(email)) {
      _emailController.text = email;
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
        _sentOk = false;
        _sentMsg = 'Ingresa un correo electrónico real.';
      });
      return;
    }

    setState(() => _sending = true);
    try {
      final res = await Provider.of<PaymentProvider>(context, listen: false).reenviarComprobante(
        numeroCuenta: session.numeroCuenta,
        folioTicket: widget.item.folioTicket,
        correoDestino: target.isNotEmpty ? target : null,
      );
      if (!mounted) return;
      setState(() {
        _sentOk = res.enviado;
        _sentMsg = res.mensaje ?? (res.enviado ? 'Comprobante enviado.' : 'No se pudo enviar.');
        _sending = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _sentOk = false;
        _sentMsg = e.toString();
        _sending = false;
      });
    }
  }

  Future<void> _downloadPdf() async {
    final folio = widget.item.folioTicket.trim();
    if (folio.isEmpty) return;

    setState(() {
      _downloadingPdf = true;
      _sentMsg = null;
    });
    try {
      final bytes = await Provider.of<PaymentProvider>(context, listen: false)
          .downloadReceiptPdf(folio);
      final saved = await savePdfReceipt(bytes, 'comprobante_$folio.pdf');
      if (!mounted) return;
      setState(() {
        _downloadingPdf = false;
        _sentOk = true;
        _sentMsg = 'PDF descargado: $saved';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _downloadingPdf = false;
        _sentOk = false;
        _sentMsg = 'No se pudo descargar el PDF: $e';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final item = widget.item;
    final currencyFormatter = NumberFormat.currency(locale: 'es_MX', symbol: '\$');

    Color statusColor;
    IconData statusIcon;
    if (item.isSuccess) {
      statusColor = AppTheme.accentEmerald;
      statusIcon = Icons.check_circle_rounded;
    } else if (item.isPending) {
      statusColor = AppTheme.warningAmber;
      statusIcon = Icons.schedule_rounded;
    } else {
      statusColor = AppTheme.errorRed;
      statusIcon = Icons.cancel_rounded;
    }

    return Align(
      alignment: Alignment.bottomCenter,
      heightFactor: 1.0,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 520),
        child: Container(
          decoration: const BoxDecoration(
            color: AppTheme.surfaceWhite,
            borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          ),
          padding: EdgeInsets.fromLTRB(
            24,
            16,
            24,
            MediaQuery.of(context).viewInsets.bottom + 24,
          ),
          child: SingleChildScrollView(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Center(
                  child: Container(
                    width: 40,
                    height: 4,
                    decoration: BoxDecoration(
                      color: AppTheme.borderSlate,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                const SizedBox(height: 18),

                Row(
                  children: [
                    BrandAvatar(imageUrl: item.logoUrl, label: item.servicio, size: 52),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            item.servicio,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 17,
                              fontWeight: FontWeight.bold,
                              color: AppTheme.textDark,
                            ),
                          ),
                          Text(
                            item.producto,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(fontSize: 13, color: AppTheme.textMuted),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                    Flexible(
                      flex: 0,
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                        decoration: BoxDecoration(
                          color: statusColor.withValues(alpha: 0.12),
                          borderRadius: BorderRadius.circular(16),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(statusIcon, color: statusColor, size: 15),
                            const SizedBox(width: 4),
                            Text(
                              item.estado,
                              style: TextStyle(
                                color: statusColor,
                                fontSize: 11,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),

                const SizedBox(height: 20),

                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: AppTheme.backgroundSlate,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: AppTheme.borderSlate),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text(
                        'Monto Pagado',
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                          color: AppTheme.textMuted,
                        ),
                      ),
                      Flexible(
                        child: Text(
                          '${currencyFormatter.format(item.monto)} MXN',
                          textAlign: TextAlign.end,
                          style: const TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                            color: AppTheme.primaryNavy,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 18),

                _buildRow('Folio Ticket', item.folioTicket, isMonospace: true),
                const Divider(height: 16, color: AppTheme.borderSlate),
                _buildRow('No. Autorización', item.numeroAutorizacion, isMonospace: true),
                const Divider(height: 16, color: AppTheme.borderSlate),
                if (item.isRecarga) ...[
                  _buildRow('Teléfono Recargado', item.telefono),
                  const Divider(height: 16, color: AppTheme.borderSlate),
                ] else if (item.referencia.isNotEmpty) ...[
                  _buildRow('Referencia de Pago', item.referencia),
                  const Divider(height: 16, color: AppTheme.borderSlate),
                ],
                _buildRow('Saldo Anterior', currencyFormatter.format(item.saldoAnterior)),
                const Divider(height: 16, color: AppTheme.borderSlate),
                _buildRow('Saldo Posterior', currencyFormatter.format(item.saldoRestante), isBold: true),
                const Divider(height: 16, color: AppTheme.borderSlate),
                _buildRow('Fecha de Operación', item.fechaOperacion),
                const Divider(height: 16, color: AppTheme.borderSlate),
                _buildRow('Concepto', item.concepto),

                const SizedBox(height: 18),

                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppTheme.backgroundSlate,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: AppTheme.borderSlate),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const Text(
                        'Enviar comprobante oficial por correo (Brevo)',
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                          color: AppTheme.primaryNavy,
                        ),
                      ),
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
                            onPressed: _sending ? null : _reenviar,
                            child: _sending
                                ? const SizedBox(
                                    width: 14,
                                    height: 14,
                                    child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                                  )
                                : const Text('Enviar'),
                          ),
                        ],
                      ),
                      if (_sentMsg != null) ...[
                        const SizedBox(height: 6),
                        Text(
                          _sentMsg!,
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                            color: _sentOk == true ? AppTheme.accentEmerald : AppTheme.errorRed,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),

                const SizedBox(height: 14),

                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: () {
                          final text = 'Comprobante ${item.servicio}\n'
                              'Folio: ${item.folioTicket}\n'
                              'Autorización: ${item.numeroAutorizacion}\n'
                              'Monto: ${currencyFormatter.format(item.monto)}';
                          Clipboard.setData(ClipboardData(text: text));
                          setState(() {
                            _sentOk = true;
                            _sentMsg = 'Folio copiado al portapapeles.';
                          });
                        },
                        icon: const Icon(Icons.copy_rounded, size: 18),
                        label: const Text('Copiar Folio'),
                      ),
                    ),
                    if (item.isSuccess && item.folioTicket.isNotEmpty) ...[
                      const SizedBox(width: 10),
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: _downloadingPdf ? null : _downloadPdf,
                          icon: _downloadingPdf
                              ? const SizedBox(
                                  width: 16,
                                  height: 16,
                                  child: CircularProgressIndicator(strokeWidth: 2),
                                )
                              : const Icon(Icons.picture_as_pdf_rounded, size: 18),
                          label: const Text('Descargar PDF'),
                        ),
                      ),
                    ],
                  ],
                ),

                const SizedBox(height: 12),

                ElevatedButton(
                  onPressed: () => Navigator.of(context).pop(),
                  style: ElevatedButton.styleFrom(backgroundColor: AppTheme.primaryNavy),
                  child: const Text('Cerrar Comprobante'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildRow(String label, String value, {bool isBold = false, bool isMonospace = false}) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: const TextStyle(fontSize: 13, color: AppTheme.textMuted)),
        const SizedBox(width: 12),
        Flexible(
          child: Text(
            value,
            textAlign: TextAlign.end,
            style: TextStyle(
              fontSize: 13,
              fontWeight: isBold ? FontWeight.bold : FontWeight.w600,
              color: AppTheme.textDark,
              fontFamily: isMonospace ? 'monospace' : null,
            ),
          ),
        ),
      ],
    );
  }
}
