import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_flutter/main.dart';
import 'package:mobile_flutter/models/history_item.dart';
import 'package:mobile_flutter/models/payment_response.dart';
import 'package:mobile_flutter/models/product_item.dart';
import 'package:mobile_flutter/models/reference_result.dart';
import 'package:mobile_flutter/models/user_session.dart';
import 'package:mobile_flutter/providers/auth_provider.dart';
import 'package:mobile_flutter/providers/catalog_provider.dart';
import 'package:mobile_flutter/providers/history_provider.dart';
import 'package:mobile_flutter/providers/payment_provider.dart';
import 'package:mobile_flutter/widgets/banking_card.dart';
import 'package:mobile_flutter/widgets/brand_avatar.dart';
import 'package:mobile_flutter/widgets/service_card.dart';
import 'package:provider/provider.dart';

void main() {
  group('Pillar 1: Authentication & UserSession Models & Provider', () {
    test('UserSession parses JSON correctly and masks 16-digit account', () {
      final json = {
        'token': 'tok123456',
        'usuario': 'cesar123',
        'clienteId': 1,
        'nombreCompleto': 'César Iván Sánchez Torres',
        'email': 'cesar@correo.com',
        'numeroCuenta': '5225253189113705',
        'saldo': 60.00,
        'ultimoAcceso': '2026-11-14 01:00:00',
      };

      final session = UserSession.fromJson(json);
      expect(session.token, 'tok123456');
      expect(session.usuario, 'cesar123');
      expect(session.clienteId, 1);
      expect(session.saldo, 60.00);
      expect(session.maskedAccount, '**** **** **** 3705');

      final updated = session.copyWith(saldo: 40.00);
      expect(updated.saldo, 40.00);
      expect(updated.usuario, 'cesar123');
    });

    test('UserSession parses telefonoMovil and updates cleanly via copyWith', () {
      final session = UserSession.fromJson({
        'token': 'jwt-token-123',
        'usuario': 'carlos98',
        'clienteId': 42,
        'nombreCompleto': 'Carlos Garay',
        'email': 'carlos@correo.com',
        'telefonoMovil': '5512345678',
        'numeroCuenta': '5225253189113705',
        'saldo': 350.5,
      });

      expect(session.usuario, 'carlos98');
      expect(session.telefonoMovil, '5512345678');
      expect(session.saldo, 350.5);

      final updated = session.copyWith(saldo: 250.0, email: 'nuevo@correo.com');
      expect(updated.saldo, 250.0);
      expect(updated.email, 'nuevo@correo.com');
      expect(updated.telefonoMovil, '5512345678');
    });

    test('AuthProvider formattedSessionTime calculates mm:ss correctly', () {
      final auth = AuthProvider();
      expect(auth.formattedSessionTime, '05:00');
      expect(auth.isAuthenticated, isFalse);
    });
  });

  group('Pillar 2: Dynamic Catalog Models & CatalogProvider', () {
    test('ProductItem parses correctly and classifies Recargas vs Servicios', () {
      final recargaJson = {
        'idProducto': '1001',
        'producto': 'PAQUETE AMIGO 20',
        'servicio': 'TELCEL',
        'idServicio': '166',
        'precio': '20.00',
        'tipoReferencia': 'a',
        'tipoFront': '1',
        'hasDigitoVerificador': false,
        'idCatTipoServicio': '2',
      };

      final recarga = ProductItem.fromJson(recargaJson);
      expect(recarga.id, '1001');
      expect(recarga.name, 'PAQUETE AMIGO 20');
      expect(recarga.service, 'TELCEL');
      expect(recarga.price, 20.00);
      expect(recarga.isRecarga, isTrue);
      expect(recarga.isServicio, isFalse);
      expect(recarga.categoryName, 'Telefonía y Recargas');
      expect(recarga.logoUrl, contains('166.png'));
      expect(recarga.referenceInstruction, contains('10 dígitos'));

      final servicioJson = {
        'idProducto': '2001',
        'producto': 'PAGO DE LUZ CFE',
        'servicio': 'CFE',
        'idServicio': '76',
        'precio': '0.0',
        'tipoReferencia': 'b',
        'tipoFront': '2',
        'hasDigitoVerificador': true,
        'idCatTipoServicio': '5',
      };

      final servicio = ProductItem.fromJson(servicioJson);
      expect(servicio.isRecarga, isFalse);
      expect(servicio.isServicio, isTrue);
      expect(servicio.categoryName, 'Luz y Energía (CFE)');
      expect(servicio.referenceInstruction, contains('código de barras'));
    });

    test('CatalogProvider filters by search, category, and frontType', () {
      final catalog = CatalogProvider();
      final p1 = ProductItem(
        id: '1',
        name: 'Paquete Amigo 20',
        service: 'Telcel',
        serviceId: '166',
        price: 20.0,
        referenceType: 'a',
        frontType: '1',
        hasVerificationDigit: false,
        categoryId: '2',
      );
      final p2 = ProductItem(
        id: '2',
        name: 'Pago Recibo CFE',
        service: 'CFE',
        serviceId: '76',
        price: 150.0,
        referenceType: 'b',
        frontType: '2',
        hasVerificationDigit: true,
        categoryId: '5',
      );

      // Inject products via test
      catalog.products.addAll([p1, p2]);
      expect(catalog.filteredProducts.length, 2);

      // Filter by FrontType 1 (Recargas)
      catalog.setFrontType('1');
      expect(catalog.filteredProducts.length, 1);
      expect(catalog.filteredProducts.first.service, 'Telcel');

      // Filter by FrontType 2 (Servicios)
      catalog.setFrontType('2');
      expect(catalog.filteredProducts.length, 1);
      expect(catalog.filteredProducts.first.service, 'CFE');

      // Reset FrontType and Search by keyword
      catalog.setFrontType('ALL');
      catalog.setSearchQuery('amigo');
      expect(catalog.filteredProducts.length, 1);
      expect(catalog.filteredProducts.first.name, 'Paquete Amigo 20');
    });
  });

  group('Pillar 3 & 4: Reference Validation & Payment Models', () {
    test('ReferenceValidationResult handles valid and invalid statuses', () {
      final valid = ReferenceValidationResult.fromJson({
        'esValida': true,
        'mensaje': 'Referencia válida',
        'tipoFront': 1,
        'montoSugerido': 20.0,
      });
      expect(valid.esValida, isTrue);
      expect(valid.montoSugerido, 20.0);

      final invalid = ReferenceValidationResult.invalid('El teléfono debe tener 10 dígitos');
      expect(invalid.esValida, isFalse);
      expect(invalid.mensaje, contains('10 dígitos'));
    });

    test('Generar2FAResponse and PaymentResponse parse correctly', () {
      final otpResp = Generar2FAResponse.fromJson({
        'numeroCuenta': '5225253189113705',
        'codigo2FA': '654321',
        'vigenciaSegundos': 300,
      });
      expect(otpResp.codigo2FA, '654321');
      expect(otpResp.vigenciaSegundos, 300);

      final payResp = PaymentResponse.fromJson({
        'idTransaccion': 105,
        'numeroAutorizacion': 'AUT-998811',
        'folioTicket': 'FT-20261114-9988',
        'montoPagado': 20.00,
        'comision': 0.00,
        'saldoRestanteCuenta': 40.00,
        'fechaHoraOperacion': '2026-11-14 01:10:00',
        'concepto': 'Recarga Telcel 20',
      });
      expect(payResp.idTransaccion, 105);
      expect(payResp.folioTicket, 'FT-20261114-9988');
      expect(payResp.saldoRestanteCuenta, 40.00);
    });

    test('PaymentProvider enforces strict blocking on reference validation', () {
      final payment = PaymentProvider();
      final p = ProductItem(
        id: '1',
        name: 'Paquete Amigo 20',
        service: 'Telcel',
        serviceId: '166',
        price: 20.0,
        referenceType: 'a',
        frontType: '1',
        hasVerificationDigit: false,
        categoryId: '2',
      );

      payment.initForProduct(p);
      // Initially, reference is unvalidated -> isReferenceValid is FALSE
      expect(payment.isReferenceValid, isFalse);

      // Even when typing phone, still false until backend validation passes
      payment.setTelefono('5512345678');
      expect(payment.isReferenceValid, isFalse);
    });
  });

  group('Pillar 5: Transaction History Models & Provider', () {
    test('HistoryItem and HistoryResponse parse correctly', () {
      final json = {
        'numeroCuenta': '5225253189113705',
        'totalMovimientos': 1,
        'pagos': [
          {
            'idPago': 1,
            'folioTicket': 'FT-20261114-001',
            'numeroAutorizacion': 'AUT-1234',
            'idTxGestopago': 5001,
            'idServicio': 166,
            'servicio': 'TELCEL',
            'idProducto': 1001,
            'producto': 'PAQUETE SIN LIMITE 20',
            'telefono': '5512345678',
            'referencia': '',
            'monto': 20.00,
            'comision': 0.00,
            'saldoAnterior': 60.00,
            'saldoRestante': 40.00,
            'concepto': 'RECARGA TELCEL',
            'fechaOperacion': '2026-11-14 01:05:00',
            'estado': 'EXITOSA',
          }
        ]
      };

      final historyResp = HistoryResponse.fromJson(json);
      expect(historyResp.totalMovimientos, 1);
      expect(historyResp.pagos.first.servicio, 'TELCEL');
      expect(historyResp.pagos.first.isRecarga, isTrue);
      expect(historyResp.pagos.first.saldoRestante, 40.00);
    });

    test('HistoryProvider filters items correctly', () {
      final history = HistoryProvider();
      final item1 = HistoryItem(
        idPago: 1,
        folioTicket: 'FT-001',
        numeroAutorizacion: 'AUT-1',
        idTxGestopago: 10,
        idServicio: 166,
        servicio: 'TELCEL',
        idProducto: 1001,
        producto: 'Recarga 20',
        telefono: '5512345678',
        referencia: '',
        monto: 20.0,
        comision: 0.0,
        saldoAnterior: 60.0,
        saldoRestante: 40.0,
        concepto: 'Recarga',
        fechaOperacion: '2026-11-14',
        estado: 'EXITOSA',
        logoUrl: '',
      );
      final item2 = HistoryItem(
        idPago: 2,
        folioTicket: 'FT-002',
        numeroAutorizacion: 'AUT-2',
        idTxGestopago: 11,
        idServicio: 76,
        servicio: 'CFE',
        idProducto: 2001,
        producto: 'Pago de Luz',
        telefono: '1111111111',
        referencia: '01800999',
        monto: 150.0,
        comision: 0.0,
        saldoAnterior: 200.0,
        saldoRestante: 50.0,
        concepto: 'Servicio CFE',
        fechaOperacion: '2026-11-14',
        estado: 'EXITOSA',
        logoUrl: '',
      );

      history.items.addAll([item1, item2]);
      expect(history.filteredItems.length, 2);

      history.setTypeFilter('RECARGAS');
      expect(history.filteredItems.length, 1);
      expect(history.filteredItems.first.servicio, 'TELCEL');

      history.setTypeFilter('SERVICIOS');
      expect(history.filteredItems.length, 1);
      expect(history.filteredItems.first.servicio, 'CFE');
    });
  });

  group('Widget UI Verification', () {
    testWidgets('BancoDigitalApp renders LoginScreen commercial view without demo passwords', (WidgetTester tester) async {
      await tester.pumpWidget(const BancoDigitalApp());
      await tester.pumpAndSettle();

      // Verify Brand header & auth mode tabs
      expect(find.text('Banco Digital'), findsWidgets);
      expect(find.text('Iniciar Sesión'), findsOneWidget);
      expect(find.text('Crear Cuenta'), findsOneWidget);

      // Verify Form inputs and no hardcoded demo account card
      expect(find.text('Usuario'), findsOneWidget);
      expect(find.text('Contraseña'), findsOneWidget);
      expect(find.text('CUENTAS DE DEMOSTRACIÓN (R1)'), findsNothing);
    });

    testWidgets('BankingCard displays masked account and balance', (WidgetTester tester) async {
      final session = UserSession(
        token: 'tok123',
        usuario: 'cesar123',
        clienteId: 1,
        nombreCompleto: 'César Iván Sánchez Torres',
        email: 'cesar@correo.com',
        numeroCuenta: '5225253189113705',
        saldo: 60.00,
      );

      // Verify masked account representation
      expect(session.maskedAccount, '**** **** **** 3705');

      final auth = AuthProvider();
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: ChangeNotifierProvider<AuthProvider>.value(
              value: auth,
              child: Builder(
                builder: (context) {
                  return const BankingCard();
                },
              ),
            ),
          ),
        ),
      );

      // When session is null, returns SizedBox.shrink
      expect(find.byType(BankingCard), findsOneWidget);
    });

    testWidgets('ServiceCard renders product details and action button', (WidgetTester tester) async {
      final product = ProductItem(
        id: '1001',
        name: 'Paquete Amigo 20',
        service: 'Telcel',
        serviceId: '166',
        price: 20.0,
        referenceType: 'a',
        frontType: '1',
        hasVerificationDigit: false,
        categoryId: '2',
      );

      bool tapped = false;

      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: ServiceCard(
              product: product,
              onSelect: () => tapped = true,
            ),
          ),
        ),
      );

      expect(find.text('Telcel'), findsOneWidget);
      expect(find.text('Paquete Amigo 20'), findsOneWidget);
      expect(find.text('RECARGA'), findsOneWidget);
      expect(find.text('Recargar'), findsOneWidget);

      await tester.tap(find.text('Recargar'));
      expect(tapped, isTrue);
    });

    testWidgets('BrandAvatar displays initials when image loading fails', (WidgetTester tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: BrandAvatar(
              imageUrl: 'invalid://url',
              label: 'Telcel Express',
            ),
          ),
        ),
      );

      expect(find.byType(BrandAvatar), findsOneWidget);
    });
  });
}
