import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../providers/auth_provider.dart';
import '../services/api_client.dart';
import '../services/catalog_service.dart';
import '../theme/app_theme.dart';
import '../utils/mexico_catalogs.dart';
import '../utils/validation_utils.dart';
import '../widgets/totp_widgets.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  static const String _mensajeCorreoDuplicado =
      'Este correo electrónico ya está en uso en otra cuenta. Inicia sesión o utiliza otro correo.';

  final _loginFormKey = GlobalKey<FormState>();
  final _regFormKey = GlobalKey<FormState>();
  final List<GlobalKey> _stepChipKeys = List.generate(6, (_) => GlobalKey());

  bool _isRegisterMode = false;
  final bool _enrollBiometricOnLogin = false;

  // Login controllers
  final _usuarioController = TextEditingController();
  final _contrasenaController = TextEditingController();
  bool _obscurePassword = true;

  // Register Step 1..6
  int _regStep = 1;

  // Step 1: Datos Personales
  final _nombreController = TextEditingController();
  final _segundoNombreController = TextEditingController();
  final _apPaternoController = TextEditingController();
  final _apMaternoController = TextEditingController();
  final _fechaNacController = TextEditingController(text: '1998-05-15');
  String _sexo = 'HOMBRE';
  String _nacionalidad = 'MEXICANA';
  String _estadoCivil = 'SOLTERO';

  // Step 2: Datos Fiscales
  String _entidadCodigo = 'DF';
  final _curpController = TextEditingController();
  final _rfcController = TextEditingController();
  String? _curpDisponibilidadError;
  String? _rfcDisponibilidadError;
  bool _checkingFiscalDisponibilidad = false;

  // Step 3: Ocupación e Ingresos
  String _ocupacionCatalogo = MexicoCatalogs.catalogoOcupaciones.first;
  String _profesionTabulador = MexicoCatalogs.tabuladorProfesiones.first.label;
  final _empresaController = TextEditingController();
  double _ingresoSeleccionado = MexicoCatalogs.tabuladorSueldos[2].montoRepresentativo;

  // Step 4: Domicilio SEPOMEX
  String _tipoVialidad = 'Calle';
  final _calleController = TextEditingController();
  final _numExtController = TextEditingController();
  final _numIntController = TextEditingController();
  final _cpController = TextEditingController();
  String _estadoDomicilioCodigo = '';
  String _estadoDomicilioNombre = '';
  String _municipioSeleccionado = '';
  List<String> _coloniasDelCp = <String>[];
  String? _coloniaSeleccionada;
  bool _loadingCpCatalogo = false;
  String? _cpErrorRemoto;

  // Step 5: Contacto y Acceso
  final _correoController = TextEditingController();
  final _telefonoController = TextEditingController();
  final _telAltController = TextEditingController();
  final _regUsuarioController = TextEditingController();
  final _regContrasenaController = TextEditingController();
  final _regConfirmContrasenaController = TextEditingController();
  bool _obscureRegPassword = true;
  bool _obscureRegConfirmPassword = true;
  String? _correoDisponibilidadError;
  String? _usuarioDisponibilidadError;
  bool _checkingCorreoDisponibilidad = false;

  // Step 6: Google Authenticator (TOTP 30s)
  Map<String, dynamic>? _totpSetupData;
  final _regTotpController = TextEditingController();
  Timer? _totpClockTimer;
  int _totpSecondsLeft = TotpUtils.secondsRemainingInWindow();
  String? _totpFeedback;

  bool _warmingCloud = false;
  bool _cloudReady = false;
  String? _inlineRegError;
  final Set<String> _blurredFields = <String>{};

  final _apiUrlController = TextEditingController(text: ApiClient.defaultBaseUrl);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      final auth = Provider.of<AuthProvider>(context, listen: false);
      if (!auth.biometricsInitialized) {
        await auth.initBiometrics();
      }
      if (mounted) {
        unawaited(_warmUpRenderIfNeeded());
      }
      if (mounted &&
          auth.storedBiometricProfile != null &&
          _usuarioController.text.isEmpty) {
        _usuarioController.text = auth.storedBiometricProfile!.usuario;
      }
    });
  }

  Future<void> _warmUpRenderIfNeeded() async {
    final currentUrl = ApiClient().baseUrl.toLowerCase();
    if (!currentUrl.contains('onrender.com')) {
      if (mounted && (_warmingCloud || _cloudReady)) {
        setState(() {
          _warmingCloud = false;
          _cloudReady = false;
        });
      }
      return;
    }
    if (mounted) {
      setState(() {
        _warmingCloud = true;
        _cloudReady = false;
      });
    }
    final ok = await ApiClient().warmUpServer();
    if (!mounted) return;
    setState(() {
      _warmingCloud = false;
      _cloudReady = ok;
    });
  }

  @override
  void dispose() {
    _totpClockTimer?.cancel();
    _usuarioController.dispose();
    _contrasenaController.dispose();
    _nombreController.dispose();
    _segundoNombreController.dispose();
    _apPaternoController.dispose();
    _apMaternoController.dispose();
    _fechaNacController.dispose();
    _curpController.dispose();
    _rfcController.dispose();
    _empresaController.dispose();
    _calleController.dispose();
    _numExtController.dispose();
    _numIntController.dispose();
    _cpController.dispose();
    _correoController.dispose();
    _telefonoController.dispose();
    _telAltController.dispose();
    _regUsuarioController.dispose();
    _regContrasenaController.dispose();
    _regConfirmContrasenaController.dispose();
    _regTotpController.dispose();
    _apiUrlController.dispose();
    super.dispose();
  }

  void _startTotpClockTimer({int periodSeconds = 30}) {
    _totpClockTimer?.cancel();
    _totpSecondsLeft = TotpUtils.secondsRemainingInWindow(periodSeconds: periodSeconds);
    _totpClockTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!mounted || _regStep != 6 || !_isRegisterMode) {
        timer.cancel();
        return;
      }
      setState(() {
        _totpSecondsLeft = TotpUtils.secondsRemainingInWindow(periodSeconds: periodSeconds);
      });
    });
  }

  void _goToRegStep(int step) {
    if (step != 6) {
      _totpClockTimer?.cancel();
    }
    setState(() {
      _inlineRegError = null;
      _regStep = step;
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final idx = (step - 1).clamp(0, _stepChipKeys.length - 1);
      final chipCtx = _stepChipKeys[idx].currentContext;
      if (chipCtx != null) {
        Scrollable.ensureVisible(
          chipCtx,
          duration: const Duration(milliseconds: 220),
          alignment: 0.4,
        );
      }
    });
  }

  String get _selectedEstadoNombre {
    if (_estadoDomicilioNombre.trim().isNotEmpty) {
      return _estadoDomicilioNombre.trim();
    }
    final est = MexicoCatalogs.obtenerEstadoPorNombreOCodigo(_estadoDomicilioCodigo);
    return est?.nombre ?? '';
  }

  Future<void> _onCodigoPostalChanged(String val) async {
    final clean = val.trim();
    if (clean.length < 5) {
      setState(() {
        _estadoDomicilioCodigo = '';
        _estadoDomicilioNombre = '';
        _municipioSeleccionado = '';
        _coloniasDelCp = <String>[];
        _coloniaSeleccionada = null;
        _loadingCpCatalogo = false;
        _cpErrorRemoto = null;
      });
      return;
    }

    final localRes = MexicoCatalogs.resolverCodigoPostalMexicano(clean);
    setState(() {
      _coloniaSeleccionada = null;
      _cpErrorRemoto = null;
      if (localRes.valido) {
        _estadoDomicilioCodigo = localRes.codigoRenapo ?? '';
        _estadoDomicilioNombre = localRes.estadoNombre ?? '';
        _municipioSeleccionado = localRes.municipioSugerido ?? '';
        _coloniasDelCp = List<String>.from(localRes.coloniasSugeridas);
        _loadingCpCatalogo = true;
      } else {
        _estadoDomicilioCodigo = '';
        _estadoDomicilioNombre = '';
        _municipioSeleccionado = '';
        _coloniasDelCp = <String>[];
        _loadingCpCatalogo = false;
      }
    });

    if (!localRes.valido) return;

    try {
      final remote = await CatalogService().consultarCodigoPostal(clean);
      if (!mounted || _cpController.text.trim() != clean) return;
      if (remote != null) {
        final valido = remote['valido'] != false;
        if (!valido) {
          setState(() {
            _loadingCpCatalogo = false;
            _estadoDomicilioCodigo = '';
            _estadoDomicilioNombre = '';
            _municipioSeleccionado = '';
            _coloniasDelCp = <String>[];
            _coloniaSeleccionada = null;
            _cpErrorRemoto = (remote['mensaje']?.toString().trim().isNotEmpty ?? false)
                ? remote['mensaje'].toString().trim()
                : 'El C.P. $clean no está registrado en el catálogo oficial SEPOMEX.';
          });
          return;
        }
        final estadoRemoto = remote['estado']?.toString().trim() ?? '';
        final codigoEstadoRemoto = remote['codigoEstado']?.toString().trim() ?? '';
        final municipioRemoto = remote['municipio']?.toString().trim() ?? '';
        final rawColonias = remote['colonias'];
        final coloniasRemotas = rawColonias is List
            ? rawColonias
                .map((e) => e.toString().trim())
                .where((e) => e.isNotEmpty)
                .toSet()
                .toList()
            : <String>[];
        setState(() {
          _loadingCpCatalogo = false;
          if (codigoEstadoRemoto.isNotEmpty) {
            _estadoDomicilioCodigo = codigoEstadoRemoto;
          }
          if (estadoRemoto.isNotEmpty) {
            _estadoDomicilioNombre = estadoRemoto;
          }
          if (municipioRemoto.isNotEmpty) {
            _municipioSeleccionado = municipioRemoto;
          }
          if (coloniasRemotas.isNotEmpty) {
            _coloniasDelCp = coloniasRemotas;
          }
          if (_coloniaSeleccionada != null &&
              !_coloniasDelCp.contains(_coloniaSeleccionada)) {
            _coloniaSeleccionada = null;
          }
        });
      } else {
        if (mounted) setState(() => _loadingCpCatalogo = false);
      }
    } catch (_) {
      if (mounted && _cpController.text.trim() == clean) {
        setState(() => _loadingCpCatalogo = false);
      }
    }
  }

  String _buildCalleCompleta() {
    final clean = _calleController.text.trim();
    final hasPrefix = RegExp(
      r'^(calle|av\.?|avenida|blvd\.?|boulevard|calz\.?|calzada|priv\.?|privada|cerr\.?|cerrada|prol\.?|cto\.?|circuito|andador|carr\.?|carretera)\b',
      caseSensitive: false,
    ).hasMatch(clean);
    return hasPrefix ? clean : '$_tipoVialidad $clean'.trim();
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

  void _autoGenerateFiscal() {
    setState(() {
      _inlineRegError = null;
      _curpDisponibilidadError = null;
      _rfcDisponibilidadError = null;
    });
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

  Future<bool> _checkFiscalDisponibilidad({String? curp, String? rfc}) async {
    final cleanCurp = curp?.trim().toUpperCase();
    final cleanRfc = rfc?.trim().toUpperCase();
    final checkCurp = (cleanCurp != null && ValidationUtils.esCurpValida(cleanCurp))
        ? cleanCurp
        : null;
    final checkRfc = (cleanRfc != null && ValidationUtils.esRfcValido(cleanRfc))
        ? cleanRfc
        : null;
    if (checkCurp == null && checkRfc == null) return true;

    final auth = Provider.of<AuthProvider>(context, listen: false);
    setState(() => _checkingFiscalDisponibilidad = true);
    final resp = await auth.verificarDisponibilidad(curp: checkCurp, rfc: checkRfc);
    if (!mounted) return false;
    setState(() => _checkingFiscalDisponibilidad = false);
    if (resp == null) return true;

    final mensajes = resp['mensajes'] is Map ? (resp['mensajes'] as Map) : const {};
    String? curpErr;
    String? rfcErr;
    if (checkCurp != null && resp['curpDisponible'] == false) {
      final m = mensajes['curp']?.toString().trim();
      curpErr = (m != null && m.isNotEmpty)
          ? m
          : 'Esta CURP ya está registrada en otra cuenta.';
    }
    if (checkRfc != null && resp['rfcDisponible'] == false) {
      final m = mensajes['rfc']?.toString().trim();
      rfcErr = (m != null && m.isNotEmpty)
          ? m
          : 'Este RFC ya está registrado en otra cuenta.';
    }
    if (curpErr != null || rfcErr != null) {
      setState(() {
        if (checkCurp != null) _curpDisponibilidadError = curpErr;
        if (checkRfc != null) _rfcDisponibilidadError = rfcErr;
      });
      return false;
    }
    return true;
  }

  Future<bool> _checkContactoDisponibilidad({String? correo, String? usuario}) async {
    final cleanMail = correo?.trim();
    final cleanUser = usuario?.trim();
    final checkMail = (cleanMail != null &&
            ValidationUtils.esCorreoValido(cleanMail) &&
            !ValidationUtils.esCorreoPlaceholder(cleanMail))
        ? cleanMail
        : null;
    final checkUser = (cleanUser != null && ValidationUtils.esUsuarioValido(cleanUser))
        ? cleanUser
        : null;
    if (checkMail == null && checkUser == null) return true;

    final auth = Provider.of<AuthProvider>(context, listen: false);
    if (checkMail != null) {
      setState(() => _checkingCorreoDisponibilidad = true);
    }
    final resp = await auth.verificarDisponibilidad(
      correo: checkMail,
      usuario: checkUser,
    );
    if (!mounted) return false;
    if (checkMail != null) {
      setState(() => _checkingCorreoDisponibilidad = false);
    }
    if (resp == null) return true;

    final mensajes = resp['mensajes'] is Map ? (resp['mensajes'] as Map) : const {};
    String? mailErr;
    String? userErr;
    if (checkMail != null && resp['correoDisponible'] == false) {
      final m = mensajes['correo']?.toString().trim();
      mailErr = (m != null && m.isNotEmpty) ? m : _mensajeCorreoDuplicado;
    }
    if (checkUser != null && resp['usuarioDisponible'] == false) {
      final m = mensajes['usuario']?.toString().trim();
      userErr = (m != null && m.isNotEmpty)
          ? m
          : 'Este nombre de usuario ya está ocupado. Elige otro.';
    }

    if (mailErr != null || userErr != null) {
      setState(() {
        if (checkMail != null) _correoDisponibilidadError = mailErr;
        if (checkUser != null) _usuarioDisponibilidadError = userErr;
      });
      return false;
    }
    return true;
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
        ValidationUtils.diagnosticarApellido(_apPaternoController.text, 'El apellido paterno');
    if (pErr != null) return pErr;
    final mErr =
        ValidationUtils.diagnosticarApellido(_apMaternoController.text, 'El apellido materno');
    if (mErr != null) return mErr;
    final fErr = ValidationUtils.diagnosticarFechaNacimiento(_fechaNacController.text);
    if (fErr != null) return fErr;
    return null;
  }

  String? _validateStep2() {
    final curpErr = ValidationUtils.diagnosticarCurp(_curpController.text);
    if (curpErr != null) return curpErr;
    if (_curpDisponibilidadError != null) return _curpDisponibilidadError;
    final rfcErr = ValidationUtils.diagnosticarRfc(_rfcController.text);
    if (rfcErr != null) return rfcErr;
    if (_rfcDisponibilidadError != null) return _rfcDisponibilidadError;
    return null;
  }

  String? _validateStep3() {
    if (_ocupacionCatalogo.trim().isEmpty ||
        !MexicoCatalogs.catalogoOcupaciones.contains(_ocupacionCatalogo)) {
      return 'Selecciona tu ocupación del catálogo oficial.';
    }
    if (_profesionTabulador.trim().isEmpty) {
      return 'Selecciona tu profesión del tabulador.';
    }
    final empErr = ValidationUtils.diagnosticarEmpresa(_empresaController.text);
    if (empErr != null) return empErr;
    if (_ingresoSeleccionado <= 0) {
      return 'Selecciona tu rango de sueldo mensual del tabulador.';
    }
    return null;
  }

  String? _validateStep4() {
    final cpErr = ValidationUtils.diagnosticarCodigoPostal(_cpController.text);
    if (cpErr != null) return cpErr;
    if (_cpErrorRemoto != null && _cpErrorRemoto!.isNotEmpty) {
      return _cpErrorRemoto;
    }
    if (_selectedEstadoNombre.trim().isEmpty || _municipioSeleccionado.trim().length < 2) {
      return 'Ingresa un Código Postal válido de 5 dígitos para autocompletar Estado y Municipio.';
    }
    if (_coloniaSeleccionada == null || _coloniaSeleccionada!.trim().isEmpty) {
      return 'Selecciona una colonia perteneciente a tu código postal.';
    }
    if (!_coloniasDelCp.contains(_coloniaSeleccionada)) {
      return 'Selecciona una colonia válida del catálogo de tu código postal.';
    }
    final calleErr = ValidationUtils.diagnosticarCalle(_calleController.text);
    if (calleErr != null) return calleErr;
    final extErr = ValidationUtils.diagnosticarNumeroDomicilio(_numExtController.text);
    if (extErr != null) return extErr;
    final intErr = ValidationUtils.diagnosticarNumeroDomicilio(
      _numIntController.text,
      optional: true,
      campo: 'El número interior',
    );
    if (intErr != null) return intErr;
    return null;
  }

  String? _validateStep5() {
    final mail = _correoController.text.trim();
    if (!ValidationUtils.esCorreoValido(mail)) return 'Ingresa un correo electrónico válido.';
    if (ValidationUtils.esCorreoPlaceholder(mail)) {
      return 'Usa un correo electrónico real (Gmail, Outlook, etc.).';
    }
    if (_correoDisponibilidadError != null) return _correoDisponibilidadError;
    final telErr = ValidationUtils.diagnosticarTelefono(_telefonoController.text);
    if (telErr != null) return telErr;
    if (_telAltController.text.trim().isNotEmpty) {
      final altErr = ValidationUtils.diagnosticarTelefono(_telAltController.text);
      if (altErr != null) return 'Teléfono alternativo: $altErr';
    }
    final usrErr = ValidationUtils.diagnosticarUsuario(_regUsuarioController.text);
    if (usrErr != null) return usrErr;
    if (_usuarioDisponibilidadError != null) return _usuarioDisponibilidadError;
    if (!ValidationUtils.esContrasenaSegura(_regContrasenaController.text)) {
      return 'La contraseña requiere mínimo 8 caracteres, mayúscula, minúscula y número.';
    }
    if (_regContrasenaController.text != _regConfirmContrasenaController.text) {
      return 'Las contraseñas no coinciden.';
    }
    return null;
  }

  void _advanceFromStep1() {
    _regFormKey.currentState?.validate();
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
    _goToRegStep(2);
  }

  Future<void> _advanceFromStep2() async {
    _regFormKey.currentState?.validate();
    final err = _validateStep2();
    if (err != null) {
      setState(() => _inlineRegError = err);
      return;
    }
    final available = await _checkFiscalDisponibilidad(
      curp: _curpController.text,
      rfc: _rfcController.text,
    );
    if (!mounted) return;
    if (!available) {
      _regFormKey.currentState?.validate();
      setState(() {
        _inlineRegError = _curpDisponibilidadError ?? _rfcDisponibilidadError;
      });
      return;
    }
    _goToRegStep(3);
  }

  void _advanceFromStep3() {
    _regFormKey.currentState?.validate();
    final err = _validateStep3();
    if (err != null) {
      setState(() => _inlineRegError = err);
      return;
    }
    _goToRegStep(4);
  }

  void _advanceFromStep4() {
    _regFormKey.currentState?.validate();
    final err = _validateStep4();
    if (err != null) {
      setState(() => _inlineRegError = err);
      return;
    }
    _goToRegStep(5);
  }

  Future<void> _advanceToTotpStep({bool isRegenerate = false}) async {
    setState(() {
      _inlineRegError = null;
      _totpFeedback = null;
    });

    final mail = _correoController.text.trim();
    final usr = _regUsuarioController.text.trim();

    if (!isRegenerate) {
      // Check email/user availability immediately if syntactically valid so duplicate emails
      // always surface right under the email field even before checking other steps.
      if (ValidationUtils.esCorreoValido(mail) && !ValidationUtils.esCorreoPlaceholder(mail)) {
        final okDisp = await _checkContactoDisponibilidad(correo: mail, usuario: usr);
        if (!mounted) return;
        if (!okDisp) {
          _regFormKey.currentState?.validate();
          setState(() {
            _inlineRegError = _correoDisponibilidadError ?? _usuarioDisponibilidadError;
          });
          return;
        }
      }

      _regFormKey.currentState?.validate();
      final e1 = _validateStep1();
      if (e1 != null) {
        _goToRegStep(1);
        setState(() => _inlineRegError = e1);
        return;
      }
      final e2 = _validateStep2();
      if (e2 != null) {
        _goToRegStep(2);
        setState(() => _inlineRegError = e2);
        return;
      }
      final e3 = _validateStep3();
      if (e3 != null) {
        _goToRegStep(3);
        setState(() => _inlineRegError = e3);
        return;
      }
      final e4 = _validateStep4();
      if (e4 != null) {
        _goToRegStep(4);
        setState(() => _inlineRegError = e4);
        return;
      }
      final e5 = _validateStep5();
      if (e5 != null) {
        setState(() => _inlineRegError = e5);
        return;
      }
    }

    final auth = Provider.of<AuthProvider>(context, listen: false);
    final totpResp = await auth.iniciarTotpRegistro(
      correo: mail,
      usuario: usr,
    );
    if (!mounted) return;
    if (totpResp != null) {
      final period = (totpResp['periodoSegundos'] is int)
          ? (totpResp['periodoSegundos'] as int)
          : 30;
      setState(() {
        _totpSetupData = totpResp;
        _regTotpController.clear();
        _totpFeedback = isRegenerate
            ? 'Se generó una nueva clave secreta y código QR para Google Authenticator.'
            : null;
      });
      _goToRegStep(6);
      _startTotpClockTimer(periodSeconds: period);
    } else {
      final errMsg = auth.errorMessage ?? 'No fue posible iniciar Google Authenticator.';
      final lower = errMsg.toLowerCase();
      if (lower.contains('correo') &&
          (lower.contains('uso') || lower.contains('registrad') || lower.contains('existe'))) {
        auth.clearError();
        setState(() {
          _correoDisponibilidadError = errMsg;
          _inlineRegError = errMsg;
        });
      } else {
        _showError(errMsg);
      }
    }
  }

  Future<void> _submitRegister() async {
    final code = _regTotpController.text.trim();
    if (!RegExp(r'^\d{6}$').hasMatch(code)) {
      _showError('Ingresa el código dinámico de 6 dígitos de Google Authenticator.');
      return;
    }
    final secret = (_totpSetupData?['secret'] ?? '').toString().trim();
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
      ocupacion: _ocupacionCatalogo.trim(),
      profesion: _profesionTabulador.trim(),
      empresa: _empresaController.text.trim(),
      ingresoMensual: _ingresoSeleccionado,
      calle: _buildCalleCompleta(),
      numeroExterior: _numExtController.text.trim(),
      numeroInterior: _numIntController.text.trim().isEmpty
          ? null
          : _numIntController.text.trim(),
      colonia: (_coloniaSeleccionada ?? '').trim(),
      codigoPostal: _cpController.text.trim(),
      municipio: _municipioSeleccionado.trim(),
      estado: _selectedEstadoNombre,
      pais: 'México',
      correo: _correoController.text.trim(),
      telefonoMovil: _telefonoController.text.trim(),
      telefonoAlternativo: _telAltController.text.trim().isEmpty
          ? null
          : _telAltController.text.trim(),
      usuario: _regUsuarioController.text.trim(),
      contrasena: _regContrasenaController.text,
      totpSecret: secret,
      codigoTotp: code,
    );
    if (!success && mounted) {
      _showError(auth.errorMessage ?? 'No fue posible registrar la cuenta');
    } else if (success) {
      _totpClockTimer?.cancel();
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
                        _apiUrlController.text = 'http://192.168.1.77:10000';
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
                unawaited(_warmUpRenderIfNeeded());
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
                        Flexible(
                          child: Container(
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
                                Flexible(
                                  child: Text(
                                    'Banca Privada · GestoPago',
                                    overflow: TextOverflow.ellipsis,
                                    style: TextStyle(
                                      fontSize: 11,
                                      fontWeight: FontWeight.w700,
                                      color: AppTheme.textPrimary(context),
                                    ),
                                  ),
                                ),
                              ],
                            ),
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

                    if (_warmingCloud || _cloudReady) ...[
                      const SizedBox(height: 6),
                      Center(
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                          decoration: BoxDecoration(
                            color: (_cloudReady ? AppTheme.accentEmerald : AppTheme.secondaryBlue)
                                .withValues(alpha: 0.1),
                            borderRadius: BorderRadius.circular(12),
                            border: Border.all(
                              color: (_cloudReady ? AppTheme.accentEmerald : AppTheme.secondaryBlue)
                                  .withValues(alpha: 0.35),
                            ),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              if (_warmingCloud)
                                const SizedBox(
                                  width: 12,
                                  height: 12,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 1.8,
                                    color: AppTheme.secondaryBlue,
                                  ),
                                )
                              else
                                const Icon(
                                  Icons.cloud_done_rounded,
                                  size: 14,
                                  color: AppTheme.accentEmerald,
                                ),
                              const SizedBox(width: 6),
                              Flexible(
                                child: Text(
                                  _warmingCloud
                                      ? 'Despertando servidor en la nube (Render)...'
                                      : 'Nube (Render) activa y lista',
                                  style: TextStyle(
                                    fontSize: 11,
                                    fontWeight: FontWeight.w600,
                                    color: _cloudReady
                                        ? AppTheme.accentEmerald
                                        : AppTheme.secondaryBlue,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],

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
                        _totpClockTimer?.cancel();
                        setState(() {
                          _isRegisterMode = sel.first;
                          _regStep = 1;
                          _inlineRegError = null;
                          _regTotpController.clear();
                          _totpFeedback = null;
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
                                  'Expediente KYC Oficial · Google Authenticator · GestoPago',
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
                            'Validación normativa de CURP y RFC, SEPOMEX, código dinámico TOTP cada 30s, biometría y recibos con leyenda oficial.',
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

  Widget _buildRegisterStepper(Color accent, String currentStepTitle) {
    const chipLabels = [
      '1. Datos Personales',
      '2. Datos Fiscales',
      '3. Ocupación',
      '4. Domicilio',
      '5. Contacto',
      '6. Autenticador',
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 3),
              decoration: BoxDecoration(
                color: accent.withValues(alpha: 0.14),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: accent.withValues(alpha: 0.35)),
              ),
              child: Text(
                'Paso $_regStep de 6',
                style: TextStyle(
                  fontSize: 10.5,
                  fontWeight: FontWeight.w900,
                  color: accent,
                ),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                'Paso $_regStep de 6 · $currentStepTitle',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  color: AppTheme.textSecondary(context),
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        ClipRRect(
          borderRadius: BorderRadius.circular(6),
          child: LinearProgressIndicator(
            value: _regStep / 6.0,
            minHeight: 5,
            backgroundColor: AppTheme.borderCol(context),
            color: _regStep == 6 ? AppTheme.accentEmerald : accent,
          ),
        ),
        const SizedBox(height: 10),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: List.generate(6, (idx) {
              final stepNum = idx + 1;
              final isActive = _regStep == stepNum;
              final isDone = _regStep > stepNum;
              return Padding(
                key: _stepChipKeys[idx],
                padding: EdgeInsets.only(right: idx < 5 ? 6 : 0),
                child: InkWell(
                  borderRadius: BorderRadius.circular(10),
                  onTap: isDone ? () => _goToRegStep(stepNum) : null,
                  child: Container(
                    padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 10),
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
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        if (isDone) ...[
                          const Icon(
                            Icons.check_circle_rounded,
                            size: 13,
                            color: AppTheme.accentEmerald,
                          ),
                          const SizedBox(width: 4),
                        ],
                        Text(
                          chipLabels[idx],
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
                      ],
                    ),
                  ),
                ),
              );
            }),
          ),
        ),
      ],
    );
  }

  Widget _wrapBlurValidation({
    required String fieldId,
    VoidCallback? onBlurExtra,
    required Widget Function(AutovalidateMode mode) builder,
  }) {
    final mode = _blurredFields.contains(fieldId)
        ? AutovalidateMode.always
        : AutovalidateMode.onUserInteraction;
    return Focus(
      canRequestFocus: false,
      onFocusChange: (hasFocus) {
        if (!hasFocus) {
          if (!_blurredFields.contains(fieldId)) {
            setState(() => _blurredFields.add(fieldId));
          }
          onBlurExtra?.call();
        }
      },
      child: builder(mode),
    );
  }

  Widget _buildRegisterForm(AuthProvider auth) {
    final pwd = _regContrasenaController.text;
    final accent = AppTheme.brandAccent(context);
    final cpVerificado = _cpController.text.trim().length == 5 &&
        _cpErrorRemoto == null &&
        _selectedEstadoNombre.isNotEmpty &&
        _municipioSeleccionado.isNotEmpty;

    const shortTitles = [
      'Datos Personales',
      'Datos Fiscales',
      'Ocupación e Ingresos',
      'Domicilio SEPOMEX',
      'Contacto y Acceso',
      'Autenticador de Google (TOTP 30s)',
    ];
    const titles = [
      'Datos Personales del Titular',
      'Datos Fiscales (RENAPO / SAT)',
      'Ocupación e Ingresos Mensuales',
      'Domicilio en México (SEPOMEX)',
      'Contacto Verificado y Acceso',
      'Autenticador de Google (TOTP 30s)',
    ];
    const subtitles = [
      'Ingresa tu nombre (2 a 60 letras) y apellidos (mín. 3 letras, mayor de 18 años).',
      'Verifica o calcula tu CURP (18 caracteres) y RFC con homoclave (13 caracteres).',
      'Selecciona tu ocupación del catálogo oficial, profesión, empresa y rango salarial.',
      'Ingresa tu Código Postal de 5 dígitos para autocompletar Estado, Municipio y colonias.',
      'Configura tu correo electrónico único, teléfono móvil y credenciales seguras.',
      'Escanea el QR o copia la clave en Google Authenticator e ingresa el código de 6 dígitos.',
    ];

    return Form(
      key: _regFormKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _buildRegisterStepper(accent, shortTitles[_regStep - 1]),
          const SizedBox(height: 12),
          Text(
            titles[_regStep - 1],
            style: TextStyle(
              fontSize: 16.5,
              fontWeight: FontWeight.bold,
              color: AppTheme.textPrimary(context),
            ),
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

          // --- PASO 1: DATOS PERSONALES ---
          if (_regStep == 1) ...[
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: _wrapBlurValidation(
                    fieldId: 'nombre',
                    builder: (mode) => TextFormField(
                      controller: _nombreController,
                      autovalidateMode: mode,
                      maxLength: 60,
                      textCapitalization: TextCapitalization.words,
                      validator: (v) => ValidationUtils.diagnosticarNombre(v, 'El nombre'),
                      onChanged: (_) {
                        if (_inlineRegError != null) setState(() => _inlineRegError = null);
                      },
                      decoration: const InputDecoration(
                        labelText: 'Nombre *',
                        counterText: '',
                        errorMaxLines: 2,
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _wrapBlurValidation(
                    fieldId: 'segundoNombre',
                    builder: (mode) => TextFormField(
                      controller: _segundoNombreController,
                      autovalidateMode: mode,
                      maxLength: 60,
                      textCapitalization: TextCapitalization.words,
                      validator: (v) => ValidationUtils.diagnosticarNombre(
                        v,
                        'El segundo nombre',
                        optional: true,
                      ),
                      onChanged: (_) {
                        if (_inlineRegError != null) setState(() => _inlineRegError = null);
                      },
                      decoration: const InputDecoration(
                        labelText: 'Segundo nombre',
                        counterText: '',
                        errorMaxLines: 2,
                      ),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: _wrapBlurValidation(
                    fieldId: 'apPaterno',
                    builder: (mode) => TextFormField(
                      controller: _apPaternoController,
                      autovalidateMode: mode,
                      maxLength: 40,
                      textCapitalization: TextCapitalization.words,
                      validator: (v) =>
                          ValidationUtils.diagnosticarApellido(v, 'El apellido paterno'),
                      onChanged: (_) {
                        if (_inlineRegError != null) setState(() => _inlineRegError = null);
                      },
                      decoration: const InputDecoration(
                        labelText: 'Apellido paterno *',
                        counterText: '',
                        errorMaxLines: 2,
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _wrapBlurValidation(
                    fieldId: 'apMaterno',
                    builder: (mode) => TextFormField(
                      controller: _apMaternoController,
                      autovalidateMode: mode,
                      maxLength: 40,
                      textCapitalization: TextCapitalization.words,
                      validator: (v) =>
                          ValidationUtils.diagnosticarApellido(v, 'El apellido materno'),
                      onChanged: (_) {
                        if (_inlineRegError != null) setState(() => _inlineRegError = null);
                      },
                      decoration: const InputDecoration(
                        labelText: 'Apellido materno *',
                        counterText: '',
                        errorMaxLines: 2,
                      ),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            _wrapBlurValidation(
              fieldId: 'fechaNac',
              builder: (mode) => TextFormField(
                controller: _fechaNacController,
                autovalidateMode: mode,
                keyboardType: TextInputType.datetime,
                validator: (v) => ValidationUtils.diagnosticarFechaNacimiento(v),
                decoration: InputDecoration(
                  labelText: 'Fecha de nacimiento (YYYY-MM-DD) *',
                  prefixIcon: const Icon(Icons.cake_outlined),
                  errorMaxLines: 2,
                  suffixIcon: IconButton(
                    icon: const Icon(Icons.calendar_month_rounded),
                    tooltip: 'Elegir fecha',
                    onPressed: _pickBirthDate,
                  ),
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

          // --- PASO 2: DATOS FISCALES ---
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
              decoration: const InputDecoration(labelText: 'Entidad de Nacimiento (CURP) *'),
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
            _wrapBlurValidation(
              fieldId: 'curp',
              onBlurExtra: () {
                if (ValidationUtils.esCurpValida(_curpController.text)) {
                  unawaited(_checkFiscalDisponibilidad(curp: _curpController.text));
                }
              },
              builder: (mode) => TextFormField(
                controller: _curpController,
                autovalidateMode:
                    _curpDisponibilidadError != null ? AutovalidateMode.always : mode,
                maxLength: 18,
                textCapitalization: TextCapitalization.characters,
                validator: (v) =>
                    ValidationUtils.diagnosticarCurp(v) ?? _curpDisponibilidadError,
                onChanged: (val) {
                  final upper = val.toUpperCase().replaceAll(RegExp(r'[^A-Z0-9]'), '');
                  if (upper != val) {
                    _curpController.value = TextEditingValue(
                      text: upper,
                      selection: TextSelection.collapsed(offset: upper.length),
                    );
                  }
                  setState(() {
                    _inlineRegError = null;
                    _curpDisponibilidadError = null;
                  });
                },
                decoration: InputDecoration(
                  labelText: 'CURP (18 caracteres) *',
                  counterText: '${_curpController.text.length}/18',
                  errorMaxLines: 2,
                  prefixIcon: const Icon(Icons.badge_outlined),
                  suffixIcon: (_curpDisponibilidadError == null &&
                          ValidationUtils.esCurpValida(_curpController.text))
                      ? const Icon(
                          Icons.check_circle_rounded,
                          color: AppTheme.accentEmerald,
                          size: 19,
                        )
                      : null,
                ),
              ),
            ),
            const SizedBox(height: 10),
            _wrapBlurValidation(
              fieldId: 'rfc',
              onBlurExtra: () {
                if (ValidationUtils.esRfcValido(_rfcController.text)) {
                  unawaited(_checkFiscalDisponibilidad(rfc: _rfcController.text));
                }
              },
              builder: (mode) => TextFormField(
                controller: _rfcController,
                autovalidateMode:
                    _rfcDisponibilidadError != null ? AutovalidateMode.always : mode,
                maxLength: 13,
                textCapitalization: TextCapitalization.characters,
                validator: (v) =>
                    ValidationUtils.diagnosticarRfc(v) ?? _rfcDisponibilidadError,
                onChanged: (val) {
                  final upper = val.toUpperCase().replaceAll(RegExp(r'[^A-ZÑ&0-9]'), '');
                  if (upper != val) {
                    _rfcController.value = TextEditingValue(
                      text: upper,
                      selection: TextSelection.collapsed(offset: upper.length),
                    );
                  }
                  setState(() {
                    _inlineRegError = null;
                    _rfcDisponibilidadError = null;
                  });
                },
                decoration: InputDecoration(
                  labelText: 'RFC con Homoclave (13) *',
                  counterText: '${_rfcController.text.length}/13',
                  errorMaxLines: 2,
                  prefixIcon: const Icon(Icons.assignment_ind_outlined),
                  suffixIcon: (_rfcDisponibilidadError == null &&
                          ValidationUtils.esRfcValido(_rfcController.text))
                      ? const Icon(
                          Icons.check_circle_rounded,
                          color: AppTheme.accentEmerald,
                          size: 19,
                        )
                      : null,
                ),
              ),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                OutlinedButton.icon(
                  onPressed: _checkingFiscalDisponibilidad ? null : () => _goToRegStep(1),
                  icon: const Icon(Icons.arrow_back_rounded, size: 16),
                  label: const Text('Atrás'),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _checkingFiscalDisponibilidad ? null : _advanceFromStep2,
                    icon: _checkingFiscalDisponibilidad
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                          )
                        : const Icon(Icons.arrow_forward_rounded, size: 18),
                    label: Text(
                      _checkingFiscalDisponibilidad
                          ? 'Verificando...'
                          : 'Continuar a Ocupación e Ingresos',
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ),
              ],
            ),
          ],

          // --- PASO 3: OCUPACIÓN E INGRESOS ---
          if (_regStep == 3) ...[
            DropdownButtonFormField<String>(
              key: ValueKey('ocup_cat_$_ocupacionCatalogo'),
              initialValue: _ocupacionCatalogo,
              isExpanded: true,
              decoration: const InputDecoration(
                labelText: 'Ocupación (Catálogo) *',
                prefixIcon: Icon(Icons.badge_rounded),
              ),
              items: MexicoCatalogs.catalogoOcupaciones
                  .map(
                    (oc) => DropdownMenuItem<String>(
                      value: oc,
                      child: Text(oc, overflow: TextOverflow.ellipsis),
                    ),
                  )
                  .toList(),
              onChanged: (v) {
                if (v == null) return;
                setState(() {
                  _ocupacionCatalogo = v;
                  _inlineRegError = null;
                  final currentEmp = _empresaController.text.trim().toUpperCase();
                  if (currentEmp.isEmpty) {
                    if (v == 'Estudiante') {
                      _empresaController.text = 'INSTITUCIÓN EDUCATIVA';
                    } else if (v == 'Labores del hogar') {
                      _empresaController.text = 'HOGAR';
                    } else if (v == 'Jubilado(a) / Pensionado(a)') {
                      _empresaController.text = 'PENSIONADO';
                    } else if (v == 'Profesionista independiente') {
                      _empresaController.text = 'INDEPENDIENTE';
                    }
                  }
                });
              },
            ),
            const SizedBox(height: 10),
            DropdownButtonFormField<String>(
              key: ValueKey('prof_$_profesionTabulador'),
              initialValue: _profesionTabulador,
              isExpanded: true,
              decoration: const InputDecoration(
                labelText: 'Profesión (Tabulador) *',
                prefixIcon: Icon(Icons.work_outline_rounded),
              ),
              items: MexicoCatalogs.tabuladorProfesiones
                  .map(
                    (p) => DropdownMenuItem<String>(
                      value: p.label,
                      child: Text(p.label, overflow: TextOverflow.ellipsis),
                    ),
                  )
                  .toList(),
              onChanged: (v) {
                if (v == null) return;
                setState(() {
                  _profesionTabulador = v;
                  _inlineRegError = null;
                  final prof = MexicoCatalogs.tabuladorProfesiones.firstWhere(
                    (p) => p.label == v,
                    orElse: () => MexicoCatalogs.tabuladorProfesiones.first,
                  );
                  final currentEmp = _empresaController.text.trim().toUpperCase();
                  if (prof.empresaSugerida != null &&
                      (currentEmp.isEmpty ||
                          const {
                            'INDEPENDIENTE',
                            'INSTITUCIÓN EDUCATIVA',
                            'PENSIONADO',
                            'HOGAR',
                          }.contains(currentEmp))) {
                    _empresaController.text = prof.empresaSugerida!;
                  }
                });
              },
            ),
            const SizedBox(height: 10),
            _wrapBlurValidation(
              fieldId: 'empresa',
              builder: (mode) => TextFormField(
                controller: _empresaController,
                autovalidateMode: mode,
                maxLength: 80,
                validator: ValidationUtils.diagnosticarEmpresa,
                onChanged: (_) {
                  if (_inlineRegError != null) setState(() => _inlineRegError = null);
                },
                decoration: const InputDecoration(
                  labelText: 'Empresa / Institución *',
                  counterText: '',
                  errorMaxLines: 2,
                  prefixIcon: Icon(Icons.business_rounded),
                ),
              ),
            ),
            const SizedBox(height: 10),
            DropdownButtonFormField<double>(
              key: ValueKey('sueldo_$_ingresoSeleccionado'),
              initialValue: _ingresoSeleccionado,
              isExpanded: true,
              decoration: const InputDecoration(
                labelText: 'Tabulador de Sueldo Mensual *',
                prefixIcon: Icon(Icons.payments_outlined),
              ),
              items: MexicoCatalogs.tabuladorSueldos
                  .map(
                    (s) => DropdownMenuItem<double>(
                      value: s.montoRepresentativo,
                      child: Text(s.label, overflow: TextOverflow.ellipsis),
                    ),
                  )
                  .toList(),
              onChanged: (v) {
                if (v != null) {
                  setState(() {
                    _ingresoSeleccionado = v;
                    _inlineRegError = null;
                  });
                }
              },
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                OutlinedButton.icon(
                  onPressed: () => _goToRegStep(2),
                  icon: const Icon(Icons.arrow_back_rounded, size: 16),
                  label: const Text('Atrás'),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _advanceFromStep3,
                    icon: const Icon(Icons.arrow_forward_rounded, size: 18),
                    label: const Text('Continuar a Domicilio'),
                  ),
                ),
              ],
            ),
          ],

          // --- PASO 4: DOMICILIO SEPOMEX ---
          if (_regStep == 4) ...[
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  flex: 5,
                  child: _wrapBlurValidation(
                    fieldId: 'cp',
                    builder: (mode) => TextFormField(
                      controller: _cpController,
                      autovalidateMode: mode,
                      keyboardType: TextInputType.number,
                      maxLength: 5,
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                      validator: (v) =>
                          ValidationUtils.diagnosticarCodigoPostal(v) ?? _cpErrorRemoto,
                      onChanged: (val) {
                        _inlineRegError = null;
                        _onCodigoPostalChanged(val);
                      },
                      decoration: InputDecoration(
                        labelText: 'Código Postal (5 díg) *',
                        counterText: '',
                        errorMaxLines: 3,
                        suffixIcon: _loadingCpCatalogo
                            ? const Padding(
                                padding: EdgeInsets.all(12),
                                child: SizedBox(
                                  width: 14,
                                  height: 14,
                                  child: CircularProgressIndicator(strokeWidth: 2),
                                ),
                              )
                            : cpVerificado
                                ? const Icon(
                                    Icons.verified_rounded,
                                    color: AppTheme.accentEmerald,
                                    size: 18,
                                  )
                                : null,
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  flex: 6,
                  child: TextFormField(
                    key: ValueKey('est_dom_auto_$_selectedEstadoNombre'),
                    initialValue: _selectedEstadoNombre,
                    readOnly: true,
                    decoration: const InputDecoration(
                      labelText: 'Estado (Automático) *',
                      hintText: 'Se llena con C.P.',
                    ),
                  ),
                ),
              ],
            ),
            if (cpVerificado) ...[
              const SizedBox(height: 6),
              Text(
                'SEPOMEX verificado: $_selectedEstadoNombre · $_municipioSeleccionado (${_coloniasDelCp.length} ${_coloniasDelCp.length == 1 ? "colonia" : "colonias"})',
                style: const TextStyle(
                  fontSize: 11.5,
                  fontWeight: FontWeight.w600,
                  color: AppTheme.accentEmerald,
                ),
              ),
            ],
            const SizedBox(height: 10),
            TextFormField(
              key: ValueKey('mun_auto_$_municipioSeleccionado'),
              initialValue: _municipioSeleccionado,
              readOnly: true,
              decoration: const InputDecoration(
                labelText: 'Municipio / Alcaldía (Automático) *',
                hintText: 'Se autocompleta con tu Código Postal',
                prefixIcon: Icon(Icons.location_city_rounded),
              ),
            ),
            const SizedBox(height: 10),
            DropdownButtonFormField<String>(
              key: ValueKey(
                'col_cp_${_cpController.text}_${_coloniasDelCp.join("|")}_$_coloniaSeleccionada',
              ),
              initialValue: (_coloniaSeleccionada != null &&
                      _coloniasDelCp.contains(_coloniaSeleccionada))
                  ? _coloniaSeleccionada
                  : null,
              isExpanded: true,
              hint: Text(
                _coloniasDelCp.isEmpty
                    ? 'Ingresa primero tu C.P. de 5 dígitos'
                    : _loadingCpCatalogo
                        ? 'Consultando colonias SEPOMEX...'
                        : 'Selecciona tu colonia (${_coloniasDelCp.length} opciones)',
                overflow: TextOverflow.ellipsis,
              ),
              validator: (v) => (v == null || v.trim().isEmpty)
                  ? 'Selecciona una colonia perteneciente a tu código postal.'
                  : null,
              decoration: const InputDecoration(
                labelText: 'Colonia (Catálogo del C.P.) *',
                prefixIcon: Icon(Icons.map_outlined),
                errorMaxLines: 2,
              ),
              items: _coloniasDelCp
                  .map(
                    (col) => DropdownMenuItem<String>(
                      value: col,
                      child: Text(col, overflow: TextOverflow.ellipsis),
                    ),
                  )
                  .toList(),
              onChanged: _coloniasDelCp.isEmpty
                  ? null
                  : (v) {
                      setState(() {
                        _coloniaSeleccionada = v;
                        _inlineRegError = null;
                      });
                    },
            ),
            const SizedBox(height: 10),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  flex: 4,
                  child: DropdownButtonFormField<String>(
                    key: ValueKey('vial_$_tipoVialidad'),
                    initialValue: _tipoVialidad,
                    isExpanded: true,
                    decoration: const InputDecoration(labelText: 'Vialidad *'),
                    items: MexicoCatalogs.tiposVialidad
                        .map(
                          (tv) => DropdownMenuItem<String>(
                            value: tv.key,
                            child: Text(tv.key, overflow: TextOverflow.ellipsis),
                          ),
                        )
                        .toList(),
                    onChanged: (v) {
                      if (v != null) setState(() => _tipoVialidad = v);
                    },
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  flex: 7,
                  child: _wrapBlurValidation(
                    fieldId: 'calle',
                    builder: (mode) => TextFormField(
                      controller: _calleController,
                      autovalidateMode: mode,
                      maxLength: 80,
                      validator: ValidationUtils.diagnosticarCalle,
                      onChanged: (_) {
                        if (_inlineRegError != null) setState(() => _inlineRegError = null);
                      },
                      decoration: const InputDecoration(
                        labelText: 'Calle y Avenida *',
                        counterText: '',
                        errorMaxLines: 2,
                      ),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: _wrapBlurValidation(
                    fieldId: 'numExt',
                    builder: (mode) => TextFormField(
                      controller: _numExtController,
                      autovalidateMode: mode,
                      maxLength: 10,
                      validator: (v) => ValidationUtils.diagnosticarNumeroDomicilio(v),
                      onChanged: (_) {
                        if (_inlineRegError != null) setState(() => _inlineRegError = null);
                      },
                      decoration: InputDecoration(
                        labelText: 'Núm. Ext. *',
                        counterText: '',
                        errorMaxLines: 2,
                        suffixIcon: TextButton(
                          style: TextButton.styleFrom(
                            padding: const EdgeInsets.symmetric(horizontal: 6),
                            minimumSize: const Size(36, 30),
                            tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                          ),
                          onPressed: () {
                            setState(() {
                              _numExtController.text = 'S/N';
                              _inlineRegError = null;
                            });
                          },
                          child: const Text('S/N', style: TextStyle(fontSize: 11)),
                        ),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _wrapBlurValidation(
                    fieldId: 'numInt',
                    builder: (mode) => TextFormField(
                      controller: _numIntController,
                      autovalidateMode: mode,
                      maxLength: 10,
                      validator: (v) => ValidationUtils.diagnosticarNumeroDomicilio(
                        v,
                        optional: true,
                        campo: 'El número interior',
                      ),
                      decoration: const InputDecoration(
                        labelText: 'Núm. Int.',
                        counterText: '',
                        errorMaxLines: 2,
                      ),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                OutlinedButton.icon(
                  onPressed: () => _goToRegStep(3),
                  icon: const Icon(Icons.arrow_back_rounded, size: 16),
                  label: const Text('Atrás'),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _advanceFromStep4,
                    icon: const Icon(Icons.arrow_forward_rounded, size: 18),
                    label: const Text('Continuar a Contacto y Acceso'),
                  ),
                ),
              ],
            ),
          ],

          // --- PASO 5: CONTACTO Y ACCESO ---
          if (_regStep == 5) ...[
            _wrapBlurValidation(
              fieldId: 'correo',
              onBlurExtra: () {
                final mail = _correoController.text.trim();
                if (ValidationUtils.esCorreoValido(mail) &&
                    !ValidationUtils.esCorreoPlaceholder(mail)) {
                  unawaited(_checkContactoDisponibilidad(correo: mail));
                }
              },
              builder: (mode) => TextFormField(
                controller: _correoController,
                autovalidateMode:
                    _correoDisponibilidadError != null ? AutovalidateMode.always : mode,
                keyboardType: TextInputType.emailAddress,
                onChanged: (_) {
                  if (_correoDisponibilidadError != null || _inlineRegError != null) {
                    setState(() {
                      _correoDisponibilidadError = null;
                      _inlineRegError = null;
                    });
                  }
                },
                validator: (v) {
                  final mail = (v ?? '').trim();
                  if (!ValidationUtils.esCorreoValido(mail)) {
                    return 'Ingresa un correo electrónico válido.';
                  }
                  if (ValidationUtils.esCorreoPlaceholder(mail)) {
                    return 'Usa un correo electrónico real (Gmail, Outlook, etc.).';
                  }
                  if (_correoDisponibilidadError != null) {
                    return _correoDisponibilidadError;
                  }
                  return null;
                },
                decoration: InputDecoration(
                  labelText: 'Correo electrónico real *',
                  prefixIcon: const Icon(Icons.mail_outline_rounded),
                  errorMaxLines: 3,
                  suffixIcon: _checkingCorreoDisponibilidad
                      ? const Padding(
                          padding: EdgeInsets.all(12),
                          child: SizedBox(
                            width: 14,
                            height: 14,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          ),
                        )
                      : (_correoDisponibilidadError == null &&
                              ValidationUtils.esCorreoValido(_correoController.text.trim()) &&
                              !ValidationUtils.esCorreoPlaceholder(
                                _correoController.text.trim(),
                              ))
                          ? const Icon(
                              Icons.check_circle_rounded,
                              color: AppTheme.accentEmerald,
                              size: 18,
                            )
                          : null,
                ),
              ),
            ),
            const SizedBox(height: 10),
            _wrapBlurValidation(
              fieldId: 'telefono',
              builder: (mode) => TextFormField(
                controller: _telefonoController,
                autovalidateMode: mode,
                keyboardType: TextInputType.number,
                maxLength: 10,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                validator: ValidationUtils.diagnosticarTelefono,
                decoration: const InputDecoration(
                  labelText: 'Teléfono móvil México (10 dígitos) *',
                  prefixIcon: Icon(Icons.phone_android_rounded),
                  counterText: '',
                  errorMaxLines: 2,
                ),
              ),
            ),
            const SizedBox(height: 10),
            _wrapBlurValidation(
              fieldId: 'telAlt',
              builder: (mode) => TextFormField(
                controller: _telAltController,
                autovalidateMode: mode,
                keyboardType: TextInputType.number,
                maxLength: 10,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                validator: (v) {
                  if (v == null || v.trim().isEmpty) return null;
                  return ValidationUtils.diagnosticarTelefono(v);
                },
                decoration: const InputDecoration(
                  labelText: 'Teléfono alternativo (opcional)',
                  prefixIcon: Icon(Icons.phone_outlined),
                  counterText: '',
                  errorMaxLines: 2,
                ),
              ),
            ),
            const SizedBox(height: 10),
            _wrapBlurValidation(
              fieldId: 'regUsuario',
              onBlurExtra: () {
                final usr = _regUsuarioController.text.trim();
                if (ValidationUtils.esUsuarioValido(usr)) {
                  unawaited(_checkContactoDisponibilidad(usuario: usr));
                }
              },
              builder: (mode) => TextFormField(
                controller: _regUsuarioController,
                autovalidateMode:
                    _usuarioDisponibilidadError != null ? AutovalidateMode.always : mode,
                onChanged: (_) {
                  if (_usuarioDisponibilidadError != null || _inlineRegError != null) {
                    setState(() {
                      _usuarioDisponibilidadError = null;
                      _inlineRegError = null;
                    });
                  }
                },
                validator: (v) =>
                    ValidationUtils.diagnosticarUsuario(v) ?? _usuarioDisponibilidadError,
                decoration: const InputDecoration(
                  labelText: 'Usuario (mín. 4 caracteres) *',
                  prefixIcon: Icon(Icons.alternate_email_rounded),
                  errorMaxLines: 2,
                ),
              ),
            ),
            const SizedBox(height: 10),
            _wrapBlurValidation(
              fieldId: 'regPwd',
              builder: (mode) => TextFormField(
                controller: _regContrasenaController,
                autovalidateMode: mode,
                obscureText: _obscureRegPassword,
                onChanged: (_) => setState(() {}),
                validator: (v) => ValidationUtils.esContrasenaSegura(v)
                    ? null
                    : 'Mínimo 8 caracteres, mayúscula, minúscula y número.',
                decoration: InputDecoration(
                  labelText: 'Contraseña (8+ car., Mayús, Minús, Núm) *',
                  prefixIcon: const Icon(Icons.lock_outline_rounded),
                  errorMaxLines: 2,
                  suffixIcon: IconButton(
                    icon: Icon(
                      _obscureRegPassword
                          ? Icons.visibility_outlined
                          : Icons.visibility_off_outlined,
                    ),
                    onPressed: () =>
                        setState(() => _obscureRegPassword = !_obscureRegPassword),
                  ),
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
            _wrapBlurValidation(
              fieldId: 'regConfirmPwd',
              builder: (mode) => TextFormField(
                controller: _regConfirmContrasenaController,
                autovalidateMode: mode,
                obscureText: _obscureRegConfirmPassword,
                validator: (v) => (v ?? '') == _regContrasenaController.text
                    ? null
                    : 'Las contraseñas no coinciden.',
                decoration: InputDecoration(
                  labelText: 'Confirmar contraseña *',
                  prefixIcon: const Icon(Icons.check_circle_outline_rounded),
                  errorMaxLines: 2,
                  suffixIcon: IconButton(
                    icon: Icon(
                      _obscureRegConfirmPassword
                          ? Icons.visibility_outlined
                          : Icons.visibility_off_outlined,
                    ),
                    onPressed: () => setState(
                      () => _obscureRegConfirmPassword = !_obscureRegConfirmPassword,
                    ),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                OutlinedButton.icon(
                  onPressed: (auth.isLoading || _checkingCorreoDisponibilidad)
                      ? null
                      : () => _goToRegStep(4),
                  icon: const Icon(Icons.arrow_back_rounded, size: 16),
                  label: const Text('Atrás'),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: (auth.isLoading || _checkingCorreoDisponibilidad)
                        ? null
                        : () => _advanceToTotpStep(isRegenerate: false),
                    style: ElevatedButton.styleFrom(backgroundColor: AppTheme.accentEmerald),
                    icon: (auth.isLoading || _checkingCorreoDisponibilidad)
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.white,
                            ),
                          )
                        : const Icon(Icons.qr_code_scanner_rounded, size: 18),
                    label: Text(
                      (auth.isLoading || _checkingCorreoDisponibilidad)
                          ? 'Preparando...'
                          : 'Continuar a Google Authenticator',
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ),
              ],
            ),
          ],

          // --- PASO 6: GOOGLE AUTHENTICATOR (TOTP 30s) ---
          if (_regStep == 6) ...[
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
            if (_totpSetupData != null)
              TotpQrSetupCard(
                secret: (_totpSetupData!['secret'] ?? '').toString(),
                secretFormateado: _totpSetupData!['secretFormateado']?.toString(),
                qrPngBase64: _totpSetupData!['qrPngBase64']?.toString(),
                cuentaLabel: _totpSetupData!['cuenta']?.toString() ?? _correoController.text.trim(),
                periodSeconds: (_totpSetupData!['periodoSegundos'] is int)
                    ? (_totpSetupData!['periodoSegundos'] as int)
                    : 30,
                secondsLeft: _totpSecondsLeft,
                onCopied: () {
                  setState(() {
                    _totpFeedback = 'Clave secreta copiada al portapapeles.';
                  });
                },
              ),
            if (_totpFeedback != null) ...[
              const SizedBox(height: 10),
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: AppTheme.accentEmerald.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: AppTheme.accentEmerald.withValues(alpha: 0.4)),
                ),
                child: Text(
                  _totpFeedback!,
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
              controller: _regTotpController,
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
                labelText: 'Código Google Authenticator (6 dígitos)',
                hintText: '000000',
                counterText: '',
                prefixIcon: Icon(Icons.phonelink_lock_rounded),
              ),
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: (auth.isLoading || _regTotpController.text.trim().length < 6)
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
                            auth.clearError();
                            _regTotpController.clear();
                            _totpFeedback = null;
                            _goToRegStep(5);
                          },
                    icon: const Icon(Icons.arrow_back_rounded, size: 16),
                    label: const Text('Editar datos', style: TextStyle(fontSize: 12)),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: TextButton.icon(
                    onPressed: auth.isLoading
                        ? null
                        : () => _advanceToTotpStep(isRegenerate: true),
                    icon: const Icon(Icons.refresh_rounded, size: 16),
                    label: const Text('Nueva clave QR', style: TextStyle(fontSize: 12)),
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
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
