import 'dart:typed_data';
import '../models/history_item.dart';
import '../models/payment_response.dart';
import '../models/reference_result.dart';
import 'api_client.dart';

class PaymentService {
  final ApiClient _client;

  PaymentService({ApiClient? client}) : _client = client ?? ApiClient();

  Future<ReferenceValidationResult> validarReferencia({
    required int idServicio,
    required int idProducto,
    required String referencia,
    double? monto,
  }) async {
    final response = await _client.post(
      '/api/v1/pagos/validar-referencia',
      body: {
        'idServicio': idServicio,
        'idProducto': idProducto,
        'referencia': referencia.trim(),
        if (monto != null && monto > 0) 'monto': monto,
      },
    );

    if (response is Map<String, dynamic>) {
      return ReferenceValidationResult.fromJson(response);
    }
    throw ApiException(statusCode: 500, message: 'Error al validar referencia en servidor');
  }

  Future<Generar2FAResponse> generar2FA(String numeroCuenta) async {
    final response = await _client.post(
      '/api/v1/pagos/2fa/generar',
      body: {
        'numeroCuenta': numeroCuenta.trim(),
      },
    );

    if (response is Map<String, dynamic>) {
      return Generar2FAResponse.fromJson(response);
    }
    throw ApiException(statusCode: 500, message: 'Error al generar código 2FA');
  }

  Future<PaymentResponse> pagarServicio({
    required String numeroCuenta,
    required int idServicio,
    required int idProducto,
    required double monto,
    String? telefono,
    required String referencia,
    required String codigo2FA,
  }) async {
    try {
      final res = await _client.postWithStatus(
        '/api/v1/pagos/servicios',
        body: {
          'numeroCuenta': numeroCuenta.trim(),
          'idServicio': idServicio,
          'idProducto': idProducto,
          'monto': monto,
          if (telefono != null && telefono.trim().isNotEmpty) 'telefono': telefono.trim(),
          'referencia': referencia.trim(),
          'codigo2FA': codigo2FA.trim(),
        },
      );

      if (res.body is Map<String, dynamic>) {
        return PaymentResponse.fromJson(
          res.body as Map<String, dynamic>,
          httpStatusCode: res.statusCode,
        );
      }
      throw ApiException(statusCode: 500, message: 'Error inesperado al procesar el pago');
    } on ApiException catch (e) {
      // 400, 409, 422 -> structured RECHAZADA; 504 -> structured PENDIENTE
      if (e.statusCode == 400 ||
          e.statusCode == 409 ||
          e.statusCode == 422 ||
          e.statusCode == 504) {
        return PaymentResponse.fromApiException(e);
      }
      rethrow;
    }
  }

  Future<EmailEnvioResultado> reenviarComprobante({
    required String numeroCuenta,
    required String folioTicket,
    String? correoDestino,
  }) async {
    final response = await _client.post(
      '/api/v1/pagos/comprobante/reenviar',
      body: {
        'numeroCuenta': numeroCuenta.trim(),
        'folioTicket': folioTicket.trim(),
        if (correoDestino != null && correoDestino.trim().isNotEmpty)
          'correoDestino': correoDestino.trim(),
      },
    );

    if (response is Map<String, dynamic>) {
      return EmailEnvioResultado.fromJson(response);
    }
    throw ApiException(statusCode: 500, message: 'Error inesperado al reenviar comprobante');
  }

  Future<Uint8List> downloadReceiptPdf(String folioTicket) async {
    final encoded = Uri.encodeComponent(folioTicket.trim());
    return _client.getBytes('/api/v1/pagos/comprobante/$encoded/pdf');
  }

  Future<HistoryResponse> getHistorial(String numeroCuenta) async {
    final response = await _client.post(
      '/api/v1/pagos/historial',
      body: {
        'numeroCuenta': numeroCuenta.trim(),
      },
    );

    if (response is Map<String, dynamic>) {
      return HistoryResponse.fromJson(response);
    }
    throw ApiException(statusCode: 500, message: 'Error al consultar el historial de pagos');
  }
}
