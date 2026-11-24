import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../providers/auth_provider.dart';
import '../services/api_client.dart';
import '../theme/app_theme.dart';
import '../utils/validation_utils.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _loginFormKey = GlobalKey<FormState>();

  bool _isRegisterMode = false;
  final bool _enrollBiometricOnLogin = false;

  // Login controllers
  final _usuarioController = TextEditingController();
  final _contrasenaController = TextEditingController();
  bool _obscurePassword = true;

  // Register Step 1: Identity
  int _regStep = 1; // 1 = Identidad, 2 = Fiscal y Domicilio, 3 = Acceso, 4 = OTP 2FA
  final _nombreController = TextEditingController();
  final _segundoNombreController = TextEditingController();
  final _apPaternoController = TextEditingController();
  final _apMaternoController = TextEditingController();
  final _fechaNacController = TextEditingController(text: '1998-05-15');
  String _sexo = 'HOMBRE';
  String _nacionalidad = 'MEXICANA';
  String _estadoCivil = 'SOLTERO';

  // Register Step 2: Fiscal (CURP / RFC), Economic & Address
  String _entidadCodigo = 'DF';
  final _curpController = TextEditingController();
  final _rfcController = TextEditingController();
  final _ocupacionController = TextEditingController(text: 'Profesionista');
  final _empresaController = TextEditingController(text: 'Independiente');
  final _ingresoController = TextEditingController(text: '18000');
  final _calleController = TextEditingController();
  final _numExtController = TextEditingController();
  final _numIntController = TextEditingController();
  final _coloniaController = TextEditingController();
  final _cpController = TextEditingController();
  final _municipioController = TextEditingController();

  // Register Step 3: Contact & Credentials
  final _correoController = TextEditingController();
  final _telefonoController = TextEditingController();
  final _telAltController = TextEditingController();
  final _regUsuarioController = TextEditingController();
  final _regContrasenaController = TextEditingController();
  final _regConfirmContrasenaController = TextEditingController();
  bool _obscureRegPassword = true;
  bool _obscureRegConfirmPassword = true;

  // Register Step 4: 2FA OTP
  final _regOtpController = TextEditingController();
  Timer? _regOtpTimer;
  int _regOtpSecondsRemaining = 0;
  String _regOtpSentTo = '';
  String? _regOtpFeedback;
  String? _inlineRegError;

  final _apiUrlController = TextEditingController(text: ApiClient.defaultBaseUrl);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      final auth = Provider.of<AuthProvider>(context, listen: false);
      if (!auth.biometricsInitialized) {
        await auth.initBiometrics();
      }
      if (mounted &&
          auth.storedBiometricProfile != null &&
          _usuarioController.text.isEmpty) {
        _usuarioController.text = auth.storedBiometricProfile!.usuario;
      }
    });
  }

  @override
  void dispose() {
    _regOtpTimer?.cancel();
    _usuarioController.dispose();
    _contrasenaController.dispose();
    _nombreController.dispose();
    _segundoNombreController.dispose();
    _apPaternoController.dispose();
    _apMaternoController.dispose();
    _fechaNacController.dispose();
    _curpController.dispose();
    _rfcController.dispose();
    _ocupacionController.dispose();
    _empresaController.dispose();
    _ingresoController.dispose();
    _calleController.dispose();
    _numExtController.dispose();
    _numIntController.dispose();
    _coloniaController.dispose();
    _cpController.dispose();
    _municipioController.dispose();
    _correoController.dispose();
    _telefonoController.dispose();
    _telAltController.dispose();
    _regUsuarioController.dispose();
    _regContrasenaController.dispose();
    _regConfirmContrasenaController.dispose();
    _regOtpController.dispose();
    _apiUrlController.dispose();
    super.dispose();
  }

  String get _selectedEstadoNombre {
    for (final st in ValidationUtils.estadosMexico) {
      if (st.key == _entidadCodigo) return st.value;
    }
    return 'Ciudad de México';
  }

  Future<void> _submitLogin() async {
    if (!_loginFormKey.currentState!.validate()) return;
    final auth = Provider.of<AuthProvider>(context, listen: false);
    final success = await auth.login(
      _usuarioController.text.trim(),
      _contrasenaController.text,
      enrollBiometricAfter: _enrollBiometricOnLogin,
    );
    if (!success && mounted) {
      _showError(auth.errorMessage ?? 'Credenciales inválidas');
    }
  }

  Future<void> _submitBiometricLogin() async {
    final auth = Provider.of<AuthProvider>(context, listen: false);
    final success = await auth.loginWithBiometrics();
    if (!success && mounted && auth.errorMessage != null) {
      _showError(auth.errorMessage!);
    }
  }

  void _startRegOtpCountdown(int seconds) {
    _regOtpTimer?.cancel();
    _regOtpSecondsRemaining = seconds > 0 ? seconds : 300;
    _regOtpTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!mounted) {
        timer.cancel();
        return;
      }
      if (_regOtpSecondsRemaining > 1) {
        setState(() => _regOtpSecondsRemaining--);
      } else {
        setState(() => _regOtpSecondsRemaining = 0);
        timer.cancel();
      }
    });
  }

  String _formatCountdown(int secs) {
    final m = (secs ~/ 60).toString().padLeft(2, '0');
    final s = (secs % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  void _autoGenerateFiscal() {
    setState(() => _inlineRegError = null);
    final res = ValidationUtils.generarCurpYRfcSugeridos(
      nombre: _nombreController.text,
      segundoNombre: _segundoNombreController.text,
      apellidoPaterno: _apPaternoController.text,
      apellidoMaterno: _apMaternoController.text,
      fechaNacimiento: _fechaNacController.text,
      sexo: _sexo,
      estadoCodigo: _entidadCodigo,
    );
    if (res == null) {
      setState(() {
        _inlineRegError =
            'Completa primero tu nombre, apellido paterno y fecha de nacimiento en el Paso 1.';
      });
      return;
    }
    setState(() {
      _curpController.text = res.curp;
      _rfcController.text = res.rfc;
    });
  }

  String? _validateStep1() {
    final nErr = ValidationUtils.diagnosticarNombre(_nombreController.text, 'El nombre');
    if (nErr != null) return nErr;
    final sErr = ValidationUtils.diagnosticarNombre(
      _segundoNombreController.text,
      'El segundo nombre',
      optional: true,
    );
    if (sErr != null) return sErr;
    final pErr =
        ValidationUtils.diagnosticarNombre(_apPaternoController.text, 'El apellido paterno');
    if (pErr != null) return pErr;
    final mErr =
        ValidationUtils.diagnosticarNombre(_apMaternoController.text, 'El apellido materno');
    if (mErr != null) return mErr;
    final fErr = ValidationUtils.diagnosticarFechaNacimiento(_fechaNacController.text);
    if (fErr != null) return fErr;
    return null;
  }

  String? _validateStep2() {
    final curpErr = ValidationUtils.diagnosticarCurp(_curpController.text);
    if (curpErr != null) return curpErr;
    final rfcErr = ValidationUtils.diagnosticarRfc(_rfcController.text);
    if (rfcErr != null) return rfcErr;
    if (_ocupacionController.text.trim().length < 2) {
      return 'Ingresa tu ocupación o actividad económica.';
    }
    if (_empresaController.text.trim().length < 2) {
      return 'Ingresa tu empresa o institución.';
    }
    final ing = double.tryParse(_ingresoController.text.trim()) ?? 0;
    if (ing <= 0) {
      return 'Ingresa un ingreso mensual estimado válido.';
    }
    if (_calleController.text.trim().length < 3) {
      return 'Ingresa la calle y avenida de tu domicilio.';
    }
    if (_numExtController.text.trim().isEmpty) {
      return 'Ingresa el número exterior (o S/N).';
    }
    if (_coloniaController.text.trim().length < 2) {
      return 'Ingresa tu colonia o fraccionamiento.';
    }
    if (!ValidationUtils.esCodigoPostalValido(_cpController.text)) {
      return 'El código postal debe tener 5 dígitos válidos (01000 a 99999).';
    }
    if (_municipioController.text.trim().length < 2) {
      return 'Ingresa tu municipio o alcaldía.';
    }
    return null;
  }

  String? _validateStep3() {
    final mail = _correoController.text.trim();
    if (!ValidationUtils.esCorreoValido(mail)) return 'Ingresa un correo electrónico válido.';
    if (ValidationUtils.esCorreoPlaceholder(mail)) {
      return 'Usa un correo electrónico real (Gmail, Outlook, etc.) para recibir tu código 2FA.';
    }
    final telErr = ValidationUtils.diagnosticarTelefono(_telefonoController.text);
    if (telErr != null) return telErr;
    if (_telAltController.text.trim().isNotEmpty) {
      final altErr = ValidationUtils.diagnosticarTelefono(_telAltController.text);
      if (altErr != null) return 'Teléfono alternativo: $altErr';
    }
    final usrErr = ValidationUtils.diagnosticarUsuario(_regUsuarioController.text);
    if (usrErr != null) return usrErr;
    if (!ValidationUtils.esContrasenaSegura(_regContrasenaController.text)) {
      return 'La contraseña requiere mínimo 8 caracteres, mayúscula, minúscula y número.';
    }
    if (_regContrasenaController.text != _regConfirmContrasenaController.text) {
      return 'Las contraseñas no coinciden.';
    }
    return null;
  }

  void _advanceFromStep1() {
    final err = _validateStep1();
    if (err != null) {
      setState(() => _inlineRegError = err);
      return;
    }
    if (_curpController.text.trim().isEmpty && _rfcController.text.trim().isEmpty) {
      final suggested = ValidationUtils.generarCurpYRfcSugeridos(
        nombre: _nombreController.text,
        segundoNombre: _segundoNombreController.text,
        apellidoPaterno: _apPaternoController.text,
        apellidoMaterno: _apMaternoController.text,
        fechaNacimiento: _fechaNacController.text,
        sexo: _sexo,
        estadoCodigo: _entidadCodigo,
      );
      if (suggested != null) {
        _curpController.text = suggested.curp;
        _rfcController.text = suggested.rfc;
      }
    }
    setState(() {
      _inlineRegError = null;
      _regStep = 2;
    });
  }

  void _advanceFromStep2() {
    final err = _validateStep2();
    if (err != null) {
      setState(() => _inlineRegError = err);
      return;
    }
    setState(() {
      _inlineRegError = null;
      _regStep = 3;
    });
  }

  Future<void> _sendRegistrationCode({bool isResend = false}) async {
    setState(() {
      _inlineRegError = null;
      _regOtpFeedback = null;
    });

    if (!isResend) {
      final e1 = _validateStep1();
      if (e1 != null) {
        setState(() {
          _regStep = 1;
          _inlineRegError = e1;
        });
        return;
      }
      final e2 = _validateStep2();
      if (e2 != null) {
        setState(() {
          _regStep = 2;
          _inlineRegError = e2;
        });
        return;
      }
      final e3 = _validateStep3();
      if (e3 != null) {
        setState(() {
          _regStep = 3;
          _inlineRegError = e3;
        });
        return;
      }
    }

    final auth = Provider.of<AuthProvider>(context, listen: false);
    final email = _correoController.text.trim();
    final expiresIn = await auth.enviarCodigoRegistro(
      correo: email,
      nombre: _nombreController.text.trim(),
      usuario: _regUsuarioController.text.trim(),
      curp: _curpController.text.trim().toUpperCase(),
      rfc: _rfcController.text.trim().toUpperCase(),
    );
    if (!mounted) return;
    if (expiresIn != null) {
      setState(() {
        _regStep = 4;
        _regOtpSentTo = email;
        _regOtpController.clear();
        _regOtpFeedback = isResend
            ? 'Se envió un nuevo código 2FA de 6 dígitos a tu correo.'
            : null;
      });
      _startRegOtpCountdown(expiresIn);
    } else {
      _showError(
        auth.errorMessage ?? 'No fue posible enviar el código de verificación al correo.',
      );
    }
  }

  Future<void> _submitRegister() async {
    final code = _regOtpController.text.trim();
    if (!RegExp(r'^\d{6}$').hasMatch(code)) {
      _showError('Ingresa el código de verificación de 6 dígitos enviado a tu correo.');
      return;
    }
    final auth = Provider.of<AuthProvider>(context, listen: false);
    final success = await auth.register(
      nombre: _nombreController.text.trim(),
      segundoNombre: _segundoNombreController.text.trim().isEmpty
          ? null
          : _segundoNombreController.text.trim(),
      apellidoPaterno: _apPaternoController.text.trim(),
      apellidoMaterno: _apMaternoController.text.trim(),
      fechaNacimiento: _fechaNacController.text.trim(),
      sexo: _sexo,
      nacionalidad: _nacionalidad,
      estadoCivil: _estadoCivil,
      curp: _curpController.text.trim().toUpperCase(),
      rfc: _rfcController.text.trim().toUpperCase(),
      ocupacion: _ocupacionController.text.trim(),
      empresa: _empresaController.text.trim(),
      ingresoMensual: double.tryParse(_ingresoController.text.trim()) ?? 15000,
      calle: _calleController.text.trim(),
      numeroExterior: _numExtController.text.trim(),
      numeroInterior: _numIntController.text.trim().isEmpty
          ? null
          : _numIntController.text.trim(),
      colonia: _coloniaController.text.trim(),
      codigoPostal: _cpController.text.trim(),
      municipio: _municipioController.text.trim(),
      estado: _selectedEstadoNombre,
      pais: 'México',
      correo: _correoController.text.trim(),
      telefonoMovil: _telefonoController.text.trim(),
      telefonoAlternativo: _telAltController.text.trim().isEmpty
          ? null
          : _telAltController.text.trim(),
      usuario: _regUsuarioController.text.trim(),
      contrasena: _regContrasenaController.text,
      codigoVerificacion: code,
    );
    if (!success && mounted) {
      _showError(auth.errorMessage ?? 'No fue posible registrar la cuenta');
    } else if (success) {
      _regOtpTimer?.cancel();
    }
  }

  Future<void> _pickBirthDate() async {
    final now = DateTime.now();
    final maxDate = DateTime(now.year - 18, now.month, now.day);
    final initial = DateTime.tryParse(_fechaNacController.text.trim()) ?? DateTime(1998, 5, 15);
    final safeInitial = initial.isAfter(maxDate) ? maxDate : initial;

    final picked = await showDatePicker(
      context: context,
      initialDate: safeInitial,
      firstDate: DateTime(now.year - 100, 1, 1),
      lastDate: maxDate,
    );
    if (picked != null && mounted) {
      final y = picked.year.toString().padLeft(4, '0');
      final m = picked.month.toString().padLeft(2, '0');
      final d = picked.day.toString().padLeft(2, '0');
      setState(() {
        _fechaNacController.text = '$y-$m-$d';
      });
    }
  }

  void _showError(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(msg),
        backgroundColor: AppTheme.errorRed,
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  void _showApiConfigDialog() {
    _apiUrlController.text = ApiClient().baseUrl;
    String? inlineError;
    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          title: Row(
            children: [
              Icon(Icons.dns_rounded, color: AppTheme.brandAccent(ctx)),
              const SizedBox(width: 8),
              const Flexible(
                child: Text('Configurar Servidor API', style: TextStyle(fontSize: 17)),
              ),
            ],
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'Elige un servidor rápido o escribe la URL del backend Spring Boot:',
                style: TextStyle(fontSize: 12, color: AppTheme.textMuted),
              ),
              const SizedBox(height: 10),
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: [
                  ActionChip(
                    visualDensity: VisualDensity.compact,
                    label: const Text('Nube (Render)', style: TextStyle(fontSize: 11)),
                    onPressed: () {
                      setDialogState(() {
                        _apiUrlController.text =
                            'https://prueba-aplicaciones-web-progresivas.onrender.com';
                        inlineError = null;
                      });
                    },
                  ),
                  ActionChip(
                    visualDensity: VisualDensity.compact,
                    label: const Text('USB Local', style: TextStyle(fontSize: 11)),
                    onPressed: () {
                      setDialogState(() {
                        _apiUrlController.text = 'http://localhost:10000';
                        inlineError = null;
                      });
                    },
                  ),
                  ActionChip(
                    visualDensity: VisualDensity.compact,
                    label: const Text('Wi-Fi Casa', style: TextStyle(fontSize: 11)),
                    onPressed: () {
                      setDialogState(() {
                        _apiUrlController.text = 'http://192.168.1.56:10000';
                        inlineError = null;
                      });
                    },
                  ),
                ],
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _apiUrlController,
                keyboardType: TextInputType.url,
                onChanged: (_) {
                  if (inlineError != null) {
                    setDialogState(() => inlineError = null);
                  }
                },
                decoration: InputDecoration(
                  labelText: 'URL Base',
                  hintText: 'https://api.midominio.com',
                  errorText: inlineError,
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('Cancelar'),
            ),
            ElevatedButton(
              onPressed: () async {
                final rawUrl = _apiUrlController.text.trim();
                final validationErr = ApiClient.validateBaseUrl(rawUrl);
                if (validationErr != null) {
                  setDialogState(() => inlineError = validationErr);
                  return;
                }
                final normalized = ApiClient.normalizeBaseUrl(rawUrl);
                await Provider.of<AuthProvider>(context, listen: false).saveApiUrl(normalized);
                if (!ctx.mounted) return;
                Navigator.of(ctx).pop();
                if (!mounted) return;
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text('Servidor API actualizado a: $normalized'),
                    behavior: SnackBarBehavior.floating,
                  ),
                );
              },
              child: const Text('Guardar'),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final auth = Provider.of<AuthProvider>(context);
    final isDark = AppTheme.isDark(context);

    return Scaffold(
      backgroundColor: AppTheme.scaffoldBg(context),
      body: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            colors: isDark
                ? const [Color(0xFF040814), Color(0xFF0A193B), Color(0xFF060D20)]
                : [
                    AppTheme.backgroundSlate,
                    const Color(0xFFEFF6FF),
                    AppTheme.backgroundSlate,
                  ],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
        ),
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 500),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                          decoration: BoxDecoration(
                            color: AppTheme.brandAccent(context).withValues(alpha: 0.1),
                            borderRadius: BorderRadius.circular(16),
                            border: Border.all(
                              color: AppTheme.brandAccent(context).withValues(alpha: 0.28),
                            ),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Container(
                                width: 7,
                                height: 7,
                                decoration: const BoxDecoration(
                                  color: Color(0xFF10B981),
                                  shape: BoxShape.circle,
                                ),
                              ),
                              const SizedBox(width: 6),
                              Text(
                                'Banca Privada · GestoPago',
                                style: TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.w700,
                                  color: AppTheme.textPrimary(context),
                                ),
                              ),
                            ],
                          ),
                        ),
                        Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            IconButton(
                              onPressed: () => AppTheme.toggleTheme(),
                              icon: Icon(
                                isDark ? Icons.light_mode_rounded : Icons.dark_mode_rounded,
                                color: isDark ? const Color(0xFFFDE68A) : AppTheme.textMuted,
                              ),
                              tooltip: isDark ? 'Modo Claro' : 'Modo Oscuro',
                            ),
                            IconButton(
                              onPressed: _showApiConfigDialog,
                              icon: Icon(
                                Icons.settings_ethernet_rounded,
                                color: AppTheme.textSecondary(context),
                              ),
                              tooltip: 'Configurar URL de API',
                            ),
                          ],
                        ),
                      ],
                    ),

                    const SizedBox(height: 8),

                    // Executive Emblem Header
                    Center(
                      child: Container(
                        width: 68,
                        height: 68,
                        decoration: BoxDecoration(
                          gradient: const LinearGradient(
                            colors: [Color(0xFF0C1E4A), AppTheme.secondaryBlue],
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                          ),
                          borderRadius: BorderRadius.circular(22),
                          border: Border.all(
                            color: const Color(0xFFFDE68A).withValues(alpha: 0.5),
                          ),
                          boxShadow: [
                            BoxShadow(
                              color: AppTheme.primaryNavy.withValues(alpha: 0.32),
                              blurRadius: 18,
                              offset: const Offset(0, 8),
                            ),
                          ],
                        ),
                        child: const Icon(
                          Icons.account_balance_rounded,
                          color: Color(0xFFFDE68A),
                          size: 34,
                        ),
                      ),
                    ),

                    const SizedBox(height: 12),

                    Text(
                      'Banco Digital',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 27,
                        fontWeight: FontWeight.w900,
                        color: AppTheme.textPrimary(context),
                        letterSpacing: -0.5,
                      ),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      'Portal Ejecutivo de Pagos, Recargas y Expediente KYC',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 12.5, color: AppTheme.textSecondary(context)),
                    ),

                    const SizedBox(height: 10),

                    Center(
                      child: ActionChip(
                        avatar: const Icon(
                          Icons.dns_rounded,
                          size: 15,
                          color: AppTheme.secondaryBlue,
                        ),
                        label: Text(
                          'Servidor: ${auth.apiServerLabel} · Cambiar',
                          style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600),
                        ),
                        onPressed: _showApiConfigDialog,
                      ),
                    ),

                    const SizedBox(height: 14),

                    if (auth.errorMessage != null && auth.errorMessage!.isNotEmpty) ...[
                      Container(
                        padding: const EdgeInsets.all(12),
                        margin: const EdgeInsets.only(bottom: 14),
                        decoration: BoxDecoration(
                          color: AppTheme.errorRed.withValues(alpha: 0.08),
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: AppTheme.errorRed.withValues(alpha: 0.4)),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                const Icon(
                                  Icons.info_outline_rounded,
                                  color: AppTheme.errorRed,
                                  size: 18,
                                ),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: Text(
                                    auth.errorMessage!,
                                    style: const TextStyle(
                                      fontSize: 12,
                                      fontWeight: FontWeight.w600,
                                      color: AppTheme.errorRed,
                                    ),
                                  ),
                                ),
                                IconButton(
                                  icon: const Icon(
                                    Icons.close_rounded,
                                    size: 16,
                                    color: AppTheme.errorRed,
                                  ),
                                  padding: EdgeInsets.zero,
                                  constraints: const BoxConstraints(),
                                  onPressed: () => auth.clearError(),
                                ),
                              ],
                            ),
                            if (auth.errorMessage!.toLowerCase().contains('conexión') ||
                                auth.errorMessage!.toLowerCase().contains('wi-fi') ||
                                auth.errorMessage!.toLowerCase().contains('render')) ...[
                              const SizedBox(height: 8),
                              Align(
                                alignment: Alignment.centerRight,
                                child: TextButton.icon(
                                  onPressed: _showApiConfigDialog,
                                  icon: const Icon(Icons.settings_ethernet_rounded, size: 16),
                                  label: const Text(
                                    'Cambiar servidor',
                                    style: TextStyle(fontSize: 12),
                                  ),
                                  style: TextButton.styleFrom(
                                    visualDensity: VisualDensity.compact,
                                    padding: const EdgeInsets.symmetric(horizontal: 8),
                                  ),
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                    ],

                    // Segmented Tabs: Login vs Register
                    SegmentedButton<bool>(
                      segments: const [
                        ButtonSegment<bool>(
                          value: false,
                          icon: Icon(Icons.login_rounded, size: 18),
                          label: Text('Iniciar Sesión'),
                        ),
                        ButtonSegment<bool>(
                          value: true,
                          icon: Icon(Icons.person_add_alt_1_rounded, size: 18),
                          label: Text('Crear Cuenta'),
                        ),
                      ],
                      selected: {_isRegisterMode},
                      onSelectionChanged: (sel) {
                        auth.clearError();
                        _regOtpTimer?.cancel();
                        setState(() {
                          _isRegisterMode = sel.first;
                          _regStep = 1;
                          _inlineRegError = null;
                          _regOtpController.clear();
                          _regOtpFeedback = null;
                          _regOtpSecondsRemaining = 0;
                        });
                      },
                    ),

                    const SizedBox(height: 16),

                    Card(
                      elevation: 2,
                      shadowColor: AppTheme.primaryNavy.withValues(alpha: 0.12),
                      child: Padding(
                        padding: const EdgeInsets.all(20),
                        child: _isRegisterMode
                            ? _buildRegisterForm(auth)
                            : _buildLoginSection(auth),
                      ),
                    ),

                    const SizedBox(height: 16),

                    Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: AppTheme.cardBg(context).withValues(alpha: 0.75),
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(color: AppTheme.borderCol(context)),
                      ),
                      child: Column(
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              const Icon(
                                Icons.verified_user_rounded,
                                size: 16,
                                color: AppTheme.accentEmerald,
                              ),
                              const SizedBox(width: 6),
                              Flexible(
                                child: Text(
                                  'Expediente KYC Oficial · 2FA Brevo · GestoPago',
                                  textAlign: TextAlign.center,
                                  style: TextStyle(
                                    fontWeight: FontWeight.bold,
                                    fontSize: 11.5,
                                    color: AppTheme.textPrimary(context),
                                  ),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 4),
                          Text(
                            'Validación normativa de CURP y RFC, acceso biométrico con huella digital y recibos PDF con ID_TX.',
                            textAlign: TextAlign.center,
                            style: TextStyle(
                              fontSize: 11,
                              color: AppTheme.textSecondary(context),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildLoginSection(AuthProvider auth) {
    final canChooseBiometric = auth.canUseBiometrics && auth.hasStoredBiometricCredentials;
    final useBiometricTab = canChooseBiometric && auth.preferredLoginMethod == 'biometric';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          'Accede a tu cuenta',
          style: TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.bold,
            color: AppTheme.textPrimary(context),
          ),
        ),
        const SizedBox(height: 4),
        Text(
          'Elige tu método de ingreso preferido.',
          style: TextStyle(fontSize: 12, color: AppTheme.textSecondary(context)),
        ),
        if (canChooseBiometric) ...[
          const SizedBox(height: 14),
          SegmentedButton<String>(
            showSelectedIcon: false,
            segments: [
              ButtonSegment<String>(
                value: 'biometric',
                icon: const Icon(Icons.fingerprint_rounded, size: 17),
                label: Text(
                  auth.biometricLabel,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              const ButtonSegment<String>(
                value: 'password',
                icon: Icon(Icons.password_rounded, size: 17),
                label: Text(
                  'Contraseña',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
            selected: {auth.preferredLoginMethod},
            onSelectionChanged: (sel) {
              auth.clearError();
              auth.setPreferredLoginMethod(sel.first);
            },
          ),
        ],
        const SizedBox(height: 16),
        if (useBiometricTab) _buildBiometricLoginPanel(auth) else _buildPasswordLoginForm(auth),
      ],
    );
  }

  Widget _buildBiometricLoginPanel(AuthProvider auth) {
    final profile = auth.storedBiometricProfile!;
    final busy = auth.isBiometricBusy || auth.isLoading;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: AppTheme.elevatedBg(context),
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: AppTheme.borderCol(context)),
          ),
          child: Row(
            children: [
              CircleAvatar(
                radius: 20,
                backgroundColor: AppTheme.brandAccent(context).withValues(alpha: 0.16),
                child: Icon(Icons.person_rounded, color: AppTheme.brandAccent(context)),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      profile.nombreCompleto,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 14,
                        color: AppTheme.textPrimary(context),
                      ),
                    ),
                    Text(
                      '@${profile.usuario} · Huella vinculada',
                      style: const TextStyle(
                        fontSize: 12,
                        color: AppTheme.accentEmerald,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 20),
        Center(
          child: InkWell(
            onTap: busy ? null : _submitBiometricLogin,
            borderRadius: BorderRadius.circular(48),
            child: Container(
              width: 88,
              height: 88,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: const LinearGradient(
                  colors: [AppTheme.primaryNavy, AppTheme.secondaryBlue],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                boxShadow: [
                  BoxShadow(
                    color: AppTheme.secondaryBlue.withValues(alpha: 0.3),
                    blurRadius: 18,
                    offset: const Offset(0, 6),
                  ),
                ],
              ),
              child: busy
                  ? const Center(
                      child: SizedBox(
                        width: 28,
                        height: 28,
                        child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2.5),
                      ),
                    )
                  : const Icon(Icons.fingerprint_rounded, color: Colors.white, size: 46),
            ),
          ),
        ),
        const SizedBox(height: 16),
        ElevatedButton.icon(
          onPressed: busy ? null : _submitBiometricLogin,
          icon: const Icon(Icons.fingerprint_rounded, size: 20),
          label: Text(busy ? 'Verificando huella...' : 'Ingresar con ${auth.biometricLabel}'),
        ),
        const SizedBox(height: 10),
        Wrap(
          alignment: WrapAlignment.spaceBetween,
          spacing: 8,
          children: [
            TextButton(
              onPressed: busy ? null : () => auth.setPreferredLoginMethod('password'),
              child: const Text('Usar contraseña', style: TextStyle(fontSize: 12)),
            ),
            TextButton(
              onPressed: busy
                  ? null
                  : () async {
                      await auth.clearStoredBiometricAccount();
                      _usuarioController.clear();
                    },
              child: const Text(
                'Cambiar de cuenta',
                style: TextStyle(fontSize: 12, color: AppTheme.textMuted),
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildPasswordLoginForm(AuthProvider auth) {
    final busy = auth.isLoading || auth.isBiometricBusy;

    return Form(
      key: _loginFormKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextFormField(
            controller: _usuarioController,
            textInputAction: TextInputAction.next,
            decoration: const InputDecoration(
              labelText: 'Usuario',
              hintText: 'Ingresa tu usuario',
              prefixIcon: Icon(Icons.person_outline_rounded, color: AppTheme.textMuted),
            ),
            validator: (val) => (val == null || val.trim().isEmpty) ? 'Ingresa tu usuario' : null,
          ),
          const SizedBox(height: 14),
          TextFormField(
            controller: _contrasenaController,
            obscureText: _obscurePassword,
            textInputAction: TextInputAction.done,
            onFieldSubmitted: (_) => busy ? null : _submitLogin(),
            decoration: InputDecoration(
              labelText: 'Contraseña',
              hintText: '••••••••',
              prefixIcon: const Icon(Icons.lock_outline_rounded, color: AppTheme.textMuted),
              suffixIcon: IconButton(
                icon: Icon(
                  _obscurePassword ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                  color: AppTheme.textMuted,
                ),
                onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
              ),
            ),
            validator: (val) => (val == null || val.isEmpty) ? 'Ingresa tu contraseña' : null,
          ),
          const SizedBox(height: 18),
          ElevatedButton(
            onPressed: busy ? null : _submitLogin,
            child: busy
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                  )
                : const Text('Acceder a mi Cuenta'),
          ),
          if (auth.canUseBiometrics && auth.hasStoredBiometricCredentials) ...[
            const SizedBox(height: 10),
            OutlinedButton.icon(
              onPressed: busy ? null : _submitBiometricLogin,
              icon: const Icon(Icons.fingerprint_rounded, size: 19),
              label: Text('Entrar rápido con ${auth.biometricLabel}'),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildRegisterStepper(Color accent) {
    const labels = ['1. Identidad', '2. Fiscal', '3. Acceso', '4. 2FA'];
    return Row(
      children: List.generate(4, (idx) {
        final stepNum = idx + 1;
        final isActive = _regStep == stepNum;
        final isDone = _regStep > stepNum;
        return Expanded(
          child: Padding(
            padding: EdgeInsets.only(right: idx < 3 ? 6 : 0),
            child: InkWell(
              borderRadius: BorderRadius.circular(10),
              onTap: isDone
                  ? () => setState(() {
                        _inlineRegError = null;
                        _regStep = stepNum;
                      })
                  : null,
              child: Container(
                padding: const EdgeInsets.symmetric(vertical: 7, horizontal: 4),
                decoration: BoxDecoration(
                  color: isActive
                      ? accent.withValues(alpha: 0.14)
                      : isDone
                          ? AppTheme.accentEmerald.withValues(alpha: 0.14)
                          : AppTheme.elevatedBg(context),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(
                    color: isActive
                        ? accent
                        : isDone
                            ? AppTheme.accentEmerald.withValues(alpha: 0.5)
                            : AppTheme.borderCol(context),
                  ),
                ),
                child: Text(
                  labels[idx],
                  textAlign: TextAlign.center,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 10.5,
                    fontWeight: FontWeight.w800,
                    color: isActive
                        ? accent
                        : isDone
                            ? AppTheme.accentEmerald
                            : AppTheme.textSecondary(context),
                  ),
                ),
              ),
            ),
          ),
        );
      }),
    );
  }

  Widget _buildRegisterForm(AuthProvider auth) {
    final pwd = _regContrasenaController.text;
    final accent = AppTheme.brandAccent(context);

    final titles = [
      'Datos Personales del Titular',
      'Expediente Fiscal (CURP / RFC) y Domicilio',
      'Contacto Verificado y Credenciales',
      'Verifica tu correo (2FA)',
    ];
    final subtitles = [
      'Ingresa tu nombre legal completo y fecha de nacimiento oficial (+18 años).',
      'Valida tu CURP de 18 caracteres, RFC con homoclave y domicilio en México.',
      'Configura tu correo real para recibir códigos 2FA y tu acceso Web + Móvil.',
      'Ingresa el código de 6 dígitos enviado a tu correo para aperturar tu cuenta.',
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _buildRegisterStepper(accent),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: Text(
                titles[_regStep - 1],
                style: TextStyle(
                  fontSize: 16.5,
                  fontWeight: FontWeight.bold,
                  color: AppTheme.textPrimary(context),
                ),
              ),
            ),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 3),
              decoration: BoxDecoration(
                color: accent.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: accent.withValues(alpha: 0.35)),
              ),
              child: Text(
                'Paso $_regStep de 4',
                style: TextStyle(fontSize: 10.5, fontWeight: FontWeight.w800, color: accent),
              ),
            ),
          ],
        ),
        const SizedBox(height: 4),
        Text(
          subtitles[_regStep - 1],
          style: TextStyle(fontSize: 12, color: AppTheme.textSecondary(context)),
        ),
        if (_inlineRegError != null) ...[
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: AppTheme.errorRed.withValues(alpha: 0.09),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: AppTheme.errorRed.withValues(alpha: 0.4)),
            ),
            child: Text(
              _inlineRegError!,
              style: const TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: AppTheme.errorRed,
              ),
            ),
          ),
        ],
        const SizedBox(height: 14),

        // --- STEP 1: IDENTITY ---
        if (_regStep == 1) ...[
          Row(
            children: [
              Expanded(
                child: TextFormField(
                  controller: _nombreController,
                  textCapitalization: TextCapitalization.words,
                  decoration: const InputDecoration(labelText: 'Nombre *'),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: TextFormField(
                  controller: _segundoNombreController,
                  textCapitalization: TextCapitalization.words,
                  decoration: const InputDecoration(labelText: 'Segundo nombre'),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: TextFormField(
                  controller: _apPaternoController,
                  textCapitalization: TextCapitalization.words,
                  decoration: const InputDecoration(labelText: 'Apellido paterno *'),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: TextFormField(
                  controller: _apMaternoController,
                  textCapitalization: TextCapitalization.words,
                  decoration: const InputDecoration(labelText: 'Apellido materno *'),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          TextFormField(
            controller: _fechaNacController,
            keyboardType: TextInputType.datetime,
            decoration: InputDecoration(
              labelText: 'Fecha de nacimiento (YYYY-MM-DD) *',
              prefixIcon: const Icon(Icons.cake_outlined),
              suffixIcon: IconButton(
                icon: const Icon(Icons.calendar_month_rounded),
                tooltip: 'Elegir fecha',
                onPressed: _pickBirthDate,
              ),
            ),
          ),
          const SizedBox(height: 10),
          DropdownButtonFormField<String>(
            initialValue: _sexo,
            decoration: const InputDecoration(labelText: 'Sexo *'),
            items: const [
              DropdownMenuItem(value: 'HOMBRE', child: Text('Hombre (H)')),
              DropdownMenuItem(value: 'MUJER', child: Text('Mujer (M)')),
            ],
            onChanged: (v) {
              if (v != null) setState(() => _sexo = v);
            },
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: DropdownButtonFormField<String>(
                  initialValue: _nacionalidad,
                  isExpanded: true,
                  decoration: const InputDecoration(labelText: 'Nacionalidad *'),
                  items: const [
                    DropdownMenuItem(value: 'MEXICANA', child: Text('Mexicana')),
                    DropdownMenuItem(value: 'EXTRANJERA', child: Text('Extranjera')),
                  ],
                  onChanged: (v) {
                    if (v != null) setState(() => _nacionalidad = v);
                  },
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: DropdownButtonFormField<String>(
                  initialValue: _estadoCivil,
                  isExpanded: true,
                  decoration: const InputDecoration(labelText: 'Estado civil *'),
                  items: const [
                    DropdownMenuItem(value: 'SOLTERO', child: Text('Soltero(a)')),
                    DropdownMenuItem(value: 'CASADO', child: Text('Casado(a)')),
                    DropdownMenuItem(value: 'UNION_LIBRE', child: Text('Unión libre')),
                    DropdownMenuItem(value: 'DIVORCIADO', child: Text('Divorciado(a)')),
                    DropdownMenuItem(value: 'VIUDO', child: Text('Viudo(a)')),
                  ],
                  onChanged: (v) {
                    if (v != null) setState(() => _estadoCivil = v);
                  },
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          ElevatedButton.icon(
            onPressed: _advanceFromStep1,
            icon: const Icon(Icons.arrow_forward_rounded, size: 18),
            label: const Text('Continuar a Datos Fiscales'),
          ),
        ],

        // --- STEP 2: FISCAL (CURP & RFC), ECONOMIC & ADDRESS ---
        if (_regStep == 2) ...[
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: accent.withValues(alpha: 0.08),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: accent.withValues(alpha: 0.28)),
            ),
            child: Row(
              children: [
                Icon(Icons.auto_awesome_rounded, size: 18, color: accent),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Asistente RENAPO / SAT',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color: AppTheme.textPrimary(context),
                    ),
                  ),
                ),
                TextButton.icon(
                  onPressed: _autoGenerateFiscal,
                  icon: const Icon(Icons.bolt_rounded, size: 16),
                  label: const Text('Calcular CURP / RFC', style: TextStyle(fontSize: 12)),
                ),
              ],
            ),
          ),
          const SizedBox(height: 10),
          DropdownButtonFormField<String>(
            initialValue: _entidadCodigo,
            isExpanded: true,
            decoration: const InputDecoration(labelText: 'Entidad Federativa *'),
            items: ValidationUtils.estadosMexico
                .map(
                  (st) => DropdownMenuItem<String>(
                    value: st.key,
                    child: Text('${st.value} (${st.key})', overflow: TextOverflow.ellipsis),
                  ),
                )
                .toList(),
            onChanged: (v) {
              if (v != null) setState(() => _entidadCodigo = v);
            },
          ),
          const SizedBox(height: 10),
          TextFormField(
            controller: _curpController,
            maxLength: 18,
            textCapitalization: TextCapitalization.characters,
            onChanged: (val) {
              final upper = val.toUpperCase().replaceAll(RegExp(r'[^A-Z0-9]'), '');
              if (upper != val) {
                _curpController.value = TextEditingValue(
                  text: upper,
                  selection: TextSelection.collapsed(offset: upper.length),
                );
              }
              setState(() {});
            },
            decoration: InputDecoration(
              labelText: 'CURP (18 caracteres) *',
              counterText: '${_curpController.text.length}/18',
              prefixIcon: const Icon(Icons.badge_outlined),
              suffixIcon: ValidationUtils.esCurpValida(_curpController.text)
                  ? const Icon(Icons.check_circle_rounded, color: AppTheme.accentEmerald, size: 19)
                  : null,
            ),
          ),
          const SizedBox(height: 10),
          TextFormField(
            controller: _rfcController,
            maxLength: 13,
            textCapitalization: TextCapitalization.characters,
            onChanged: (val) {
              final upper = val.toUpperCase().replaceAll(RegExp(r'[^A-ZÑ&0-9]'), '');
              if (upper != val) {
                _rfcController.value = TextEditingValue(
                  text: upper,
                  selection: TextSelection.collapsed(offset: upper.length),
                );
              }
              setState(() {});
            },
            decoration: InputDecoration(
              labelText: 'RFC con Homoclave (13) *',
              counterText: '${_rfcController.text.length}/13',
              prefixIcon: const Icon(Icons.assignment_ind_outlined),
              suffixIcon: ValidationUtils.esRfcValido(_rfcController.text)
                  ? const Icon(Icons.check_circle_rounded, color: AppTheme.accentEmerald, size: 19)
                  : null,
            ),
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: TextFormField(
                  controller: _ocupacionController,
                  decoration: const InputDecoration(labelText: 'Ocupación *'),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: TextFormField(
                  controller: _empresaController,
                  decoration: const InputDecoration(labelText: 'Empresa *'),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          TextFormField(
            controller: _ingresoController,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: const InputDecoration(
              labelText: 'Ingreso mensual estimado (MXN) *',
              prefixText: '\$ ',
            ),
          ),
          const SizedBox(height: 12),
          Text(
            'Domicilio del Titular',
            style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.bold, color: accent),
          ),
          const SizedBox(height: 8),
          TextFormField(
            controller: _calleController,
            decoration: const InputDecoration(labelText: 'Calle y Avenida *'),
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: TextFormField(
                  controller: _numExtController,
                  decoration: const InputDecoration(labelText: 'Núm. Ext. *'),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: TextFormField(
                  controller: _numIntController,
                  decoration: const InputDecoration(labelText: 'Núm. Int.'),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: TextFormField(
                  controller: _coloniaController,
                  decoration: const InputDecoration(labelText: 'Colonia *'),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: TextFormField(
                  controller: _cpController,
                  keyboardType: TextInputType.number,
                  maxLength: 5,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                  decoration: const InputDecoration(
                    labelText: 'Código Postal (5 díg) *',
                    counterText: '',
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          TextFormField(
            controller: _municipioController,
            decoration: const InputDecoration(labelText: 'Municipio / Alcaldía *'),
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              OutlinedButton.icon(
                onPressed: () => setState(() {
                  _inlineRegError = null;
                  _regStep = 1;
                }),
                icon: const Icon(Icons.arrow_back_rounded, size: 16),
                label: const Text('Atrás'),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: _advanceFromStep2,
                  icon: const Icon(Icons.arrow_forward_rounded, size: 18),
                  label: const Text('Continuar a Contacto y Acceso'),
                ),
              ),
            ],
          ),
        ],

        // --- STEP 3: CONTACT & ACCESS ---
        if (_regStep == 3) ...[
          TextFormField(
            controller: _correoController,
            keyboardType: TextInputType.emailAddress,
            decoration: const InputDecoration(
              labelText: 'Correo electrónico real (para 2FA y recibos) *',
              prefixIcon: Icon(Icons.mail_outline_rounded),
            ),
          ),
          const SizedBox(height: 10),
          TextFormField(
            controller: _telefonoController,
            keyboardType: TextInputType.number,
            maxLength: 10,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            decoration: const InputDecoration(
              labelText: 'Teléfono móvil México (10 dígitos) *',
              prefixIcon: Icon(Icons.phone_android_rounded),
              counterText: '',
            ),
          ),
          const SizedBox(height: 10),
          TextFormField(
            controller: _telAltController,
            keyboardType: TextInputType.number,
            maxLength: 10,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            decoration: const InputDecoration(
              labelText: 'Teléfono alternativo (opcional)',
              prefixIcon: Icon(Icons.phone_outlined),
              counterText: '',
            ),
          ),
          const SizedBox(height: 10),
          TextFormField(
            controller: _regUsuarioController,
            decoration: const InputDecoration(
              labelText: 'Usuario (mín. 4 caracteres) *',
              prefixIcon: Icon(Icons.alternate_email_rounded),
            ),
          ),
          const SizedBox(height: 10),
          TextFormField(
            controller: _regContrasenaController,
            obscureText: _obscureRegPassword,
            onChanged: (_) => setState(() {}),
            decoration: InputDecoration(
              labelText: 'Contraseña (8+ car., Mayús, Minús, Núm) *',
              prefixIcon: const Icon(Icons.lock_outline_rounded),
              suffixIcon: IconButton(
                icon: Icon(
                  _obscureRegPassword ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                ),
                onPressed: () => setState(() => _obscureRegPassword = !_obscureRegPassword),
              ),
            ),
          ),
          if (pwd.isNotEmpty) ...[
            const SizedBox(height: 8),
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: [
                _buildPwdRule('8+ car.', pwd.length >= 8),
                _buildPwdRule('Mayúscula', RegExp(r'[A-Z]').hasMatch(pwd)),
                _buildPwdRule('Minúscula', RegExp(r'[a-z]').hasMatch(pwd)),
                _buildPwdRule('Número', RegExp(r'\d').hasMatch(pwd)),
              ],
            ),
          ],
          const SizedBox(height: 10),
          TextFormField(
            controller: _regConfirmContrasenaController,
            obscureText: _obscureRegConfirmPassword,
            decoration: InputDecoration(
              labelText: 'Confirmar contraseña *',
              prefixIcon: const Icon(Icons.check_circle_outline_rounded),
              suffixIcon: IconButton(
                icon: Icon(
                  _obscureRegConfirmPassword
                      ? Icons.visibility_outlined
                      : Icons.visibility_off_outlined,
                ),
                onPressed: () =>
                    setState(() => _obscureRegConfirmPassword = !_obscureRegConfirmPassword),
              ),
            ),
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              OutlinedButton.icon(
                onPressed: auth.isLoading
                    ? null
                    : () => setState(() {
                          _inlineRegError = null;
                          _regStep = 2;
                        }),
                icon: const Icon(Icons.arrow_back_rounded, size: 16),
                label: const Text('Atrás'),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: auth.isLoading ? null : () => _sendRegistrationCode(isResend: false),
                  style: ElevatedButton.styleFrom(backgroundColor: AppTheme.accentEmerald),
                  icon: auth.isLoading
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                        )
                      : const Icon(Icons.mark_email_unread_outlined, size: 18),
                  label: Text(
                    auth.isLoading ? 'Enviando código...' : 'Enviar Código 2FA al Correo',
                  ),
                ),
              ),
            ],
          ),
        ],

        // --- STEP 4: 2FA OTP VERIFICATION ---
        if (_regStep == 4) ...[
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: AppTheme.elevatedBg(context),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: AppTheme.borderCol(context)),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  [
                    _nombreController.text.trim(),
                    _segundoNombreController.text.trim(),
                    _apPaternoController.text.trim(),
                    _apMaternoController.text.trim(),
                  ].where((s) => s.isNotEmpty).join(' '),
                  style: TextStyle(
                    fontSize: 12.5,
                    fontWeight: FontWeight.bold,
                    color: AppTheme.textPrimary(context),
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'CURP: ${_curpController.text.trim()} · RFC: ${_rfcController.text.trim()}',
                  style: TextStyle(
                    fontSize: 11,
                    fontFamily: 'monospace',
                    fontWeight: FontWeight.w600,
                    color: accent,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: accent.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: accent.withValues(alpha: 0.3)),
            ),
            child: Row(
              children: [
                Icon(Icons.mark_email_read_outlined, size: 18, color: accent),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    _regOtpSentTo.isNotEmpty ? _regOtpSentTo : _correoController.text.trim(),
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w700,
                      color: AppTheme.textPrimary(context),
                    ),
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: _regOtpSecondsRemaining > 0
                        ? AppTheme.cardBg(context)
                        : Colors.amber.withValues(alpha: 0.2),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(
                      color:
                          _regOtpSecondsRemaining > 0 ? AppTheme.borderCol(context) : Colors.amber,
                    ),
                  ),
                  child: Text(
                    _regOtpSecondsRemaining > 0
                        ? _formatCountdown(_regOtpSecondsRemaining)
                        : 'Expirado',
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.bold,
                      fontFamily: 'monospace',
                      color: _regOtpSecondsRemaining > 0 ? accent : Colors.amber.shade700,
                    ),
                  ),
                ),
              ],
            ),
          ),
          if (_regOtpFeedback != null) ...[
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: AppTheme.accentEmerald.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: AppTheme.accentEmerald.withValues(alpha: 0.4)),
              ),
              child: Text(
                _regOtpFeedback!,
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: AppTheme.accentEmerald,
                ),
              ),
            ),
          ],
          const SizedBox(height: 14),
          TextField(
            controller: _regOtpController,
            keyboardType: TextInputType.number,
            maxLength: 6,
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.w800,
              letterSpacing: 8,
              color: AppTheme.textPrimary(context),
            ),
            inputFormatters: [
              FilteringTextInputFormatter.digitsOnly,
              LengthLimitingTextInputFormatter(6),
            ],
            onChanged: (_) => setState(() {}),
            decoration: const InputDecoration(
              labelText: 'Código 2FA (6 dígitos)',
              hintText: '000000',
              counterText: '',
              prefixIcon: Icon(Icons.pin_rounded),
            ),
          ),
          const SizedBox(height: 16),
          ElevatedButton(
            onPressed: (auth.isLoading || _regOtpController.text.trim().length < 6)
                ? null
                : _submitRegister,
            style: ElevatedButton.styleFrom(backgroundColor: AppTheme.accentEmerald),
            child: auth.isLoading
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                  )
                : const Text('Verificar y Crear Cuenta'),
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: auth.isLoading
                      ? null
                      : () {
                          _regOtpTimer?.cancel();
                          auth.clearError();
                          setState(() {
                            _regStep = 3;
                            _regOtpController.clear();
                            _regOtpFeedback = null;
                          });
                        },
                  icon: const Icon(Icons.arrow_back_rounded, size: 16),
                  label: const Text('Editar datos', style: TextStyle(fontSize: 12)),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: TextButton.icon(
                  onPressed: auth.isLoading ? null : () => _sendRegistrationCode(isResend: true),
                  icon: const Icon(Icons.refresh_rounded, size: 16),
                  label: const Text('Reenviar código', style: TextStyle(fontSize: 12)),
                ),
              ),
            ],
          ),
        ],
      ],
    );
  }

  Widget _buildPwdRule(String label, bool ok) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: ok ? AppTheme.accentEmerald.withValues(alpha: 0.14) : AppTheme.elevatedBg(context),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(
          color: ok ? AppTheme.accentEmerald.withValues(alpha: 0.45) : AppTheme.borderCol(context),
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            ok ? Icons.check_circle_rounded : Icons.radio_button_unchecked_rounded,
            size: 13,
            color: ok ? AppTheme.accentEmerald : AppTheme.textSecondary(context),
          ),
          const SizedBox(width: 4),
          Text(
            label,
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w600,
              color: ok ? AppTheme.accentEmerald : AppTheme.textSecondary(context),
            ),
          ),
        ],
      ),
    );
  }
}
