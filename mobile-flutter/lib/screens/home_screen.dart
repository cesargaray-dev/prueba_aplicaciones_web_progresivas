import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../models/product_item.dart';
import '../providers/auth_provider.dart';
import '../providers/catalog_provider.dart';
import '../providers/history_provider.dart';
import '../theme/app_theme.dart';
import '../utils/validation_utils.dart';
import '../widgets/banking_card.dart';
import '../widgets/category_chips.dart';
import '../widgets/profile_sheet.dart';
import '../widgets/service_card.dart';
import 'admin_users_screen.dart';
import 'history_screen.dart';
import 'payment_flow_dialog.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  static const List<String> _popularProviders = [
    'Telcel',
    'AT&T',
    'Movistar',
    'CFE',
    'IZZI',
    'Telmex',
    'PASE',
    'Amazon',
  ];

  int _currentTabIndex = 0;
  final _searchController = TextEditingController();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final catalog = Provider.of<CatalogProvider>(context, listen: false);
      catalog.loadProducts();

      final auth = Provider.of<AuthProvider>(context, listen: false);
      final sessionNotice = auth.sessionNotice;
      if (sessionNotice != null) {
        auth.consumeSessionNotice();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Row(
              children: [
                const Icon(Icons.security_rounded, color: Colors.white, size: 18),
                const SizedBox(width: 8),
                Expanded(child: Text(sessionNotice)),
              ],
            ),
            backgroundColor: AppTheme.warningAmber,
            duration: const Duration(seconds: 6),
            behavior: SnackBarBehavior.floating,
          ),
        );
      }
      if (auth.shouldPromptBiometricOnboarding && auth.canUseBiometrics) {
        auth.consumeBiometricOnboardingPrompt();
        _showPostRegistrationBiometricPrompt();
      }
    });
  }

  void _showPostRegistrationBiometricPrompt() {
    final auth = Provider.of<AuthProvider>(context, listen: false);
    showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: AppTheme.secondaryBlue.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(12),
              ),
              child: const Icon(
                Icons.fingerprint_rounded,
                color: AppTheme.primaryNavy,
                size: 24,
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                '¿Activar acceso con ${auth.biometricLabel}?',
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                  color: AppTheme.primaryNavy,
                ),
              ),
            ),
          ],
        ),
        content: const Text(
          '¡Tu cuenta bancaria ha sido creada con éxito!\n\n'
          '¿Deseas agregar tu huella digital ahora para iniciar sesión más fácil y rápido la próxima vez?\n\n'
          'Si eliges "Ahora no", podrás activarla cuando gustes desde Mi Perfil → Seguridad.',
          style: TextStyle(fontSize: 13, color: AppTheme.textDark, height: 1.45),
        ),
        actions: [
          TextButton(
            onPressed: () {
              Navigator.of(ctx).pop();
              if (!mounted) return;
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content: Text(
                    'Puedes activar tu huella digital cuando quieras desde Mi Perfil → Seguridad.',
                  ),
                  behavior: SnackBarBehavior.floating,
                ),
              );
            },
            child: const Text('Ahora no'),
          ),
          ElevatedButton.icon(
            style: ElevatedButton.styleFrom(
              backgroundColor: AppTheme.accentEmerald,
              foregroundColor: Colors.white,
            ),
            icon: const Icon(Icons.fingerprint_rounded, size: 18),
            label: const Text('Sí, agregar huella'),
            onPressed: () async {
              Navigator.of(ctx).pop();
              final err = await auth.enableBiometricForCurrentUser();
              if (!mounted) return;
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(
                  content: Text(
                    err ??
                        '¡${auth.biometricLabel} activada! Ya puedes usarla para iniciar sesión fácilmente.',
                  ),
                  backgroundColor: err == null ? AppTheme.accentEmerald : AppTheme.warningAmber,
                  behavior: SnackBarBehavior.floating,
                ),
              );
            },
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  void _openProfileSheet({int initialTab = 0}) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppTheme.cardBg(context),
      constraints: const BoxConstraints(maxWidth: 520),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (_) => ProfileSheet(initialTab: initialTab),
    );
  }

  void _openPaymentDialog(ProductItem product) {
    PaymentFlowDialog.show(
      context,
      product: product,
      onViewHistory: () {
        setState(() {
          _currentTabIndex = 1;
        });
        final session = Provider.of<AuthProvider>(context, listen: false).session;
        if (session != null) {
          Provider.of<HistoryProvider>(context, listen: false)
              .loadHistory(session.numeroCuenta);
        }
      },
    );
  }

  void _confirmLogout() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Cerrar Sesión'),
        content: const Text('¿Deseas salir de tu cuenta de Banco Digital?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Cancelar'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: AppTheme.errorRed),
            onPressed: () {
              Navigator.of(ctx).pop();
              Provider.of<AuthProvider>(context, listen: false).logout();
            },
            child: const Text('Cerrar Sesión'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final auth = Provider.of<AuthProvider>(context);
    final catalog = Provider.of<CatalogProvider>(context);
    final session = auth.session;
    final isDark = AppTheme.isDark(context);
    final isAdminUser = session != null && session.isAdmin;

    return Listener(
      behavior: HitTestBehavior.translucent,
      onPointerDown: (_) => auth.resetInactivityTimer(),
      child: Scaffold(
        backgroundColor: AppTheme.scaffoldBg(context),
        appBar: AppBar(
          title: Row(
            children: [
              Icon(
                isAdminUser
                    ? Icons.admin_panel_settings_rounded
                    : (_currentTabIndex == 0
                        ? Icons.account_balance_rounded
                        : Icons.receipt_long_rounded),
                color: isAdminUser ? const Color(0xFFFDE68A) : Colors.white,
                size: 22,
              ),
              const SizedBox(width: 8),
              Flexible(
                child: Text(
                  isAdminUser
                      ? 'Panel Administrador'
                      : (_currentTabIndex == 0 ? 'Banco Digital' : 'Historial de Pagos'),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
          actions: [
            IconButton(
              icon: Icon(
                isDark ? Icons.light_mode_rounded : Icons.dark_mode_rounded,
                color: isDark ? const Color(0xFFFDE68A) : Colors.white,
              ),
              tooltip: isDark ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro',
              onPressed: () => AppTheme.toggleTheme(),
            ),
            if (!isAdminUser && _currentTabIndex == 1 && session != null)
              IconButton(
                icon: const Icon(Icons.refresh_rounded),
                tooltip: 'Actualizar historial',
                onPressed: () => Provider.of<HistoryProvider>(context, listen: false)
                    .loadHistory(session.numeroCuenta),
              ),
            if (session != null) ...[
              if (!isAdminUser)
                Padding(
                  padding: const EdgeInsets.only(right: 4),
                  child: InkWell(
                    borderRadius: BorderRadius.circular(16),
                    onTap: _openProfileSheet,
                    child: Tooltip(
                      message: ValidationUtils.esCorreoPlaceholder(session.email)
                          ? 'Configura tu correo real, huella y abona saldo'
                          : 'Mi perfil, huella digital y abonar saldo',
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                        decoration: BoxDecoration(
                          color: Colors.white.withValues(alpha: 0.16),
                          borderRadius: BorderRadius.circular(16),
                          border: ValidationUtils.esCorreoPlaceholder(session.email)
                              ? Border.all(color: Colors.amberAccent, width: 1.2)
                              : null,
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              ValidationUtils.esCorreoPlaceholder(session.email)
                                  ? Icons.warning_amber_rounded
                                  : (auth.biometricEnabledForCurrentUser
                                      ? Icons.fingerprint_rounded
                                      : Icons.account_circle_rounded),
                              size: 15,
                              color: ValidationUtils.esCorreoPlaceholder(session.email)
                                  ? Colors.amberAccent
                                  : Colors.white,
                            ),
                            const SizedBox(width: 5),
                            ConstrainedBox(
                              constraints: const BoxConstraints(maxWidth: 90),
                              child: Text(
                                session.usuario,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w600,
                                  color: Colors.white,
                                ),
                              ),
                            ),
                            const SizedBox(width: 4),
                            const Icon(Icons.tune_rounded, size: 13, color: Colors.white70),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              IconButton(
                icon: const Icon(Icons.logout_rounded),
                tooltip: 'Cerrar sesión',
                onPressed: _confirmLogout,
              ),
            ],
          ],
        ),
        body: isAdminUser
            ? const AdminUsersScreen()
            : (_currentTabIndex == 0
                ? _buildCatalogView(catalog)
                : HistoryScreen(
                    onExploreCatalog: () {
                      setState(() {
                        _currentTabIndex = 0;
                      });
                    },
                  )),
        bottomNavigationBar: isAdminUser
            ? null
            : NavigationBar(
                selectedIndex: _currentTabIndex > 1 ? 0 : _currentTabIndex,
                backgroundColor: AppTheme.cardBg(context),
                indicatorColor:
                    AppTheme.secondaryBlue.withValues(alpha: isDark ? 0.28 : 0.14),
                onDestinationSelected: (index) {
                  setState(() {
                    _currentTabIndex = index;
                  });
                  if (index == 1 && session != null) {
                    Provider.of<HistoryProvider>(context, listen: false)
                        .loadHistory(session.numeroCuenta);
                  }
                },
                destinations: const [
                  NavigationDestination(
                    icon: Icon(Icons.dashboard_outlined),
                    selectedIcon:
                        Icon(Icons.dashboard_rounded, color: AppTheme.secondaryBlue),
                    label: 'Catálogo',
                  ),
                  NavigationDestination(
                    icon: Icon(Icons.receipt_long_outlined),
                    selectedIcon:
                        Icon(Icons.receipt_long_rounded, color: AppTheme.secondaryBlue),
                    label: 'Historial',
                  ),
                ],
              ),
      ),
    );
  }

  Widget _buildCatalogView(CatalogProvider catalog) {
    return RefreshIndicator(
      onRefresh: () async {
        final auth = Provider.of<AuthProvider>(context, listen: false);
        await auth.refreshProfile();
        await catalog.loadProducts();
      },
      color: AppTheme.primaryNavy,
      child: CustomScrollView(
        slivers: [
          // Banking Card Section
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
              child: BankingCard(onFundTap: _openProfileSheet),
            ),
          ),

          // Search Bar with reactive clear icon
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 10),
              child: ValueListenableBuilder<TextEditingValue>(
                valueListenable: _searchController,
                builder: (context, value, _) {
                  return TextField(
                    controller: _searchController,
                    onChanged: (val) => catalog.setSearchQuery(val),
                    decoration: InputDecoration(
                      hintText: 'Buscar proveedor o servicio (ej. Telcel, CFE)...',
                      prefixIcon: const Icon(Icons.search_rounded, color: AppTheme.textMuted),
                      suffixIcon: value.text.isNotEmpty
                          ? IconButton(
                              icon: const Icon(Icons.clear_rounded, color: AppTheme.textMuted),
                              onPressed: () {
                                _searchController.clear();
                                catalog.clearSearch();
                              },
                            )
                          : null,
                    ),
                  );
                },
              ),
            ),
          ),

          // Popular Providers Quick Access (Wrap on wide viewports, scroll on mobile)
          SliverToBoxAdapter(
            child: LayoutBuilder(
              builder: (context, constraints) {
                Widget buildBrandChip(String brand) {
                  final selected =
                      catalog.searchQuery.trim().toLowerCase() == brand.toLowerCase();
                  return ActionChip(
                    visualDensity: VisualDensity.compact,
                    backgroundColor:
                        selected ? AppTheme.secondaryBlue : AppTheme.cardBg(context),
                    side: BorderSide(
                      color:
                          selected ? AppTheme.secondaryBlue : AppTheme.borderCol(context),
                    ),
                    label: Text(
                      brand,
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        color: selected ? Colors.white : AppTheme.textPrimary(context),
                      ),
                    ),
                    onPressed: () {
                      if (selected) {
                        _searchController.clear();
                        catalog.clearSearch();
                      } else {
                        _searchController.text = brand;
                        catalog.setCategory('Todos');
                        catalog.setFrontType('ALL');
                        catalog.setSearchQuery(brand);
                      }
                    },
                  );
                }

                if (constraints.maxWidth >= 600) {
                  return Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Wrap(
                      spacing: 6,
                      runSpacing: 6,
                      children: _popularProviders.map(buildBrandChip).toList(),
                    ),
                  );
                }

                return SizedBox(
                  height: 36,
                  child: ListView.separated(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    scrollDirection: Axis.horizontal,
                    itemCount: _popularProviders.length,
                    separatorBuilder: (_, _) => const SizedBox(width: 6),
                    itemBuilder: (context, index) => buildBrandChip(_popularProviders[index]),
                  ),
                );
              },
            ),
          ),

          const SliverToBoxAdapter(child: SizedBox(height: 10)),

          // Category Chips Section
          const SliverToBoxAdapter(
            child: Padding(
              padding: EdgeInsets.only(bottom: 12),
              child: CategoryChips(),
            ),
          ),

          // Section Title & Counter
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text(
                    'SERVICIOS DISPONIBLES',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                      color: AppTheme.textLight,
                      letterSpacing: 0.8,
                    ),
                  ),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (catalog.searchQuery.trim().isNotEmpty ||
                          catalog.selectedCategory != 'Todos' ||
                          catalog.selectedFrontType != 'ALL') ...[
                        InkWell(
                          borderRadius: BorderRadius.circular(12),
                          onTap: () {
                            _searchController.clear();
                            catalog.clearSearch();
                            catalog.setCategory('Todos');
                            catalog.setFrontType('ALL');
                          },
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                            decoration: BoxDecoration(
                              color: AppTheme.secondaryBlue.withValues(alpha: 0.1),
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: const Text(
                              'Limpiar ✕',
                              style: TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.bold,
                                color: AppTheme.secondaryBlue,
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                      ],
                      Text(
                        '${catalog.filteredProducts.length} productos',
                        style: const TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                          color: AppTheme.textMuted,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),

          // Catalog Products List
          if (catalog.isLoading) ...[
            const SliverFillRemaining(
              child: Center(
                child: CircularProgressIndicator(color: AppTheme.primaryNavy),
              ),
            ),
          ] else if (catalog.errorMessage != null) ...[
            SliverFillRemaining(
              child: Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.cloud_off_rounded, size: 48, color: AppTheme.errorRed),
                      const SizedBox(height: 12),
                      Text(
                        catalog.errorMessage!,
                        textAlign: TextAlign.center,
                        style: const TextStyle(fontSize: 14, color: AppTheme.textDark),
                      ),
                      const SizedBox(height: 16),
                      ElevatedButton(
                        onPressed: () => catalog.loadProducts(),
                        child: const Text('Reintentar'),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ] else if (catalog.filteredProducts.isEmpty) ...[
            SliverFillRemaining(
              child: Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.search_off_rounded, size: 48, color: AppTheme.textLight.withValues(alpha: 0.5)),
                    const SizedBox(height: 12),
                    const Text(
                      'No se encontraron productos coincidentes',
                      style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: AppTheme.textMuted),
                    ),
                    const SizedBox(height: 6),
                    const Text(
                      'Intenta buscar con otro término o categoría.',
                      style: TextStyle(fontSize: 12, color: AppTheme.textLight),
                    ),
                    const SizedBox(height: 12),
                    OutlinedButton(
                      onPressed: () {
                        _searchController.clear();
                        catalog.clearSearch();
                        catalog.setCategory('Todos');
                        catalog.setFrontType('ALL');
                      },
                      child: const Text('Ver todos los productos'),
                    ),
                  ],
                ),
              ),
            ),
          ] else ...[
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
              sliver: SliverLayoutBuilder(
                builder: (context, constraints) {
                  final width = constraints.crossAxisExtent;
                  final products = catalog.filteredProducts;
                  if (width < 600) {
                    return SliverList(
                      delegate: SliverChildBuilderDelegate(
                        (context, index) {
                          final product = products[index];
                          return Padding(
                            padding: const EdgeInsets.only(bottom: 10),
                            child: ServiceCard(
                              product: product,
                              onSelect: () => _openPaymentDialog(product),
                            ),
                          );
                        },
                        childCount: products.length,
                      ),
                    );
                  }
                  final crossAxisCount = width >= 1024
                      ? (width >= 1360 ? 4 : 3)
                      : 2;
                  return SliverGrid(
                    gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                      crossAxisCount: crossAxisCount,
                      mainAxisExtent: 148,
                      mainAxisSpacing: 10,
                      crossAxisSpacing: 10,
                    ),
                    delegate: SliverChildBuilderDelegate(
                      (context, index) {
                        final product = products[index];
                        return ServiceCard(
                          product: product,
                          onSelect: () => _openPaymentDialog(product),
                        );
                      },
                      childCount: products.length,
                    ),
                  );
                },
              ),
            ),
          ],
        ],
      ),
    );
  }
}
