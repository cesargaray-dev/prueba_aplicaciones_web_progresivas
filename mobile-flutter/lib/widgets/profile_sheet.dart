import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../providers/auth_provider.dart';
import '../theme/app_theme.dart';
import '../utils/validation_utils.dart';

class ProfileSheet extends StatefulWidget {
  final int initialTab;

  const ProfileSheet({
    super.key,
    this.initialTab = 0,
  });

  @override
  State<ProfileSheet> createState() => _ProfileSheetState();
}

class _ProfileSheetState extends State<ProfileSheet> {
  late int _selectedTab; // 0 = Mis Datos, 1 = Abonar, 2 = Seguridad

  final _nombreController = TextEditingController();
  final _segundoNombreController = TextEditingController();
  final _apellidoPaternoController = TextEditingController();
  final _apellidoMaternoController = TextEditingController();
  final _correoController = TextEditingController();
  final _telefonoController = TextEditingController();

  final _abonoController = TextEditingController(text: '250');
  final _conceptoController = TextEditingController(text: 'Abono a billetera digital');

  final _passActualController = TextEditingController();
  final _passNuevaController = TextEditingController();
  final _passConfirmController = TextEditingController();
  bool _showCurrentPass = false;
  bool _showNewPass = false;

  bool _syncingProfile = false;
  String? _statusMsg;
  bool _statusIsError = false;

  @override
  void initState() {
    super.initState();
    _selectedTab = widget.initialTab.clamp(0, 2);
    final auth = Provider.of<AuthProvider>(context, listen: false);
    _fillFieldsFromSession(auth);
    WidgetsBinding.instance.addPostFrameCallback((_) => _syncProfileFromServer());
  }

  void _fillFieldsFromSession(AuthProvider auth) {
    final session = auth.session;
    if (session == null) return;
    _nombreController.text = session.nombre ?? '';
    _segundoNombreController.text = session.segundoNombre ?? '';
    _apellidoPaternoController.text = session.apellidoPaterno ?? '';
    _apellidoMaternoController.text = session.apellidoMaterno ?? '';
    _correoController.text = session.email;
    _telefonoController.text = session.telefonoMovil ?? '';
  }

  Future<void> _syncProfileFromServer() async {
    final auth = Provider.of<AuthProvider>(context, listen: false);
    if (auth.session == null) return;
    setState(() => _syncingProfile = true);
    await auth.refreshProfile();
    if (!mounted) return;
    setState(() {
      _syncingProfile = false;
      _fillFieldsFromSession(auth);
    });
  }

  @override
  void dispose() {
    _nombreController.dispose();
    _segundoNombreController.dispose();
    _apellidoPaternoController.dispose();
    _apellidoMaternoController.dispose();
    _correoController.dispose();
    _telefonoController.dispose();
    _abonoController.dispose();
    _conceptoController.dispose();
    _passActualController.dispose();
    _passNuevaController.dispose();
    _passConfirmController.dispose();
    super.dispose();
  }

  String? _liveEmailError() {
    final correo = _correoController.text.trim();
    if (correo.isEmpty) return 'El correo electrónico es obligatorio.';
    if (!ValidationUtils.esCorreoValido(correo)) return 'Ingresa un correo electrónico válido.';
    if (ValidationUtils.esCorreoPlaceholder(correo)) {
      return 'Usa un correo electrónico real (no @ejemplo.com).';
    }
    return null;
  }

  String? _livePhoneError() {
    final tel = _telefonoController.text.trim();
    if (tel.isEmpty) return 'El número de teléfono es obligatorio.';
    return ValidationUtils.diagnosticarTelefono(tel);
  }

