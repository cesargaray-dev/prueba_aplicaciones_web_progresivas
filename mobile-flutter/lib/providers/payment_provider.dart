import 'dart:async';
import 'package:flutter/foundation.dart';
import '../models/payment_response.dart';
import '../models/product_item.dart';
import '../models/reference_result.dart';
import '../services/api_client.dart';
import '../services/payment_service.dart';
import '../utils/validation_utils.dart';

class PaymentProvider with ChangeNotifier {
  final PaymentService _paymentService;

  ProductItem? _selectedProduct;
  String _referencia = '';
  String _telefono = '';
  double _monto = 0.0;
  bool _isValidating = false;
  ReferenceValidationResult? _validationResult;
  String? _validationErrorMessage;

  bool _isGenerating2FA = false;
  Generar2FAResponse? _twoFAResponse;
  String _enteredOtp = '';
  bool _isConfirmed = false;
  Timer? _twoFATimer;
  final ValueNotifier<int> twoFASecondsNotifier = ValueNotifier<int>(0);

  bool _isPaying = false;
  String? _paymentError;

  PaymentProvider({PaymentService? paymentService})
      : _paymentService = paymentService ?? PaymentService();

  ProductItem? get selectedProduct => _selectedProduct;
  String get referencia => _referencia;
  String get telefono => _telefono;
  double get monto => _monto;
  bool get isValidating => _isValidating;
  ReferenceValidationResult? get validationResult => _validationResult;
  String? get validationErrorMessage => _validationErrorMessage;

  bool get isGenerating2FA => _isGenerating2FA;
  Generar2FAResponse? get twoFAResponse => _twoFAResponse;
  String get enteredOtp => _enteredOtp;
  bool get isConfirmed => _isConfirmed;
  int get twoFASecondsLeft => twoFASecondsNotifier.value;
  int get twoFARemainingSeconds => twoFASecondsNotifier.value;

