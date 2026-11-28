import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'providers/auth_provider.dart';
import 'providers/catalog_provider.dart';
import 'providers/history_provider.dart';
import 'providers/payment_provider.dart';
import 'screens/home_screen.dart';
import 'screens/login_screen.dart';
import 'theme/app_theme.dart';

final GlobalKey<NavigatorState> appNavigatorKey = GlobalKey<NavigatorState>();

class _AppScrollBehavior extends MaterialScrollBehavior {
  const _AppScrollBehavior();

  @override
  Set<PointerDeviceKind> get dragDevices => PointerDeviceKind.values.toSet();
}

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AppTheme.loadSavedTheme();
  runApp(const BancoDigitalApp());
}

class BancoDigitalApp extends StatelessWidget {
  const BancoDigitalApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => CatalogProvider()),
        ChangeNotifierProvider(create: (_) => PaymentProvider()),
        ChangeNotifierProvider(create: (_) => HistoryProvider()),
        ChangeNotifierProvider(
          create: (ctx) => AuthProvider(
            onSessionReset: () {
              ctx.read<CatalogProvider>().reset();
              ctx.read<PaymentProvider>().reset();
              ctx.read<HistoryProvider>().reset();
              appNavigatorKey.currentState?.popUntil((route) => route.isFirst);
            },
          ),
        ),
      ],
      child: ValueListenableBuilder<ThemeMode>(
        valueListenable: AppTheme.themeModeNotifier,
        builder: (context, currentThemeMode, _) {
          return MaterialApp(
            navigatorKey: appNavigatorKey,
            title: 'Banco Digital - Pagos y Recargas',
            debugShowCheckedModeBanner: false,
            scrollBehavior: const _AppScrollBehavior(),
            theme: AppTheme.lightTheme,
            darkTheme: AppTheme.darkTheme,
            themeMode: currentThemeMode,
            builder: (context, child) {
              final auth = Provider.of<AuthProvider>(context, listen: false);
              return Listener(
                behavior: HitTestBehavior.translucent,
                onPointerDown: (_) => auth.recordUserActivity(),
                onPointerMove: (_) => auth.recordUserActivity(),
                onPointerSignal: (_) => auth.recordUserActivity(),
                child: child ?? const SizedBox.shrink(),
              );
            },
            home: const AuthGate(),
          );
        },
      ),
    );
  }
}

class AuthGate extends StatefulWidget {
  const AuthGate({super.key});

  @override
  State<AuthGate> createState() => _AuthGateState();
}

class _AuthGateState extends State<AuthGate> {
  AppLifecycleListener? _lifecycleListener;

  @override
  void initState() {
    super.initState();
    _lifecycleListener = AppLifecycleListener(
      onResume: () {
        if (!mounted) return;
        Provider.of<AuthProvider>(context, listen: false).verifySessionNow();
      },
    );
  }

  @override
  void dispose() {
    _lifecycleListener?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final auth = Provider.of<AuthProvider>(context);

    if (auth.isAuthenticated) {
      return const HomeScreen();
    }
    return const LoginScreen();
  }
}
