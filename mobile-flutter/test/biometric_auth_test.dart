import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:mobile_flutter/models/history_item.dart';
import 'package:mobile_flutter/models/product_item.dart';
import 'package:mobile_flutter/models/user_session.dart';
import 'package:mobile_flutter/providers/auth_provider.dart';
import 'package:mobile_flutter/providers/payment_provider.dart';
import 'package:mobile_flutter/screens/admin_users_screen.dart';
import 'package:mobile_flutter/screens/login_screen.dart';
import 'package:mobile_flutter/screens/payment_flow_dialog.dart';
import 'package:mobile_flutter/services/api_client.dart';
import 'package:mobile_flutter/services/auth_service.dart';
import 'package:mobile_flutter/services/biometric_service.dart';
import 'package:mobile_flutter/theme/app_theme.dart';
import 'package:mobile_flutter/widgets/profile_sheet.dart';
import 'package:provider/provider.dart';

class _FakeBiometricService extends BiometricService {
  BiometricAvailability availability;
  BiometricAuthResult authResult;
  StoredBiometricProfile? storedProfile;
  String? storedToken;

  _FakeBiometricService({
    required this.availability,
    this.authResult = const BiometricAuthResult(success: true),
    this.storedProfile,
    this.storedToken,
  });

  @override
  Future<BiometricAvailability> checkAvailability() async => availability;

  @override
  Future<BiometricAuthResult> authenticate({required String localizedReason}) async => authResult;

  @override
  Future<StoredBiometricProfile?> getStoredProfile() async => storedProfile;

  @override
  Future<String?> getStoredToken() async => storedToken;

  @override
  Future<void> saveEnrolledProfile({
    required String usuario,
    required String nombreCompleto,
    required String tokenBiometrico,
    String preferredMethod = 'biometric',
  }) async {
    storedToken = tokenBiometrico;
    storedProfile = StoredBiometricProfile(
      usuario: usuario,
      nombreCompleto: nombreCompleto,
      preferredMethod: preferredMethod,
    );
  }

  @override
  Future<void> setPreferredMethod(String method) async {
    if (storedProfile != null) {
      storedProfile = StoredBiometricProfile(
        usuario: storedProfile!.usuario,
        nombreCompleto: storedProfile!.nombreCompleto,
        preferredMethod: method,
      );
    }
  }

  @override
  Future<void> clearEnrolledProfile() async {
    storedProfile = null;
    storedToken = null;
  }

  @override
  Future<String?> getSavedApiUrl() async => null;

  @override
  Future<void> saveApiUrl(String url) async {}
}

class _FakeAuthService extends AuthService {
  bool biometricRegisteredOnServer = false;
  String? lastRegisteredToken;

  @override
  Future<UserSession> login(String usuario, String contrasena) async {
    return UserSession(
      token: 'jwt-pass-1',
      usuario: usuario,
      clienteId: 11,
      nombreCompleto: 'César Garay',
      email: 'cesar@gmail.com',
      telefonoMovil: '5512345678',
      numeroCuenta: '5915019417993769',
      saldo: 500.0,
    );
  }

  @override
  Future<UserSession> register({
    required String nombre,
    String? segundoNombre,
    required String apellidoPaterno,
    String apellidoMaterno = '',
    String fechaNacimiento = '1995-01-01',
    String sexo = 'HOMBRE',
    required String correo,
    required String telefonoMovil,
    required String usuario,
    required String contrasena,
  }) async {
    return UserSession(
      token: 'jwt-reg-1',
      usuario: usuario,
      clienteId: 25,
      nombre: nombre,
      segundoNombre: segundoNombre ?? '',
      apellidoPaterno: apellidoPaterno,
      apellidoMaterno: apellidoMaterno,
      nombreCompleto: '$nombre $apellidoPaterno $apellidoMaterno',
      email: correo,
      telefonoMovil: telefonoMovil,
      numeroCuenta: '5111222233334444',
      saldo: 200.0,
    );
  }

  @override
  Future<bool> consultarEstadoBiometria({required String usuario}) async =>
      biometricRegisteredOnServer;

  @override
  Future<bool> registrarBiometria({
    required String usuario,
    required String tokenBiometrico,
    String? dispositivo,
  }) async {
    biometricRegisteredOnServer = true;
    lastRegisteredToken = tokenBiometrico;
    return true;
  }

