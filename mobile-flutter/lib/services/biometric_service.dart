import 'dart:math';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:local_auth/local_auth.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'api_client.dart';

class BiometricAvailability {
  final bool isSupported;
  final bool hasEnrolledBiometrics;
  final bool hasFingerprint;
  final String label;
  final String? unavailableReason;

  const BiometricAvailability({
    required this.isSupported,
    required this.hasEnrolledBiometrics,
    required this.hasFingerprint,
    required this.label,
    this.unavailableReason,
  });

  bool get canUseBiometrics => isSupported && hasEnrolledBiometrics;
}

class BiometricAuthResult {
  final bool success;
  final bool cancelled;
  final String? errorMessage;

  const BiometricAuthResult({
    required this.success,
    this.cancelled = false,
    this.errorMessage,
  });
}

class StoredBiometricProfile {
  final String usuario;
  final String nombreCompleto;
  final String preferredMethod; // 'biometric' or 'password'

  const StoredBiometricProfile({
    required this.usuario,
    required this.nombreCompleto,
    required this.preferredMethod,
  });
}

/// Servicio centralizado de autenticación biométrica (huella digital)
/// y resguardo cifrado del token biométrico en Keystore (Android) / Keychain (iOS).
class BiometricService {
  static const String _keyToken = 'banco_digital_biometric_token_v1';
  static const String _prefUsuario = 'biometric_usuario';
  static const String _prefNombre = 'biometric_nombre';
  static const String _prefEnabled = 'biometric_enabled';
  static const String _prefLoginMethod = 'preferred_login_method'; // 'biometric' | 'password'
  static const String _prefApiUrl = 'saved_api_base_url';

  final LocalAuthentication _localAuth;
  final FlutterSecureStorage _secureStorage;

  BiometricService({
    LocalAuthentication? localAuth,
    FlutterSecureStorage? secureStorage,
  })  : _localAuth = localAuth ?? LocalAuthentication(),
        _secureStorage = secureStorage ?? const FlutterSecureStorage();

  Future<BiometricAvailability> checkAvailability() async {
    if (kIsWeb) {
      return const BiometricAvailability(
        isSupported: false,
        hasEnrolledBiometrics: false,
        hasFingerprint: false,
        label: 'Huella Digital',
        unavailableReason:
            'El lector de huella nativo está disponible al ejecutar la app en un celular Android o iOS.',
      );
    }

    try {
      final supported = await _localAuth.isDeviceSupported();
      final canCheck = await _localAuth.canCheckBiometrics;
      if (!supported || !canCheck) {
        return const BiometricAvailability(
          isSupported: false,
          hasEnrolledBiometrics: false,
          hasFingerprint: false,
          label: 'Huella Digital',
          unavailableReason:
              'Este dispositivo no cuenta con lector biométrico activo. Puedes iniciar sesión con tu contraseña.',
        );
      }

      final biometrics = await _localAuth.getAvailableBiometrics();
      final hasFingerprint = biometrics.contains(BiometricType.fingerprint) ||
          biometrics.contains(BiometricType.strong) ||
          biometrics.contains(BiometricType.weak);
      final hasFace = biometrics.contains(BiometricType.face);

      if (biometrics.isEmpty) {
        return const BiometricAvailability(
          isSupported: true,
          hasEnrolledBiometrics: false,
          hasFingerprint: false,
          label: 'Huella Digital',
          unavailableReason:
              'Registra al menos una huella digital en los ajustes de tu teléfono para activar este acceso.',
        );
      }

      return BiometricAvailability(
        isSupported: true,
        hasEnrolledBiometrics: true,
        hasFingerprint: hasFingerprint,
        label: hasFingerprint ? 'Huella Digital' : (hasFace ? 'Face ID / Biometría' : 'Biometría'),
      );
    } catch (_) {
      return const BiometricAvailability(
        isSupported: false,
        hasEnrolledBiometrics: false,
        hasFingerprint: false,
        label: 'Huella Digital',
        unavailableReason: 'No fue posible consultar el lector biométrico en este dispositivo.',
      );
    }
  }

