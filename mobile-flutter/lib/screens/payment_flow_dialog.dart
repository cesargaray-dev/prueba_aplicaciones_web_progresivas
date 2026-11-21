import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../models/product_item.dart';
import '../providers/auth_provider.dart';
import '../providers/payment_provider.dart';
import '../theme/app_theme.dart';
import '../utils/validation_utils.dart';
import '../widgets/brand_avatar.dart';
import '../widgets/profile_sheet.dart';
import '../widgets/receipt_dialog.dart';

class PaymentFlowDialog extends StatefulWidget {
  final ProductItem product;
  final VoidCallback onViewHistory;

  const PaymentFlowDialog({
    super.key,
    required this.product,
    required this.onViewHistory,
  });

  static Future<void> show(
    BuildContext context, {
    required ProductItem product,
    required VoidCallback onViewHistory,
  }) {
    return showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => PaymentFlowDialog(
        product: product,
        onViewHistory: onViewHistory,
      ),
    );
  }

  @override
  State<PaymentFlowDialog> createState() => _PaymentFlowDialogState();
}

class _PaymentFlowDialogState extends State<PaymentFlowDialog> {
  int _currentStep = 1; // 1 = Reference validation, 2 = 2FA and Confirmation
  final _referenceController = TextEditingController();
  final _phoneController = TextEditingController();
  final _montoController = TextEditingController();
  final _otpController = TextEditingController();
  bool _confirmed = false;
  bool _phoneValidateAttempted = false;

  bool get _isFixedPrice => widget.product.frontType == '1' && widget.product.price > 0;

  @override
  void initState() {
    super.initState();
    final payment = Provider.of<PaymentProvider>(context, listen: false);
    payment.initForProduct(widget.product, notify: false);

    if (_isFixedPrice) {
      _montoController.text = widget.product.price.toStringAsFixed(2);
    }
  }

  @override
  void dispose() {
    _referenceController.dispose();
    _phoneController.dispose();
    _montoController.dispose();
    _otpController.dispose();
    super.dispose();
  }

  String _format2FA(int totalSeconds) {
    if (totalSeconds <= 0) return 'Expirado';
    final m = (totalSeconds ~/ 60).toString().padLeft(2, '0');
    final s = (totalSeconds % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  Future<void> _handleValidate() async {
    final payment = Provider.of<PaymentProvider>(context, listen: false);

    double montoVal = _isFixedPrice ? widget.product.price : 0.0;
    if (_montoController.text.isNotEmpty) {
      montoVal = double.tryParse(_montoController.text) ?? montoVal;
    }
    payment.setMonto(montoVal);

    if (widget.product.isRecarga) {
      final phone = _phoneController.text.trim();
      setState(() {
        _phoneValidateAttempted = true;
      });
      final diag = ValidationUtils.diagnosticarTelefono(phone);
      if (diag != null) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(diag),
            backgroundColor: AppTheme.errorRed,
            duration: const Duration(seconds: 3),
          ),
        );
        return;
      }
      payment.setTelefono(phone);
    } else {
      payment.setReferencia(_referenceController.text.trim());
    }

    final isValid = await payment.validateReference();
    if (!mounted) return;

    if (isValid && payment.monto > 0 && _montoController.text.isEmpty) {
      _montoController.text = payment.monto.toStringAsFixed(2);
    }

