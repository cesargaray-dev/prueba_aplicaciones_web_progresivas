import '../models/user_session.dart';
import 'api_client.dart';

class AuthService {
  final ApiClient _client;

  AuthService({ApiClient? client}) : _client = client ?? ApiClient();

  Future<UserSession> login(String usuario, String contrasena) async {
    final response = await _client.post(
      '/api/v1/auth/login',
      body: {
        'usuario': usuario.trim(),
        'contrasena': contrasena,
      },
    );

    if (response is Map<String, dynamic>) {
      final session = UserSession.fromJson(response);
      _client.setToken(session.token);
      return session;
    }
    throw ApiException(statusCode: 500, message: 'Formato de respuesta inesperado del servidor');
  }

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
    final cleanSegundo = segundoNombre?.trim();
    final cleanMaterno = apellidoMaterno.trim();
    final cleanFecha = fechaNacimiento.trim();
    final cleanSexo = sexo.trim();
    final response = await _client.post(
      '/api/v1/auth/register',
      body: {
        'nombre': nombre.trim(),
        if (cleanSegundo != null && cleanSegundo.isNotEmpty) 'segundoNombre': cleanSegundo,
        'apellidoPaterno': apellidoPaterno.trim(),
        if (cleanMaterno.isNotEmpty) 'apellidoMaterno': cleanMaterno,
        if (cleanFecha.isNotEmpty) 'fechaNacimiento': cleanFecha,
        if (cleanSexo.isNotEmpty) 'sexo': cleanSexo,
        'correo': correo.trim(),
        'telefonoMovil': telefonoMovil.trim(),
        'usuario': usuario.trim(),
        'contrasena': contrasena,
      },
    );

    if (response is Map<String, dynamic>) {
      final session = UserSession.fromJson(response);
      _client.setToken(session.token);
      return session;
    }
    throw ApiException(statusCode: 500, message: 'Formato de respuesta inesperado del servidor');
  }

  Future<void> logout(String token, String usuario) async {
    if (token.isEmpty) {
      _client.setToken(null);
      return;
    }
    try {
      await _client.post(
        '/api/v1/auth/logout',
        body: {
          if (usuario.trim().isNotEmpty) 'usuario': usuario.trim(),
        },
      );
    } finally {
      _client.setToken(null);
    }
  }

  Future<UserSession> getProfile(String token, String usuario) async {
    final response = await _client.get('/api/v1/auth/me');

    if (response is Map<String, dynamic>) {
      return UserSession.fromJson(response, existingToken: token);
    }
    throw ApiException(statusCode: 500, message: 'No se pudo leer el perfil del usuario');
  }

  Future<void> verificarSesionActiva() async {
    await _client.get('/api/v1/auth/session-status');
  }

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
    final response = await _client.put(
      '/api/v1/auth/me/perfil',
      body: {
        if (nombre != null) 'nombre': nombre.trim(),
        if (segundoNombre != null) 'segundoNombre': segundoNombre.trim(),
        if (apellidoPaterno != null) 'apellidoPaterno': apellidoPaterno.trim(),
        if (apellidoMaterno != null) 'apellidoMaterno': apellidoMaterno.trim(),
        'correo': correo.trim(),
        'telefonoMovil': telefonoMovil.trim(),
      },
    );

    if (response is Map<String, dynamic>) {
      return UserSession.fromJson(response, existingToken: token);
    }
    throw ApiException(statusCode: 500, message: 'No se pudo actualizar el perfil');
  }

  Future<void> cambiarContrasena({
    required String usuario,
    required String contrasenaActual,
    required String contrasenaNueva,
  }) async {
    await _client.put(
      '/api/v1/auth/me/contrasena',
      body: {
        'contrasenaActual': contrasenaActual,
        'contrasenaNueva': contrasenaNueva,
      },
    );
  }

  Future<UserSession> abonarSaldo({
    required String token,
    required String usuario,
    required double monto,
    String? concepto,
  }) async {
    final response = await _client.post(
      '/api/v1/auth/me/abonar',
      body: {
        'monto': monto,
        if (concepto != null && concepto.trim().isNotEmpty) 'concepto': concepto.trim(),
      },
    );

    if (response is Map<String, dynamic>) {
      return UserSession.fromJson(response, existingToken: token);
    }
    throw ApiException(statusCode: 500, message: 'No se pudo aplicar el abono');
  }

  Future<bool> registrarBiometria({
    required String usuario,
    required String tokenBiometrico,
    String? dispositivo,
  }) async {
    final response = await _client.post(
      '/api/v1/auth/biometria/registrar',
      body: {
        'tokenBiometrico': tokenBiometrico,
        if (dispositivo != null && dispositivo.isNotEmpty) 'dispositivo': dispositivo,
      },
    );
    if (response is Map<String, dynamic>) {
      return response['habilitada'] == true;
    }
    return false;
  }

  Future<bool> desactivarBiometria({required String usuario}) async {
    final response = await _client.delete('/api/v1/auth/biometria');
    if (response is Map<String, dynamic>) {
      return response['habilitada'] == false;
    }
    return true;
  }

  Future<bool> consultarEstadoBiometria({required String usuario}) async {
    final response = await _client.get('/api/v1/auth/biometria/estado');
    if (response is Map<String, dynamic>) {
      return response['habilitada'] == true;
    }
    return false;
  }

  Future<UserSession> loginBiometrico({
    required String usuario,
    required String tokenBiometrico,
  }) async {
    final response = await _client.post(
      '/api/v1/auth/login/biometrico',
      body: {
        'usuario': usuario.trim(),
        'tokenBiometrico': tokenBiometrico,
      },
    );

    if (response is Map<String, dynamic>) {
      final session = UserSession.fromJson(response);
      _client.setToken(session.token);
      return session;
    }
    throw ApiException(statusCode: 500, message: 'Formato de respuesta inesperado del servidor');
  }

  void setBaseUrl(String newUrl) {
    _client.setBaseUrl(newUrl);
  }
}