  Future<void> _savePerfil(AuthProvider auth) async {
    setState(() {
      _statusMsg = null;
      _statusIsError = false;
    });

    final nombre = _nombreController.text.trim();
    final segundoNombre = _segundoNombreController.text.trim();
    final apellidoPaterno = _apellidoPaternoController.text.trim();
    final apellidoMaterno = _apellidoMaternoController.text.trim();
    final correo = _correoController.text.trim();
    final tel = _telefonoController.text.trim();

    final nombreErr = ValidationUtils.diagnosticarNombre(nombre, 'El nombre', maxLength: 80);
    if (nombreErr != null) {
      setState(() {
        _statusIsError = true;
        _statusMsg = nombreErr;
      });
      return;
    }
    final segundoErr = ValidationUtils.diagnosticarNombre(
      segundoNombre,
      'El segundo nombre',
      optional: true,
      maxLength: 80,
    );
    if (segundoErr != null) {
      setState(() {
        _statusIsError = true;
        _statusMsg = segundoErr;
      });
      return;
    }
    final apPatErr = ValidationUtils.diagnosticarNombre(
      apellidoPaterno,
      'El apellido paterno',
      maxLength: 80,
    );
    if (apPatErr != null) {
      setState(() {
        _statusIsError = true;
        _statusMsg = apPatErr;
      });
      return;
    }
    final apMatErr = ValidationUtils.diagnosticarNombre(
      apellidoMaterno,
      'El apellido materno',
      maxLength: 80,
      optional: true,
    );
    if (apMatErr != null) {
      setState(() {
        _statusIsError = true;
        _statusMsg = apMatErr;
      });
      return;
    }
    if (!ValidationUtils.esCorreoValido(correo)) {
      setState(() {
        _statusIsError = true;
        _statusMsg = 'Ingresa un correo electrónico válido.';
      });
      return;
    }
    if (ValidationUtils.esCorreoPlaceholder(correo)) {
      setState(() {
        _statusIsError = true;
        _statusMsg = 'Usa un correo electrónico real (no de ejemplo @ejemplo.com).';
      });
      return;
    }
    final telErr = ValidationUtils.diagnosticarTelefono(tel);
    if (telErr != null) {
      setState(() {
        _statusIsError = true;
        _statusMsg = telErr;
      });
      return;
    }

    final ok = await auth.updateContacto(
      nombre: nombre,
      segundoNombre: segundoNombre,
      apellidoPaterno: apellidoPaterno,
      apellidoMaterno: apellidoMaterno,
      correo: correo,
      telefonoMovil: tel,
    );
    if (!mounted) return;
    setState(() {
      _statusIsError = !ok;
      _statusMsg = ok
          ? 'Perfil actualizado (${auth.session?.nombreCompleto ?? nombre}). Códigos 2FA y recibos se enviarán a $correo.'
          : (auth.errorMessage ?? 'No fue posible actualizar tus datos.');
    });
  }

  Future<void> _applyAbono(AuthProvider auth) async {
    setState(() {
      _statusMsg = null;
      _statusIsError = false;
    });
    final monto = double.tryParse(_abonoController.text.trim()) ?? 0.0;
    if (monto < 1 || monto > 50000) {
      setState(() {
        _statusIsError = true;
        _statusMsg = 'El abono debe estar entre \$1.00 y \$50,000.00 MXN.';
      });
      return;
    }

    final ok = await auth.abonarSaldo(
      monto: monto,
      concepto: _conceptoController.text.trim(),
    );
    if (!mounted) return;
    setState(() {
      _statusIsError = !ok;
      _statusMsg = ok
          ? 'Abono aplicado. Nuevo saldo disponible: ${NumberFormat.currency(locale: 'es_MX', symbol: '\$').format(auth.session?.saldo ?? 0)} MXN'
          : (auth.errorMessage ?? 'Error al aplicar el abono.');
    });
  }

  Future<void> _toggleBiometric(AuthProvider auth, bool enable) async {
    setState(() {
      _statusMsg = null;
      _statusIsError = false;
    });
    final err = enable
        ? await auth.enableBiometricForCurrentUser()
        : await auth.disableBiometricForCurrentUser();
    if (!mounted) return;
    setState(() {
      _statusIsError = err != null;
      _statusMsg = err ??
          (enable
              ? '${auth.biometricLabel} activada. Ahora puedes elegir entrar con huella o contraseña.'
              : '${auth.biometricLabel} desactivada. Tu cuenta usará solo contraseña.');
    });
  }

