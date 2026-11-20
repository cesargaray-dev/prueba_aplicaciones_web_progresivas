import 'dart:async';
import 'package:flutter/foundation.dart';
import '../models/user_session.dart';
import '../services/api_client.dart';
import '../services/auth_service.dart';
import '../services/biometric_service.dart';

class AuthProvider with ChangeNotifier {
  final AuthService _authService;
  final BiometricService _biometricService;
  final ApiClient _apiClient;
  final VoidCallback? _onSessionReset;

  UserSession? _session;
  bool _isLoading = false;
  bool _isBiometricBusy = false;
  String? _errorMessage;
  Timer? _sessionTimer;
  final ValueNotifier<int> sessionSecondsNotifier = ValueNotifier<int>(300);

  // Biometric state
  BiometricAvailability _biometricAvailability = const BiometricAvailability(
    isSupported: false,
    hasEnrolledBiometrics: false,
    hasFingerprint: false,
    label: 'Huella Digital',
  );
  StoredBiometricProfile? _storedBiometricProfile;
  bool _biometricEnabledForCurrentUser = false;
  String _preferredLoginMethod = 'password'; // 'biometric' | 'password'
  bool _biometricsInitialized = false;
  bool _shouldPromptBiometricOnboarding = false;

  AuthProvider({
    AuthService? authService,
    BiometricService? biometricService,
    ApiClient? apiClient,
    VoidCallback? onSessionReset,
  })  : _authService = authService ?? AuthService(),
        _biometricService = biometricService ?? BiometricService(),
        _apiClient = apiClient ?? ApiClient(),
        // ignore: prefer_initializing_formals
        _onSessionReset = onSessionReset {
    _apiClient.onUnauthorized = _handleApiUnauthorized;
  }

  void _handleApiUnauthorized(String message) {
    logout(skipServerLogout: true, sessionExpiredMessage: message);
  }

  UserSession? get session => _session;
  bool get isAuthenticated => _session != null;
  bool get isLoading => _isLoading;
  bool get isBiometricBusy => _isBiometricBusy;
  String? get errorMessage => _errorMessage;
  int get secondsRemaining => sessionSecondsNotifier.value;
  int get sessionRemainingSeconds => sessionSecondsNotifier.value;

  BiometricAvailability get biometricAvailability => _biometricAvailability;
  bool get canUseBiometrics => _biometricAvailability.canUseBiometrics;
  String get biometricLabel => _biometricAvailability.label;
  StoredBiometricProfile? get storedBiometricProfile => _storedBiometricProfile;
  bool get hasStoredBiometricCredentials => _storedBiometricProfile != null;
  bool get biometricEnabledForCurrentUser => _biometricEnabledForCurrentUser;
  String get preferredLoginMethod => _preferredLoginMethod;
  bool get biometricsInitialized => _biometricsInitialized;
  bool get shouldPromptBiometricOnboarding => _shouldPromptBiometricOnboarding;

  void consumeBiometricOnboardingPrompt() {
    _shouldPromptBiometricOnboarding = false;
  }

  String get formattedSessionTime {
    final secs = sessionSecondsNotifier.value;
    final minutes = (secs ~/ 60).toString().padLeft(2, '0');
    final seconds = (secs % 60).toString().padLeft(2, '0');
    return '$minutes:$seconds';
  }

  Future<void> initBiometrics() async {
    final savedUrl = await _biometricService.getSavedApiUrl();
    if (savedUrl != null && savedUrl.isNotEmpty) {
      if (ApiClient.validateBaseUrl(savedUrl) == null) {
        _apiClient.setBaseUrl(savedUrl);
      } else {
        _apiClient.setBaseUrl(ApiClient.defaultBaseUrl);
        await _biometricService.clearSavedApiUrl();
      }
    }

    _biometricAvailability = await _biometricService.checkAvailability();
    _storedBiometricProfile = await _biometricService.getStoredProfile();
    if (_storedBiometricProfile != null && _biometricAvailability.canUseBiometrics) {
      _preferredLoginMethod = _storedBiometricProfile!.preferredMethod;
    } else {
      _preferredLoginMethod = 'password';
    }
    _biometricsInitialized = true;
    notifyListeners();
  }

  Future<void> saveApiUrl(String url) async {
    _apiClient.setBaseUrl(url);
    await _biometricService.saveApiUrl(url);
    notifyListeners();
  }

  Future<void> setPreferredLoginMethod(String method) async {
    final normalized = (method == 'biometric' && hasStoredBiometricCredentials && canUseBiometrics)
        ? 'biometric'
        : 'password';
    _preferredLoginMethod = normalized;
    await _biometricService.setPreferredMethod(normalized);
    if (_storedBiometricProfile != null) {
      _storedBiometricProfile = StoredBiometricProfile(
        usuario: _storedBiometricProfile!.usuario,
        nombreCompleto: _storedBiometricProfile!.nombreCompleto,
        preferredMethod: normalized,
      );
    }
    notifyListeners();
  }

