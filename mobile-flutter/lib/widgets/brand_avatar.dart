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

  Widget _buildFallback() {
    return Container(
      color: AppTheme.primaryNavy.withValues(alpha: 0.08),
      alignment: Alignment.center,
      child: Text(
        _initials,
        style: TextStyle(
          color: AppTheme.primaryNavy,
          fontWeight: FontWeight.bold,
          fontSize: size * 0.35,
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final trimmedUrl = imageUrl.trim();
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(size * 0.25),
        border: Border.all(color: AppTheme.borderSlate, width: 1),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 4,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      clipBehavior: Clip.antiAlias,
      child: trimmedUrl.isEmpty
          ? _buildFallback()
          : Image.network(
              trimmedUrl,
              fit: BoxFit.contain,
              errorBuilder: (context, error, stackTrace) => _buildFallback(),
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
