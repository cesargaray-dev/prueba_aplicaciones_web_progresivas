import '../services/api_client.dart';

class Generar2FAResponse {
  final String numeroCuenta;
  final String? codigo2FA;
  final int vigenciaSegundos;
  final String? fechaExpiracion;
  final bool? correoEnviado;
  final String? correoDestino;
  final String? mensajeCorreo;
  final String? messageId;

  Generar2FAResponse({
    required this.numeroCuenta,
    this.codigo2FA,
    required this.vigenciaSegundos,
    this.fechaExpiracion,
    this.correoEnviado,
    this.correoDestino,
    this.mensajeCorreo,
    this.messageId,
  });

  factory Generar2FAResponse.fromJson(Map<String, dynamic> json) {
    final rawVigencia = json['vigenciaSegundos'] ?? json['expiraEnSegundos'];
    final vigencia = rawVigencia is int
        ? rawVigencia
        : int.tryParse(rawVigencia?.toString() ?? '300') ?? 300;

    return Generar2FAResponse(
      numeroCuenta: (json['numeroCuenta'] ?? '').toString(),
      codigo2FA: json['codigo2FA']?.toString(),
      vigenciaSegundos: vigencia,
      fechaExpiracion: json['fechaExpiracion']?.toString(),
      correoEnviado: json['correoEnviado'] as bool?,
      correoDestino: json['correoDestino']?.toString(),
      mensajeCorreo: json['mensajeCorreo']?.toString(),
      messageId: (json['messageId'] ?? json['brevoMessageId'])?.toString(),
    );
  }
}

enum PaymentResultStatus {
  exitosa,
  rechazada,
  pendiente,
}

class PaymentResponse {
  final int codigo;
  final String mensaje;
  final int? idTransaccion;
  final String numeroAutorizacion;
  final String folioTicket;
  final double montoPagado;
  final double comision;
  final double saldoRestanteCuenta;
  final String fechaHoraOperacion;
  final String concepto;
  final bool? correoEnviado;
  final String? correoDestino;
  final String? mensajeCorreo;
  final String? brevoMessageId;
  final String estadoOperacion;
  final String? codigoProveedor;
  final String? mensajeOperacion;
  final String? upc;
  final String? servicio;
  final String? producto;
  final String? logoUrl;
  final int? httpStatusCode;

  PaymentResponse({
    required this.codigo,
    required this.mensaje,
    this.idTransaccion,
    required this.numeroAutorizacion,
    required this.folioTicket,
    required this.montoPagado,
    required this.comision,
    required this.saldoRestanteCuenta,
    required this.fechaHoraOperacion,
    required this.concepto,
    this.correoEnviado,
    this.correoDestino,
    this.mensajeCorreo,
    this.brevoMessageId,
    String? estadoOperacion,
    this.codigoProveedor,
    this.mensajeOperacion,
    this.upc,
    this.servicio,
    this.producto,
    this.logoUrl,
    this.httpStatusCode,
  }) : estadoOperacion = estadoOperacion ?? (codigo == 0 ? 'EXITOSA' : 'RECHAZADA');

  PaymentResultStatus get status {
    final norm = estadoOperacion.trim().toUpperCase();
    if (norm == 'PENDIENTE' || norm == 'EN_PROCESO') {
      return PaymentResultStatus.pendiente;
    }
    if (norm == 'EXITOSA' || norm == 'COMPLETADA') {
      return PaymentResultStatus.exitosa;
    }
    return PaymentResultStatus.rechazada;
  }

  bool get isSuccess => status == PaymentResultStatus.exitosa;
  bool get isRejected => status == PaymentResultStatus.rechazada;
  bool get isPending => status == PaymentResultStatus.pendiente;