  Future<BiometricAuthResult> authenticate({
    required String localizedReason,
  }) async {
    if (kIsWeb) {
      return const BiometricAuthResult(
        success: false,
        errorMessage: 'El lector de huella requiere un dispositivo móvil Android o iOS.',
      );
    }

    try {
      final ok = await _localAuth.authenticate(
        localizedReason: localizedReason,
        biometricOnly: true,
        sensitiveTransaction: true,
        persistAcrossBackgrounding: true,
      );
      if (ok) {
        return const BiometricAuthResult(success: true);
      }
      return const BiometricAuthResult(
        success: false,
        cancelled: true,
        errorMessage: 'Verificación de huella cancelada o no reconocida.',
      );
    } on LocalAuthException catch (e) {
      final code = e.code.name;
      if (code.contains('Cancel')) {
        return const BiometricAuthResult(
          success: false,
          cancelled: true,
          errorMessage: 'Autenticación con huella cancelada.',
        );
      }
      if (code.contains('LockedOut')) {
        return const BiometricAuthResult(
          success: false,
          errorMessage:
              'Lector bloqueado temporalmente por intentos fallidos. Ingresa con tu contraseña.',
        );
      }
      if (code.contains('NotEnrolled') || code.contains('NoBiometrics')) {
        return const BiometricAuthResult(
          success: false,
          errorMessage:
              'No hay huellas registradas en el dispositivo. Configúrala en Ajustes del teléfono.',
        );
      }
      return BiometricAuthResult(
        success: false,
        errorMessage: e.description ?? 'No fue posible verificar tu huella digital.',
      );
    } on PlatformException catch (e) {
      return BiometricAuthResult(
        success: false,
        errorMessage: e.message ?? 'Error al comunicarse con el sensor biométrico.',
      );
    } catch (_) {
      return const BiometricAuthResult(
        success: false,
        errorMessage: 'No fue posible completar la lectura de huella digital.',
      );
    }
  }

  String generateSecureToken() {
    final random = Random.secure();
    final bytes = List<int>.generate(32, (_) => random.nextInt(256));
    return bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  }

  Future<void> saveEnrolledProfile({
    required String usuario,
    required String nombreCompleto,
    required String tokenBiometrico,
    String preferredMethod = 'biometric',
  }) async {
    await _secureStorage.write(key: _keyToken, value: tokenBiometrico);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_prefEnabled, true);
    await prefs.setString(_prefUsuario, usuario.trim());
    await prefs.setString(_prefNombre, nombreCompleto.trim());
    await prefs.setString(_prefLoginMethod, preferredMethod);
  }

  Future<StoredBiometricProfile?> getStoredProfile() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final enabled = prefs.getBool(_prefEnabled) ?? false;
      final usuario = prefs.getString(_prefUsuario) ?? '';
      if (!enabled || usuario.isEmpty) return null;

      final token = await getStoredToken();
      if (token == null || token.length < 32) return null;

      return StoredBiometricProfile(
        usuario: usuario,
        nombreCompleto: prefs.getString(_prefNombre) ?? usuario,
        preferredMethod: prefs.getString(_prefLoginMethod) ?? 'biometric',
      );
    } catch (_) {
      return null;
    }
  }

  Future<String?> getStoredToken() async {
    try {
      return await _secureStorage.read(key: _keyToken);
    } catch (_) {
      return null;
    }
  }

  Future<void> setPreferredMethod(String method) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_prefLoginMethod, method == 'biometric' ? 'biometric' : 'password');
    } catch (_) {
      // Ignore storage error
    }
  }

  Future<void> clearEnrolledProfile() async {
    try {
      await _secureStorage.delete(key: _keyToken);
    } catch (_) {
      // Ignore storage error
    }
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(_prefEnabled);
      await prefs.remove(_prefUsuario);
      await prefs.remove(_prefNombre);
      await prefs.setString(_prefLoginMethod, 'password');
    } catch (_) {
      // Ignore prefs error
    }
  }

  Future<String?> getSavedApiUrl() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getString(_prefApiUrl);
    } catch (_) {
      return null;
    }
  }

  Future<void> saveApiUrl(String url) async {
    final err = ApiClient.validateBaseUrl(url);
    if (err != null) {
      throw ArgumentError(err);
    }
    final normalized = ApiClient.normalizeBaseUrl(url);
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_prefApiUrl, normalized);
    } catch (_) {
      // Ignore prefs error
    }
  }

  Future<void> clearSavedApiUrl() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(_prefApiUrl);
    } catch (_) {
      // Ignore prefs error
    }
  }
}