    if (!isValid) {
      final msg = payment.validationResult?.mensaje ??
          payment.validationErrorMessage ??
          'Referencia no válida';
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(msg),
          backgroundColor: AppTheme.errorRed,
          duration: const Duration(seconds: 3),
        ),
      );
    }
  }

  Future<void> _proceedToStep2() async {
    final auth = Provider.of<AuthProvider>(context, listen: false);
    final payment = Provider.of<PaymentProvider>(context, listen: false);

    if (!payment.isReferenceValid) return;

    final montoVal = _isFixedPrice
        ? widget.product.price
        : (double.tryParse(_montoController.text.trim()) ?? payment.monto);
    if (!ValidationUtils.esMontoPagoValido(
      montoVal,
      frontType: widget.product.frontType,
    )) {
      final minText = widget.product.frontType.trim() == '1' ? '\$1.00' : '\$10.00';
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Ingresa un monto válido entre $minText y \$50,000.00 MXN.'),
          backgroundColor: AppTheme.errorRed,
          duration: const Duration(seconds: 3),
        ),
      );
      return;
    }
    payment.setMonto(montoVal);

    final session = auth.session;
    if (session == null) return;

    setState(() {
      _currentStep = 2;
      _confirmed = false;
    });
    _otpController.clear();
    payment.setEnteredOtp('');

    await payment.request2FA(session.numeroCuenta);
  }

  Future<void> _resend2FA() async {
    final auth = Provider.of<AuthProvider>(context, listen: false);
    final payment = Provider.of<PaymentProvider>(context, listen: false);
    final session = auth.session;
    if (session == null) return;

    _otpController.clear();
    payment.setEnteredOtp('');
    await payment.request2FA(session.numeroCuenta);
  }

  Future<void> _openAbonarSheet() async {
    await showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: AppTheme.cardBg(context),
      constraints: const BoxConstraints(maxWidth: 520),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (_) => const ProfileSheet(initialTab: 1),
    );
  }

  Future<void> _executePayment() async {
    final auth = Provider.of<AuthProvider>(context, listen: false);
    final payment = Provider.of<PaymentProvider>(context, listen: false);
    final session = auth.session;
    if (session == null) return;

    final result = await payment.executePayment(
      session.numeroCuenta,
      telefonoPerfil: session.telefonoMovil,
    );
    if (!mounted) return;

    if (result != null) {
      if (result.isSuccess) {
        auth.updateBalance(result.saldoRestanteCuenta);
      }

      Navigator.of(context).pop();

      showDialog(
        context: context,
        barrierDismissible: false,
        builder: (ctx) => ReceiptDialog(
          receipt: result,
          product: widget.product,
          referenciaORPhone: widget.product.isRecarga ? payment.telefono : payment.referencia,
          onViewHistory: widget.onViewHistory,
        ),
      );
    } else {
      final error = payment.paymentError ?? 'No se pudo procesar el pago';
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(error),
          backgroundColor: AppTheme.errorRed,
          duration: const Duration(seconds: 4),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final payment = Provider.of<PaymentProvider>(context);
    final auth = Provider.of<AuthProvider>(context);
    final session = auth.session;
    final currencyFormatter = NumberFormat.currency(locale: 'es_MX', symbol: '\$');
    final saldoActual = session?.saldo ?? 0.0;
    final saldoPosterior = saldoActual - payment.monto;
    final insufficientBalance = saldoActual < payment.monto;
    final showModHint = widget.product.hasVerificationDigit ||
        (payment.validationResult?.hasVerificationDigit ?? false);

    return Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      backgroundColor: AppTheme.cardBg(context),
      insetPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 24),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 480),
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Dialog Header: Step progress & close button
              Row(
                children: [
                  Expanded(
                    child: Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                          decoration: BoxDecoration(
                            color: AppTheme.brandAccent(context).withValues(alpha: 0.14),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Text(
                            'PASO $_currentStep DE 2',
                            style: TextStyle(
                              color: AppTheme.brandAccent(context),
                              fontSize: 11,
                              fontWeight: FontWeight.bold,
                              letterSpacing: 0.5,
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Flexible(
                          child: Text(
                            _currentStep == 1 ? 'Validación de Referencia' : 'Confirmación 2FA',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.bold,
                              color: AppTheme.textPrimary(context),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    icon: Icon(Icons.close_rounded, color: AppTheme.textSecondary(context)),
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ],
              ),

              const SizedBox(height: 12),

              // Selected Product Info Banner
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppTheme.elevatedBg(context),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: AppTheme.borderCol(context)),
                ),
                child: Row(
                  children: [
                    BrandAvatar(
                      imageUrl: widget.product.logoUrl,
                      label: widget.product.service,
                      size: 42,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            widget.product.service,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.bold,
                              color: AppTheme.textPrimary(context),
                            ),
                          ),
                          Text(
                            widget.product.name,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              fontSize: 12,
                              color: AppTheme.textSecondary(context),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      _isFixedPrice
                          ? currencyFormatter.format(widget.product.price)
                          : 'Monto libre',
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.bold,
                        color: AppTheme.brandAccent(context),
                      ),
                    ),
                  ],
                ),
              ),

              if (widget.product.legend != null && widget.product.legend!.isNotEmpty) ...[
                const SizedBox(height: 10),
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: AppTheme.secondaryBlue.withValues(alpha: 0.06),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: AppTheme.secondaryBlue.withValues(alpha: 0.2)),
                  ),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Icon(Icons.info_outline_rounded, size: 16, color: AppTheme.secondaryBlue),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          widget.product.legend!,
                          style: TextStyle(fontSize: 12, color: AppTheme.textPrimary(context)),
                        ),
                      ),
                    ],
                  ),
                ),
              ],

              const SizedBox(height: 18),

              // Step 1: Input & Validation
              if (_currentStep == 1) ...[
                Text(
                  widget.product.referenceInstruction,
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: AppTheme.textPrimary(context),
                  ),
                ),
                if (showModHint) ...[
                  const SizedBox(height: 6),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                    decoration: BoxDecoration(
                      color: AppTheme.brandAccent(context).withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(
                        color: AppTheme.brandAccent(context).withValues(alpha: 0.3),
                      ),
                    ),
                    child: Row(
                      children: [
                        Icon(
                          Icons.calculate_outlined,
                          size: 15,
                          color: AppTheme.brandAccent(context),
                        ),
                        const SizedBox(width: 6),
                        Expanded(
                          child: Text(
                            'Esta referencia incluye dígito verificador (Módulo 10 / 11). Verifica el último dígito.',
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.w600,
                              color: AppTheme.brandAccent(context),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
                const SizedBox(height: 8),

                if (widget.product.isRecarga) ...[
                  if (session != null && ValidationUtils.esTelefonoMexicanoValido(session.telefonoMovil)) ...[
                    Align(
                      alignment: Alignment.centerRight,
                      child: ActionChip(
                        backgroundColor: AppTheme.brandAccent(context).withValues(alpha: 0.12),
                        side: BorderSide(
                          color: AppTheme.brandAccent(context).withValues(alpha: 0.35),
                        ),
                        avatar: Icon(
                          Icons.smartphone_rounded,
                          size: 16,
                          color: AppTheme.brandAccent(context),
                        ),
                        label: Text(
                          'Usar mi celular (${session.telefonoMovil})',
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: AppTheme.brandAccent(context),
                          ),
                        ),
                        onPressed: () {
                          final tel = session.telefonoMovil!.trim();
                          setState(() {
                            _phoneController.text = tel;
                            _phoneValidateAttempted = false;
                          });
                          payment.setTelefono(tel);
                        },
                      ),
                    ),
                    const SizedBox(height: 6),
                  ],
                  TextField(
                    controller: _phoneController,
                    keyboardType: TextInputType.phone,
                    maxLength: 10,
                    inputFormatters: [
                      FilteringTextInputFormatter.digitsOnly,
                      LengthLimitingTextInputFormatter(10),
                    ],
                    onChanged: (val) {
                      setState(() {});
                      payment.setTelefono(val);
                    },
                    decoration: InputDecoration(
                      labelText: 'Número Celular (10 dígitos)',
                      hintText: '5512345678',
                      counterText: '${_phoneController.text.length}/10',
                      errorText: (_phoneValidateAttempted || _phoneController.text.length == 10)
                          ? ValidationUtils.diagnosticarTelefono(_phoneController.text)
                          : null,
                      prefixIcon: Icon(
                        Icons.phone_iphone_rounded,
                        color: AppTheme.textSecondary(context),
                      ),
                    ),
                  ),
                ] else ...[
                  TextField(
                    controller: _referenceController,
                    keyboardType: TextInputType.text,
                    inputFormatters: [
                      LengthLimitingTextInputFormatter(100),
                    ],
                    onChanged: (val) => payment.setReferencia(val),
                    decoration: InputDecoration(
                      labelText: widget.product.isFront1VoucherOrTag
                          ? 'Referencia / No. de Cuenta o TAG'
                          : 'Referencia / No. de Servicio',
                      hintText: widget.product.referenceType == 'b' ? 'Código de barras' : 'Ej. 0180020300',
                      prefixIcon: Icon(
                        Icons.receipt_long_rounded,
                        color: AppTheme.textSecondary(context),
                      ),
                    ),
                  ),
                ],

                if (!_isFixedPrice) ...[
                  const SizedBox(height: 14),
                  TextField(
                    controller: _montoController,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    onChanged: (val) {
                      final p = double.tryParse(val) ?? 0.0;
                      payment.setMonto(p);
                    },
                    decoration: InputDecoration(
                      labelText: 'Monto a Pagar (MXN)',
                      hintText: 'Ej. 250.00',
                      helperText: widget.product.frontType.trim() != '1'
                          ? (widget.product.price > 0
                              ? 'Mínimo \$10.00 MXN · Sugerido: \$${widget.product.price.toStringAsFixed(2)} MXN'
                              : 'Monto mínimo: \$10.00 MXN (máx. \$50,000.00)')
                          : (widget.product.price > 0
                              ? 'Referencia sugerida: \$${widget.product.price.toStringAsFixed(2)} MXN'
                              : null),
                      prefixIcon: Icon(
                        Icons.attach_money_rounded,
                        color: AppTheme.textSecondary(context),
                      ),
                    ),
                  ),
                ],

                const SizedBox(height: 16),

                OutlinedButton.icon(
                  onPressed: payment.isValidating ? null : _handleValidate,
                  icon: payment.isValidating
                      ? const SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.verified_outlined, size: 18),
                  label: Text(payment.isValidating ? 'Validando con Gesto Pago...' : 'Validar Referencia'),
                ),

                const SizedBox(height: 14),

                if (payment.validationResult != null) ...[
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: payment.isReferenceValid
                          ? AppTheme.accentEmerald.withValues(alpha: 0.1)
                          : AppTheme.errorRed.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(
                        color: payment.isReferenceValid
                            ? AppTheme.accentEmerald
                            : AppTheme.errorRed,
                        width: 1,
                      ),
                    ),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Icon(
                          payment.isReferenceValid
                              ? Icons.check_circle_rounded
                              : Icons.error_outline_rounded,
                          color: payment.isReferenceValid
                              ? AppTheme.accentEmerald
                              : AppTheme.errorRed,
                          size: 20,
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Text(
                            payment.validationResult!.mensaje,
                            style: TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.w600,
                              color: payment.isReferenceValid
                                  ? AppTheme.accentEmerald
                                  : AppTheme.errorRed,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],

                const SizedBox(height: 20),

                ElevatedButton(
                  onPressed: payment.isReferenceValid ? _proceedToStep2 : null,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppTheme.brandAccent(context),
                    foregroundColor: Colors.white,
                    disabledBackgroundColor: AppTheme.elevatedBg(context),
                    disabledForegroundColor: AppTheme.textSecondary(context),
                    padding: const EdgeInsets.symmetric(vertical: 14),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Flexible(
                        child: Text(
                          payment.isReferenceValid
                              ? 'Continuar al Pago'
                              : 'Valida la Referencia para Continuar',
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      const SizedBox(width: 8),
                      const Icon(Icons.arrow_forward_rounded, size: 18),
                    ],
                  ),
                ),
              ],

              // Step 2: 2FA & Confirmation
              if (_currentStep == 2) ...[
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: AppTheme.elevatedBg(context),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: AppTheme.borderCol(context)),
                  ),
                  child: Column(
                    children: [
                      _buildSummaryRow(
                        'Cuenta Cargo',
                        session?.maskedAccount ?? '****',
                      ),
                      Divider(height: 14, color: AppTheme.borderCol(context)),
                      _buildSummaryRow(
                        'Destino',
                        widget.product.isRecarga ? payment.telefono : payment.referencia,
                      ),
                      Divider(height: 14, color: AppTheme.borderCol(context)),
                      _buildSummaryRow(
                        'Monto a Pagar',
                        '${currencyFormatter.format(payment.monto)} MXN',
                        isBold: true,
                      ),
                      Divider(height: 14, color: AppTheme.borderCol(context)),
                      _buildSummaryRow(
                        'Comisión Bancaria',
                        '\$0.00 MXN',
                        highlightColor: AppTheme.accentEmerald,
                      ),
                      Divider(height: 14, color: AppTheme.borderCol(context)),
                      _buildSummaryRow(
                        'Saldo Actual',
                        '${currencyFormatter.format(saldoActual)} MXN',
                      ),
                      Divider(height: 14, color: AppTheme.borderCol(context)),
                      _buildSummaryRow(
                        'Saldo Posterior',
                        '${currencyFormatter.format(saldoPosterior)} MXN',
                        isBold: true,
                        highlightColor:
                            insufficientBalance ? AppTheme.errorRed : AppTheme.accentEmerald,
                      ),
                    ],
                  ),
                ),

                if (insufficientBalance) ...[
                  const SizedBox(height: 12),
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: AppTheme.errorRed.withValues(alpha: 0.08),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: AppTheme.errorRed.withValues(alpha: 0.4)),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        const Row(
                          children: [
                            Icon(Icons.account_balance_wallet_outlined, color: AppTheme.errorRed, size: 18),
                            SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                'Saldo insuficiente para completar este pago.',
                                style: TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.bold,
                                  color: AppTheme.errorRed,
                                ),
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 8),
                        Align(
                          alignment: Alignment.centerRight,
                          child: OutlinedButton.icon(
                            onPressed: _openAbonarSheet,
                            style: OutlinedButton.styleFrom(
                              foregroundColor: AppTheme.errorRed,
                              side: const BorderSide(color: AppTheme.errorRed),
                              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                            ),
                            icon: const Icon(Icons.add_card_rounded, size: 16),
                            label: const Text('Abonar Saldo', style: TextStyle(fontSize: 12)),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],

                const SizedBox(height: 16),

                // 2FA OTP Card
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: AppTheme.secondaryBlue.withValues(alpha: 0.06),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: AppTheme.secondaryBlue.withValues(alpha: 0.2)),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Expanded(
                            child: Row(
                              children: [
                                Icon(Icons.security_rounded, color: AppTheme.brandAccent(context), size: 18),
                                const SizedBox(width: 6),
                                Flexible(
                                  child: Text(
                                    'Autorización 2FA Requerida',
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: TextStyle(
                                      fontWeight: FontWeight.bold,
                                      fontSize: 13,
                                      color: AppTheme.brandAccent(context),
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(width: 8),
                          ValueListenableBuilder<int>(
                            valueListenable: payment.twoFASecondsNotifier,
                            builder: (context, secsLeft, _) {
                              final isUrgent = secsLeft <= 60;
                              return Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                                decoration: BoxDecoration(
                                  color: isUrgent
                                      ? AppTheme.errorRed.withValues(alpha: 0.15)
                                      : AppTheme.secondaryBlue.withValues(alpha: 0.15),
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                child: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Icon(
                                      Icons.timer_outlined,
                                      size: 13,
                                      color: isUrgent ? AppTheme.errorRed : AppTheme.secondaryBlue,
                                    ),
                                    const SizedBox(width: 4),
                                    Text(
                                      _format2FA(secsLeft),
                                      style: TextStyle(
                                        fontSize: 11,
                                        fontWeight: FontWeight.bold,
                                        color: isUrgent ? AppTheme.errorRed : AppTheme.secondaryBlue,
                                      ),
                                    ),
                                  ],
                                ),
                              );
                            },
                          ),
                        ],
                      ),

                      if (payment.isGenerating2FA) ...[
                        const SizedBox(height: 10),
                        const LinearProgressIndicator(minHeight: 2),
                        const SizedBox(height: 6),
                        const Text(
                          'Generando y enviando código 2FA...',
                          style: TextStyle(fontSize: 12, color: AppTheme.textMuted),
                        ),
                      ],

                      if (payment.twoFAResponse != null && !payment.isGenerating2FA) ...[
                        const SizedBox(height: 10),
                        Container(
                          padding: const EdgeInsets.all(10),
                          decoration: BoxDecoration(
                            color: (payment.twoFAResponse!.correoEnviado == true)
                                ? AppTheme.accentEmerald.withValues(alpha: 0.1)
                                : Colors.amber.withValues(alpha: 0.12),
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(
                              color: (payment.twoFAResponse!.correoEnviado == true)
                                  ? AppTheme.accentEmerald.withValues(alpha: 0.4)
                                  : Colors.amber.shade700.withValues(alpha: 0.45),
                            ),
                          ),
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Icon(
                                payment.twoFAResponse!.correoEnviado == true
                                    ? Icons.mark_email_read_outlined
                                    : Icons.mark_email_unread_outlined,
                                size: 17,
                                color: payment.twoFAResponse!.correoEnviado == true
                                    ? AppTheme.accentEmerald
                                    : Colors.amber.shade800,
                              ),
                              const SizedBox(width: 8),
                              Expanded(
                                child: Text(
                                  payment.twoFAResponse!.correoEnviado == true
                                      ? 'Código enviado a ${payment.twoFAResponse!.correoDestino ?? session?.email ?? 'tu correo'}. Revisa tu bandeja o spam.'
                                      : (payment.twoFAResponse!.mensajeCorreo ??
                                          'No se pudo enviar el código a tu correo registrado. Verifica tu email en Mi Perfil.'),
                                  style: TextStyle(
                                    fontSize: 12,
                                    color: payment.twoFAResponse!.correoEnviado == true
                                        ? AppTheme.accentEmerald
                                        : Colors.amber.shade900,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],

                      const SizedBox(height: 8),
                      Align(
                        alignment: Alignment.centerRight,
                        child: TextButton.icon(
                          onPressed: (payment.isGenerating2FA || payment.isPaying)
                              ? null
                              : _resend2FA,
                          icon: const Icon(Icons.refresh_rounded, size: 16),
                          label: const Text('Reenviar código 2FA', style: TextStyle(fontSize: 12)),
                        ),
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 16),

                TextField(
                  controller: _otpController,
                  keyboardType: TextInputType.number,
                  inputFormatters: [
                    FilteringTextInputFormatter.digitsOnly,
                    LengthLimitingTextInputFormatter(6),
                  ],
                  onChanged: (val) => payment.setEnteredOtp(val),
                  decoration: InputDecoration(
                    labelText: 'Ingresa el código 2FA (6 dígitos)',
                    hintText: '123456',
                    prefixIcon: Icon(Icons.pin_rounded, color: AppTheme.textSecondary(context)),
                  ),
                ),

                const SizedBox(height: 10),

                CheckboxListTile(
                  value: _confirmed,
                  contentPadding: EdgeInsets.zero,
                  controlAffinity: ListTileControlAffinity.leading,
                  activeColor: AppTheme.accentEmerald,
                  onChanged: (val) {
                    setState(() {
                      _confirmed = val ?? false;
                    });
                  },
                  title: Text(
                    'Confirmo que verifiqué la referencia y autorizo el cargo de ${currencyFormatter.format(payment.monto)} MXN.',
                    style: TextStyle(fontSize: 12, color: AppTheme.textPrimary(context)),
                  ),
                ),

                if (payment.paymentError != null && payment.paymentError!.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: AppTheme.errorRed.withValues(alpha: 0.08),
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: AppTheme.errorRed.withValues(alpha: 0.35)),
                    ),
                    child: Text(
                      payment.paymentError!,
                      style: const TextStyle(fontSize: 12, color: AppTheme.errorRed),
                    ),
                  ),
                ],

                const SizedBox(height: 16),

                Row(
                  children: [
                    Expanded(
                      flex: 1,
                      child: OutlinedButton(
                        onPressed: payment.isPaying
                            ? null
                            : () {
                                setState(() {
                                  _currentStep = 1;
                                });
                              },
                        child: const Text('Volver'),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      flex: 2,
                      child: ValueListenableBuilder<int>(
                        valueListenable: payment.twoFASecondsNotifier,
                        builder: (context, secsLeft, _) {
                          final disabled = payment.isPaying ||
                              payment.isGenerating2FA ||
                              insufficientBalance ||
                              secsLeft <= 0 ||
                              payment.enteredOtp.length != 6 ||
                              !_confirmed;
                          return ElevatedButton(
                            onPressed: disabled ? null : _executePayment,
                            style: ElevatedButton.styleFrom(
                              backgroundColor: AppTheme.accentEmerald,
                              foregroundColor: Colors.white,
                              disabledBackgroundColor: AppTheme.elevatedBg(context),
                              disabledForegroundColor: AppTheme.textSecondary(context),
                              padding: const EdgeInsets.symmetric(vertical: 14),
                            ),
                            child: payment.isPaying
                                ? const SizedBox(
                                    width: 20,
                                    height: 20,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                      color: Colors.white,
                                    ),
                                  )
                                : const Text('Confirmar y Pagar'),
                          );
                        },
                      ),
                    ),
                  ],
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSummaryRow(String label, String value, {bool isBold = false, Color? highlightColor}) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: TextStyle(fontSize: 13, color: AppTheme.textSecondary(context)),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            value,
            textAlign: TextAlign.end,
            softWrap: true,
            style: TextStyle(
              fontSize: 13,
              fontWeight: isBold ? FontWeight.bold : FontWeight.w600,
              color: highlightColor ?? AppTheme.textPrimary(context),
            ),
          ),
        ),
      ],
    );
  }
}
