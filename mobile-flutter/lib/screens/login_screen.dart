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
  final _regFormKey = GlobalKey<FormState>();

  bool _isRegisterMode = false;
  bool _enrollBiometricOnLogin = false;

  // Login controllers
  final _usuarioController = TextEditingController();
  final _contrasenaController = TextEditingController();
  bool _obscurePassword = true;

  // Register controllers
  final _nombreController = TextEditingController();
  final _segundoNombreController = TextEditingController();
  final _apPaternoController = TextEditingController();
  final _apMaternoController = TextEditingController();
  final _fechaNacController = TextEditingController(text: '1998-01-15');
  String _sexo = 'HOMBRE';
  final _correoController = TextEditingController();
  final _telefonoController = TextEditingController();
  final _regUsuarioController = TextEditingController();
  final _regContrasenaController = TextEditingController();
  final _regConfirmContrasenaController = TextEditingController();
  bool _obscureRegPassword = true;
  bool _obscureRegConfirmPassword = true;

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
    _usuarioController.dispose();
    _contrasenaController.dispose();
    _nombreController.dispose();
    _segundoNombreController.dispose();
    _apPaternoController.dispose();
    _apMaternoController.dispose();
    _fechaNacController.dispose();
    _correoController.dispose();
    _telefonoController.dispose();
    _regUsuarioController.dispose();
    _regContrasenaController.dispose();
    _regConfirmContrasenaController.dispose();
    _apiUrlController.dispose();
    super.dispose();
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

  Future<void> _submitRegister() async {
    if (!_regFormKey.currentState!.validate()) return;
    final auth = Provider.of<AuthProvider>(context, listen: false);
    final success = await auth.register(
      nombre: _nombreController.text.trim(),
      segundoNombre: _segundoNombreController.text.trim(),
      apellidoPaterno: _apPaternoController.text.trim(),
      apellidoMaterno: _apMaternoController.text.trim(),
      fechaNacimiento: _fechaNacController.text.trim(),
      sexo: _sexo,
      correo: _correoController.text.trim(),
      telefonoMovil: _telefonoController.text.trim(),
      usuario: _regUsuarioController.text.trim(),
      contrasena: _regContrasenaController.text,
    );
    if (!success && mounted) {
      _showError(auth.errorMessage ?? 'No fue posible registrar la cuenta');
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

  Future<void> _pickFechaNacimiento() async {
    final now = DateTime.now();
    final initial = DateTime(now.year - 25, now.month, now.day);
    final picked = await showDatePicker(
      context: context,
      initialDate: initial,
      firstDate: DateTime(now.year - 100),
      lastDate: DateTime(now.year - 18, now.month, now.day),
    );
    if (picked != null && mounted) {
      final mm = picked.month.toString().padLeft(2, '0');
      final dd = picked.day.toString().padLeft(2, '0');
      _fechaNacController.text = '${picked.year}-$mm-$dd';
    }
  }

  void _showApiConfigDialog() {
    _apiUrlController.text = ApiClient().baseUrl;
    String? inlineError;
    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          title: const Row(
            children: [
              Icon(Icons.dns_rounded, color: AppTheme.primaryNavy),
              SizedBox(width: 8),
              Flexible(
                child: Text('Configurar Servidor API', style: TextStyle(fontSize: 17)),
              ),
            ],
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text(
                'URL base del backend Spring Boot. En tu celular conectado al mismo Wi-Fi usa la IP local de tu PC (ej. http://192.168.1.50:10000) o https:// si está en la nube.',
                style: TextStyle(fontSize: 12, color: AppTheme.textMuted),
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
                final newUrl = _apiUrlController.text.trim();
                final err = ApiClient.validateBaseUrl(newUrl);
                if (err != null) {
                  setDialogState(() => inlineError = err);
                  return;
                }
                final normalized = ApiClient.normalizeBaseUrl(newUrl);
                try {
                  await Provider.of<AuthProvider>(context, listen: false).saveApiUrl(normalized);
                  if (ctx.mounted) Navigator.of(ctx).pop();
                  if (mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                        content: Text('API guardada en: $normalized'),
                        backgroundColor: AppTheme.accentEmerald,
                      ),
                    );
                  }
                } catch (e) {
                  setDialogState(() => inlineError = e.toString());
                }
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

    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            colors: [Color(0xFFF8FAFC), Color(0xFFEFF6FF), Color(0xFFF8FAFC)],
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
          ),
        ),
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 20),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 440),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Align(
                      alignment: Alignment.topRight,
                      child: IconButton(
                        onPressed: _showApiConfigDialog,
                        icon: const Icon(Icons.settings_ethernet_rounded, color: AppTheme.textMuted),
                        tooltip: 'Configurar URL de API',
                      ),
                    ),

                    Center(
                      child: Container(
                        width: 72,
                        height: 72,
                        decoration: BoxDecoration(
                          gradient: const LinearGradient(
                            colors: [AppTheme.primaryNavy, AppTheme.secondaryBlue],
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                          ),
                          borderRadius: BorderRadius.circular(22),
                          boxShadow: [
                            BoxShadow(
                              color: AppTheme.primaryNavy.withValues(alpha: 0.28),
                              blurRadius: 18,
                              offset: const Offset(0, 8),
                            ),
                          ],
                        ),
                        child: const Icon(Icons.account_balance_rounded, color: Colors.white, size: 36),
                      ),
                    ),

                    const SizedBox(height: 16),

                    const Text(
                      'Banco Digital',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 27,
                        fontWeight: FontWeight.bold,
                        color: AppTheme.primaryNavy,
                        letterSpacing: -0.5,
                      ),
                    ),
                    const SizedBox(height: 4),
                    const Text(
                      'Portal Comercial de Pagos y Recargas GestoPago',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 13, color: AppTheme.textMuted),
                    ),

                    const SizedBox(height: 22),

                    if (auth.errorMessage != null && auth.errorMessage!.isNotEmpty) ...[
                      Container(
                        padding: const EdgeInsets.all(12),
                        margin: const EdgeInsets.only(bottom: 14),
                        decoration: BoxDecoration(
                          color: AppTheme.errorRed.withValues(alpha: 0.08),
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: AppTheme.errorRed.withValues(alpha: 0.4)),
                        ),
                        child: Row(
                          children: [
                            const Icon(Icons.info_outline_rounded, color: AppTheme.errorRed, size: 18),
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
                              icon: const Icon(Icons.close_rounded, size: 16, color: AppTheme.errorRed),
                              padding: EdgeInsets.zero,
                              constraints: const BoxConstraints(),
                              onPressed: () => auth.clearError(),
                            ),
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
                        setState(() => _isRegisterMode = sel.first);
                      },
                    ),

                    const SizedBox(height: 16),

                    Card(
                      elevation: 1,
                      shadowColor: AppTheme.primaryNavy.withValues(alpha: 0.08),
                      child: Padding(
                        padding: const EdgeInsets.all(22),
                        child: _isRegisterMode
                            ? _buildRegisterForm(auth)
                            : _buildLoginSection(auth),
                      ),
                    ),

                    const SizedBox(height: 16),

                    Container(
                      padding: const EdgeInsets.all(14),
                      decoration: BoxDecoration(
                        color: AppTheme.primaryNavy.withValues(alpha: 0.05),
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(color: AppTheme.borderSlate),
                      ),
                      child: const Column(
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(Icons.verified_user_rounded, size: 16, color: AppTheme.accentEmerald),
                              SizedBox(width: 6),
                              Text(
                                'Credenciales únicas Web & Móvil',
                                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12),
                              ),
                            ],
                          ),
                          SizedBox(height: 4),
                          Text(
                            'Acceso con contraseña o huella digital, 2FA en tu correo y comprobantes oficiales inmediatos.',
                            textAlign: TextAlign.center,
                            style: TextStyle(fontSize: 11, color: AppTheme.textMuted),
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
        const Text(
          'Accede a tu cuenta',
          style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppTheme.textDark),
        ),
        const SizedBox(height: 4),
        const Text(
          'Elige tu método de ingreso preferido.',
          style: TextStyle(fontSize: 12, color: AppTheme.textMuted),
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

        if (useBiometricTab)
          _buildBiometricLoginPanel(auth)
        else
          _buildPasswordLoginForm(auth),
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
            color: AppTheme.backgroundSlate,
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: AppTheme.borderSlate),
          ),
          child: Row(
            children: [
              CircleAvatar(
                radius: 20,
                backgroundColor: AppTheme.primaryNavy.withValues(alpha: 0.12),
                child: const Icon(Icons.person_rounded, color: AppTheme.primaryNavy),
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
                      style: const TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 14,
                        color: AppTheme.textDark,
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

          if (auth.canUseBiometrics && !auth.hasStoredBiometricCredentials) ...[
            const SizedBox(height: 10),
            CheckboxListTile(
              value: _enrollBiometricOnLogin,
              onChanged: busy ? null : (v) => setState(() => _enrollBiometricOnLogin = v ?? false),
              contentPadding: EdgeInsets.zero,
              controlAffinity: ListTileControlAffinity.leading,
              activeColor: AppTheme.accentEmerald,
              title: Text(
                'Activar acceso con ${auth.biometricLabel} al ingresar',
                style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
              ),
            ),
          ] else if (auth.biometricsInitialized && !auth.canUseBiometrics) ...[
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: AppTheme.backgroundSlate,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: AppTheme.borderSlate),
              ),
              child: Row(
                children: [
                  const Icon(Icons.fingerprint_rounded, size: 18, color: AppTheme.textMuted),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      auth.biometricAvailability.unavailableReason ??
                          'Acceso con huella disponible en dispositivos móviles compatibles.',
                      style: const TextStyle(fontSize: 11, color: AppTheme.textMuted),
                    ),
                  ),
                ],
              ),
            ),
          ],

          const SizedBox(height: 20),

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

  Widget _buildRegisterForm(AuthProvider auth) {
    final pwd = _regContrasenaController.text;

    return Form(
      key: _regFormKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Text(
            'Abre tu cuenta digital',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: AppTheme.textDark),
          ),
          const SizedBox(height: 4),
          const Text(
            'Mismo usuario y contraseña para la aplicación Web y Móvil.',
            style: TextStyle(fontSize: 12, color: AppTheme.textMuted),
          ),
          const SizedBox(height: 14),
          Row(
            children: [
              Expanded(
                child: TextFormField(
                  controller: _nombreController,
                  textCapitalization: TextCapitalization.words,
                  decoration: const InputDecoration(labelText: 'Nombre *'),
                  validator: (val) => ValidationUtils.diagnosticarNombre(val, 'El nombre'),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: TextFormField(
                  controller: _segundoNombreController,
                  textCapitalization: TextCapitalization.words,
                  decoration: const InputDecoration(
                    labelText: 'Segundo nombre',
                    hintText: 'Opcional',
                  ),
                  validator: (val) => ValidationUtils.diagnosticarNombre(
                    val,
                    'El segundo nombre',
                    optional: true,
                  ),
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
                  decoration: const InputDecoration(labelText: 'Ap. Paterno *'),
                  validator: (val) =>
                      ValidationUtils.diagnosticarNombre(val, 'El apellido paterno'),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: TextFormField(
                  controller: _apMaternoController,
                  textCapitalization: TextCapitalization.words,
                  decoration: const InputDecoration(labelText: 'Ap. Materno *'),
                  validator: (val) =>
                      ValidationUtils.diagnosticarNombre(val, 'El apellido materno'),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: TextFormField(
                  controller: _fechaNacController,
                  readOnly: true,
                  onTap: _pickFechaNacimiento,
                  decoration: const InputDecoration(
                    labelText: 'Nacimiento *',
                    suffixIcon: Icon(Icons.calendar_today_rounded, size: 18),
                  ),
                  validator: (val) => ValidationUtils.diagnosticarFechaNacimiento(val),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: DropdownButtonFormField<String>(
                  initialValue: _sexo,
                  decoration: const InputDecoration(labelText: 'Sexo'),
                  items: const [
                    DropdownMenuItem(value: 'HOMBRE', child: Text('Hombre')),
                    DropdownMenuItem(value: 'MUJER', child: Text('Mujer')),
                  ],
                  onChanged: (v) {
                    if (v != null) setState(() => _sexo = v);
                  },
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          TextFormField(
            controller: _correoController,
            keyboardType: TextInputType.emailAddress,
            decoration: const InputDecoration(
              labelText: 'Correo electrónico real (para 2FA y recibos) *',
              prefixIcon: Icon(Icons.mail_outline_rounded),
            ),
            validator: (val) {
              if (!ValidationUtils.esCorreoValido(val)) return 'Correo electrónico inválido';
              if (ValidationUtils.esCorreoPlaceholder(val)) return 'Usa un correo real, no de ejemplo';
              return null;
            },
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
            validator: (val) => ValidationUtils.diagnosticarTelefono(val),
          ),
          const SizedBox(height: 10),
          TextFormField(
            controller: _regUsuarioController,
            decoration: const InputDecoration(
              labelText: 'Usuario (mín. 4 caracteres) *',
              prefixIcon: Icon(Icons.alternate_email_rounded),
            ),
            validator: (val) => ValidationUtils.diagnosticarUsuario(val),
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
            validator: (val) => ValidationUtils.esContrasenaSegura(val)
                ? null
                : 'Mín. 8 caracteres, mayúscula, minúscula y número',
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
            validator: (val) {
              if (val == null || val.isEmpty) {
                return 'Confirma tu contraseña';
              }
              if (val != _regContrasenaController.text) {
                return 'Las contraseñas no coinciden';
              }
              return null;
            },
          ),
          const SizedBox(height: 18),
          ElevatedButton(
            onPressed: auth.isLoading ? null : _submitRegister,
            style: ElevatedButton.styleFrom(backgroundColor: AppTheme.accentEmerald),
            child: auth.isLoading
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                  )
                : const Text('Crear Cuenta y Acceder'),
          ),
        ],
      ),
    );
  }

  Widget _buildPwdRule(String label, bool ok) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: ok ? AppTheme.accentEmerald.withValues(alpha: 0.1) : AppTheme.backgroundSlate,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(
          color: ok ? AppTheme.accentEmerald.withValues(alpha: 0.4) : AppTheme.borderSlate,
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            ok ? Icons.check_circle_rounded : Icons.radio_button_unchecked_rounded,
            size: 13,
            color: ok ? AppTheme.accentEmerald : AppTheme.textLight,
          ),
          const SizedBox(width: 4),
          Text(
            label,
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w600,
              color: ok ? AppTheme.accentEmerald : AppTheme.textMuted,
            ),
          ),
        ],
      ),
    );
  }
}
