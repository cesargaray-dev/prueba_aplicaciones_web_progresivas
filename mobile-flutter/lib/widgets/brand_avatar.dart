import 'package:flutter/material.dart';
import '../theme/app_theme.dart';

class BrandAvatar extends StatelessWidget {
  final String imageUrl;
  final String label;
  final double size;

  const BrandAvatar({
    super.key,
    required this.imageUrl,
    required this.label,
    this.size = 48,
  });

  String get _initials {
    if (label.isEmpty) return 'BD';
    final parts = label.trim().split(RegExp(r'\s+'));
    if (parts.length >= 2 && parts[0].isNotEmpty && parts[1].isNotEmpty) {
      return '${parts[0][0]}${parts[1][0]}'.toUpperCase();
    }
    return label.substring(0, label.length >= 2 ? 2 : 1).toUpperCase();
  }

  Widget _buildFallback(bool isDark) {
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: isDark
              ? const [Color(0xFF1E293B), Color(0xFF0F172A)]
              : const [Color(0xFFF8FAFC), Color(0xFFE2E8F0)],
        ),
      ),
      alignment: Alignment.center,
      child: Text(
        _initials,
        style: TextStyle(
          color: isDark ? Colors.white : AppTheme.primaryNavy,
          fontWeight: FontWeight.w800,
          fontSize: size * 0.34,
          letterSpacing: 0.4,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final trimmedUrl = imageUrl.trim();
    final isDark = AppTheme.isDark(context);
    final pad = (size * 0.12).clamp(4.0, 8.0);

    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        gradient: trimmedUrl.isEmpty
            ? null
            : const LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [Color(0xFFFFFFFF), Color(0xFFE2E8F0)],
              ),
        borderRadius: BorderRadius.circular(size * 0.26),
        border: Border.all(
          color: isDark
              ? (trimmedUrl.isEmpty
                  ? Colors.white.withValues(alpha: 0.22)
                  : const Color(0xFFE2E8F0))
              : AppTheme.borderSlate,
          width: isDark && trimmedUrl.isNotEmpty ? 1.2 : 1.0,
        ),
        boxShadow: [
          BoxShadow(
            color: isDark
                ? Colors.black.withValues(alpha: 0.38)
                : Colors.black.withValues(alpha: 0.05),
            blurRadius: isDark ? 8 : 4,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      clipBehavior: Clip.antiAlias,
      child: trimmedUrl.isEmpty
          ? _buildFallback(isDark)
          : Image.network(
              trimmedUrl,
              fit: BoxFit.contain,
              frameBuilder: (context, child, frame, wasSynchronouslyLoaded) {
                return Padding(
                  padding: EdgeInsets.all(pad),
                  child: child,
                );
              },
              errorBuilder: (context, error, stackTrace) => _buildFallback(isDark),
              loadingBuilder: (context, child, loadingProgress) {
                if (loadingProgress == null) return child;
                return Center(
                  child: SizedBox(
                    width: size * 0.4,
                    height: size * 0.4,
                    child: const CircularProgressIndicator(
                      strokeWidth: 2,
                      color: AppTheme.secondaryBlue,
                    ),
                  ),
                );
              },
            ),
    );
  }
}
