class ReferenceValidationResult {
  final bool esValida;
  final String mensaje;
  final int? tipoFront;
  final String? tipoReferencia;
  final bool requiereMonto;
  final double? montoSugerido;
  final String? servicio;
  final String? producto;
  final bool hasVerificationDigit;

  ReferenceValidationResult({
    required this.esValida,
    required this.mensaje,
    this.tipoFront,
    this.tipoReferencia,
    this.requiereMonto = false,
    this.montoSugerido,
    this.servicio,
    this.producto,
    this.hasVerificationDigit = false,
  });

  factory ReferenceValidationResult.fromJson(Map<String, dynamic> json) {
    double? suggested;
    if (json['montoSugerido'] != null) {
      if (json['montoSugerido'] is num) {
        suggested = (json['montoSugerido'] as num).toDouble();
      } else {
        suggested = double.tryParse(json['montoSugerido'].toString());
      }
    }

    final rawFront = json['tipoFront'];
    final parsedFront = rawFront is int
        ? rawFront
        : int.tryParse(rawFront?.toString() ?? '');

    final rawHasDigit = json['hasVerificationDigit'] ?? json['hasDigitoVerificador'];
    final hasDigit = rawHasDigit == true ||
        rawHasDigit?.toString().toLowerCase() == 'true';

    return ReferenceValidationResult(
      esValida: json['esValida'] == true,
      mensaje: json['mensaje'] as String? ??
          (json['esValida'] == true ? 'Referencia válida' : 'Referencia inválida'),
      tipoFront: parsedFront,
      tipoReferencia: json['tipoReferencia'] as String?,
      requiereMonto: json['requiereMonto'] == true,
      montoSugerido: suggested,
      servicio: json['servicio'] as String?,
      producto: json['producto'] as String?,
      hasVerificationDigit: hasDigit,
    );
  }

  factory ReferenceValidationResult.invalid(String message) {
    return ReferenceValidationResult(
      esValida: false,
      mensaje: message,
    );
  }
}
