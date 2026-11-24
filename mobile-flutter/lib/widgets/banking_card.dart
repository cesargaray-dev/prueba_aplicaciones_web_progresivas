import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../providers/auth_provider.dart';
import '../theme/app_theme.dart';

class BankingCard extends StatelessWidget {
  final VoidCallback? onFundTap;

  const BankingCard({super.key, this.onFundTap});

  String _formatSeconds(int totalSeconds) {
    final safe = totalSeconds < 0 ? 0 : totalSeconds;
    final minutes = (safe ~/ 60).toString().padLeft(2, '0');
    final seconds = (safe % 60).toString().padLeft(2, '0');
    return '$minutes:$seconds';
  }

  @override
  Widget build(BuildContext context) {
    final auth = Provider.of<AuthProvider>(context);
    final session = auth.session;
    final currencyFormatter = NumberFormat.currency(locale: 'es_MX', symbol: '\$');

    if (session == null) return const SizedBox.shrink();

    return Container(
      width: double.infinity,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(26),
        gradient: const LinearGradient(
          colors: [
            Color(0xFF040914),
            Color(0xFF0C1E4A),
            Color(0xFF1D4ED8),
            Color(0xFF0A1128),
          ],
          stops: [0.0, 0.42, 0.78, 1.0],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        border: Border.all(
          color: Colors.white.withValues(alpha: 0.24),
          width: 1.3,
        ),
        boxShadow: [
          BoxShadow(
            color: const Color(0xFF050D26).withValues(alpha: 0.55),
            blurRadius: 28,
            spreadRadius: 1,
            offset: const Offset(0, 16),
          ),
          BoxShadow(
            color: const Color(0xFF2563EB).withValues(alpha: 0.32),
            blurRadius: 20,
            spreadRadius: -2,
            offset: const Offset(0, 8),
          ),
          BoxShadow(
            color: const Color(0xFFF59E0B).withValues(alpha: 0.12),
            blurRadius: 24,
            spreadRadius: -4,
            offset: const Offset(-6, -4),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(25),
        child: Stack(
          children: [
            // Ambient luxury radial glow - Top Right
            Positioned(
              top: -55,
              right: -45,
              child: IgnorePointer(
                child: Container(
                  width: 190,
                  height: 190,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: RadialGradient(
                      colors: [
                        const Color(0xFF60A5FA).withValues(alpha: 0.28),
                        const Color(0xFF3B82F6).withValues(alpha: 0.08),
                        Colors.transparent,
                      ],
                    ),
                  ),
                ),
              ),
            ),
            // Ambient golden glow - Bottom Left
            Positioned(
              bottom: -65,
              left: -40,
              child: IgnorePointer(
                child: Container(
                  width: 180,
                  height: 180,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: RadialGradient(
                      colors: [
                        const Color(0xFFF59E0B).withValues(alpha: 0.18),
                        const Color(0xFFD97706).withValues(alpha: 0.05),
                        Colors.transparent,
                      ],
                    ),
                  ),
                ),
              ),
            ),
            // Subtle glass diagonal reflection ring
            Positioned(
              top: -90,
              left: 60,
              child: IgnorePointer(
                child: Transform.rotate(
                  angle: -0.45,
                  child: Container(
                    width: 120,
                    height: 360,
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        colors: [
                          Colors.white.withValues(alpha: 0.0),
                          Colors.white.withValues(alpha: 0.06),
                          Colors.white.withValues(alpha: 0.0),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ),

            // Main Card Content
            Padding(
              padding: const EdgeInsets.fromLTRB(22, 20, 22, 20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Header: Brand, Platinum Badge & Session Timer
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Flexible(
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Container(
                              padding: const EdgeInsets.all(7),
                              decoration: BoxDecoration(
                                gradient: LinearGradient(
                                  colors: [
                                    Colors.white.withValues(alpha: 0.24),
                                    Colors.white.withValues(alpha: 0.08),
                                  ],
                                  begin: Alignment.topLeft,
                                  end: Alignment.bottomRight,
                                ),
                                borderRadius: BorderRadius.circular(12),
                                border: Border.all(
                                  color: const Color(0xFFFDE68A).withValues(alpha: 0.45),
                                  width: 1,
                                ),
                                boxShadow: [
                                  BoxShadow(
                                    color: Colors.black.withValues(alpha: 0.25),
                                    blurRadius: 8,
                                    offset: const Offset(0, 3),
                                  ),
                                ],
                              ),
                              child: const Icon(
                                Icons.account_balance_rounded,
                                color: Color(0xFFFDE68A),
                                size: 18,
                              ),
                            ),
                            const SizedBox(width: 10),
                            Flexible(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  const Text(
                                    'BANCO DIGITAL',
                                    overflow: TextOverflow.ellipsis,
                                    style: TextStyle(
                                      color: Colors.white,
                                      fontWeight: FontWeight.w900,
                                      fontSize: 13.5,
                                      letterSpacing: 1.5,
                                    ),
                                  ),
                                  const SizedBox(height: 1),
                                  Row(
                                    mainAxisSize: MainAxisSize.min,
                                    children: [
                                      Container(
                                        width: 5,
                                        height: 5,
                                        decoration: const BoxDecoration(
                                          color: Color(0xFFFBBF24),
                                          shape: BoxShape.circle,
                                        ),
                                      ),
                                      const SizedBox(width: 4),
                                      const Flexible(
                                        child: Text(
                                          'PLATINUM • 2FA VERIFIED',
                                          overflow: TextOverflow.ellipsis,
                                          style: TextStyle(
                                            color: Color(0xFFFDE68A),
                                            fontSize: 9,
                                            fontWeight: FontWeight.w800,
                                            letterSpacing: 1.1,
                                          ),
                                        ),
                                      ),
                                    ],
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
                      Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            Icons.contactless_rounded,
                            color: Colors.white.withValues(alpha: 0.75),
                            size: 22,
                          ),
                          const SizedBox(width: 8),
                          ValueListenableBuilder<int>(
                            valueListenable: auth.sessionSecondsNotifier,
                            builder: (context, secondsLeft, _) {
                              final isLowTime = secondsLeft <= 60;
                              return Container(
                                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                                decoration: BoxDecoration(
                                  color: isLowTime
                                      ? AppTheme.warningAmber.withValues(alpha: 0.92)
                                      : Colors.black.withValues(alpha: 0.28),
                                  borderRadius: BorderRadius.circular(20),
                                  border: Border.all(
                                    color: isLowTime
                                        ? Colors.amberAccent
                                        : Colors.white.withValues(alpha: 0.2),
                                  ),
                                ),
                                child: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    const Icon(
                                      Icons.timer_outlined,
                                      size: 13,
                                      color: Colors.white,
                                    ),
                                    const SizedBox(width: 4),
                                    Text(
                                      _formatSeconds(secondsLeft),
                                      style: const TextStyle(
                                        color: Colors.white,
                                        fontSize: 11.5,
                                        fontWeight: FontWeight.w700,
                                      ),
                                    ),
                                  ],
                                ),
                              );
                            },
                          ),
                        ],
                      ),
                    ],
                  ),

                  const SizedBox(height: 18),

                  // Metallic EMV Chip & Balance Display
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.center,
                    children: [
                      // Realistic Metallic Gold EMV Chip
                      Container(
                        width: 44,
                        height: 32,
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(7),
                          gradient: const LinearGradient(
                            colors: [
                              Color(0xFFFEF08A),
                              Color(0xFFF59E0B),
                              Color(0xFFB45309),
                              Color(0xFFFBBF24),
                            ],
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                          ),
                          border: Border.all(
                            color: const Color(0xFFFEF9C3).withValues(alpha: 0.8),
                            width: 1,
                          ),
                          boxShadow: [
                            BoxShadow(
                              color: Colors.black.withValues(alpha: 0.35),
                              blurRadius: 6,
                              offset: const Offset(0, 3),
                            ),
                          ],
                        ),
                        child: Stack(
                          children: [
                            Center(
                              child: Container(
                                width: 22,
                                height: 18,
                                decoration: BoxDecoration(
                                  borderRadius: BorderRadius.circular(4),
                                  border: Border.all(
                                    color: const Color(0xFF78350F).withValues(alpha: 0.55),
                                    width: 1,
                                  ),
                                ),
                              ),
                            ),
                            Align(
                              alignment: Alignment.centerLeft,
                              child: Container(
                                width: 11,
                                height: 1,
                                color: const Color(0xFF78350F).withValues(alpha: 0.5),
                              ),
                            ),
                            Align(
                              alignment: Alignment.centerRight,
                              child: Container(
                                width: 11,
                                height: 1,
                                color: const Color(0xFF78350F).withValues(alpha: 0.5),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                Container(
                                  width: 7,
                                  height: 7,
                                  decoration: BoxDecoration(
                                    color: const Color(0xFF34D399),
                                    shape: BoxShape.circle,
                                    boxShadow: [
                                      BoxShadow(
                                        color: const Color(0xFF34D399).withValues(alpha: 0.7),
                                        blurRadius: 6,
                                      ),
                                    ],
                                  ),
                                ),
                                const SizedBox(width: 6),
                                Text(
                                  'SALDO DISPONIBLE',
                                  style: TextStyle(
                                    color: Colors.white.withValues(alpha: 0.78),
                                    fontSize: 10.5,
                                    fontWeight: FontWeight.w800,
                                    letterSpacing: 1.2,
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 3),
                            FittedBox(
                              fit: BoxFit.scaleDown,
                              alignment: Alignment.centerLeft,
                              child: Text(
                                '${currencyFormatter.format(session.saldo)} MXN',
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 28,
                                  fontWeight: FontWeight.w900,
                                  letterSpacing: 0.4,
                                  shadows: [
                                    Shadow(
                                      color: Colors.black45,
                                      blurRadius: 10,
                                      offset: Offset(0, 3),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 6),
                      Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (onFundTap != null) ...[
                            FilledButton.tonalIcon(
                              onPressed: onFundTap,
                              style: FilledButton.styleFrom(
                                backgroundColor: Colors.white.withValues(alpha: 0.16),
                                foregroundColor: Colors.white,
                                side: BorderSide(
                                  color: const Color(0xFFFDE68A).withValues(alpha: 0.55),
                                  width: 1,
                                ),
                                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
                                minimumSize: Size.zero,
                                elevation: 3,
                              ),
                              icon: const Icon(
                                Icons.add_circle_outline_rounded,
                                size: 15,
                                color: Color(0xFFFDE68A),
                              ),
                              label: const Text(
                                'Abonar',
                                style: TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w800,
                                  letterSpacing: 0.3,
                                ),
                              ),
                            ),
                            const SizedBox(width: 2),
                          ],
                          IconButton(
                            onPressed: () => auth.refreshProfile(),
                            icon: const Icon(Icons.refresh_rounded, color: Colors.white),
                            tooltip: 'Actualizar saldo',
                          ),
                        ],
                      ),
                    ],
                  ),

                  const SizedBox(height: 16),

                  // Subtle Metallic Divider
                  Container(
                    height: 1,
                    width: double.infinity,
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        colors: [
                          Colors.white.withValues(alpha: 0.05),
                          Colors.white.withValues(alpha: 0.25),
                          const Color(0xFFFDE68A).withValues(alpha: 0.35),
                          Colors.white.withValues(alpha: 0.05),
                        ],
                      ),
                    ),
                  ),

                  const SizedBox(height: 14),

                  // Footer: Account Holder Name, Embossed Number & Payment Network Emblem
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'TITULAR DE LA CUENTA',
                              style: TextStyle(
                                color: Colors.white.withValues(alpha: 0.55),
                                fontSize: 9,
                                fontWeight: FontWeight.w800,
                                letterSpacing: 1.3,
                              ),
                            ),
                            const SizedBox(height: 2),
                            Text(
                              session.nombreCompleto.toUpperCase(),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 14,
                                fontWeight: FontWeight.w800,
                                letterSpacing: 0.8,
                                shadows: [
                                  Shadow(
                                    color: Colors.black54,
                                    blurRadius: 4,
                                    offset: Offset(0, 1),
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(height: 4),
                            Row(
                              children: [
                                Flexible(
                                  child: Text(
                                    session.maskedAccount,
                                    overflow: TextOverflow.ellipsis,
                                    style: const TextStyle(
                                      color: Color(0xFFFDE68A),
                                      fontSize: 13.5,
                                      fontWeight: FontWeight.w700,
                                      letterSpacing: 2.2,
                                      fontFamily: 'monospace',
                                    ),
                                  ),
                                ),
                                const SizedBox(width: 6),
                                InkWell(
                                  borderRadius: BorderRadius.circular(8),
                                  onTap: () {
                                    Clipboard.setData(ClipboardData(text: session.numeroCuenta));
                                    ScaffoldMessenger.of(context).showSnackBar(
                                      const SnackBar(
                                        content: Text('Número de cuenta copiado al portapapeles'),
                                        behavior: SnackBarBehavior.floating,
                                        duration: Duration(seconds: 2),
                                      ),
                                    );
                                  },
                                  child: Container(
                                    padding: const EdgeInsets.all(4),
                                    decoration: BoxDecoration(
                                      color: Colors.white.withValues(alpha: 0.12),
                                      borderRadius: BorderRadius.circular(6),
                                    ),
                                    child: const Icon(
                                      Icons.copy_rounded,
                                      size: 13,
                                      color: Color(0xFFFDE68A),
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            if ((session.curp != null && session.curp!.isNotEmpty) ||
                                (session.rfc != null && session.rfc!.isNotEmpty)) ...[
                              const SizedBox(height: 3),
                              Text(
                                [
                                  if (session.curp != null && session.curp!.isNotEmpty)
                                    'CURP: ${session.curp}',
                                  if (session.rfc != null && session.rfc!.isNotEmpty)
                                    'RFC: ${session.rfc}',
                                ].join(' · '),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(
                                  color: Colors.white.withValues(alpha: 0.72),
                                  fontSize: 10,
                                  fontFamily: 'monospace',
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                      const SizedBox(width: 12),
                      // Dual-Circle Metallic Network Emblem
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          SizedBox(
                            width: 42,
                            height: 26,
                            child: Stack(
                              children: [
                                Positioned(
                                  left: 0,
                                  child: Container(
                                    width: 26,
                                    height: 26,
                                    decoration: BoxDecoration(
                                      shape: BoxShape.circle,
                                      color: const Color(0xFFF59E0B).withValues(alpha: 0.85),
                                      border: Border.all(
                                        color: Colors.white.withValues(alpha: 0.35),
                                        width: 0.8,
                                      ),
                                    ),
                                  ),
                                ),
                                Positioned(
                                  right: 0,
                                  child: Container(
                                    width: 26,
                                    height: 26,
                                    decoration: BoxDecoration(
                                      shape: BoxShape.circle,
                                      color: const Color(0xFF38BDF8).withValues(alpha: 0.78),
                                      border: Border.all(
                                        color: Colors.white.withValues(alpha: 0.35),
                                        width: 0.8,
                                      ),
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(height: 3),
                          Text(
                            'DIGITAL INFINITE',
                            style: TextStyle(
                              color: Colors.white.withValues(alpha: 0.65),
                              fontSize: 8,
                              fontWeight: FontWeight.w800,
                              letterSpacing: 1.0,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