  Future<void> _changePassword(AuthProvider auth) async {
    setState(() {
      _statusMsg = null;
      _statusIsError = false;
    });

    final actual = _passActualController.text;
    final nueva = _passNuevaController.text;
    final confirm = _passConfirmController.text;

    if (actual.isEmpty) {
      setState(() {
        _statusIsError = true;
        _statusMsg = 'Ingresa tu contraseña actual.';
      });
      return;
    }
    if (!ValidationUtils.esContrasenaSegura(nueva)) {
      setState(() {
        _statusIsError = true;
        _statusMsg =
            'La nueva contraseña debe tener mínimo 8 caracteres, una mayúscula, una minúscula y un número.';
      });
      return;
    }
    if (nueva != confirm) {
      setState(() {
        _statusIsError = true;
        _statusMsg = 'La confirmación no coincide con la nueva contraseña.';
      });
      return;
    }
    if (actual == nueva) {
      setState(() {
        _statusIsError = true;
        _statusMsg = 'La nueva contraseña debe ser diferente a la actual.';
      });
      return;
    }

    final ok = await auth.cambiarContrasena(
      contrasenaActual: actual,
      contrasenaNueva: nueva,
    );
    if (!mounted) return;
    if (ok) {
      _passActualController.clear();
      _passNuevaController.clear();
      _passConfirmController.clear();
    }
    setState(() {
      _statusIsError = !ok;
      _statusMsg = ok
          ? 'Contraseña actualizada exitosamente. Úsala en tu próximo acceso en Web y Móvil.'
          : (auth.errorMessage ?? 'No fue posible actualizar tu contraseña.');
    });
  }

