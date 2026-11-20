import '../utils/validation_utils.dart';

class ProductItem {
  final String id;
  final String name;
  final String service;
  final String serviceId;
  final double price;
  final String referenceType;
  final String frontType;
  final bool hasVerificationDigit;
  final String categoryId;
  final String? legend;
  final bool showAyuda;
  final String? _rawLogoUrl;

  ProductItem({
    required this.id,
    required this.name,
    required this.service,
    required this.serviceId,
    required this.price,
    required this.referenceType,
    required this.frontType,
    required this.hasVerificationDigit,
    required this.categoryId,
    this.legend,
    this.showAyuda = false,
    String? logoUrl,
  }) : _rawLogoUrl = logoUrl;

  static String? sanitizeLegend(String? raw) {
    if (raw == null) return null;
    final cleaned = raw
        .replaceAll(RegExp(r'<br\s*/?>', caseSensitive: false), ' ')
        .replaceAll(RegExp(r'<[^>]*>'), ' ')
        .replaceAll(RegExp(r'\s+'), ' ')
        .trim();
    return cleaned.isEmpty ? null : cleaned;
  }

  factory ProductItem.fromJson(Map<String, dynamic> json) {
    final rawPrice = json['precio']?.toString() ?? json['price']?.toString() ?? '0';
    final parsedPrice = double.tryParse(rawPrice) ?? 0.0;

    final rawDigit = json['hasDigitoVerificador']?.toString() ??
        json['hasVerificationDigit']?.toString() ??
        'false';

    final rawShowAyuda = json['showAyuda']?.toString() ?? 'false';
    final rawLogo = json['logoUrl']?.toString().trim();

    return ProductItem(
      id: (json['idProducto'] ?? json['id'] ?? '').toString(),
      name: (json['producto'] ?? json['name'] ?? '').toString(),
      service: (json['servicio'] ?? json['service'] ?? '').toString(),
      serviceId: (json['idServicio'] ?? json['serviceId'] ?? '').toString(),
      price: parsedPrice,
      referenceType: (json['tipoReferencia'] ?? json['referenceType'] ?? 'a').toString(),
      frontType: (json['tipoFront'] ?? json['frontType'] ?? '2').toString(),
      hasVerificationDigit: rawDigit.toLowerCase() == 'true',
      categoryId: (json['idCatTipoServicio'] ?? json['categoryId'] ?? '').toString(),
      legend: sanitizeLegend(json['legend']?.toString() ?? json['leyenda']?.toString()),
      showAyuda: rawShowAyuda.toLowerCase() == 'true',
      logoUrl: (rawLogo != null && rawLogo.isNotEmpty) ? rawLogo : null,
    );
  }

  /// Indica si el producto es únicamente informativo (consulta de saldo frontType 4 o 5).
  bool get isConsultaSaldo => frontType == '4' || frontType == '5';

  /// Indica si corresponde a recarga celular o paquete móvil con validación telefónica obligatoria.
  bool get isRecarga => ValidationUtils.esRecargaTelefonica(
        frontType: frontType,
        categoryId: categoryId,
        service: service,
      );

  bool get isServicio => !isRecarga;

  bool get isFront1VoucherOrTag => frontType == '1' && !isRecarga;

  /// Determina la categoría legible en UI (alineada con Web CatalogPage)
  String get categoryName {
    final s = service.toUpperCase();
    final n = name.toUpperCase();
    if (isRecarga || ValidationUtils.categoriasTelefonia.contains(categoryId)) {
      return 'Telefonía y Recargas';
    }
    if (categoryId == '5' || s.contains('CFE') || s.contains('LUZ') || n.contains('CFE')) {
      return 'Luz y Energía (CFE)';
    }
    if (categoryId == '15' ||
        s.contains('AGUA') ||
        s.contains('SAPA') ||
        s.contains('SIAPA') ||
        s.contains('CEA') ||
        s.contains('JAPAY') ||
        s.contains('COMAPA')) {
      return 'Agua Potable';
    }
    if (categoryId == '4' ||
        s.contains('TELMEX') ||
        s.contains('IZZI') ||
        s.contains('TOTALPLAY') ||
        s.contains('SKY') ||
        s.contains('DISH') ||
        s.contains('MEGACABLE')) {
      return 'Internet y Televisión';
    }
    if (categoryId == '14' ||
        categoryId == '18' ||
        s.contains('TAG') ||
        s.contains('PASE') ||
        s.contains('TELEVIA') ||
        s.contains('EASYTRIP')) {
      return 'TAG y Casetas';
    }
    if (categoryId == '8' ||
        s.contains('CREDITO') ||
        s.contains('BANCO') ||
        s.contains('AFORE') ||
        s.contains('FINAN') ||
        s.contains('INFONAVIT')) {
      return 'Financiero y Créditos';
    }
    if (categoryId == '10' ||
        categoryId == '11' ||
        categoryId == '16' ||
        s.contains('AMAZON') ||
        s.contains('XBOX') ||
        s.contains('NETFLIX') ||
        s.contains('PLAYSTATION') ||
        s.contains('SPOTIFY') ||
        s.contains('YOUTUBE') ||
        s.contains('NINTENDO') ||
        s.contains('GOOGLE PLAY') ||
        s.contains('KATAPLUM') ||
        s.contains('ACUARIO') ||
        s.contains('AZTLAN') ||
        s.contains('RECORCHOLIS') ||
        s.contains('AMESHOP') ||
        s.contains('KINGSBET')) {
      return 'Streaming y Pines';
    }
    return 'Otros Servicios';
  }

  String get logoUrl {
    if (_rawLogoUrl != null && _rawLogoUrl.isNotEmpty) return _rawLogoUrl;
    if (serviceId.isEmpty) return '';
    return 'https://gestopago.portalventas.net/sistema/images/gestopago/servicios/$serviceId.png';
  }

  String get referenceInstruction {
    if (isRecarga) {
      return 'Ingresa el número celular de 10 dígitos (México).';
    }
    if (isFront1VoucherOrTag) {
      return 'Ingresa el identificador, tarjeta o TAG para acreditar el servicio.';
    }
    switch (referenceType.toLowerCase()) {
      case 'b':
        return 'Ingresa el código de barras numérico (8 a 30 dígitos).';
      case 'c':
        return 'Ingresa el número de contrato/cuenta (6 a 16 dígitos).';
      case 'bc':
        return 'Ingresa contrato (10-12 dígitos) o código de barras (20-30 dígitos).';
      case 'a':
      default:
        return 'Ingresa la referencia alfanumérica de tu recibo o cuenta.';
    }
  }
}
