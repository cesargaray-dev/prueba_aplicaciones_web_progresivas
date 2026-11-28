import 'package:flutter/material.dart';
import '../theme/app_theme.dart';

class OfficialLegendUtils {
  OfficialLegendUtils._();

  static String resolve({
    String? leyenda,
    required bool isRejected,
    required String numeroAutorizacion,
    required String folioTicket,
  }) {
    final trimmed = leyenda?.trim() ?? '';
    if (trimmed.isNotEmpty) {
      return trimmed;
    }
    final numAuth = numeroAutorizacion.trim().isNotEmpty
        ? numeroAutorizacion.trim()
        : (isRejected ? '-1' : 'N/A');
    final folio = folioTicket.trim().isNotEmpty ? folioTicket.trim() : 'SIN-FOLIO';

    if (isRejected) {
      return 'Operación no aplicada: no se realizó ningún cargo a su cuenta. '
          'Conserve este comprobante digital como constancia del intento de operación. '
          'Para cualquier duda o aclaración comuníquese a nuestra Línea de Aclaraciones al '
          '800-226-2600 (sin costo, las 24 horas) o envíe un correo a aclaraciones@bancodigital.com '
          'mencionando su Folio de Ticket: $folio.';
    }

    return 'Conserve este comprobante digital como constancia oficial de pago. '
        'Para cualquier duda, aclaración, reporte de saldo o reclamación relacionada con esta '
        'transacción o la aplicación del servicio por parte del proveedor, comuníquese a nuestra '
        'Línea de Aclaraciones al 800-226-2600 (sin costo, las 24 horas) o envíe un correo a '
        'aclaraciones@bancodigital.com dentro de los 30 días naturales posteriores a la fecha de '
        'operación. Favor de mencionar el Número de Autorización: $numAuth y su Folio de Ticket: $folio.';
  }
}

class OfficialLegendCard extends StatelessWidget {
  final String leyenda;

  const OfficialLegendCard({
    super.key,
    required this.leyenda,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = AppTheme.isDark(context);
    final bgColor = isDark
        ? const Color(0xFF451A03).withValues(alpha: 0.42)
        : const Color(0xFFFFFBEB);
    final borderColor = isDark
        ? const Color(0xFFF59E0B).withValues(alpha: 0.5)
        : const Color(0xFFFCD34D);
    final headerColor = isDark ? const Color(0xFFFDE68A) : const Color(0xFF92400E);
    final bodyColor = isDark ? const Color(0xFFFEF3C7) : const Color(0xFF78350F);

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: borderColor),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.gavel_rounded, size: 16, color: headerColor),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  'LEYENDA OFICIAL Y ACLARACIONES',
                  style: TextStyle(
                    fontSize: 10.5,
                    fontWeight: FontWeight.w900,
                    letterSpacing: 0.5,
                    color: headerColor,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            leyenda,
            style: TextStyle(
              fontSize: 11.5,
              height: 1.4,
              color: bodyColor,
            ),
          ),
        ],
      ),
    );
  }
}
