import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_flutter/models/payment_response.dart';
import 'package:mobile_flutter/models/product_item.dart';
import 'package:mobile_flutter/providers/catalog_provider.dart';
import 'package:mobile_flutter/services/catalog_service.dart';
import 'package:mobile_flutter/utils/validation_utils.dart';

class _FakeCatalogService extends CatalogService {
  final List<ProductItem> items;
  _FakeCatalogService(this.items);

  @override
  Future<List<ProductItem>> getProducts() async => items;
}

void main() {
  group('ValidationUtils - Validación Telefónica Mexicana (IFT)', () {
    test('rechaza dígitos repetidos, secuencias y prefijos 0 o 1 (excepto 1111111111)', () {
      for (final invalido in [
        '',
        '55123',
        '0000000000',
        '5555555555',
        '1234567890',
        '0123456789',
        '9876543210',
        '0512345678',
        '1512345678',
      ]) {
        expect(
          ValidationUtils.esTelefonoMexicanoValido(invalido),
          isFalse,
          reason: 'Debe rechazar teléfono inválido: $invalido',
        );
        expect(ValidationUtils.diagnosticarTelefono(invalido), isNotNull);
      }
    });

    test('acepta teléfonos reales mexicanos de 10 dígitos y el número de prueba GestoPago 1111111111', () {
      for (final valido in ['1111111111', '5512345678', '3319876543', '8182345679']) {
        expect(ValidationUtils.esTelefonoMexicanoValido(valido), isTrue);
        expect(ValidationUtils.diagnosticarTelefono(valido), isNull);
      }
    });

    test('normalizarBusquedaCatalogo empareja AT&T con el nombre GestoPago AT-T', () async {
      expect(ValidationUtils.normalizarBusquedaCatalogo('AT&T'), 'att');
      expect(ValidationUtils.normalizarBusquedaCatalogo('AT-T'), 'att');
      expect(ValidationUtils.normalizarBusquedaCatalogo('  At&t  '), 'att');

      final attRecharge = ProductItem(
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
      final telcelRecharge = ProductItem(
        id: '403',
        name: 'Recarga \$20',
        service: 'Telcel',
        serviceId: '133',
        price: 20,
        referenceType: 'a',
        frontType: '1',
        hasVerificationDigit: false,
        categoryId: '3',
      );

      final provider = CatalogProvider(
        catalogService: _FakeCatalogService([attRecharge, telcelRecharge]),
      );
      await provider.loadProducts();
      provider.setSearchQuery('AT&T');
      expect(provider.filteredProducts.length, 1);
      expect(provider.filteredProducts.first.service, 'AT-T');
      expect(provider.filteredProducts.first.isRecarga, isTrue);
    });
  });

  group('ValidationUtils & ProductItem - Clasificación GestoPago', () {
    test('identifica recargas reales vs pines/tags con tipoFront=1', () {
      final telcel = ProductItem(
        id: '10',
        name: 'Recarga 100',
        service: 'Telcel',
        serviceId: '1',
        price: 100,
        referenceType: 'a',
        frontType: '1',
        hasVerificationDigit: false,
        categoryId: '1',
      );
      final amazon = ProductItem(
        id: '20',
        name: 'Amazon \$300',
        service: 'Amazon Cash',
        serviceId: '2',
        price: 300,
        referenceType: 'a',
        frontType: '1',
        hasVerificationDigit: false,
        categoryId: '10',
      );
      final pase = ProductItem(
        id: '30',
        name: 'PASE Tag',
        service: 'PASE Urbano',
        serviceId: '3',
        price: 200,
        referenceType: 'a',
        frontType: '1',
        hasVerificationDigit: false,
        categoryId: '18',
      );
      final consultaSaldo = ProductItem(
        id: '40',
        name: 'Consulta Saldo Telmex',
        service: 'Telmex Consulta',
        serviceId: '4',
        price: 0,
        referenceType: 'a',
        frontType: '4',
        hasVerificationDigit: false,
        categoryId: '4',
      );

      expect(telcel.isRecarga, isTrue);
      expect(telcel.isFront1VoucherOrTag, isFalse);

      expect(amazon.isRecarga, isFalse);
      expect(amazon.isFront1VoucherOrTag, isTrue);

      expect(pase.isRecarga, isFalse);
      expect(pase.isFront1VoucherOrTag, isTrue);
      expect(pase.categoryName, 'TAG y Casetas');

      expect(consultaSaldo.isConsultaSaldo, isTrue);
    });
  });

  group('Validación de Correos, Contraseñas y Modelos Brevo', () {
    test('detecta correos placeholder y contraseñas fuertes', () {
      expect(ValidationUtils.esCorreoPlaceholder('cesar.demo@ejemplo.com'), isTrue);
      expect(ValidationUtils.esCorreoPlaceholder('user@example.com'), isTrue);
      expect(ValidationUtils.esCorreoPlaceholder('cliente@gmail.com'), isFalse);

      expect(ValidationUtils.esCorreoValido('cliente+e2e@gmail.com'), isTrue);
      expect(ValidationUtils.esCorreoValido('sin-arroba'), isFalse);

      expect(ValidationUtils.esContrasenaSegura('Pass1234'), isTrue);
      expect(ValidationUtils.esContrasenaSegura('corta1A'), isFalse);
      expect(ValidationUtils.esContrasenaSegura('sinnumerosA'), isFalse);
    });

    test('Generar2FAResponse y PaymentResponse parsean estado real de correo Brevo', () {
      final resp2fa = Generar2FAResponse.fromJson({
        'numeroCuenta': '5225253189113705',
        'vigenciaSegundos': 300,
        'correoEnviado': true,
        'correoDestino': 'cliente@gmail.com',
        'mensajeCorreo': 'Enviado correctamente',
        'messageId': '<msg-123@brevo.com>',
      });
      expect(resp2fa.codigo2FA, isNull);
      expect(resp2fa.correoEnviado, isTrue);
      expect(resp2fa.correoDestino, 'cliente@gmail.com');
      expect(resp2fa.messageId, '<msg-123@brevo.com>');

      final pago = PaymentResponse.fromJson({
        'codigo': 0,
        'mensaje': 'Pago exitoso',
        'numeroAutorizacion': 'AUTH-99',
        'folioTicket': 'FOL-99',
        'montoPagado': 150.0,
        'comision': 0.0,
        'saldoRestanteCuenta': 350.0,
        'fechaHoraOperacion': '2026-11-14T10:00:00',
        'concepto': 'Telcel 150',
        'correoEnviado': true,
        'correoDestino': 'cliente@gmail.com',
        'mensajeCorreo': 'Comprobante entregado',
        'brevoMessageId': '<rec-99@brevo.com>',
      });
      expect(pago.correoEnviado, isTrue);
      expect(pago.brevoMessageId, '<rec-99@brevo.com>');
    });
  });
}
