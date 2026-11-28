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
import 'package:mobile_flutter/services/payment_service.dart';
import 'package:mobile_flutter/theme/app_theme.dart';
import 'package:mobile_flutter/utils/mexico_catalogs.dart';
import 'package:mobile_flutter/widgets/profile_sheet.dart';
import 'package:mobile_flutter/widgets/receipt_dialog.dart';
import 'package:provider/provider.dart';

http.Response _jsonUtf8(Object data, int status) => http.Response.bytes(
      utf8.encode(jsonEncode(data)),
      status,
      headers: {'content-type': 'application/json; charset=utf-8'},
    );

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
  static const String valid1x1PngBase64 =
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=';

  bool biometricRegisteredOnServer = false;
  String? lastRegisteredToken;
  String? lastRegistrationCode;
  String? lastTotpSecret;
  String? lastCodigoTotp;
  String? lastOcupacion;
  String? lastProfesion;
  String? lastCurp;
  String? lastRfc;
  String? lastColonia;
  double currentBalance = 500.0;
  int sendCodeCalls = 0;
  int iniciarTotpCalls = 0;
  int disponibilidadCalls = 0;
  int keepAliveCalls = 0;
  bool totpPerfilEnabled = false;

  @override
  Future<Map<String, dynamic>> verificarDisponibilidad({
    String? correo,
    String? usuario,
    String? curp,
    String? rfc,
  }) async {
    disponibilidadCalls++;
    final isDupEmail = correo?.trim().toLowerCase() == 'yaexiste@gmail.com';
    final isDupUser = usuario?.trim().toLowerCase() == 'usuario_ocupado';
    return {
      if (correo != null) 'correoDisponible': !isDupEmail,
      if (usuario != null) 'usuarioDisponible': !isDupUser,
      if (curp != null) 'curpDisponible': true,
      if (rfc != null) 'rfcDisponible': true,
      'mensajes': {
        if (isDupEmail)
          'correo':
              'Este correo electrónico ya está en uso en otra cuenta. Inicia sesión o utiliza otro correo.',
        if (isDupUser) 'usuario': 'Este nombre de usuario ya está ocupado.',
      },
    };
  }

  @override
  Future<Map<String, dynamic>> iniciarTotpRegistro({
    required String correo,
    required String usuario,
  }) async {
    iniciarTotpCalls++;
    if (correo.trim().toLowerCase() == 'yaexiste@gmail.com') {
      throw ApiException(
        statusCode: 409,
        message:
            'Este correo electrónico ya está en uso en otra cuenta. Inicia sesión o utiliza otro correo.',
      );
    }
    return {
      'secret': 'JBSWY3DPEHPK3PXP',
      'secretFormateado': 'JBSW Y3DP EHPK 3PXP',
      'otpauthUri':
          'otpauth://totp/Banco%20Digital:${Uri.encodeComponent(correo)}?secret=JBSWY3DPEHPK3PXP&issuer=Banco%20Digital',
      'qrPngBase64': valid1x1PngBase64,
      'emisor': 'Banco Digital',
      'cuenta': correo.trim(),
      'digitos': 6,
      'periodoSegundos': 30,
    };
  }

  @override
  Future<Map<String, dynamic>> consultarEstadoTotpPerfil() async => {
        'totpHabilitado': totpPerfilEnabled,
        'mensaje': totpPerfilEnabled ? 'Activo' : 'Inactivo',
      };

  @override
  Future<Map<String, dynamic>> iniciarTotpPerfil() async => {
        'secret': 'JBSWY3DPEHPK3PXP',
        'secretFormateado': 'JBSW Y3DP EHPK 3PXP',
        'otpauthUri': 'otpauth://totp/Banco%20Digital:cesar@gmail.com?secret=JBSWY3DPEHPK3PXP',
        'qrPngBase64': valid1x1PngBase64,
        'emisor': 'Banco Digital',
        'cuenta': 'cesar@gmail.com',
        'digitos': 6,
        'periodoSegundos': 30,
      };

  @override
  Future<Map<String, dynamic>> activarTotpPerfil({
    required String secret,
    required String codigo,
  }) async {
    totpPerfilEnabled = true;
    return {'totpHabilitado': true, 'mensaje': 'Google Authenticator activado'};
  }

  @override
  Future<Map<String, dynamic>> desactivarTotpPerfil({required String codigo}) async {
    totpPerfilEnabled = false;
    return {'totpHabilitado': false, 'mensaje': 'Google Authenticator desactivado'};
  }

  @override
  Future<Map<String, dynamic>> enviarCodigoRegistro({
    required String correo,
    String? nombre,
    String? usuario,
    String? curp,
    String? rfc,
  }) async {
    sendCodeCalls++;
    return {
      'codigo': 0,
      'mensaje': 'Código enviado',
      'correoEnviado': true,
      'correoDestino': correo,
      'expiraEnSegundos': 300,
    };
  }

  @override
  Future<void> keepAlive() async {
    keepAliveCalls++;
  }

  @override
  Future<UserSession> login(String usuario, String contrasena) async {
    currentBalance = 500.0;
    totpPerfilEnabled = false;
    return UserSession(
      token: 'jwt-pass-1',
      usuario: usuario,
      clienteId: 11,
      nombreCompleto: 'César Garay',
      email: 'cesar@gmail.com',
      telefonoMovil: '5512345678',
      curp: 'GAGC990515HDFRRL01',
      rfc: 'GAGC990515AB1',
      numeroCuenta: '5915019417993769',
      saldo: currentBalance,
      esAdmin: usuario == 'admin',
      totpHabilitado: totpPerfilEnabled,
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
    String? nacionalidad,
    String? estadoCivil,
    String? curp,
    String? rfc,
    String? ocupacion,
    String? profesion,
    String? empresa,
    double? ingresoMensual,
    String? calle,
    String? numeroExterior,
    String? numeroInterior,
    String? colonia,
    String? codigoPostal,
    String? municipio,
    String? estado,
    String? pais,
    required String correo,
    required String telefonoMovil,
    String? telefonoAlternativo,
    required String usuario,
    required String contrasena,
    String? totpSecret,
    String? codigoTotp,
    String? codigoVerificacion,
  }) async {
    lastTotpSecret = totpSecret;
    lastCodigoTotp = codigoTotp;
    lastRegistrationCode = codigoTotp ?? codigoVerificacion;
    lastOcupacion = ocupacion;
    lastProfesion = profesion;
    lastCurp = curp;
    lastRfc = rfc;
    lastColonia = colonia;
    currentBalance = 0.0;
    totpPerfilEnabled = totpSecret != null && totpSecret.isNotEmpty;
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
      curp: curp,
      rfc: rfc,
      ocupacion: ocupacion,
      profesion: profesion,
      numeroCuenta: '5111222233334444',
      saldo: 0.0,
      totpHabilitado: totpPerfilEnabled,
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
      saldo: currentBalance,
      esAdmin: usuario == 'admin',
      totpHabilitado: totpPerfilEnabled,
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
          return _jsonUtf8(users, 200);
        }
        if (req.method == 'PUT' && req.url.path == '/api/v1/admin/usuarios/2') {
          putCalls++;
          await Future<void>.delayed(const Duration(milliseconds: 80));
          users[1]['nombreCompleto'] = 'CARLOS EDITADO';
          users[1]['saldo'] = 900.0;
          return _jsonUtf8(users[1], 200);
        }
        if (req.method == 'DELETE' && req.url.path == '/api/v1/admin/usuarios') {
          deleteAllCalled = true;
          users.removeWhere((u) => u['esAdmin'] != true);
          return _jsonUtf8(
            {
              'codigo': 0,
              'mensaje':
                  'Se eliminaron permanentemente 1 usuarios clientes (se conserva la cuenta @admin).',
            },
            200,
          );
        }
        return _jsonUtf8(<String, dynamic>{}, 404);
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
      await tester.ensureVisible(saveBtn);
      await tester.pumpAndSettle();
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

      // 1a. Cliente normal no puede auto-abonar: ve aviso de Fondeo Autorizado por Administrador ($0.00 inicial)
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
      expect(find.textContaining('Fondeo Autorizado por Administrador'), findsOneWidget);

      // 1b. Admin en modo oscuro: los montos ($100, $250, $500, $1000) tienen texto claro sin tocarlos
      await auth.login('admin', 'Admin12345!');
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

      // 3. LoginScreen Crear Cuenta incluye expediente KYC completo (Identidad, CURP y RFC)
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
      expect(find.text('Apellido paterno *'), findsOneWidget);
      expect(find.text('Apellido materno *'), findsOneWidget);
      expect(find.text('Nacionalidad *'), findsOneWidget);

      await tester.pumpWidget(const SizedBox.shrink());
      auth.dispose();
    });

    testWidgets(
        'LoginScreen ejecuta registro KYC en 6 pasos con Catálogo de Ocupación, SEPOMEX, Google Authenticator TOTP y acepta 1111111111',
        (WidgetTester tester) async {
      final fakeBio = _FakeBiometricService(
        availability: const BiometricAvailability(
          isSupported: false,
          hasEnrolledBiometrics: false,
          hasFingerprint: false,
          label: 'Huella Digital',
        ),
      );
      final fakeAuthSvc = _FakeAuthService();
      final auth = AuthProvider(authService: fakeAuthSvc, biometricService: fakeBio);
      await auth.initBiometrics();

      await tester.pumpWidget(
        ChangeNotifierProvider<AuthProvider>.value(
          value: auth,
          child: MaterialApp(
            theme: AppTheme.lightTheme,
            darkTheme: AppTheme.darkTheme,
            home: const LoginScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Crear Cuenta'));
      await tester.pumpAndSettle();

      expect(find.text('Paso 1 de 6'), findsOneWidget);

      // Paso 1: Datos Personales — validación inmediata al escribir 1 letra en nombre o 2 en apellido
      await tester.enterText(find.widgetWithText(TextFormField, 'Nombre *'), 'A');
      await tester.pump();
      expect(find.textContaining('al menos 2 letras'), findsOneWidget);

      await tester.enterText(find.widgetWithText(TextFormField, 'Apellido paterno *'), 'Ga');
      await tester.pump();
      expect(find.textContaining('al menos 3 letras'), findsOneWidget);

      await tester.enterText(find.widgetWithText(TextFormField, 'Nombre *'), 'Cesar');
      await tester.enterText(find.widgetWithText(TextFormField, 'Apellido paterno *'), 'Garay');
      await tester.enterText(find.widgetWithText(TextFormField, 'Apellido materno *'), 'Garcia');
      await tester.pump();
      expect(find.textContaining('al menos 2 letras'), findsNothing);
      expect(find.textContaining('al menos 3 letras'), findsNothing);

      final next1 = find.text('Continuar a Datos Fiscales');
      await tester.ensureVisible(next1);
      await tester.tap(next1);
      await tester.pumpAndSettle();

      // Paso 2: Datos Fiscales (CURP y RFC autocalculados + disponibilidad)
      expect(find.text('Paso 2 de 6'), findsOneWidget);
      expect(find.text('CURP (18 caracteres) *'), findsOneWidget);
      expect(find.text('RFC con Homoclave (13) *'), findsOneWidget);

      final next2 = find.text('Continuar a Ocupación e Ingresos');
      await tester.ensureVisible(next2);
      await tester.tap(next2);
      await tester.pumpAndSettle();

      // Paso 3: Ocupación e Ingresos (Catálogo de Ocupación + Tabulador Profesión + Empresa + Sueldo)
      expect(find.text('Paso 3 de 6'), findsOneWidget);
      final ocupDropdown = find.widgetWithText(
        DropdownButtonFormField<String>,
        'Ocupación (Catálogo) *',
      );
      expect(ocupDropdown, findsOneWidget);
      expect(find.text('Profesión (Tabulador) *'), findsOneWidget);
      expect(find.text('Tabulador de Sueldo Mensual *'), findsOneWidget);

      await tester.ensureVisible(ocupDropdown);
      await tester.tap(ocupDropdown);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Docente / Académico(a)').last);
      await tester.pumpAndSettle();

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Empresa / Institución *'),
        'Universidad Nacional',
      );

      final next3 = find.text('Continuar a Domicilio');
      await tester.ensureVisible(next3);
      await tester.tap(next3);
      await tester.pumpAndSettle();

      // Paso 4: Domicilio SEPOMEX
      expect(find.text('Paso 4 de 6'), findsOneWidget);
      final cpField = find.widgetWithText(TextFormField, 'Código Postal (5 díg) *');
      await tester.ensureVisible(cpField);
      await tester.enterText(cpField, '18500');
      await tester.pump();
      expect(find.textContaining('no existe en el catálogo oficial SEPOMEX'), findsOneWidget);

      await tester.enterText(cpField, '06600');
      await tester.pumpAndSettle();
      expect(find.textContaining('SEPOMEX verificado: Ciudad de México'), findsOneWidget);

      final coloniaDropdown = find.widgetWithText(
        DropdownButtonFormField<String>,
        'Colonia (Catálogo del C.P.) *',
      );
      await tester.ensureVisible(coloniaDropdown);
      await tester.tap(coloniaDropdown);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Juárez').last);
      await tester.pumpAndSettle();

      final calleField = find.widgetWithText(TextFormField, 'Calle y Avenida *');
      await tester.ensureVisible(calleField);
      await tester.enterText(calleField, 'Paseo de la Reforma');

      final numExtField = find.widgetWithText(TextFormField, 'Núm. Ext. *');
      await tester.ensureVisible(numExtField);
      await tester.enterText(numExtField, '250');

      final next4 = find.text('Continuar a Contacto y Acceso');
      await tester.ensureVisible(next4);
      await tester.tap(next4);
      await tester.pumpAndSettle();

      // Paso 5: Contacto y Acceso
      expect(find.text('Paso 5 de 6'), findsOneWidget);
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Correo electrónico real *'),
        'cesar@gmail.com',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Teléfono móvil México (10 dígitos) *'),
        '1111111111',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Usuario (mín. 4 caracteres) *'),
        'cesar2fa',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Contraseña (8+ car., Mayús, Minús, Núm) *'),
        'Venta2026#',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Confirmar contraseña *'),
        'Venta2026#',
      );

      final totpStepBtn = find.text('Continuar a Google Authenticator');
      await tester.ensureVisible(totpStepBtn);
      await tester.tap(totpStepBtn);
      await tester.pumpAndSettle();

      // Paso 6: Google Authenticator (TOTP 30s)
      expect(find.text('Paso 6 de 6'), findsOneWidget);
      expect(fakeAuthSvc.iniciarTotpCalls, 1);
      expect(find.text('Google Authenticator (TOTP 30s)'), findsOneWidget);
      expect(find.text('JBSW Y3DP EHPK 3PXP'), findsOneWidget);
      expect(find.text('Copiar clave'), findsOneWidget);

      await tester.enterText(
        find.widgetWithText(TextField, 'Código Google Authenticator (6 dígitos)'),
        '654321',
      );
      await tester.pump();

      final verifyBtn = find.text('Verificar y Crear Cuenta');
      await tester.ensureVisible(verifyBtn);
      await tester.tap(verifyBtn);
      await tester.pumpAndSettle();

      expect(fakeAuthSvc.lastTotpSecret, 'JBSWY3DPEHPK3PXP');
      expect(fakeAuthSvc.lastCodigoTotp, '654321');
      expect(fakeAuthSvc.lastOcupacion, 'Docente / Académico(a)');
      expect(fakeAuthSvc.lastProfesion, MexicoCatalogs.tabuladorProfesiones.first.label);
      expect(fakeAuthSvc.lastCurp?.length, 18);
      expect(fakeAuthSvc.lastRfc?.length, 13);
      expect(fakeAuthSvc.lastColonia, 'Juárez');
      expect(auth.session?.totpHabilitado, isTrue);
      expect(auth.session?.saldo, 0.0);
      expect(auth.isAuthenticated, isTrue);

      await tester.pumpWidget(const SizedBox.shrink());
      auth.dispose();
    });

    testWidgets(
        'LoginScreen avisa inmediatamente si el correo ya está registrado (yaexiste@gmail.com) y bloquea avance al Paso 6',
        (WidgetTester tester) async {
      final fakeBio = _FakeBiometricService(
        availability: const BiometricAvailability(
          isSupported: false,
          hasEnrolledBiometrics: false,
          hasFingerprint: false,
          label: 'Huella Digital',
        ),
      );
      final fakeAuthSvc = _FakeAuthService();
      final auth = AuthProvider(authService: fakeAuthSvc, biometricService: fakeBio);
      await auth.initBiometrics();

      await tester.pumpWidget(
        ChangeNotifierProvider<AuthProvider>.value(
          value: auth,
          child: MaterialApp(
            theme: AppTheme.lightTheme,
            home: const LoginScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Crear Cuenta'));
      await tester.pumpAndSettle();

      // Avanzar hasta Paso 5
      await tester.enterText(find.widgetWithText(TextFormField, 'Nombre *'), 'Laura');
      await tester.enterText(find.widgetWithText(TextFormField, 'Apellido paterno *'), 'Mendez');
      await tester.enterText(find.widgetWithText(TextFormField, 'Apellido materno *'), 'Soto');
      final next1 = find.text('Continuar a Datos Fiscales');
      await tester.ensureVisible(next1);
      await tester.tap(next1);
      await tester.pumpAndSettle();

      final next2 = find.text('Continuar a Ocupación e Ingresos');
      await tester.ensureVisible(next2);
      await tester.tap(next2);
      await tester.pumpAndSettle();

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Empresa / Institución *'),
        'Grupo Financiero',
      );
      final next3 = find.text('Continuar a Domicilio');
      await tester.ensureVisible(next3);
      await tester.tap(next3);
      await tester.pumpAndSettle();

      final cpField = find.widgetWithText(TextFormField, 'Código Postal (5 díg) *');
      await tester.ensureVisible(cpField);
      await tester.enterText(cpField, '06600');
      await tester.pumpAndSettle();

      final coloniaDropdown = find.widgetWithText(
        DropdownButtonFormField<String>,
        'Colonia (Catálogo del C.P.) *',
      );
      await tester.ensureVisible(coloniaDropdown);
      await tester.tap(coloniaDropdown);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Juárez').last);
      await tester.pumpAndSettle();

      await tester.enterText(
        find.widgetWithText(TextFormField, 'Calle y Avenida *'),
        'Insurgentes Sur',
      );
      await tester.enterText(find.widgetWithText(TextFormField, 'Núm. Ext. *'), '100');
      final next4 = find.text('Continuar a Contacto y Acceso');
      await tester.ensureVisible(next4);
      await tester.tap(next4);
      await tester.pumpAndSettle();

      expect(find.text('Paso 5 de 6'), findsOneWidget);

      // Escribir un correo duplicado y pasar al campo teléfono (pierde foco -> verificación inmediata)
      final correoField = find.widgetWithText(TextFormField, 'Correo electrónico real *');
      final telField = find.widgetWithText(
        TextFormField,
        'Teléfono móvil México (10 dígitos) *',
      );
      await tester.enterText(correoField, 'yaexiste@gmail.com');
      await tester.tap(telField);
      await tester.pumpAndSettle();

      expect(
        find.text(
          'Este correo electrónico ya está en uso en otra cuenta. Inicia sesión o utiliza otro correo.',
        ),
        findsWidgets,
      );

      // Aunque llene el resto de los campos, no permite avanzar al Paso 6
      await tester.enterText(telField, '5512345678');
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Usuario (mín. 4 caracteres) *'),
        'laura_mendez',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Contraseña (8+ car., Mayús, Minús, Núm) *'),
        'Venta2026#',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, 'Confirmar contraseña *'),
        'Venta2026#',
      );

      final totpBtn = find.text('Continuar a Google Authenticator');
      await tester.ensureVisible(totpBtn);
      await tester.tap(totpBtn);
      await tester.pumpAndSettle();

      expect(find.text('Paso 5 de 6'), findsOneWidget);
      expect(fakeAuthSvc.iniciarTotpCalls, 0);
      expect(
        find.text(
          'Este correo electrónico ya está en uso en otra cuenta. Inicia sesión o utiliza otro correo.',
        ),
        findsWidgets,
      );

      await tester.pumpWidget(const SizedBox.shrink());
      auth.dispose();
    });

    testWidgets(
        'PaymentFlowDialog envía payload firmado a GestoPago e imprime ticket Recarga Telcel Operación rechazada Origen no valido (-1) con Leyenda Oficial',
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

      final mockHttp = MockClient((req) async {
        if (req.url.path == '/api/v1/pagos/validar-referencia') {
          return _jsonUtf8(
            {
              'esValida': true,
              'mensaje': 'Número válido',
              'referenciaNormalizada': '1111111111',
            },
            200,
          );
        }
        if (req.url.path == '/api/v1/pagos/2fa/generar') {
          return _jsonUtf8(
            {
              'numeroCuenta': '5915019417993769',
              'vigenciaSegundos': 300,
              'correoEnviado': true,
              'correoDestino': 'cesar@gmail.com',
              'metodo2FA': 'EMAIL',
            },
            200,
          );
        }
        if (req.url.path == '/api/v1/pagos/servicios') {
          return _jsonUtf8(
            {
              'codigo': -1,
              'estadoOperacion': 'RECHAZADA',
              'mensaje': 'Recarga Telcel \$20 - Operación rechazada: Origen no valido (-1)',
              'mensajeOperacion':
                  'Recarga Telcel \$20 - Operación rechazada: Origen no valido (-1)',
              'motivoProveedor': 'Origen no valido',
              'folioTicket': 'GP-INTENTO-5501',
              'numeroAutorizacion': '-1',
              'codigoProveedor': '-1',
              'firmaDigital': 'AES256CBC_SIGNED_PAYLOAD_PREVIEW_9988',
              'referenciaDestino': '1111111111',
              'upc': 'UPC-998877',
              'montoPagado': 20.0,
              'comision': 0.0,
              'saldoRestanteCuenta': 500.0,
              'fechaHoraOperacion': '2026-11-27T18:00:00',
              'concepto': 'Recarga Telcel \$20',
              'servicio': 'Telcel',
              'producto': 'Recarga Telcel \$20',
            },
            400,
          );
        }
        return _jsonUtf8(<String, dynamic>{}, 404);
      });

      final client = ApiClient.withClient(mockHttp, baseUrl: 'http://localhost:10000');
      client.setAuthToken('jwt-pass-1');
      final paymentProvider = PaymentProvider(paymentService: PaymentService(client: client));

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
      paymentProvider.initForProduct(product);

      await tester.pumpWidget(
        MultiProvider(
          providers: [
            ChangeNotifierProvider<AuthProvider>.value(value: auth),
            ChangeNotifierProvider<PaymentProvider>.value(value: paymentProvider),
          ],
          child: MaterialApp(
            theme: AppTheme.lightTheme,
            home: Builder(
              builder: (ctx) => Scaffold(
                body: ElevatedButton(
                  onPressed: () {
                    PaymentFlowDialog.show(
                      ctx,
                      product: product,
                      onViewHistory: () {},
                    );
                  },
                  child: const Text('Intentar Pago GestoPago'),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Intentar Pago GestoPago'));
      await tester.pumpAndSettle();

      await tester.enterText(
        find.widgetWithText(TextField, 'Número Celular (10 dígitos)'),
        '1111111111',
      );
      final validateBtn = find.text('Validar Referencia');
      await tester.ensureVisible(validateBtn);
      await tester.tap(validateBtn);
      await tester.pumpAndSettle();

      final step2Btn = find.text('Continuar al Pago');
      await tester.ensureVisible(step2Btn);
      await tester.tap(step2Btn);
      await tester.pumpAndSettle();

      final otpField = find.widgetWithText(TextField, 'Ingresa el código 2FA (6 dígitos)');
      await tester.ensureVisible(otpField);
      await tester.enterText(otpField, '123456');
      await tester.pump();
      final checkbox = find.byType(Checkbox);
      await tester.ensureVisible(checkbox);
      await tester.tap(checkbox);
      await tester.pump();

      final payBtn = find.text('Confirmar y Pagar');
      await tester.ensureVisible(payBtn);
      await tester.tap(payBtn);
      await tester.pumpAndSettle();

      expect(find.byType(ReceiptDialog), findsOneWidget);
      expect(find.text('OPERACIÓN RECHAZADA'), findsOneWidget);
      expect(find.text('Operación rechazada'), findsWidgets);
      expect(find.textContaining('Recarga Telcel'), findsWidgets);
      expect(find.text('Origen no valido'), findsWidgets);
      expect(find.text('-1'), findsWidgets);
      expect(find.text('GP-INTENTO-5501'), findsOneWidget);
      expect(find.text('LEYENDA OFICIAL Y ACLARACIONES'), findsOneWidget);
      expect(
        find.textContaining('Operación no aplicada: no se realizó ningún cargo a su cuenta'),
        findsOneWidget,
      );
      expect(find.textContaining('800-226-2600'), findsOneWidget);
      expect(auth.session?.saldo, 500.0);

      await tester.pumpWidget(const SizedBox.shrink());
      paymentProvider.dispose();
      auth.dispose();
    });

    testWidgets(
        'Interactuar dentro de PaymentFlowDialog reinicia el temporizador de inactividad',
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

      final product = ProductItem(
        id: '374',
        name: 'Recarga \$100',
        service: 'AT-T',
        serviceId: '115',
        price: 100,
        referenceType: 'a',
        frontType: '1',
        hasVerificationDigit: false,
        categoryId: '3',
      );
      final paymentProvider = PaymentProvider()..initForProduct(product);

      await tester.pumpWidget(
        MultiProvider(
          providers: [
            ChangeNotifierProvider<AuthProvider>.value(value: auth),
            ChangeNotifierProvider<PaymentProvider>.value(value: paymentProvider),
          ],
          child: MaterialApp(
            builder: (context, child) {
              return Listener(
                behavior: HitTestBehavior.translucent,
                onPointerDown: (_) => auth.recordUserActivity(),
                child: child ?? const SizedBox.shrink(),
              );
            },
            home: Builder(
              builder: (ctx) => Scaffold(
                body: ElevatedButton(
                  onPressed: () {
                    showDialog<void>(
                      context: ctx,
                      builder: (_) => PaymentFlowDialog(product: product, onViewHistory: () {}),
                    );
                  },
                  child: const Text('Abrir Pago'),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Abrir Pago'));
      await tester.pumpAndSettle();
      expect(find.byType(PaymentFlowDialog), findsOneWidget);

      // Simular que transcurrieron 120s de inactividad
      auth.sessionSecondsNotifier.value = 180;
      expect(auth.secondsRemaining, 180);

      // Escribir el teléfono 1111111111 dentro del diálogo modal reinicia a 300s
      await tester.enterText(
        find.widgetWithText(TextField, 'Número Celular (10 dígitos)'),
        '1111111111',
      );
      await tester.pump();
      expect(auth.secondsRemaining, 300);

      // Incluso un toque sobre el diálogo encima del Navigator reinicia a 300s
      auth.sessionSecondsNotifier.value = 120;
      await tester.tap(find.text('Validar Referencia'));
      await tester.pump();
      expect(auth.secondsRemaining, 300);

      await tester.pumpWidget(const SizedBox.shrink());
      auth.dispose();
    });

    testWidgets(
        'PaymentFlowDialog muestra tarjeta Google Authenticator (30s) cuando metodo2FA es TOTP y oculta reenviar correo',
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

      final mockHttp = MockClient((req) async {
        if (req.url.path == '/api/v1/pagos/validar-referencia') {
          return _jsonUtf8(
            {
              'esValida': true,
              'mensaje': 'Número válido',
              'referenciaNormalizada': '5512345678',
            },
            200,
          );
        }
        if (req.url.path == '/api/v1/pagos/2fa/generar') {
          return _jsonUtf8(
            {
              'numeroCuenta': '5915019417993769',
              'metodo2FA': 'TOTP',
              'periodoTotpSegundos': 30,
              'vigenciaSegundos': 300,
              'correoEnviado': false,
            },
            200,
          );
        }
        if (req.url.path == '/api/v1/pagos/servicios') {
          return _jsonUtf8(
            {
              'codigo': 0,
              'estadoOperacion': 'EXITOSA',
              'mensaje': 'Pago exitoso',
              'idTransaccion': 7788,
              'numeroAutorizacion': 'AUTH-TOTP-7788',
              'folioTicket': 'GP-TOTP-7788',
              'montoPagado': 20.0,
              'comision': 0.0,
              'saldoRestanteCuenta': 480.0,
              'fechaHoraOperacion': '2026-11-28T10:00:00',
              'concepto': 'Recarga Telcel \$20',
              'servicio': 'Telcel',
              'producto': 'Recarga \$20',
              'correoEnviado': true,
            },
            200,
          );
        }
        return _jsonUtf8(<String, dynamic>{}, 404);
      });

      final client = ApiClient.withClient(mockHttp, baseUrl: 'http://localhost:10000');
      client.setAuthToken('jwt-pass-1');
      final paymentProvider = PaymentProvider(paymentService: PaymentService(client: client));

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
      paymentProvider.initForProduct(product);

      await tester.pumpWidget(
        MultiProvider(
          providers: [
            ChangeNotifierProvider<AuthProvider>.value(value: auth),
            ChangeNotifierProvider<PaymentProvider>.value(value: paymentProvider),
          ],
          child: MaterialApp(
            theme: AppTheme.lightTheme,
            home: Builder(
              builder: (ctx) => Scaffold(
                body: ElevatedButton(
                  onPressed: () {
                    PaymentFlowDialog.show(
                      ctx,
                      product: product,
                      onViewHistory: () {},
                    );
                  },
                  child: const Text('Pagar con TOTP'),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Pagar con TOTP'));
      await tester.pumpAndSettle();

      await tester.enterText(
        find.widgetWithText(TextField, 'Número Celular (10 dígitos)'),
        '5512345678',
      );
      final validateBtn = find.text('Validar Referencia');
      await tester.ensureVisible(validateBtn);
      await tester.tap(validateBtn);
      await tester.pumpAndSettle();

      final step2Btn = find.text('Continuar al Pago');
      await tester.ensureVisible(step2Btn);
      await tester.tap(step2Btn);
      await tester.pumpAndSettle();

      expect(find.text('Google Authenticator (30s)'), findsOneWidget);
      expect(find.text('Reenviar código 2FA'), findsNothing);
      expect(find.textContaining('Código enviado a'), findsNothing);

      final totpField = find.widgetWithText(
        TextField,
        'Código de Google Authenticator (6 dígitos)',
      );
      await tester.ensureVisible(totpField);
      await tester.enterText(totpField, '998877');
      await tester.pump();

      final checkbox = find.byType(Checkbox);
      await tester.ensureVisible(checkbox);
      await tester.tap(checkbox);
      await tester.pump();

      final payBtn = find.text('Confirmar y Pagar');
      await tester.ensureVisible(payBtn);
      await tester.tap(payBtn);
      await tester.pumpAndSettle();

      expect(find.byType(ReceiptDialog), findsOneWidget);
      expect(find.text('¡Operación Exitosa!'), findsOneWidget);
      expect(find.text('LEYENDA OFICIAL Y ACLARACIONES'), findsOneWidget);
      expect(
        find.textContaining('Conserve este comprobante digital como constancia oficial de pago'),
        findsOneWidget,
      );

      await tester.pumpWidget(const SizedBox.shrink());
      paymentProvider.dispose();
      auth.dispose();
    });
  });
}
