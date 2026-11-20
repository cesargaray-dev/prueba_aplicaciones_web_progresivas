import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../providers/catalog_provider.dart';
import '../theme/app_theme.dart';

class CategoryChips extends StatelessWidget {
  const CategoryChips({super.key});

  IconData _getCategoryIcon(String category) {
    switch (category) {
      case 'Telefonía y Recargas':
        return Icons.phone_android_rounded;
      case 'Luz y Energía (CFE)':
        return Icons.bolt_rounded;
      case 'Agua Potable':
        return Icons.water_drop_rounded;
      case 'Internet y Televisión':
        return Icons.wifi_rounded;
      case 'Streaming y Pines':
        return Icons.videogame_asset_rounded;
      case 'Financiero y Créditos':
        return Icons.credit_card_rounded;
      case 'TAG y Casetas':
        return Icons.directions_car_rounded;
      case 'Otros Servicios':
        return Icons.category_rounded;
      case 'Todos':
      default:
        return Icons.grid_view_rounded;
    }
  }

  @override
  Widget build(BuildContext context) {
    final catalog = Provider.of<CatalogProvider>(context);
    final categories = catalog.availableCategories;
    final counts = catalog.categoryCounts;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Front Type Selector (All vs Recargas vs Servicios)
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Row(
            children: [
              _buildTypeChip(
                context: context,
                label: 'Todos',
                isSelected: catalog.selectedFrontType == 'ALL',
                onTap: () => catalog.setFrontType('ALL'),
              ),
              const SizedBox(width: 8),
              _buildTypeChip(
                context: context,
                label: 'Recargas',
                isSelected: catalog.selectedFrontType == '1',
                badgeColor: AppTheme.secondaryBlue,
                onTap: () => catalog.setFrontType('1'),
              ),
              const SizedBox(width: 8),
              _buildTypeChip(
                context: context,
                label: 'Pago de Servicios',
                isSelected: catalog.selectedFrontType == '2',
                badgeColor: AppTheme.accentEmerald,
                onTap: () => catalog.setFrontType('2'),
              ),
            ],
          ),
        ),

        const SizedBox(height: 12),

        // Category Chips (Wrap on wide/desktop viewports, horizontal scroll on mobile)
        LayoutBuilder(
          builder: (context, constraints) {
            Widget buildChip(String cat) {
              final isSelected = catalog.selectedCategory == cat;
              final count = counts[cat] ?? 0;
              return ChoiceChip(
                showCheckmark: false,
                avatar: Icon(
                  _getCategoryIcon(cat),
                  size: 16,
                  color: isSelected ? Colors.white : AppTheme.brandAccent(context),
                ),
                label: Text('$cat ($count)'),
                labelStyle: TextStyle(
                  fontSize: 13,
                  fontWeight: isSelected ? FontWeight.bold : FontWeight.w500,
                  color: isSelected ? Colors.white : AppTheme.textPrimary(context),
                ),
                selected: isSelected,
                selectedColor: AppTheme.secondaryBlue,
                backgroundColor: AppTheme.cardBg(context),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(20),
                  side: BorderSide(
                    color: isSelected ? AppTheme.secondaryBlue : AppTheme.borderCol(context),
                    width: 1,
                  ),
                ),
                onSelected: (selected) {
                  if (selected) {
                    catalog.setCategory(cat);
                  }
                },
              );
            }

            if (constraints.maxWidth >= 600) {
              return Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: categories.map(buildChip).toList(),
                ),
              );
            }

            return SizedBox(
              height: 40,
              child: ListView.separated(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                scrollDirection: Axis.horizontal,
                itemCount: categories.length,
                separatorBuilder: (_, _) => const SizedBox(width: 8),
                itemBuilder: (context, index) => buildChip(categories[index]),
              ),
            );
          },
        ),
      ],
    );
  }

  Widget _buildTypeChip({
    required BuildContext context,
    required String label,
    required bool isSelected,
    Color? badgeColor,
    required VoidCallback onTap,
  }) {
    return Expanded(
      child: GestureDetector(
        onTap: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 4),
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: isSelected
                ? (badgeColor ?? AppTheme.secondaryBlue)
                : AppTheme.cardBg(context),
            borderRadius: BorderRadius.circular(10),
            border: Border.all(
              color: isSelected
                  ? (badgeColor ?? AppTheme.secondaryBlue)
                  : AppTheme.borderCol(context),
              width: 1,
            ),
          ),
          child: Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
              fontSize: 12,
              fontWeight: isSelected ? FontWeight.bold : FontWeight.w600,
              color: isSelected ? Colors.white : AppTheme.textSecondary(context),
            ),
          ),
        ),
      ),
    );
  }
}
