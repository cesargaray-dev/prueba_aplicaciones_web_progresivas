class HistoryItem {
  final int idPago;
  final String folioTicket;
  final String numeroAutorizacion;
  final int idTxGestopago;
  final int idServicio;
  final String servicio;
  final int idProducto;
  final String producto;
  final String telefono;
  final String referencia;
  final double monto;
  final double comision;
  final double saldoAnterior;
  final double saldoRestante;
  final String concepto;
  final String fechaOperacion;
  final String estado;
  final String logoUrl;

  HistoryItem({
    required this.idPago,
    required this.folioTicket,
    required this.numeroAutorizacion,
    required this.idTxGestopago,
    required this.idServicio,
    required this.servicio,
    required this.idProducto,
    required this.producto,
    required this.telefono,
    required this.referencia,
    required this.monto,
    required this.comision,
    required this.saldoAnterior,
    required this.saldoRestante,
    required this.concepto,
    required this.fechaOperacion,
    required this.estado,
    required this.logoUrl,
  });

  factory HistoryItem.fromJson(Map<String, dynamic> json) {
    int parseInt(dynamic val) {
      if (val is num) return val.toInt();
      if (val is String) return int.tryParse(val) ?? 0;
      return 0;
    }

    double parseDouble(dynamic val) {
      if (val is num) return val.toDouble();
      if (val is String) return double.tryParse(val) ?? 0.0;
      return 0.0;
    }

    final idServicioParsed = parseInt(json['idServicio']);
    final customLogo = json['logoUrl']?.toString();
    final logo = (customLogo != null && customLogo.isNotEmpty)
        ? customLogo
        : 'https://gestopago.portalventas.net/sistema/images/gestopago/servicios/$idServicioParsed.png';

    return HistoryItem(
      idPago: parseInt(json['idPago']),
      folioTicket: json['folioTicket']?.toString() ?? '',
      numeroAutorizacion: json['numeroAutorizacion']?.toString() ?? '',
      idTxGestopago: parseInt(json['idTxGestopago']),
      idServicio: idServicioParsed,
      servicio: json['servicio']?.toString() ?? 'Servicio',
      idProducto: parseInt(json['idProducto']),
      producto: json['producto']?.toString() ?? 'Pago',
      telefono: json['telefono']?.toString() ?? '',
      referencia: json['referencia']?.toString() ?? '',
      monto: parseDouble(json['monto']),
      comision: parseDouble(json['comision']),
      saldoAnterior: parseDouble(json['saldoAnterior']),
      saldoRestante: parseDouble(json['saldoRestante']),
      concepto: json['concepto']?.toString() ?? '',
      fechaOperacion: json['fechaOperacion']?.toString() ?? '',
      estado: json['estado']?.toString() ?? 'EXITOSA',
      logoUrl: logo,
    );
  }

  bool get isRecarga =>
      telefono.isNotEmpty &&
      telefono != '1111111111' &&
      (referencia.isEmpty || referencia == telefono);

  String get destinoDisplay {
    if (isRecarga) return telefono;
    if (referencia.isNotEmpty) return referencia;
    if (telefono.isNotEmpty && telefono != '1111111111') return telefono;
    return '—';
  }

  bool get isSuccess => estado.toUpperCase() == 'EXITOSA' || estado.toUpperCase() == 'COMPLETADA';
  bool get isPending => estado.toUpperCase() == 'PENDIENTE' || estado.toUpperCase() == 'EN_PROCESO';
}

class HistoryResponse {
  final String numeroCuenta;
  final int totalMovimientos;
  final List<HistoryItem> pagos;

  HistoryResponse({
    required this.numeroCuenta,
    required this.totalMovimientos,
    required this.pagos,
  });

  factory HistoryResponse.fromJson(Map<String, dynamic> json) {
    int total = 0;
    if (json['totalMovimientos'] != null) {
      total = (json['totalMovimientos'] as num).toInt();
    } else if (json['totalPagos'] != null) {
      total = (json['totalPagos'] as num).toInt();
    }

    final rawList = json['pagos'] as List<dynamic>? ?? [];
    final items = rawList
        .map((e) => HistoryItem.fromJson(e as Map<String, dynamic>))
        .toList();

    return HistoryResponse(
      numeroCuenta: json['numeroCuenta']?.toString() ?? '',
      totalMovimientos: total > 0 ? total : items.length,
      pagos: items,
    );
  }
}
