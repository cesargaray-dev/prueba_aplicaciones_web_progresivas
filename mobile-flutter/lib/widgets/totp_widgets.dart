import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../theme/app_theme.dart';

class TotpUtils {
  TotpUtils._();

  static int secondsRemainingInWindow({int periodSeconds = 30}) {
    final safePeriod = periodSeconds > 0 ? periodSeconds : 30;
    return safePeriod - ((DateTime.now().millisecondsSinceEpoch ~/ 1000) % safePeriod);
  }

  static String formatSecretBlocks(String rawSecret, {String? formattedFromServer}) {
    final fromServer = formattedFromServer?.trim() ?? '';
    if (fromServer.isNotEmpty) return fromServer;
    final clean = rawSecret.replaceAll(RegExp(r'\s+'), '').toUpperCase();
    if (clean.isEmpty) return '';
    final buffer = StringBuffer();
    for (var i = 0; i < clean.length; i++) {
      if (i > 0 && i % 4 == 0) buffer.write(' ');
      buffer.write(clean[i]);
    }
    return buffer.toString();
  }

  static Uint8List? decodeQrPng(String? qrPngBase64) {
    if (qrPngBase64 == null) return null;
    var clean = qrPngBase64.trim();
    if (clean.isEmpty) return null;
    final commaIdx = clean.indexOf(',');
    if (clean.startsWith('data:image') && commaIdx >= 0) {
      clean = clean.substring(commaIdx + 1);
    }
    clean = clean.replaceAll(RegExp(r'\s+'), '');
    try {
      return base64Decode(clean);
    } catch (_) {
      return null;
    }
  }
}

class TotpQrSetupCard extends StatelessWidget {
  final String secret;
  final String? secretFormateado;
  final String? qrPngBase64;
  final String? cuentaLabel;
  final int periodSeconds;
  final int secondsLeft;
  final VoidCallback? onCopied;

  const TotpQrSetupCard({
    super.key,
    required this.secret,
    this.secretFormateado,
    this.qrPngBase64,
    this.cuentaLabel,
    this.periodSeconds = 30,
    required this.secondsLeft,
    this.onCopied,
  });

  @override
  Widget build(BuildContext context) {
    final accent = AppTheme.brandAccent(context);
    final qrBytes = TotpUtils.decodeQrPng(qrPngBase64);
    final displaySecret = TotpUtils.formatSecretBlocks(
      secret,
      formattedFromServer: secretFormateado,
    );
    final safePeriod = periodSeconds > 0 ? periodSeconds : 30;
    final progress = (secondsLeft.clamp(0, safePeriod)) / safePeriod;
    final isEndingSoon = secondsLeft <= 6;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppTheme.elevatedBg(context),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppTheme.borderCol(context)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(7),
                decoration: BoxDecoration(
                  color: accent.withValues(alpha: 0.14),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Icon(Icons.phonelink_lock_rounded, color: accent, size: 19),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Google Authenticator (TOTP ${safePeriod}s)',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w800,
                        color: AppTheme.textPrimary(context),
                      ),
                    ),
                    if (cuentaLabel != null && cuentaLabel!.trim().isNotEmpty)
                      Text(
                        'Banco Digital · ${cuentaLabel!.trim()}',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 11.5,
                          color: AppTheme.textSecondary(context),
                        ),
                      ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Center(
            child: Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: const Color(0xFFE2E8F0)),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.08),
                    blurRadius: 12,
                    offset: const Offset(0, 4),
                  ),
                ],
              ),
              child: qrBytes != null
                  ? Image.memory(
                      qrBytes,
                      width: 180,
                      height: 180,
                      gaplessPlayback: true,
                      filterQuality: FilterQuality.none,
                      fit: BoxFit.contain,
                    )
                  : const SizedBox(
                      width: 180,
                      height: 180,
                      child: Center(
                        child: Icon(
                          Icons.qr_code_2_rounded,
                          size: 110,
                          color: Color(0xFF0F172A),
                        ),
                      ),
                    ),
            ),
          ),
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            decoration: BoxDecoration(
              color: AppTheme.cardBg(context),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: AppTheme.borderCol(context)),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'CLAVE DE CONFIGURACIÓN (BASE32)',
                  style: TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 0.6,
                    color: AppTheme.textSecondary(context),
                  ),
                ),
                const SizedBox(height: 6),
                Row(
                  children: [
                    Expanded(
                      child: SelectableText(
                        displaySecret,
                        style: TextStyle(
                          fontFamily: 'monospace',
                          fontSize: 13,
                          fontWeight: FontWeight.w800,
                          letterSpacing: 1.1,
                          color: AppTheme.textPrimary(context),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    OutlinedButton.icon(
                      style: OutlinedButton.styleFrom(
                        visualDensity: VisualDensity.compact,
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                      ),
                      onPressed: () {
                        Clipboard.setData(ClipboardData(text: secret.trim()));
                        if (onCopied != null) {
                          onCopied!();
                        } else {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(
                              content: Text('Clave secreta copiada al portapapeles'),
                              behavior: SnackBarBehavior.floating,
                            ),
                          );
                        }
                      },
                      icon: const Icon(Icons.copy_rounded, size: 15),
                      label: const Text('Copiar clave', style: TextStyle(fontSize: 11.5)),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: accent.withValues(alpha: 0.08),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: accent.withValues(alpha: 0.25)),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Instrucciones paso a paso:',
                  style: TextStyle(
                    fontSize: 11.5,
                    fontWeight: FontWeight.w800,
                    color: accent,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  '1. Abre Google Authenticator en tu teléfono y toca el botón "+".\n'
                  '2. Escanea el código QR o elige "Ingresar clave de configuración" y pega la clave.\n'
                  '3. Ingresa abajo el código dinámico de 6 dígitos (cambia cada ${safePeriod}s).',
                  style: TextStyle(
                    fontSize: 11.5,
                    height: 1.38,
                    color: AppTheme.textPrimary(context),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 10),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Flexible(
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      Icons.timer_outlined,
                      size: 15,
                      color: isEndingSoon ? AppTheme.warningAmber : AppTheme.accentEmerald,
                    ),
                    const SizedBox(width: 5),
                    Flexible(
                      child: Text(
                        'Código vigente · se renueva en ${secondsLeft}s',
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 11.5,
                          fontWeight: FontWeight.w700,
                          color: isEndingSoon ? AppTheme.warningAmber : AppTheme.accentEmerald,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              Text(
                '${secondsLeft}s / ${safePeriod}s',
                style: TextStyle(
                  fontSize: 11,
                  fontFamily: 'monospace',
                  fontWeight: FontWeight.w800,
                  color: AppTheme.textSecondary(context),
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: LinearProgressIndicator(
              value: progress,
              minHeight: 5,
              backgroundColor: AppTheme.borderCol(context),
              color: isEndingSoon ? AppTheme.warningAmber : AppTheme.accentEmerald,
            ),
          ),
        ],
      ),
    );
  }
}