  factory PaymentResponse.fromJson(Map<String, dynamic> json, {int? httpStatusCode}) {
    final rawCodigo = json['codigo'] is int
        ? json['codigo'] as int
        : int.tryParse(json['codigo']?.toString() ?? '0') ?? 0;

    final rawEstado = json['estadoOperacion']?.toString().trim().toUpperCase();
    String resolvedEstado;
    if (rawEstado != null && rawEstado.isNotEmpty) {
      resolvedEstado = rawEstado;
    } else if (httpStatusCode == 202 || httpStatusCode == 504) {
      resolvedEstado = 'PENDIENTE';
    } else if (httpStatusCode != null && httpStatusCode >= 400) {
      resolvedEstado = 'RECHAZADA';
    } else if (rawCodigo == 0) {
      resolvedEstado = 'EXITOSA';
    } else {
      resolvedEstado = 'RECHAZADA';
    }

    final defaultMsg = resolvedEstado == 'EXITOSA'
        ? 'Pago aplicado exitosamente'
        : (resolvedEstado == 'PENDIENTE'
            ? 'Operación pendiente de confirmación'
            : 'Operación rechazada');

    final mensaje = (json['mensaje'] ?? defaultMsg).toString();
    final mensajeOp = json['mensajeOperacion']?.toString();

    return PaymentResponse(
      codigo: rawCodigo,
      mensaje: mensaje,
      idTransaccion: json['idTransaccion'] is int
          ? json['idTransaccion'] as int
          : int.tryParse(json['idTransaccion']?.toString() ?? ''),
      numeroAutorizacion: (json['numeroAutorizacion'] ?? '').toString(),
      folioTicket: (json['folioTicket'] ?? '').toString(),
      montoPagado: json['montoPagado'] is num
          ? (json['montoPagado'] as num).toDouble()
          : double.tryParse(json['montoPagado']?.toString() ?? '0') ?? 0.0,
      comision: json['comision'] is num
          ? (json['comision'] as num).toDouble()
          : double.tryParse(json['comision']?.toString() ?? '0') ?? 0.0,
      saldoRestanteCuenta: json['saldoRestanteCuenta'] is num
          ? (json['saldoRestanteCuenta'] as num).toDouble()
          : double.tryParse(json['saldoRestanteCuenta']?.toString() ?? '0') ?? 0.0,
      fechaHoraOperacion: (json['fechaHoraOperacion'] ?? '').toString(),
      concepto: (json['concepto'] ?? '').toString(),
      correoEnviado: json['correoEnviado'] as bool?,
      correoDestino: json['correoDestino']?.toString(),
      mensajeCorreo: json['mensajeCorreo']?.toString(),
      brevoMessageId: json['brevoMessageId']?.toString(),
      estadoOperacion: resolvedEstado,
      codigoProveedor: json['codigoProveedor']?.toString(),
      mensajeOperacion: (mensajeOp != null && mensajeOp.isNotEmpty) ? mensajeOp : mensaje,
      upc: json['upc']?.toString(),
      servicio: json['servicio']?.toString(),
      producto: json['producto']?.toString(),
      logoUrl: json['logoUrl']?.toString(),
      httpStatusCode: httpStatusCode,
    );
  }

  factory PaymentResponse.fromApiException(ApiException e) {
    final upcMatch = RegExp(
      r'(?:UPC[^A-Za-z0-9]*|conciliaci[oó]n[:\s]+)([A-Za-z0-9-]+)',
      caseSensitive: false,
    ).firstMatch(e.message);
    final extractedUpc = upcMatch?.group(1);

    if (e.details is Map<String, dynamic>) {
      final parsed = PaymentResponse.fromJson(
        e.details as Map<String, dynamic>,
        httpStatusCode: e.statusCode,
      );
      return PaymentResponse(
        codigo: parsed.codigo == 0 ? 1 : parsed.codigo,
        mensaje: parsed.mensaje.isNotEmpty ? parsed.mensaje : e.message,
        idTransaccion: parsed.idTransaccion,
        numeroAutorizacion: parsed.numeroAutorizacion,
        folioTicket: parsed.folioTicket,
        montoPagado: parsed.montoPagado,
        comision: parsed.comision,
        saldoRestanteCuenta: parsed.saldoRestanteCuenta,
        fechaHoraOperacion: parsed.fechaHoraOperacion,
        concepto: parsed.concepto,
        correoEnviado: parsed.correoEnviado,
        correoDestino: parsed.correoDestino,
        mensajeCorreo: parsed.mensajeCorreo,
        brevoMessageId: parsed.brevoMessageId,
        estadoOperacion: parsed.estadoOperacion,
        codigoProveedor: parsed.codigoProveedor,
        mensajeOperacion: parsed.mensajeOperacion ?? e.message,
        upc: (parsed.upc != null && parsed.upc!.isNotEmpty) ? parsed.upc : extractedUpc,
        servicio: parsed.servicio,
        producto: parsed.producto,
        logoUrl: parsed.logoUrl,
        httpStatusCode: e.statusCode,
      );
    }

    final isPendingStatus = e.statusCode == 504 || e.statusCode == 202;
    return PaymentResponse(
      codigo: 1,
      mensaje: e.message,
      numeroAutorizacion: '',
      folioTicket: '',
      montoPagado: 0.0,
      comision: 0.0,
      saldoRestanteCuenta: 0.0,
      fechaHoraOperacion: '',
      concepto: '',
      estadoOperacion: isPendingStatus ? 'PENDIENTE' : 'RECHAZADA',
      mensajeOperacion: e.message,
      upc: extractedUpc,
      httpStatusCode: e.statusCode,
    );
  }
}

class EmailEnvioResultado {
  final bool enviado;
  final String? correoDestino;
  final String? messageId;
  final String? mensaje;
  final bool? correoPlaceholder;

  EmailEnvioResultado({
    required this.enviado,
    this.correoDestino,
    this.messageId,
    this.mensaje,
    this.correoPlaceholder,
  });

  factory EmailEnvioResultado.fromJson(Map<String, dynamic> json) {
    return EmailEnvioResultado(
      enviado: json['enviado'] == true,
      correoDestino: json['correoDestino']?.toString(),
      messageId: json['messageId']?.toString(),
      mensaje: json['mensaje']?.toString(),
      correoPlaceholder: json['correoPlaceholder'] as bool?,
    );
  }
}
