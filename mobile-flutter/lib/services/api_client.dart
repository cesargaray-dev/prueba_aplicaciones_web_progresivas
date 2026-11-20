import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

class ApiException implements Exception {
  final int statusCode;
  final String message;
  final dynamic details;

  ApiException({
    required this.statusCode,
    required this.message,
    this.details,
  });

  @override
  String toString() => 'ApiException($statusCode): $message';
}

class ApiClient {
  static final ApiClient _instance = ApiClient._internal();
  factory ApiClient() => _instance;
  ApiClient._internal();

  ApiClient.withClient(
    http.Client client, {
    String baseUrl = defaultBaseUrl,
    this.onUnauthorized,
  }) : _httpClient = client {
    setBaseUrl(baseUrl);
  }

  static const String defaultBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:10000',
  );

  static const Set<String> _localDevHosts = {
    'localhost',
    '127.0.0.1',
    '10.0.2.2',
    '::1',
  };

  static final RegExp _privateLanIpPattern = RegExp(
    r'^(192\.168\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(1[6-9]|2\d|3[0-1])\.\d{1,3}\.\d{1,3})$',
  );

  http.Client _httpClient = http.Client();
  String baseUrl = defaultBaseUrl;
  String? authToken;
  void Function(String message)? onUnauthorized;
  bool _handlingUnauthorized = false;

  void setHttpClient(http.Client client) {
    _httpClient = client;
  }

  @visibleForTesting
  void setHttpClientForTest(http.Client client) {
    _httpClient = client;
  }

  @visibleForTesting
  void resetForTest() {
    _httpClient = http.Client();
    baseUrl = defaultBaseUrl;
    authToken = null;
    onUnauthorized = null;
    _handlingUnauthorized = false;
  }

  static String normalizeBaseUrl(String rawUrl) {
    var trimmed = rawUrl.trim();
    while (trimmed.endsWith('/')) {
      trimmed = trimmed.substring(0, trimmed.length - 1);
    }
    return trimmed;
  }

  static String? validateBaseUrl(String rawUrl) {
    final normalized = normalizeBaseUrl(rawUrl);
    if (normalized.isEmpty) {
      return 'La URL del servidor API no puede estar vacía.';
    }
    final uri = Uri.tryParse(normalized);
    if (uri == null || !uri.hasScheme || uri.host.isEmpty) {
      return 'Ingresa una URL válida (ej. https://api.midominio.com).';
    }
    if (uri.userInfo.isNotEmpty || uri.hasQuery || uri.hasFragment) {
      return 'La URL base no debe incluir credenciales, parámetros ni fragmentos.';
    }
    if (uri.hasPort && (uri.port <= 0 || uri.port > 65535)) {
      return 'El puerto de la URL es inválido.';
    }
    final scheme = uri.scheme.toLowerCase();
    if (scheme == 'https') {
      return null;
    }
    if (scheme == 'http') {
      final hostLower = uri.host.toLowerCase();
      if (_localDevHosts.contains(hostLower) || _privateLanIpPattern.hasMatch(hostLower)) {
        return null;
      }
      return 'Usa https:// para servidores externos. Solo se permite http:// en red local (localhost, 192.168.x.x, 10.x.x.x).';
    }
    return 'Esquema no soportado. Usa https:// (o http:// solo en localhost).';
  }

  void setAuthToken(String? token) {
    authToken = token;
  }

  void setToken(String? token) => setAuthToken(token);

  void setBaseUrl(String url) {
    final err = validateBaseUrl(url);
    if (err != null) {
      throw ArgumentError(err);
    }
    baseUrl = normalizeBaseUrl(url);
  }

  Map<String, String> _buildHeaders({Map<String, String>? extra}) {
    final headers = <String, String>{
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    };
    if (authToken != null && authToken!.isNotEmpty) {
      headers['Authorization'] = 'Bearer $authToken';
    }
    if (extra != null) {
      headers.addAll(extra);
    }
    return headers;
  }

  bool _isNonSession401(String path, String message) {
    final normalizedPath = path.split('?').first;
    if (normalizedPath.startsWith('/api/v1/auth/login') ||
        normalizedPath.startsWith('/api/v1/auth/register') ||
        normalizedPath.startsWith('/api/v1/auth/logout')) {
      return true;
    }
    final lowerMsg = message.toLowerCase();
    if (normalizedPath.startsWith('/api/v1/pagos/servicios') &&
        lowerMsg.contains('2fa')) {
      return true;
    }
    if (normalizedPath.startsWith('/api/v1/auth/me/contrasena') &&
        lowerMsg.contains('contraseña actual')) {
      return true;
    }
    return false;
  }

  void _handleUnauthorizedIfNeeded(int statusCode, String path, String message) {
    if (statusCode != 401) return;
    if (authToken == null || authToken!.isEmpty) return;
    if (_handlingUnauthorized) return;
    if (_isNonSession401(path, message)) return;

    _handlingUnauthorized = true;
    try {
      authToken = null;
      onUnauthorized?.call(
        'Tu sesión ha expirado o fue cerrada. Inicia sesión nuevamente.',
      );
    } finally {
      _handlingUnauthorized = false;
    }
  }

  Future<dynamic> get(String path, {Map<String, String>? queryParams}) async {
    Uri uri = Uri.parse('$baseUrl$path');
    if (queryParams != null && queryParams.isNotEmpty) {
      uri = uri.replace(queryParameters: queryParams);
    }

    try {
      final response = await _httpClient
          .get(uri, headers: _buildHeaders())
          .timeout(const Duration(seconds: 15));

      return _processResponse(response, path: path);
    } catch (e) {
      if (e is ApiException) rethrow;
      throw ApiException(
        statusCode: 0,
        message: 'Error de conexión con el servidor: $e',
      );
    }
  }

  Future<Uint8List> getBytes(
    String path, {
    Map<String, String>? queryParams,
    String accept = 'application/pdf',
  }) async {
    Uri uri = Uri.parse('$baseUrl$path');
    if (queryParams != null && queryParams.isNotEmpty) {
      uri = uri.replace(queryParameters: queryParams);
    }

    try {
      final response = await _httpClient
          .get(uri, headers: _buildHeaders(extra: {'Accept': accept}))
          .timeout(const Duration(seconds: 20));

      if (response.statusCode >= 200 && response.statusCode < 300) {
        return response.bodyBytes;
      }
      _processResponse(response, path: path);
      return response.bodyBytes;
    } catch (e) {
      if (e is ApiException) rethrow;
      throw ApiException(
        statusCode: 0,
        message: 'Error de conexión con el servidor: $e',
      );
    }
  }

  Future<({int statusCode, dynamic body})> postWithStatus(
    String path, {
    dynamic body,
  }) async {
    final uri = Uri.parse('$baseUrl$path');
    final payload = body != null ? jsonEncode(body) : null;

    try {
      final response = await _httpClient
          .post(uri, headers: _buildHeaders(), body: payload)
          .timeout(const Duration(seconds: 20));

      final parsed = _processResponse(response, path: path);
      return (statusCode: response.statusCode, body: parsed);
    } catch (e) {
      if (e is ApiException) rethrow;
      throw ApiException(
        statusCode: 0,
        message: 'Error de conexión con el servidor: $e',
      );
    }
  }

  Future<dynamic> post(String path, {dynamic body}) async {
    final res = await postWithStatus(path, body: body);
    return res.body;
  }

  Future<dynamic> put(String path, {dynamic body}) async {
    final uri = Uri.parse('$baseUrl$path');
    final payload = body != null ? jsonEncode(body) : null;

    try {
      final response = await _httpClient
          .put(uri, headers: _buildHeaders(), body: payload)
          .timeout(const Duration(seconds: 20));

      return _processResponse(response, path: path);
    } catch (e) {
      if (e is ApiException) rethrow;
      throw ApiException(
        statusCode: 0,
        message: 'Error de conexión con el servidor: $e',
      );
    }
  }

  Future<dynamic> delete(String path, {Map<String, String>? queryParams}) async {
    Uri uri = Uri.parse('$baseUrl$path');
    if (queryParams != null && queryParams.isNotEmpty) {
      uri = uri.replace(queryParameters: queryParams);
    }

    try {
      final response = await _httpClient
          .delete(uri, headers: _buildHeaders())
          .timeout(const Duration(seconds: 20));

      return _processResponse(response, path: path);
    } catch (e) {
      if (e is ApiException) rethrow;
      throw ApiException(
        statusCode: 0,
        message: 'Error de conexión con el servidor: $e',
      );
    }
  }

  dynamic _processResponse(http.Response response, {required String path}) {
    final bodyString = utf8.decode(response.bodyBytes);
    dynamic bodyJson;
    if (bodyString.isNotEmpty) {
      try {
        bodyJson = jsonDecode(bodyString);
      } catch (_) {
        bodyJson = bodyString;
      }
    }

    if (response.statusCode >= 200 && response.statusCode < 300) {
      return bodyJson;
    }

    String message = 'Ocurrió un error inesperado (${response.statusCode})';
    if (bodyJson is Map<String, dynamic>) {
      if (bodyJson['mensajeOperacion'] != null &&
          bodyJson['mensajeOperacion'].toString().isNotEmpty) {
        message = bodyJson['mensajeOperacion'].toString();
      } else if (bodyJson['mensaje'] != null) {
        message = bodyJson['mensaje'].toString();
      } else if (bodyJson['message'] != null) {
        message = bodyJson['message'].toString();
      } else if (bodyJson['error'] != null) {
        message = bodyJson['error'].toString();
      }
    }

    _handleUnauthorizedIfNeeded(response.statusCode, path, message);

    throw ApiException(
      statusCode: response.statusCode,
      message: message,
      details: bodyJson,
    );
  }
}
