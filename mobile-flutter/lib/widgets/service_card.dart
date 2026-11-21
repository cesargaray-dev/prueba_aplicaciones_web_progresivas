import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../models/product_item.dart';
import '../theme/app_theme.dart';
import 'brand_avatar.dart';

class ServiceCard extends StatelessWidget {
  final ProductItem product;
  final VoidCallback onSelect;

  const ServiceCard({
    super.key,
    required this.product,
    required this.onSelect,
  });

  @override
  Widget build(BuildContext context) {
    final currencyFormatter = NumberFormat.currency(locale: 'es_MX', symbol: '\$');
    final hasLegend = product.legend != null && product.legend!.isNotEmpty;

    return Card(
      child: InkWell(
        onTap: onSelect,
        borderRadius: BorderRadius.circular(16),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final isNarrow = constraints.maxWidth < 340;
            return Padding(
              padding: EdgeInsets.all(isNarrow ? 10 : 14),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  BrandAvatar(
                    imageUrl: product.logoUrl,
                    label: product.service,
                    size: isNarrow ? 44 : 50,
                  ),

                  SizedBox(width: isNarrow ? 10 : 14),

                  Expanded(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                              decoration: BoxDecoration(
                                color: (product.isRecarga || product.isFront1VoucherOrTag)
                                    ? AppTheme.secondaryBlue.withValues(alpha: 0.12)
                                    : AppTheme.accentEmerald.withValues(alpha: 0.12),
                                borderRadius: BorderRadius.circular(6),
                              ),
                              child: Text(
                                product.isRecarga
                                    ? 'RECARGA'
                                    : (product.isFront1VoucherOrTag ? 'TARJETA / PASE' : 'SERVICIO'),
                                style: TextStyle(
                                  color: product.isRecarga
                                      ? AppTheme.secondaryBlue
                                      : AppTheme.accentEmerald,
                                  fontSize: 10,
                                  fontWeight: FontWeight.bold,
                                  letterSpacing: 0.5,
                                ),
                              ),
                            ),
                            const SizedBox(width: 6),
                            Flexible(
                              child: Text(
                                product.categoryName,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  color: AppTheme.textLight,
                                  fontSize: 11,
                                  fontWeight: FontWeight.w500,
                                ),
                              ),
                            ),
                          ],
                        ),

                        const SizedBox(height: 5),

                        Text(
                          product.service,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 15,
                            fontWeight: FontWeight.bold,
                            color: AppTheme.textPrimary(context),
                          ),
                        ),

                        Text(
                          product.name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(
                            fontSize: 12,
                            color: AppTheme.textSecondary(context),
                          ),
                        ),

                        if (hasLegend) ...[
                          const SizedBox(height: 2),
                          Text(
                            product.legend!,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 11,
                              color: AppTheme.textLight,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),

                  const SizedBox(width: 8),

                  Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Text(
                        product.price > 0
                            ? currencyFormatter.format(product.price)
                            : 'Monto libre',
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.bold,
                          color: product.price > 0
                              ? AppTheme.brandAccent(context)
                              : AppTheme.lightEmerald,
                        ),
                      ),
                      const SizedBox(height: 6),
                      ElevatedButton(
                        onPressed: onSelect,
                        style: ElevatedButton.styleFrom(
                          backgroundColor:
                              product.isRecarga
                                  ? AppTheme.secondaryBlue
                                  : AppTheme.brandAccent(context),
                          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                          minimumSize: Size.zero,
                          tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(8),
                          ),
                        ),
                        child: Text(
                          product.isRecarga ? 'Recargar' : 'Pagar',
                          style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            );
          },
        ),
      ),
    );
  }
}