  @override
  Future<bool> desactivarBiometria({required String usuario}) async {
    biometricRegisteredOnServer = false;
    lastRegisteredToken = null;
    return true;
  }

  @override
  Future<UserSession> loginBiometrico({
    required String usuario,
    required String tokenBiometrico,
  }) async {
    return UserSession(
      token: 'jwt-bio-99',
      usuario: usuario,
      clienteId: 11,
      nombre: 'César',
      segundoNombre: 'Enrique',
      apellidoPaterno: 'Garay',
      apellidoMaterno: 'García',
      nombreCompleto: 'César Enrique Garay García',
      email: 'cesar@gmail.com',
      telefonoMovil: '5512345678',
      numeroCuenta: '5915019417993769',
      saldo: 500.0,
    );
  }

  @override
  Future<UserSession> getProfile(String token, String usuario) async {
    return UserSession(
      token: token,
      usuario: usuario,
      clienteId: 11,
      nombre: 'César',
      segundoNombre: 'Enrique',
      apellidoPaterno: 'Garay',
      apellidoMaterno: 'García',
      nombreCompleto: 'César Enrique Garay García',
      email: 'cesar@gmail.com',
      telefonoMovil: '5512345678',
      numeroCuenta: '5915019417993769',
      saldo: 500.0,
    );
  }

  @override
  Future<UserSession> updateContacto({
    required String token,
    required String usuario,
    required String correo,
    required String telefonoMovil,
    String? nombre,
    String? segundoNombre,
    String? apellidoPaterno,
    String? apellidoMaterno,
  }) async {
    final fullName = [
      nombre ?? 'César',
      if (segundoNombre != null && segundoNombre.isNotEmpty) segundoNombre,
      apellidoPaterno ?? 'Garay',
      apellidoMaterno ?? 'García',
    ].join(' ');
    return UserSession(
      token: token,
      usuario: usuario,
      clienteId: 11,
      nombre: nombre ?? 'César',
      segundoNombre: segundoNombre ?? '',
      apellidoPaterno: apellidoPaterno ?? 'Garay',
      apellidoMaterno: apellidoMaterno ?? 'García',
      nombreCompleto: fullName,
      email: correo,
      telefonoMovil: telefonoMovil,
      numeroCuenta: '5915019417993769',
      saldo: 500.0,
    );
  }

  @override
  Future<void> cambiarContrasena({
    required String usuario,
    required String contrasenaActual,
    required String contrasenaNueva,
  }) async {}

  @override
  Future<void> verificarSesionActiva() async {}
}