  void _startInactivityTimer() {
    _sessionTimer?.cancel();
    sessionSecondsNotifier.value = 300;
    _sessionTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      final current = sessionSecondsNotifier.value;
      if (current > 1) {
        sessionSecondsNotifier.value = current - 1;
      } else {
        sessionSecondsNotifier.value = 0;
        logout(
          sessionExpiredMessage:
              'Tu sesión expiró por inactividad. Inicia sesión nuevamente.',
        );
      }
    });
  }

  void resetInactivityTimer() {
    if (_session != null && sessionSecondsNotifier.value < 299) {
      sessionSecondsNotifier.value = 300;
    }
  }

  void clearError() {
    _errorMessage = null;
    notifyListeners();
  }

  Future<void> _hydrateProfileNonFatal(UserSession baseSession) async {
    try {
      final profile = await _authService.getProfile(
        baseSession.token,
        baseSession.usuario,
      );
      if (_session != null) {
        _session = profile;
      }
    } on ApiException catch (e) {
      if (e.statusCode == 401) rethrow;
    } catch (_) {
      // Keep base session on transient profile fetch errors
    }
  }

  Future<void> _syncCurrentUserBiometricStatus() async {
    if (_session == null) {
      _biometricEnabledForCurrentUser = false;
      return;
    }
    final localMatches = _storedBiometricProfile != null &&
        _storedBiometricProfile!.usuario.toLowerCase() == _session!.usuario.toLowerCase();
    try {
      final serverEnabled = await _authService.consultarEstadoBiometria(usuario: _session!.usuario);
      _biometricEnabledForCurrentUser = serverEnabled && localMatches;
      if (!serverEnabled && localMatches) {
        await _biometricService.clearEnrolledProfile();
        _storedBiometricProfile = null;
        _preferredLoginMethod = 'password';
      }
    } on ApiException catch (e) {
      if (e.statusCode == 401) {
        _biometricEnabledForCurrentUser = false;
        rethrow;
      }
      _biometricEnabledForCurrentUser = localMatches;
    } catch (_) {
      _biometricEnabledForCurrentUser = localMatches;
    }
    notifyListeners();
  }

  Future<bool> login(String usuario, String contrasena, {bool enrollBiometricAfter = false}) async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final session = await _authService.login(usuario, contrasena);
      _session = session;
      await _hydrateProfileNonFatal(session);
      _isLoading = false;
      _startInactivityTimer();
      notifyListeners();

      await _syncCurrentUserBiometricStatus();
      if (enrollBiometricAfter && canUseBiometrics && !_biometricEnabledForCurrentUser) {
        await enableBiometricForCurrentUser();
      }
      return true;
    } on ApiException catch (e) {
      _errorMessage = e.message;
      _isLoading = false;
      notifyListeners();
      return false;
    } catch (_) {
      _errorMessage = 'Error inesperado al iniciar sesión.';
      _isLoading = false;
      notifyListeners();
      return false;
    }
  }

  Future<bool> loginWithBiometrics() async {
    _errorMessage = null;
    if (!canUseBiometrics) {
      _errorMessage = _biometricAvailability.unavailableReason ??
          'El lector de huella no está disponible en este dispositivo.';
      notifyListeners();
      return false;
    }

    final stored = _storedBiometricProfile ?? await _biometricService.getStoredProfile();
    if (stored == null) {
      _errorMessage =
          'Aún no has vinculado tu huella digital. Ingresa con contraseña y actívala en tu Perfil.';
      notifyListeners();
      return false;
    }

    _isBiometricBusy = true;
    notifyListeners();

    try {
      final authResult = await _biometricService.authenticate(
        localizedReason: 'Coloca tu huella digital para entrar como @${stored.usuario}',
      );
      if (!authResult.success) {
        if (!authResult.cancelled) {
          _preferredLoginMethod = 'password';
          await _biometricService.setPreferredMethod('password');
          if ((authResult.errorMessage ?? '').toLowerCase().contains('no hay huellas')) {
            _biometricAvailability = await _biometricService.checkAvailability();
          }
        }
        _errorMessage = authResult.cancelled ? null : authResult.errorMessage;
        _isBiometricBusy = false;
        notifyListeners();
        return false;
      }

      final tokenBio = await _biometricService.getStoredToken();
      if (tokenBio == null || tokenBio.length < 32) {
        await _biometricService.clearEnrolledProfile();
        _storedBiometricProfile = null;
        _preferredLoginMethod = 'password';
        _errorMessage = 'La credencial biométrica local expiró. Inicia sesión con tu contraseña.';
        _isBiometricBusy = false;
        notifyListeners();
        return false;
      }

      final session = await _authService.loginBiometrico(
        usuario: stored.usuario,
        tokenBiometrico: tokenBio,
      );
      _session = session;
      await _hydrateProfileNonFatal(session);
      _biometricEnabledForCurrentUser = true;
      _isBiometricBusy = false;
      _startInactivityTimer();
      notifyListeners();

      await _syncCurrentUserBiometricStatus();
      return true;
    } on ApiException catch (e) {
      if (e.statusCode == 401) {
        await _biometricService.clearEnrolledProfile();
        _storedBiometricProfile = null;
        _biometricEnabledForCurrentUser = false;
        _preferredLoginMethod = 'password';
      }
      _errorMessage = e.message;
      _isBiometricBusy = false;
      notifyListeners();
      return false;
    } catch (_) {
      _errorMessage = 'No fue posible iniciar sesión con huella digital.';
      _isBiometricBusy = false;
      notifyListeners();
      return false;
    }
  }

  Future<String?> enableBiometricForCurrentUser() async {
    if (_session == null) return 'Debes iniciar sesión primero.';
    if (!canUseBiometrics) {
      return _biometricAvailability.unavailableReason ??
          'Tu dispositivo no cuenta con lector de huella disponible.';
    }

    _isBiometricBusy = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final authResult = await _biometricService.authenticate(
        localizedReason: 'Verifica tu huella digital para activar el acceso biométrico',
      );
      if (!authResult.success) {
        _isBiometricBusy = false;
        notifyListeners();
        return authResult.errorMessage ?? 'Verificación de huella cancelada.';
      }

      final tokenBio = _biometricService.generateSecureToken();
      final ok = await _authService.registrarBiometria(
        usuario: _session!.usuario,
        tokenBiometrico: tokenBio,
        dispositivo: defaultTargetPlatform.name,
      );

      if (!ok) {
        _isBiometricBusy = false;
        notifyListeners();
        return 'El servidor no confirmó el registro biométrico.';
      }

      await _biometricService.saveEnrolledProfile(
        usuario: _session!.usuario,
        nombreCompleto: _session!.nombreCompleto,
        tokenBiometrico: tokenBio,
        preferredMethod: 'biometric',
      );

      _storedBiometricProfile = StoredBiometricProfile(
        usuario: _session!.usuario,
        nombreCompleto: _session!.nombreCompleto,
        preferredMethod: 'biometric',
      );
      _biometricEnabledForCurrentUser = true;
      _preferredLoginMethod = 'biometric';
      _isBiometricBusy = false;
      notifyListeners();
      return null;
    } on ApiException catch (e) {
      _isBiometricBusy = false;
      notifyListeners();
      return e.message;
    } catch (_) {
      _isBiometricBusy = false;
      notifyListeners();
      return 'No fue posible activar el acceso con huella digital.';
    }
  }

  Future<String?> disableBiometricForCurrentUser() async {
    if (_session == null) return 'Sin sesión activa.';
    _isBiometricBusy = true;
    notifyListeners();

    try {
      await _authService.desactivarBiometria(usuario: _session!.usuario);
      await _biometricService.clearEnrolledProfile();
      _storedBiometricProfile = null;
      _biometricEnabledForCurrentUser = false;
      _preferredLoginMethod = 'password';
      _isBiometricBusy = false;
      notifyListeners();
      return null;
    } on ApiException catch (e) {
      _isBiometricBusy = false;
      notifyListeners();
      return e.message;
    } catch (_) {
      _isBiometricBusy = false;
      notifyListeners();
      return 'No fue posible desactivar el acceso biométrico.';
    }
  }

  Future<void> clearStoredBiometricAccount() async {
    await _biometricService.clearEnrolledProfile();
    _storedBiometricProfile = null;
    _biometricEnabledForCurrentUser = false;
    _preferredLoginMethod = 'password';
    notifyListeners();
  }

  Future<bool> register({
    required String nombre,
    String? segundoNombre,
    required String apellidoPaterno,
    required String apellidoMaterno,
    required String fechaNacimiento,
    required String sexo,
    required String correo,
    required String telefonoMovil,
    required String usuario,
    required String contrasena,
  }) async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final session = await _authService.register(
        nombre: nombre,
        segundoNombre: segundoNombre,
        apellidoPaterno: apellidoPaterno,
        apellidoMaterno: apellidoMaterno,
        fechaNacimiento: fechaNacimiento,
        sexo: sexo,
        correo: correo,
        telefonoMovil: telefonoMovil,
        usuario: usuario,
        contrasena: contrasena,
      );
      _session = session;
      await _hydrateProfileNonFatal(session);
      if (!_biometricsInitialized) {
        _biometricAvailability = await _biometricService.checkAvailability();
        _biometricsInitialized = true;
      }
      _biometricEnabledForCurrentUser = false;
      _shouldPromptBiometricOnboarding = canUseBiometrics;
      _isLoading = false;
      _startInactivityTimer();
      notifyListeners();
      return true;
    } on ApiException catch (e) {
      _errorMessage = e.message;
      _isLoading = false;
      notifyListeners();
      return false;
    } catch (_) {
      _errorMessage = 'No fue posible crear tu cuenta bancaria.';
      _isLoading = false;
      notifyListeners();
      return false;
    }
  }

  Future<bool> updateContacto({
    required String correo,
    required String telefonoMovil,
    String? nombre,
    String? segundoNombre,
    String? apellidoPaterno,
    String? apellidoMaterno,
  }) async {
    if (_session == null) return false;
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final updated = await _authService.updateContacto(
        token: _session!.token,
        usuario: _session!.usuario,
        correo: correo,
        telefonoMovil: telefonoMovil,
        nombre: nombre,
        segundoNombre: segundoNombre,
        apellidoPaterno: apellidoPaterno,
        apellidoMaterno: apellidoMaterno,
      );
      _session = updated;
      if (_storedBiometricProfile != null &&
          _storedBiometricProfile!.usuario.toLowerCase() == updated.usuario.toLowerCase()) {
        _storedBiometricProfile = StoredBiometricProfile(
          usuario: _storedBiometricProfile!.usuario,
          nombreCompleto: updated.nombreCompleto,
          preferredMethod: _storedBiometricProfile!.preferredMethod,
        );
      }
      _isLoading = false;
      notifyListeners();
      return true;
    } on ApiException catch (e) {
      _errorMessage = e.message;
      _isLoading = false;
      notifyListeners();
      return false;
    } catch (_) {
      _errorMessage = 'No se pudo actualizar tus datos de perfil.';
      _isLoading = false;
      notifyListeners();
      return false;
    }
  }

  Future<bool> cambiarContrasena({
    required String contrasenaActual,
    required String contrasenaNueva,
  }) async {
    if (_session == null) return false;
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      await _authService.cambiarContrasena(
        usuario: _session!.usuario,
        contrasenaActual: contrasenaActual,
        contrasenaNueva: contrasenaNueva,
      );
      _isLoading = false;
      notifyListeners();
      return true;
    } on ApiException catch (e) {
      _errorMessage = e.message;
      _isLoading = false;
      notifyListeners();
      return false;
    } catch (_) {
      _errorMessage = 'No fue posible actualizar tu contraseña.';
      _isLoading = false;
      notifyListeners();
      return false;
    }
  }

  Future<bool> abonarSaldo({required double monto, String? concepto}) async {
    if (_session == null) return false;
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();

    try {
      final updated = await _authService.abonarSaldo(
        token: _session!.token,
        usuario: _session!.usuario,
        monto: monto,
        concepto: concepto,
      );
      _session = updated;
      _isLoading = false;
      notifyListeners();
      return true;
    } on ApiException catch (e) {
      _errorMessage = e.message;
      _isLoading = false;
      notifyListeners();
      return false;
    } catch (_) {
      _errorMessage = 'No se pudo aplicar el abono a tu cuenta.';
      _isLoading = false;
      notifyListeners();
      return false;
    }
  }

  Future<void> logout({
    bool skipServerLogout = false,
    String? sessionExpiredMessage,
  }) async {
    _sessionTimer?.cancel();
    _sessionTimer = null;
    sessionSecondsNotifier.value = 300;

    final currentToken = _session?.token ?? '';
    final currentUser = _session?.usuario ?? '';

    _session = null;
    _isLoading = false;
    _isBiometricBusy = false;
    _biometricEnabledForCurrentUser = false;
    _errorMessage = sessionExpiredMessage;
    _onSessionReset?.call();
    notifyListeners();

    try {
      if (!skipServerLogout && currentToken.isNotEmpty) {
        await _authService.logout(currentToken, currentUser);
      }
    } catch (_) {
      // Ignore network error during logout
    } finally {
      _apiClient.setToken(null);
    }
  }

  void updateSaldo(double newSaldo) {
    if (_session != null) {
      _session = _session!.copyWith(saldo: newSaldo);
      notifyListeners();
    }
  }

  void updateBalance(double newSaldo) => updateSaldo(newSaldo);

  Future<void> refreshProfile() async {
    if (_session == null) return;
    try {
      final updated = await _authService.getProfile(_session!.token, _session!.usuario);
      _session = updated;
      notifyListeners();
    } catch (_) {
      // Keep previous session on refresh failure
    }
  }

  @override
  void dispose() {
    _sessionTimer?.cancel();
    sessionSecondsNotifier.dispose();
    if (identical(_apiClient.onUnauthorized, _handleApiUnauthorized)) {
      _apiClient.onUnauthorized = null;
    }
    super.dispose();
  }
}