  String get formatted2FATime {
    final secs = twoFASecondsNotifier.value;
    if (secs <= 0) return 'Expirado';
    final m = (secs ~/ 60).toString().padLeft(2, '0');
    final s = (secs % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  bool get isPaying => _isPaying;
  String? get paymentError => _paymentError;

  bool get isReferenceValid => _validationResult?.esValida == true;

  bool get canProceedToConfirm {
    if (!isReferenceValid || _selectedProduct == null) return false;
    return ValidationUtils.esMontoPagoValido(
      _monto,
      frontType: _selectedProduct!.frontType,
    );
  }

  bool get canSubmitPayment =>
      canProceedToConfirm &&
      _twoFAResponse != null &&
      twoFASecondsNotifier.value > 0 &&
      !_isGenerating2FA &&
      _enteredOtp.trim().length == 6 &&
      _isConfirmed &&
      !_isPaying;

  void initForProduct(ProductItem product, {bool notify = true}) {
    _selectedProduct = product;
    _referencia = '';
    _telefono = '';
    _monto = product.price > 0 ? product.price : 0.0;
    _isValidating = false;
    _validationResult = null;
    _validationErrorMessage = null;
    _isGenerating2FA = false;
    _twoFAResponse = null;
    _enteredOtp = '';
    _isConfirmed = false;
    _twoFATimer?.cancel();
    _twoFATimer = null;
    twoFASecondsNotifier.value = 0;
    _isPaying = false;
    _paymentError = null;
    if (notify) notifyListeners();
  }

  void setReferencia(String val) {
    _referencia = val;
    _validationResult = null;
    _validationErrorMessage = null;
    notifyListeners();
  }

  void setTelefono(String val) {
    _telefono = val;
    _validationResult = null;
    _validationErrorMessage = null;
    notifyListeners();
  }

  void setMonto(double val) {
    _monto = val;
    notifyListeners();
  }

  void setEnteredOtp(String val) {
    _enteredOtp = val;
    notifyListeners();
  }

  void setConfirmed(bool val) {
    _isConfirmed = val;
    notifyListeners();
  }

  Future<bool> validateReference() async {
    if (_selectedProduct == null) return false;

    final refToSend = _selectedProduct!.isRecarga ? _telefono.trim() : _referencia.trim();
    if (_selectedProduct!.isRecarga) {
      final diag = ValidationUtils.diagnosticarTelefono(refToSend);
      if (diag != null) {
        _validationResult = ReferenceValidationResult.invalid(diag);
        _validationErrorMessage = diag;
        notifyListeners();
        return false;
      }
    } else if (refToSend.isEmpty) {
      const msg = 'Ingresa una referencia válida antes de continuar.';
      _validationResult = ReferenceValidationResult.invalid(msg);
      _validationErrorMessage = msg;
      notifyListeners();
      return false;
    }

    _isValidating = true;
    _validationErrorMessage = null;
    _validationResult = null;
    notifyListeners();

    try {
      final result = await _paymentService.validarReferencia(
        idServicio: int.tryParse(_selectedProduct!.serviceId) ?? 0,
        idProducto: int.tryParse(_selectedProduct!.id) ?? 0,
        referencia: refToSend,
        monto: _monto > 0 ? _monto : null,
      );

      _validationResult = result;
      if (result.esValida) {
        if (result.montoSugerido != null && result.montoSugerido! > 0 && _monto <= 0) {
          _monto = result.montoSugerido!;
        }
      } else {
        _validationErrorMessage = result.mensaje;
      }
      _isValidating = false;
      notifyListeners();
      return result.esValida;
    } on ApiException catch (e) {
      _validationErrorMessage = e.message;
      _validationResult = ReferenceValidationResult.invalid(e.message);
      _isValidating = false;
      notifyListeners();
      return false;
    } catch (_) {
      const msg = 'No fue posible validar la referencia con el servidor.';
      _validationErrorMessage = msg;
      _validationResult = ReferenceValidationResult.invalid(msg);
      _isValidating = false;
      notifyListeners();
      return false;
    }
  }

  Future<bool> request2FA(String numeroCuenta) async {
    _isGenerating2FA = true;
    _paymentError = null;
    _enteredOtp = '';
    _twoFATimer?.cancel();
    _twoFATimer = null;
    notifyListeners();

    try {
      final resp = await _paymentService.generar2FA(numeroCuenta);
      _twoFAResponse = resp;
      _isGenerating2FA = false;

      twoFASecondsNotifier.value = resp.vigenciaSegundos;
      _twoFATimer = Timer.periodic(const Duration(seconds: 1), (timer) {
        final current = twoFASecondsNotifier.value;
        if (current > 1) {
          twoFASecondsNotifier.value = current - 1;
        } else {
          twoFASecondsNotifier.value = 0;
          timer.cancel();
          notifyListeners();
        }
      });

      notifyListeners();
      return true;
    } on ApiException catch (e) {
      _paymentError = e.message;
      _isGenerating2FA = false;
      notifyListeners();
      return false;
    } catch (_) {
      _paymentError = 'No fue posible generar el código 2FA.';
      _isGenerating2FA = false;
      notifyListeners();
      return false;
    }
  }

  Future<PaymentResponse?> executePayment(
    String numeroCuenta, {
    String? telefonoPerfil,
  }) async {
    if (_selectedProduct == null) return null;

    if (!ValidationUtils.esMontoPagoValido(
      _monto,
      frontType: _selectedProduct!.frontType,
    )) {
      final minStr = _selectedProduct!.frontType.trim() == '1' ? '\$1.00' : '\$10.00';
      _paymentError = 'El monto a pagar debe estar entre $minStr y \$50,000.00 MXN.';
      notifyListeners();
      return null;
    }

    if (twoFASecondsNotifier.value <= 0) {
      _paymentError = 'El código 2FA expiró. Solicita uno nuevo.';
      notifyListeners();
      return null;
    }

    _isPaying = true;
    _paymentError = null;
    notifyListeners();

    final isRecarga = _selectedProduct!.isRecarga;
    final cleanPhonePerfil = ValidationUtils.esTelefonoMexicanoValido(telefonoPerfil)
        ? telefonoPerfil!.trim()
        : null;
    final telefonoToSend = isRecarga ? _telefono.trim() : cleanPhonePerfil;
    final referenciaToSend = isRecarga ? _telefono.trim() : _referencia.trim();

    try {
      final resp = await _paymentService.pagarServicio(
        numeroCuenta: numeroCuenta,
        idServicio: int.tryParse(_selectedProduct!.serviceId) ?? 0,
        idProducto: int.tryParse(_selectedProduct!.id) ?? 0,
        monto: _monto,
        telefono: telefonoToSend,
        referencia: referenciaToSend,
        codigo2FA: _enteredOtp.trim(),
      );

      _twoFATimer?.cancel();
      _twoFATimer = null;
      _isPaying = false;
      notifyListeners();
      return resp;
    } on ApiException catch (e) {
      _paymentError = e.message;
      _isPaying = false;
      notifyListeners();
      return null;
    } catch (_) {
      _paymentError = 'Error al procesar el pago con el proveedor.';
      _isPaying = false;
      notifyListeners();
      return null;
    }
  }

  Future<EmailEnvioResultado> reenviarComprobante({
    required String numeroCuenta,
    required String folioTicket,
    String? correoDestino,
  }) {
    return _paymentService.reenviarComprobante(
      numeroCuenta: numeroCuenta,
      folioTicket: folioTicket,
      correoDestino: correoDestino,
    );
  }

  Future<Uint8List> downloadReceiptPdf(String folioTicket) {
    return _paymentService.downloadReceiptPdf(folioTicket);
  }

  void reset() {
    _twoFATimer?.cancel();
    _twoFATimer = null;
    twoFASecondsNotifier.value = 0;
    _selectedProduct = null;
    _referencia = '';
    _telefono = '';
    _monto = 0.0;
    _isValidating = false;
    _validationResult = null;
    _validationErrorMessage = null;
    _isGenerating2FA = false;
    _twoFAResponse = null;
    _enteredOtp = '';
    _isConfirmed = false;
    _isPaying = false;
    _paymentError = null;
    notifyListeners();
  }

  @override
  void dispose() {
    _twoFATimer?.cancel();
    twoFASecondsNotifier.dispose();
    super.dispose();
  }
}