void main() {
  group('Biometric Fingerprint Auth & Login Choice', () {
    test('permite activar huella, elegir método preferido, autenticar con huella y desactivar', () async {
      final fakeBio = _FakeBiometricService(
        availability: const BiometricAvailability(
          isSupported: true,
          hasEnrolledBiometrics: true,
          hasFingerprint: true,
          label: 'Huella Digital',
        ),
      );
      final fakeAuthSvc = _FakeAuthService();
      final provider = AuthProvider(authService: fakeAuthSvc, biometricService: fakeBio);

      await provider.initBiometrics();
      expect(provider.canUseBiometrics, isTrue);
      expect(provider.hasStoredBiometricCredentials, isFalse);

      // Login with password and enroll biometric automatically
      final loggedIn = await provider.login('cesar_app', 'Venta2026#', enrollBiometricAfter: true);
      expect(loggedIn, isTrue);
      expect(provider.biometricEnabledForCurrentUser, isTrue);
      expect(provider.hasStoredBiometricCredentials, isTrue);
      expect(fakeAuthSvc.lastRegisteredToken?.length, 64);
      expect(provider.preferredLoginMethod, 'biometric');

      // User can switch preference to password and back to biometric
      await provider.setPreferredLoginMethod('password');
      expect(provider.preferredLoginMethod, 'password');
      await provider.setPreferredLoginMethod('biometric');
      expect(provider.preferredLoginMethod, 'biometric');

      // Logout and re-login with fingerprint
      await provider.logout();
      expect(provider.isAuthenticated, isFalse);

      final bioLoginOk = await provider.loginWithBiometrics();
      expect(bioLoginOk, isTrue);
      expect(provider.session?.token, 'jwt-bio-99');

      // Disable biometrics cleanly
      final disableErr = await provider.disableBiometricForCurrentUser();
      expect(disableErr, isNull);
      expect(provider.biometricEnabledForCurrentUser, isFalse);
      expect(provider.hasStoredBiometricCredentials, isFalse);
      expect(provider.preferredLoginMethod, 'password');

      // Handles cancelled fingerprint gracefully
      final cancelledBio = _FakeBiometricService(
        availability: fakeBio.availability,
        authResult: const BiometricAuthResult(
          success: false,
          cancelled: true,
          errorMessage: 'Cancelado',
        ),
        storedProfile: const StoredBiometricProfile(
          usuario: 'cesar_app',
          nombreCompleto: 'César Garay',
          preferredMethod: 'biometric',
        ),
        storedToken: 'b' * 64,
      );
      final p2 = AuthProvider(authService: fakeAuthSvc, biometricService: cancelledBio);
      await p2.initBiometrics();
      expect(await p2.loginWithBiometrics(), isFalse);
      expect(p2.isAuthenticated, isFalse);
    });

    testWidgets('LoginScreen muestra selector Huella Digital vs Contraseña cuando está vinculado',
        (WidgetTester tester) async {
      final fakeBio = _FakeBiometricService(
        availability: const BiometricAvailability(
          isSupported: true,
          hasEnrolledBiometrics: true,
          hasFingerprint: true,
          label: 'Huella Digital',
        ),
        storedProfile: const StoredBiometricProfile(
          usuario: 'e2ecesar1114',
          nombreCompleto: 'César Garay',
          preferredMethod: 'biometric',
        ),
        storedToken: 'a' * 64,
      );
      final auth = AuthProvider(authService: _FakeAuthService(), biometricService: fakeBio);
      await auth.initBiometrics();

      await tester.pumpWidget(
        ChangeNotifierProvider<AuthProvider>.value(
          value: auth,
          child: const MaterialApp(home: LoginScreen()),
        ),
      );
      await tester.pumpAndSettle();

      // Selector Huella Digital vs Contraseña visible
      expect(find.text('Huella Digital'), findsOneWidget);
      expect(find.text('Contraseña'), findsOneWidget);
      expect(find.text('Ingresar con Huella Digital'), findsOneWidget);

      // User chooses Contraseña instead
      await tester.tap(find.text('Contraseña'));
      await tester.pumpAndSettle();
      expect(find.text('Acceder a mi Cuenta'), findsOneWidget);
      expect(find.text('Entrar rápido con Huella Digital'), findsOneWidget);
    });
  });

  group('HistoryItem - Clasificación Recarga vs Servicio con teléfono de perfil', () {
    test('no clasifica un pago de CFE con teléfono de perfil como recarga', () {
      final cfe = HistoryItem.fromJson({
        'idPago': 1,
        'folioTicket': 'FOL-CFE',
        'numeroAutorizacion': 'AUTH-1',
        'idTxGestopago': 10,
        'idServicio': 166,
        'servicio': 'CFE',
        'idProducto': 597,
        'producto': 'Recibo CFE',
        'telefono': '5512345678',
        'referencia': '012345678912',
        'monto': 320.0,
        'comision': 0.0,
        'saldoAnterior': 500.0,
        'saldoRestante': 180.0,
        'concepto': 'Pago CFE',
        'fechaOperacion': '2026-11-19 20:00:00',
        'estado': 'EXITOSA',
      });

      final telcel = HistoryItem.fromJson({
        'idPago': 2,
        'folioTicket': 'FOL-TEL',
        'numeroAutorizacion': 'AUTH-2',
        'idTxGestopago': 11,
        'idServicio': 133,
        'servicio': 'Telcel',
        'idProducto': 403,
        'producto': 'Recarga \$20',
        'telefono': '5512345678',
        'referencia': '5512345678',
        'monto': 20.0,
        'comision': 0.0,
        'saldoAnterior': 180.0,
        'saldoRestante': 160.0,
        'concepto': 'Recarga Telcel',
        'fechaOperacion': '2026-11-19 20:05:00',
        'estado': 'EXITOSA',
      });

      expect(cfe.isRecarga, isFalse);
      expect(cfe.destinoDisplay, '012345678912');
      expect(telcel.isRecarga, isTrue);
      expect(telcel.destinoDisplay, '5512345678');
    });
  });

  group('ProfileSheet - Edición completa de datos personales y contraseña', () {
    test('AuthProvider actualiza nombre, apellidos y contraseña', () async {
      final fakeBio = _FakeBiometricService(
        availability: const BiometricAvailability(
          isSupported: true,
          hasEnrolledBiometrics: true,
          hasFingerprint: true,
          label: 'Huella Digital',
        ),
      );
      final provider = AuthProvider(authService: _FakeAuthService(), biometricService: fakeBio);
      await provider.initBiometrics();
      await provider.login('cesar_app', 'Venta2026#');

      final updated = await provider.updateContacto(
        nombre: 'Roberto',
        segundoNombre: 'Carlos',
        apellidoPaterno: 'Méndez',
        apellidoMaterno: 'Ruiz',
        correo: 'roberto@gmail.com',
        telefonoMovil: '5512345678',
      );
      expect(updated, isTrue);
      expect(provider.session?.nombre, 'Roberto');
      expect(provider.session?.nombreCompleto, 'Roberto Carlos Méndez Ruiz');

      final changed = await provider.cambiarContrasena(
        contrasenaActual: 'Venta2026#',
        contrasenaNueva: 'NuevaPass2026#',
      );
      expect(changed, isTrue);
    });

    testWidgets('ProfileSheet renderiza tabs Mis Datos, Abonar y Seguridad sin overflow',
        (WidgetTester tester) async {
      final fakeBio = _FakeBiometricService(
        availability: const BiometricAvailability(
          isSupported: true,
          hasEnrolledBiometrics: true,
          hasFingerprint: true,
          label: 'Huella Digital',
        ),
      );
      final auth = AuthProvider(authService: _FakeAuthService(), biometricService: fakeBio);
      await auth.initBiometrics();
      await auth.login('cesar_app', 'Venta2026#');

      await tester.pumpWidget(
        ChangeNotifierProvider<AuthProvider>.value(
          value: auth,
          child: const MaterialApp(home: Scaffold(body: ProfileSheet())),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Mis Datos'), findsOneWidget);
      expect(find.text('Abonar'), findsOneWidget);
      expect(find.text('Seguridad'), findsOneWidget);
      expect(find.text('Información Personal'), findsOneWidget);
      expect(find.text('Guardar Cambios de Perfil'), findsOneWidget);

      await tester.tap(find.text('Seguridad'));
      await tester.pumpAndSettle();
      expect(find.text('Cambiar Contraseña'), findsOneWidget);
      expect(find.text('Actualizar Contraseña'), findsOneWidget);

      // Dispose provider before testWidgets verifies pending timers
      await tester.pumpWidget(const SizedBox.shrink());
      auth.dispose();
    });

    test('al registrar cuenta nueva activa shouldPromptBiometricOnboarding para preguntar si desea agregar huella',
        () async {
      final fakeBio = _FakeBiometricService(
        availability: const BiometricAvailability(
          isSupported: true,
          hasEnrolledBiometrics: true,
          hasFingerprint: true,
          label: 'Huella Digital',
        ),
      );
      final provider = AuthProvider(authService: _FakeAuthService(), biometricService: fakeBio);
      await provider.initBiometrics();

      final ok = await provider.register(
        nombre: 'Nuevo',
        apellidoPaterno: 'Usuario',
        apellidoMaterno: 'App',
        fechaNacimiento: '1999-05-10',
        sexo: 'HOMBRE',
        correo: 'nuevo@gmail.com',
        telefonoMovil: '5512345678',
        usuario: 'nuevo_user',
        contrasena: 'Venta2026#',
      );
      expect(ok, isTrue);
      expect(provider.shouldPromptBiometricOnboarding, isTrue);
      expect(provider.biometricEnabledForCurrentUser, isFalse);

      // Si el usuario elige "Ahora no", se consume el aviso y queda desactivada pero disponible en ajustes
      provider.consumeBiometricOnboardingPrompt();
      expect(provider.shouldPromptBiometricOnboarding, isFalse);
      expect(provider.biometricEnabledForCurrentUser, isFalse);

      // Y si decide activarla (en el diálogo o después en Mi Perfil -> Seguridad), queda vinculada
      final err = await provider.enableBiometricForCurrentUser();
      expect(err, isNull);
      expect(provider.biometricEnabledForCurrentUser, isTrue);
      expect(provider.preferredLoginMethod, 'biometric');
      provider.dispose();
    });

    test('UserSession parsea aviso de sesión previa cerrada en otro dispositivo', () {
      final session = UserSession.fromJson({
        'token': 'tok-new',
        'usuario': 'cesar123',
        'clienteId': 11,
        'nombreCompleto': 'César Garay',
        'email': 'cesar@gmail.com',
        'numeroCuenta': '5915019417993769',
        'saldo': 500.0,
        'sesionPreviaCerrada': true,
        'avisoSesion': 'Se cerró automáticamente una sesión activa previa en otro dispositivo por seguridad.',
      });
      expect(session.sesionPreviaCerrada, isTrue);
      expect(session.avisoSesion, contains('otro dispositivo'));
    });

    test('ApiClient muestra etiqueta de servidor y diagnostica Connection reset en Render', () {
      final client = ApiClient();
      try {
        client.setBaseUrl('https://prueba-aplicaciones-web-progresivas.onrender.com');
        expect(client.serverLabel, 'Nube (Render)');
        final wrapped = client.wrapNetworkError(
          Exception('ClientException: Connection reset by peer'),
        );
        expect(wrapped.statusCode, 0);
        expect(wrapped.message, contains('Wi-Fi'));
        expect(wrapped.message, contains('Datos Móviles'));

        client.setBaseUrl('http://10.31.5.23:10000');
        expect(client.serverLabel, 'Wi-Fi (10.31.5.23:10000)');
      } finally {
        client.resetForTest();
      }
    });

    testWidgets(
        'AdminUsersScreen guarda edición sin doble-pop (pantalla negra) y elimina todos los usuarios',
        (WidgetTester tester) async {
      final users = <Map<String, dynamic>>[
        {
          'clienteId': 1,
          'usuario': 'admin',
          'esAdmin': true,
          'nombre': 'ADMIN',
          'segundoNombre': '',
          'apellidoPaterno': 'BANCO',
          'apellidoMaterno': 'DIGITAL',
          'nombreCompleto': 'ADMIN BANCO DIGITAL',
          'correo': 'admin@bancodigital.com.mx',
          'telefonoMovil': '5523456789',
          'numeroCuenta': '4000000000000001',
          'saldo': 10000.0,
          'activo': true,
          'bloqueado': false,
        },
        {
          'clienteId': 2,
          'usuario': 'carlos98',
          'esAdmin': false,
          'nombre': 'CARLOS',
          'segundoNombre': '',
          'apellidoPaterno': 'GARAY',
          'apellidoMaterno': 'LOPEZ',
          'nombreCompleto': 'CARLOS GARAY LOPEZ',
          'correo': 'carlos@gmail.com',
          'telefonoMovil': '5512345678',
          'numeroCuenta': '5225253189113705',
          'saldo': 350.0,
          'activo': true,
          'bloqueado': false,
        },
      ];
      int putCalls = 0;
      bool deleteAllCalled = false;

      final mockHttp = MockClient((req) async {
        if (req.method == 'GET' && req.url.path == '/api/v1/admin/usuarios') {
          return http.Response(jsonEncode(users), 200);
        }
        if (req.method == 'PUT' && req.url.path == '/api/v1/admin/usuarios/2') {
          putCalls++;
          await Future<void>.delayed(const Duration(milliseconds: 80));
          users[1]['nombreCompleto'] = 'CARLOS EDITADO';
          users[1]['saldo'] = 900.0;
          return http.Response(jsonEncode(users[1]), 200);
        }
        if (req.method == 'DELETE' && req.url.path == '/api/v1/admin/usuarios') {
          deleteAllCalled = true;
          users.removeWhere((u) => u['esAdmin'] != true);
          return http.Response(
            jsonEncode({
              'codigo': 0,
              'mensaje': 'Se eliminaron permanentemente 1 usuarios clientes (se conserva la cuenta @admin).',
            }),
            200,
          );
        }
        return http.Response('{}', 404);
      });

      final client = ApiClient.withClient(mockHttp, baseUrl: 'http://localhost:10000');
      client.setAuthToken('admin-jwt');

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(body: AdminUsersScreen(apiClient: client)),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('CARLOS GARAY LOPEZ'), findsOneWidget);

      // Abrir edición del segundo usuario y pulsar Guardar Cambios dos veces seguidas
      await tester.tap(find.text('Editar').last);
      await tester.pumpAndSettle();
      expect(find.text('Editar @carlos98'), findsOneWidget);

      final saveBtn = find.text('Guardar Cambios');
      await tester.tap(saveBtn);
      await tester.tap(saveBtn, warnIfMissed: false);
      await tester.pumpAndSettle();

      // Solo una petición PUT y AdminUsersScreen sigue visible (sin pantalla negra)
      expect(putCalls, 1);
      expect(find.text('GESTIÓN TOTAL DE USUARIOS'), findsOneWidget);
      expect(find.text('CARLOS EDITADO'), findsOneWidget);

      // Botón Eliminar Todos solicita confirmación y borra solo clientes
      await tester.tap(find.text('Eliminar Todos'));
      await tester.pumpAndSettle();
      expect(find.text('¿Eliminar todos los usuarios?'), findsOneWidget);

      await tester.tap(find.text('Sí, Eliminar Todos'));
      await tester.pumpAndSettle();

      expect(deleteAllCalled, isTrue);
      expect(find.text('ADMIN BANCO DIGITAL'), findsOneWidget);
      expect(find.text('CARLOS EDITADO'), findsNothing);
    });

    testWidgets(
        'Modo oscuro mantiene visibles montos en Abonar, botones en PaymentFlowDialog y registro simplificado',
        (WidgetTester tester) async {
      final fakeBio = _FakeBiometricService(
        availability: const BiometricAvailability(
          isSupported: false,
          hasEnrolledBiometrics: false,
          hasFingerprint: false,
          label: 'Huella Digital',
        ),
      );
      final auth = AuthProvider(authService: _FakeAuthService(), biometricService: fakeBio);
      await auth.initBiometrics();
      await auth.login('cesar_app', 'Venta2026#');

      // 1. ProfileSheet Abonar en modo oscuro: los montos ($100, $250, $500, $1000) tienen texto claro sin tocarlos
      await tester.pumpWidget(
        ChangeNotifierProvider<AuthProvider>.value(
          value: auth,
          child: MaterialApp(
            theme: AppTheme.lightTheme,
            darkTheme: AppTheme.darkTheme,
            themeMode: ThemeMode.dark,
            home: const Scaffold(body: ProfileSheet(initialTab: 1)),
          ),
        ),
      );
      await tester.pumpAndSettle();

      final chipText = tester.widget<Text>(find.text('\$100'));
      expect(chipText.style?.color, AppTheme.darkTextPrimary);
      expect(find.text('\$250'), findsOneWidget);
      expect(find.text('\$500'), findsOneWidget);
      expect(find.text('\$1000'), findsOneWidget);

      // 2. PaymentFlowDialog en modo oscuro: fondo oscuro adaptable y botones visibles
      final product = ProductItem(
        id: '403',
        name: 'Recarga \$20',
        service: 'Telcel',
        serviceId: '133',
        price: 20,
        referenceType: 'a',
        frontType: '1',
        hasVerificationDigit: false,
        categoryId: '1',
      );
      final paymentProvider = PaymentProvider()..initForProduct(product);

      await tester.pumpWidget(
        MultiProvider(
          providers: [
            ChangeNotifierProvider<AuthProvider>.value(value: auth),
            ChangeNotifierProvider<PaymentProvider>.value(value: paymentProvider),
          ],
          child: MaterialApp(
            theme: AppTheme.lightTheme,
            darkTheme: AppTheme.darkTheme,
            themeMode: ThemeMode.dark,
            home: Scaffold(
              body: PaymentFlowDialog(product: product, onViewHistory: () {}),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      final dialog = tester.widget<Dialog>(find.byType(Dialog));
      expect(dialog.backgroundColor, AppTheme.darkSurface);
      expect(find.text('Valida la Referencia para Continuar'), findsOneWidget);
      expect(find.text('Usar mi celular (5512345678)'), findsOneWidget);

      // 3. LoginScreen Crear Cuenta no solicita CURP, nacionalidad, fecha de nacimiento ni sexo
      await tester.pumpWidget(
        ChangeNotifierProvider<AuthProvider>.value(
          value: auth,
          child: MaterialApp(
            theme: AppTheme.lightTheme,
            darkTheme: AppTheme.darkTheme,
            themeMode: ThemeMode.dark,
            home: const LoginScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Crear Cuenta'));
      await tester.pumpAndSettle();

      expect(find.text('Nombre *'), findsOneWidget);
      expect(find.text('Apellido(s) *'), findsOneWidget);
      expect(find.text('CURP'), findsNothing);
      expect(find.text('Nacionalidad'), findsNothing);
      expect(find.text('Nacimiento *'), findsNothing);
      expect(find.text('Sexo'), findsNothing);

      await tester.pumpWidget(const SizedBox.shrink());
      auth.dispose();
    });
  });
}