  @override
  Widget build(BuildContext context) {
    final auth = Provider.of<AuthProvider>(context);
    final session = auth.session;
    if (session == null) return const SizedBox.shrink();

    final isPlaceholder = ValidationUtils.esCorreoPlaceholder(session.email);
    final currencyFormatter = NumberFormat.currency(locale: 'es_MX', symbol: '\$');
    final emailErr = _liveEmailError();
    final phoneErr = _livePhoneError();

    return SafeArea(
      child: Align(
        alignment: Alignment.bottomCenter,
        heightFactor: 1.0,
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxWidth: 520,
            maxHeight: MediaQuery.of(context).size.height * 0.9,
          ),
          child: Padding(
            padding: EdgeInsets.only(
              left: 20,
              right: 20,
              top: 14,
              bottom: MediaQuery.of(context).viewInsets.bottom + 20,
            ),
            child: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Center(
                    child: Container(
                      width: 40,
                      height: 4,
                      margin: const EdgeInsets.only(bottom: 12),
                      decoration: BoxDecoration(
                        color: AppTheme.borderCol(context),
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                  ),

                  // Header
                  Row(
                    children: [
                      Icon(Icons.account_circle_rounded, color: AppTheme.brandAccent(context), size: 32),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              session.nombreCompleto,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: TextStyle(
                                fontWeight: FontWeight.bold,
                                fontSize: 16,
                                color: AppTheme.textPrimary(context),
                              ),
                            ),
                            Row(
                              children: [
                                Flexible(
                                  child: Text(
                                    '@${session.usuario} • ${session.numeroCuenta}',
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: TextStyle(
                                      fontSize: 12,
                                      color: AppTheme.textSecondary(context),
                                    ),
                                  ),
                                ),
                                IconButton(
                                  icon: const Icon(Icons.copy_rounded, size: 15),
                                  tooltip: 'Copiar número de cuenta',
                                  padding: const EdgeInsets.symmetric(horizontal: 6),
                                  constraints: const BoxConstraints(),
                                  visualDensity: VisualDensity.compact,
                                  color: AppTheme.secondaryBlue,
                                  onPressed: () {
                                    Clipboard.setData(ClipboardData(text: session.numeroCuenta));
                                    setState(() {
                                      _statusIsError = false;
                                      _statusMsg = 'Número de cuenta copiado al portapapeles.';
                                    });
                                  },
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.close_rounded),
                        onPressed: () => Navigator.of(context).pop(),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),

                  // 3-Tab Selector: Mis Datos | Abonar | Seguridad
                  SegmentedButton<int>(
                    showSelectedIcon: false,
                    segments: const [
                      ButtonSegment<int>(
                        value: 0,
                        icon: Icon(Icons.person_outline_rounded, size: 16),
                        label: Text('Mis Datos', maxLines: 1, overflow: TextOverflow.ellipsis),
                      ),
                      ButtonSegment<int>(
                        value: 1,
                        icon: Icon(Icons.account_balance_wallet_outlined, size: 16),
                        label: Text('Abonar', maxLines: 1, overflow: TextOverflow.ellipsis),
                      ),
                      ButtonSegment<int>(
                        value: 2,
                        icon: Icon(Icons.security_rounded, size: 16),
                        label: Text('Seguridad', maxLines: 1, overflow: TextOverflow.ellipsis),
                      ),
                    ],
                    selected: {_selectedTab},
                    onSelectionChanged: (sel) {
                      setState(() {
                        _selectedTab = sel.first;
                        _statusMsg = null;
                      });
                    },
                  ),
                  const SizedBox(height: 14),

                  if (isPlaceholder)
                    Container(
                      padding: const EdgeInsets.all(12),
                      margin: const EdgeInsets.only(bottom: 12),
                      decoration: BoxDecoration(
                        color: const Color(0xFFFFFBEB),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: const Color(0xFFFCD34D)),
                      ),
                      child: const Text(
                        'Tu correo actual es de prueba (@ejemplo.com). Actualízalo por tu correo real para recibir códigos 2FA y recibos vía Brevo.',
                        style: TextStyle(fontSize: 12, color: Color(0xFF92400E)),
                      ),
                    ),

                  if (_statusMsg != null)
                    Container(
                      padding: const EdgeInsets.all(12),
                      margin: const EdgeInsets.only(bottom: 12),
                      decoration: BoxDecoration(
                        color: _statusIsError ? Colors.red.shade50 : Colors.green.shade50,
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(
                          color: _statusIsError ? Colors.red.shade200 : Colors.green.shade200,
                        ),
                      ),
                      child: Text(
                        _statusMsg!,
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                          color: _statusIsError ? Colors.red.shade800 : Colors.green.shade800,
                        ),
                      ),
                    ),

                  // TAB 0: MIS DATOS (Personal + Contact)
                  if (_selectedTab == 0) ...[
                    if (_syncingProfile)
                      const Padding(
                        padding: EdgeInsets.only(bottom: 10),
                        child: LinearProgressIndicator(minHeight: 2),
                      ),
                    Text(
                      'Información Personal',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.bold,
                        color: AppTheme.brandAccent(context),
                      ),
                    ),
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: _nombreController,
                            textCapitalization: TextCapitalization.words,
                            decoration: const InputDecoration(
                              labelText: 'Nombre *',
                              prefixIcon: Icon(Icons.badge_outlined, size: 18),
                            ),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: TextField(
                            controller: _segundoNombreController,
                            textCapitalization: TextCapitalization.words,
                            decoration: const InputDecoration(
                              labelText: 'Segundo nombre',
                              hintText: 'Opcional',
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: _apellidoPaternoController,
                            textCapitalization: TextCapitalization.words,
                            decoration: const InputDecoration(
                              labelText: 'Apellido paterno *',
                            ),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: TextField(
                            controller: _apellidoMaternoController,
                            textCapitalization: TextCapitalization.words,
                            decoration: const InputDecoration(
                              labelText: 'Apellido materno (opcional)',
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    Text(
                      'Correo Electrónico y Celular Asociado',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.bold,
                        color: AppTheme.brandAccent(context),
                      ),
                    ),
                    const SizedBox(height: 8),
                    TextField(
                      controller: _correoController,
                      keyboardType: TextInputType.emailAddress,
                      onChanged: (_) => setState(() {}),
                      decoration: InputDecoration(
                        labelText: 'Correo electrónico real *',
                        prefixIcon: const Icon(Icons.mail_outline_rounded),
                        errorText: _correoController.text.isNotEmpty ? emailErr : null,
                        suffixIcon: emailErr == null
                            ? const Icon(Icons.check_circle_rounded, color: AppTheme.accentEmerald, size: 18)
                            : null,
                      ),
                    ),
                    const SizedBox(height: 10),
                    TextField(
                      controller: _telefonoController,
                      keyboardType: TextInputType.number,
                      maxLength: 10,
                      onChanged: (_) => setState(() {}),
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                      decoration: InputDecoration(
                        labelText: 'Teléfono móvil México (10 dígitos) *',
                        prefixIcon: const Icon(Icons.phone_android_rounded),
                        counterText: '',
                        errorText: _telefonoController.text.isNotEmpty ? phoneErr : null,
                        suffixIcon: phoneErr == null
                            ? const Icon(Icons.check_circle_rounded, color: AppTheme.accentEmerald, size: 18)
                            : null,
                      ),
                    ),
                    const SizedBox(height: 14),
                    ElevatedButton.icon(
                      onPressed: (auth.isLoading || _syncingProfile) ? null : () => _savePerfil(auth),
                      icon: auth.isLoading
                          ? const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                            )
                          : const Icon(Icons.save_rounded, size: 18),
                      label: Text(auth.isLoading ? 'Guardando...' : 'Guardar Cambios de Perfil'),
                    ),
                  ],

                  // TAB 1: ABONAR SALDO
                  if (_selectedTab == 1) ...[
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Flexible(
                          child: Text(
                            'Abonar Fondos a mi Cuenta',
                            overflow: TextOverflow.ellipsis,
                            style: TextStyle(
                              fontSize: 13,
                              fontWeight: FontWeight.bold,
                              color: AppTheme.accentEmerald,
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Text(
                          'Saldo: ${currencyFormatter.format(session.saldo)}',
                          style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    Wrap(
                      spacing: 8,
                      runSpacing: 6,
                      children: ['100', '250', '500', '1000'].map((amt) {
                        final active = _abonoController.text.trim() == amt;
                        final isDark = AppTheme.isDark(context);
                        return ChoiceChip(
                          showCheckmark: false,
                          selected: active,
                          backgroundColor: isDark ? AppTheme.darkElevated : Colors.white,
                          selectedColor: AppTheme.accentEmerald,
                          side: BorderSide(
                            color: active
                                ? AppTheme.accentEmerald
                                : AppTheme.borderCol(context),
                            width: active ? 1.5 : 1,
                          ),
                          label: Text(
                            '\$$amt',
                            style: TextStyle(
                              fontWeight: FontWeight.bold,
                              color: active ? Colors.white : AppTheme.textPrimary(context),
                            ),
                          ),
                          onSelected: (_) => setState(() => _abonoController.text = amt),
                        );
                      }).toList(),
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: TextField(
                            controller: _abonoController,
                            style: TextStyle(
                              fontSize: 15,
                              fontWeight: FontWeight.w700,
                              color: AppTheme.textPrimary(context),
                            ),
                            keyboardType: const TextInputType.numberWithOptions(decimal: true),
                            onChanged: (_) => setState(() {}),
                            decoration: const InputDecoration(
                              labelText: 'Monto MXN',
                              prefixText: '\$ ',
                            ),
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: TextField(
                            controller: _conceptoController,
                            style: TextStyle(
                              fontSize: 14,
                              color: AppTheme.textPrimary(context),
                            ),
                            decoration: const InputDecoration(labelText: 'Concepto'),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 14),
                    ElevatedButton.icon(
                      onPressed: auth.isLoading ? null : () => _applyAbono(auth),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppTheme.accentEmerald,
                        foregroundColor: Colors.white,
                        disabledBackgroundColor: AppTheme.elevatedBg(context),
                        disabledForegroundColor: AppTheme.textSecondary(context),
                      ),
                      icon: auth.isLoading
                          ? const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                            )
                          : const Icon(Icons.add_circle_outline_rounded, size: 18),
                      label: Text(auth.isLoading ? 'Abonando...' : 'Abonar a Cuenta'),
                    ),
                  ],

                  // TAB 2: SEGURIDAD (Huella Digital + Cambiar Contraseña)
                  if (_selectedTab == 2) ...[
                    Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: AppTheme.brandAccent(context).withValues(alpha: 0.08),
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(color: AppTheme.borderCol(context)),
                      ),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          Row(
                            children: [
                              Container(
                                padding: const EdgeInsets.all(8),
                                decoration: BoxDecoration(
                                  color: AppTheme.secondaryBlue.withValues(alpha: 0.14),
                                  borderRadius: BorderRadius.circular(10),
                                ),
                                child: Icon(
                                  Icons.fingerprint_rounded,
                                  color: AppTheme.brandAccent(context),
                                  size: 22,
                                ),
                              ),
                              const SizedBox(width: 10),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      'Acceso con ${auth.biometricLabel}',
                                      style: TextStyle(
                                        fontSize: 13,
                                        fontWeight: FontWeight.bold,
                                        color: AppTheme.brandAccent(context),
                                      ),
                                    ),
                                    Text(
                                      auth.canUseBiometrics
                                          ? (auth.biometricEnabledForCurrentUser
                                              ? 'Activo · Elige huella o contraseña al iniciar'
                                              : 'Inicia sesión rápido y seguro con tu huella')
                                          : (auth.biometricAvailability.unavailableReason ??
                                              'Disponible en dispositivos móviles con sensor'),
                                      style: TextStyle(
                                        fontSize: 11,
                                        color: AppTheme.textSecondary(context),
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                              Switch.adaptive(
                                value: auth.biometricEnabledForCurrentUser,
                                activeTrackColor: AppTheme.accentEmerald,
                                onChanged: (!auth.canUseBiometrics || auth.isBiometricBusy)
                                    ? null
                                    : (val) => _toggleBiometric(auth, val),
                              ),
                            ],
                          ),
                          if (auth.biometricEnabledForCurrentUser && auth.canUseBiometrics) ...[
                            const SizedBox(height: 10),
                            const Text(
                              'Método predeterminado al abrir la app:',
                              style: TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.w600,
                                color: AppTheme.textMuted,
                              ),
                            ),
                            const SizedBox(height: 6),
                            SegmentedButton<String>(
                              showSelectedIcon: false,
                              segments: [
                                ButtonSegment<String>(
                                  value: 'biometric',
                                  icon: const Icon(Icons.fingerprint_rounded, size: 16),
                                  label: Text(
                                    auth.biometricLabel,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                                const ButtonSegment<String>(
                                  value: 'password',
                                  icon: Icon(Icons.password_rounded, size: 16),
                                  label: Text(
                                    'Contraseña',
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                              ],
                              selected: {auth.preferredLoginMethod},
                              onSelectionChanged: (sel) => auth.setPreferredLoginMethod(sel.first),
                            ),
                          ],
                        ],
                      ),
                    ),

                    const SizedBox(height: 16),
                    Text(
                      'Cambiar Contraseña',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.bold,
                        color: AppTheme.brandAccent(context),
                      ),
                    ),
                    const SizedBox(height: 8),
                    TextField(
                      controller: _passActualController,
                      obscureText: !_showCurrentPass,
                      decoration: InputDecoration(
                        labelText: 'Contraseña actual',
                        prefixIcon: const Icon(Icons.lock_outline_rounded),
                        suffixIcon: IconButton(
                          icon: Icon(_showCurrentPass ? Icons.visibility_off : Icons.visibility),
                          onPressed: () => setState(() => _showCurrentPass = !_showCurrentPass),
                        ),
                      ),
                    ),
                    const SizedBox(height: 10),
                    TextField(
                      controller: _passNuevaController,
                      obscureText: !_showNewPass,
                      onChanged: (_) => setState(() {}),
                      decoration: InputDecoration(
                        labelText: 'Nueva contraseña (8+ chars, Aa1)',
                        prefixIcon: const Icon(Icons.vpn_key_outlined),
                        suffixIcon: IconButton(
                          icon: Icon(_showNewPass ? Icons.visibility_off : Icons.visibility),
                          onPressed: () => setState(() => _showNewPass = !_showNewPass),
                        ),
                      ),
                    ),
                    const SizedBox(height: 8),
                    _buildPasswordRules(_passNuevaController.text),
                    const SizedBox(height: 10),
                    TextField(
                      controller: _passConfirmController,
                      obscureText: !_showNewPass,
                      decoration: const InputDecoration(
                        labelText: 'Confirmar nueva contraseña',
                        prefixIcon: Icon(Icons.check_circle_outline_rounded),
                      ),
                    ),
                    const SizedBox(height: 14),
                    ElevatedButton.icon(
                      onPressed: auth.isLoading ? null : () => _changePassword(auth),
                      icon: auth.isLoading
                          ? const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                            )
                          : const Icon(Icons.shield_outlined, size: 18),
                      label: Text(auth.isLoading ? 'Actualizando...' : 'Actualizar Contraseña'),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildPasswordRules(String value) {
    final rules = [
      ('8+ caracteres', value.length >= 8),
      ('1 mayúscula', RegExp(r'[A-Z]').hasMatch(value)),
      ('1 minúscula', RegExp(r'[a-z]').hasMatch(value)),
      ('1 número', RegExp(r'\d').hasMatch(value)),
    ];
    return Wrap(
      spacing: 10,
      runSpacing: 4,
      children: rules.map((r) {
        final color = r.$2 ? AppTheme.accentEmerald : AppTheme.textLight;
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              r.$2 ? Icons.check_circle_rounded : Icons.radio_button_unchecked_rounded,
              size: 13,
              color: color,
            ),
            const SizedBox(width: 4),
            Text(
              r.$1,
              style: TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: color),
            ),
          ],
        );
      }).toList(),
    );
  }
}
