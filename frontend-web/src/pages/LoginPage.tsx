import React, { useState } from 'react';
import {
  Landmark,
  ShieldCheck,
  User,
  Lock,
  Eye,
  EyeOff,
  Loader2,
  AlertCircle,
  AlertTriangle,
  UserPlus,
  Mail,
  Phone,
  CheckCircle2,
  X,
} from 'lucide-react';
import { useAuth } from '../context/useAuth';
import {
  diagnosticarTelefono,
  esContrasenaSegura,
  esCorreoPlaceholder,
  esCorreoValido,
  esNombreValido,
  esTelefonoMexicanoValido,
  esUsuarioValido,
} from '../utils/validation';

type AuthMode = 'login' | 'register';

export const LoginPage: React.FC = () => {
  const { login, register, isLoading, sessionExpiredMessage, clearSessionExpired } = useAuth();
  const [mode, setMode] = useState<AuthMode>('login');

  // Login state
  const [usuario, setUsuario] = useState('');
  const [contrasena, setContrasena] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  // Register state
  const [nombre, setNombre] = useState('');
  const [apellidoPaterno, setApellidoPaterno] = useState('');
  const [correo, setCorreo] = useState('');
  const [telefonoMovil, setTelefonoMovil] = useState('');
  const [regUsuario, setRegUsuario] = useState('');
  const [regContrasena, setRegContrasena] = useState('');
  const [confirmarContrasena, setConfirmarContrasena] = useState('');
  const [showRegPassword, setShowRegPassword] = useState(false);

  const [error, setError] = useState<string | null>(null);

  const phoneDiag = telefonoMovil ? diagnosticarTelefono(telefonoMovil) : null;
  const phoneOk = esTelefonoMexicanoValido(telefonoMovil);

  const handleModeChange = (nextMode: AuthMode) => {
    setMode(nextMode);
    setError(null);
  };

  const handleLoginSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    clearSessionExpired();

    if (!usuario.trim() || !contrasena.trim()) {
      setError('Por favor, ingresa tu usuario y contraseña.');
      return;
    }

    try {
      await login({ usuario: usuario.trim(), contrasena });
    } catch (err: unknown) {
      const errorMsg =
        err instanceof Error ? err.message : 'Error al iniciar sesión. Verifica tus credenciales.';
      setError(errorMsg);
    }
  };

  const handleRegisterSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    clearSessionExpired();

    if (!esNombreValido(nombre, true)) {
      setError('El nombre debe tener entre 2 y 50 caracteres y contener solo letras y espacios.');
      return;
    }
    if (!esNombreValido(apellidoPaterno, true)) {
      setError(
        'El apellido paterno debe tener entre 2 y 50 caracteres y contener solo letras y espacios.'
      );
      return;
    }
    const emailTrim = correo.trim();
    if (!esCorreoValido(emailTrim)) {
      setError('Ingresa un correo electrónico con formato válido.');
      return;
    }
    if (esCorreoPlaceholder(emailTrim)) {
      setError(
        'Ingresa un correo electrónico real (Gmail, Outlook, iCloud, etc.) para recibir tus códigos 2FA y comprobantes.'
      );
      return;
    }
    const phoneErr = diagnosticarTelefono(telefonoMovil);
    if (phoneErr) {
      setError(phoneErr);
      return;
    }
    if (!esUsuarioValido(regUsuario)) {
      setError(
        'El usuario debe tener entre 4 y 50 caracteres (solo letras, números, puntos, guiones y guion bajo).'
      );
      return;
    }
    if (!esContrasenaSegura(regContrasena)) {
      setError(
        'La contraseña debe tener al menos 8 caracteres, una mayúscula, una minúscula y un número.'
      );
      return;
    }
    if (regContrasena !== confirmarContrasena) {
      setError('Las contraseñas no coinciden. Verifica la confirmación.');
      return;
    }

    try {
      await register({
        nombre: nombre.trim(),
        apellidoPaterno: apellidoPaterno.trim(),
        correo: emailTrim,
        telefonoMovil: telefonoMovil.trim(),
        usuario: regUsuario.trim(),
        contrasena: regContrasena,
      });
    } catch (err: unknown) {
      const errorMsg =
        err instanceof Error ? err.message : 'No fue posible completar el registro.';
      setError(errorMsg);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-blue-950 to-slate-900 flex flex-col justify-center items-center p-4 sm:p-6 lg:p-8">
      {/* Background visual elements */}
      <div className="fixed inset-0 overflow-hidden pointer-events-none">
        <div className="absolute -top-[10%] -left-[10%] w-[40%] h-[40%] bg-blue-600/20 blur-[120px] rounded-full" />
        <div className="absolute -bottom-[10%] -right-[10%] w-[40%] h-[40%] bg-indigo-600/20 blur-[120px] rounded-full" />
      </div>

      <div className="w-full max-w-lg z-10 space-y-6">
        {/* Header / Brand Logo */}
        <div className="text-center space-y-3">
          <div className="inline-flex items-center justify-center p-4 bg-white/10 backdrop-blur-md rounded-2xl border border-white/20 shadow-2xl">
            <Landmark className="w-10 h-10 text-blue-300" />
          </div>
          <h1 className="text-3xl font-extrabold text-white tracking-tight">Banco Digital</h1>
          <p className="text-blue-200/80 text-sm font-medium">
            Plataforma Comercial de Pagos de Servicios y Recargas GestoPago
          </p>
        </div>

        {/* Auth Card */}
        <div className="bg-white/95 backdrop-blur-xl rounded-3xl shadow-2xl border border-white/20 p-6 sm:p-8 space-y-5">
          {sessionExpiredMessage && (
            <div
              role="alert"
              className="p-3.5 rounded-xl bg-amber-50 border border-amber-200 flex items-start justify-between gap-2.5 text-amber-900 text-xs animate-in fade-in"
            >
              <div className="flex items-start gap-2.5">
                <AlertTriangle className="w-4 h-4 shrink-0 text-amber-600 mt-0.5" />
                <p className="font-semibold leading-relaxed">{sessionExpiredMessage}</p>
              </div>
              <button
                type="button"
                onClick={clearSessionExpired}
                aria-label="Cerrar aviso"
                className="p-0.5 text-amber-600 hover:text-amber-800 shrink-0"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          )}

          {/* Tabs */}
          <div className="grid grid-cols-2 p-1 bg-slate-100 rounded-2xl border border-slate-200">
            <button
              type="button"
              onClick={() => handleModeChange('login')}
              className={`py-2.5 text-xs font-bold rounded-xl transition-all flex items-center justify-center gap-1.5 ${
                mode === 'login'
                  ? 'bg-white text-blue-950 shadow-sm'
                  : 'text-slate-500 hover:text-slate-800'
              }`}
            >
              <ShieldCheck className="w-4 h-4" />
              <span>Iniciar Sesión</span>
            </button>
            <button
              type="button"
              onClick={() => handleModeChange('register')}
              className={`py-2.5 text-xs font-bold rounded-xl transition-all flex items-center justify-center gap-1.5 ${
                mode === 'register'
                  ? 'bg-white text-blue-950 shadow-sm'
                  : 'text-slate-500 hover:text-slate-800'
              }`}
            >
              <UserPlus className="w-4 h-4" />
              <span>Crear Cuenta</span>
            </button>
          </div>

          {error && (
            <div className="p-3.5 rounded-xl bg-red-50 border border-red-200 flex items-start gap-2.5 text-red-700 text-xs animate-in fade-in">
              <AlertCircle className="w-4 h-4 shrink-0 text-red-600 mt-0.5" />
              <p className="font-medium leading-relaxed">{error}</p>
            </div>
          )}

          {mode === 'login' ? (
            <form onSubmit={handleLoginSubmit} noValidate className="space-y-4">
              <div className="space-y-1">
                <h2 className="text-lg font-bold text-slate-900">Accede a tu banca</h2>
                <p className="text-xs text-slate-500">
                  Mismas credenciales compartidas para la aplicación Web y Móvil.
                </p>
              </div>

              <div className="space-y-1.5">
                <label
                  htmlFor="login-usuario"
                  className="text-xs font-semibold text-slate-700 uppercase tracking-wider"
                >
                  Usuario
                </label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none">
                    <User className="w-4 h-4 text-slate-400" />
                  </div>
                  <input
                    id="login-usuario"
                    type="text"
                    value={usuario}
                    onChange={(e) => setUsuario(e.target.value)}
                    placeholder="Ingresa tu usuario"
                    autoComplete="username"
                    className="w-full pl-10 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-600 focus:bg-white transition-all"
                    disabled={isLoading}
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <label
                  htmlFor="login-contrasena"
                  className="text-xs font-semibold text-slate-700 uppercase tracking-wider"
                >
                  Contraseña
                </label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none">
                    <Lock className="w-4 h-4 text-slate-400" />
                  </div>
                  <input
                    id="login-contrasena"
                    type={showPassword ? 'text' : 'password'}
                    value={contrasena}
                    onChange={(e) => setContrasena(e.target.value)}
                    placeholder="••••••••"
                    autoComplete="current-password"
                    className="w-full pl-10 pr-11 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-600 focus:bg-white transition-all"
                    disabled={isLoading}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    aria-label={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                    className="absolute inset-y-0 right-0 pr-3.5 flex items-center text-slate-400 hover:text-slate-600 focus:outline-none"
                    tabIndex={-1}
                  >
                    {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              <button
                type="submit"
                disabled={isLoading}
                className="w-full py-3.5 bg-gradient-to-r from-blue-900 to-blue-700 hover:from-blue-800 hover:to-blue-600 text-white font-bold text-sm rounded-xl shadow-lg shadow-blue-900/25 flex items-center justify-center gap-2 transition-all active:scale-[0.98] disabled:opacity-60"
              >
                {isLoading ? (
                  <>
                    <Loader2 className="w-5 h-5 animate-spin" />
                    <span>Verificando credenciales...</span>
                  </>
                ) : (
                  <>
                    <ShieldCheck className="w-5 h-5" />
                    <span>Ingresar de forma segura</span>
                  </>
                )}
              </button>
            </form>
          ) : (
            <form onSubmit={handleRegisterSubmit} noValidate className="space-y-3.5">
              <div className="space-y-1">
                <h2 className="text-lg font-bold text-slate-900">Abre tu cuenta digital</h2>
                <p className="text-xs text-slate-500">
                  Recibe tu CLABE/Cuenta bancaria inmediata, comprobantes oficiales por correo y acceso Web + App.
                </p>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                <div className="space-y-1">
                  <label htmlFor="reg-nombre" className="text-[11px] font-bold text-slate-700">
                    Nombre *
                  </label>
                  <input
                    id="reg-nombre"
                    type="text"
                    maxLength={50}
                    value={nombre}
                    onChange={(e) => setNombre(e.target.value)}
                    placeholder="Nombre"
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                    disabled={isLoading}
                  />
                </div>
                <div className="space-y-1">
                  <label htmlFor="reg-ap-paterno" className="text-[11px] font-bold text-slate-700">
                    Apellido(s) / Paterno *
                  </label>
                  <input
                    id="reg-ap-paterno"
                    type="text"
                    maxLength={50}
                    value={apellidoPaterno}
                    onChange={(e) => setApellidoPaterno(e.target.value)}
                    placeholder="Paterno"
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                    disabled={isLoading}
                  />
                </div>
              </div>

              <div className="space-y-1">
                <label htmlFor="reg-correo" className="text-[11px] font-bold text-slate-700">
                  Correo electrónico real (para 2FA y comprobantes) *
                </label>
                <div className="relative">
                  <Mail className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-2.5 pointer-events-none" />
                  <input
                    id="reg-correo"
                    type="email"
                    value={correo}
                    onChange={(e) => setCorreo(e.target.value)}
                    placeholder="tucorreo@gmail.com"
                    className="w-full pl-8 pr-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                    disabled={isLoading}
                  />
                </div>
              </div>

              <div className="space-y-1">
                <label htmlFor="reg-telefono" className="text-[11px] font-bold text-slate-700">
                  Teléfono móvil México (10 dígitos reales) *
                </label>
                <div className="relative">
                  <Phone className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-2.5 pointer-events-none" />
                  <input
                    id="reg-telefono"
                    type="tel"
                    inputMode="numeric"
                    maxLength={10}
                    value={telefonoMovil}
                    onChange={(e) => setTelefonoMovil(e.target.value.replace(/\D/g, '').slice(0, 10))}
                    placeholder="5512345678"
                    className={`w-full pl-8 pr-8 py-2 bg-slate-50 border rounded-xl text-xs font-mono text-slate-900 focus:outline-none focus:ring-2 ${
                      phoneOk
                        ? 'border-emerald-400 focus:ring-emerald-500'
                        : telefonoMovil
                        ? 'border-red-300 focus:ring-red-400'
                        : 'border-slate-200 focus:ring-blue-600'
                    }`}
                    disabled={isLoading}
                  />
                  {phoneOk && (
                    <CheckCircle2 className="w-4 h-4 text-emerald-600 absolute right-2.5 top-2" />
                  )}
                </div>
                {telefonoMovil && phoneDiag && (
                  <p className="text-[11px] text-red-600 font-medium">{phoneDiag}</p>
                )}
              </div>

              <div className="space-y-1">
                <label htmlFor="reg-usuario" className="text-[11px] font-bold text-slate-700">
                  Usuario *
                </label>
                <input
                  id="reg-usuario"
                  type="text"
                  maxLength={50}
                  value={regUsuario}
                  onChange={(e) => setRegUsuario(e.target.value)}
                  placeholder="min. 4 caracteres (letras, números, ._-)"
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                  disabled={isLoading}
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                <div className="space-y-1">
                  <label htmlFor="reg-contrasena" className="text-[11px] font-bold text-slate-700">
                    Contraseña *
                  </label>
                  <div className="relative">
                    <input
                      id="reg-contrasena"
                      type={showRegPassword ? 'text' : 'password'}
                      value={regContrasena}
                      onChange={(e) => setRegContrasena(e.target.value)}
                      placeholder="Mayús, minús y número"
                      className="w-full pl-3 pr-9 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                      disabled={isLoading}
                    />
                    <button
                      type="button"
                      onClick={() => setShowRegPassword(!showRegPassword)}
                      aria-label={showRegPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                      className="absolute inset-y-0 right-0 pr-2.5 flex items-center text-slate-400 hover:text-slate-600"
                      tabIndex={-1}
                    >
                      {showRegPassword ? (
                        <EyeOff className="w-3.5 h-3.5" />
                      ) : (
                        <Eye className="w-3.5 h-3.5" />
                      )}
                    </button>
                  </div>
                </div>
                <div className="space-y-1">
                  <label
                    htmlFor="reg-confirmar-contrasena"
                    className="text-[11px] font-bold text-slate-700"
                  >
                    Confirmar contraseña *
                  </label>
                  <input
                    id="reg-confirmar-contrasena"
                    type={showRegPassword ? 'text' : 'password'}
                    value={confirmarContrasena}
                    onChange={(e) => setConfirmarContrasena(e.target.value)}
                    placeholder="Repite tu contraseña"
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                    disabled={isLoading}
                  />
                </div>
              </div>

              {regContrasena && (
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-1.5 pt-0.5">
                  {[
                    { ok: regContrasena.length >= 8, label: '8+ caracteres' },
                    { ok: /[A-Z]/.test(regContrasena), label: 'Mayúscula' },
                    { ok: /[a-z]/.test(regContrasena), label: 'Minúscula' },
                    { ok: /\d/.test(regContrasena), label: 'Número' },
                  ].map((rule) => (
                    <div
                      key={rule.label}
                      className={`px-2 py-1 rounded-lg text-[10px] font-bold flex items-center gap-1 border ${
                        rule.ok
                          ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                          : 'bg-slate-50 text-slate-400 border-slate-200'
                      }`}
                    >
                      <CheckCircle2
                        className={`w-3 h-3 ${rule.ok ? 'text-emerald-600' : 'text-slate-300'}`}
                      />
                      <span>{rule.label}</span>
                    </div>
                  ))}
                </div>
              )}

              <button
                type="submit"
                disabled={isLoading}
                className="w-full py-3 bg-gradient-to-r from-emerald-700 to-teal-600 hover:from-emerald-600 hover:to-teal-500 text-white font-bold text-sm rounded-xl shadow-lg shadow-emerald-900/20 flex items-center justify-center gap-2 transition-all active:scale-[0.98] disabled:opacity-60"
              >
                {isLoading ? (
                  <>
                    <Loader2 className="w-5 h-5 animate-spin" />
                    <span>Creando cuenta bancaria...</span>
                  </>
                ) : (
                  <>
                    <UserPlus className="w-5 h-5" />
                    <span>Crear Cuenta y Acceder</span>
                  </>
                )}
              </button>
            </form>
          )}

          {/* Security Footer Pills */}
          <div className="pt-3 border-t border-slate-100 grid grid-cols-3 gap-2 text-center">
            <div className="p-2 rounded-xl bg-slate-50 border border-slate-100">
              <p className="text-[10px] font-bold text-slate-700">Sesión Segura</p>
              <p className="text-[10px] text-slate-500">Web + Móvil</p>
            </div>
            <div className="p-2 rounded-xl bg-slate-50 border border-slate-100">
              <p className="text-[10px] font-bold text-slate-700">2FA por Correo</p>
              <p className="text-[10px] text-slate-500">Token OTP 5 min</p>
            </div>
            <div className="p-2 rounded-xl bg-slate-50 border border-slate-100">
              <p className="text-[10px] font-bold text-slate-700">Ticket Brevo</p>
              <p className="text-[10px] text-slate-500">Envío Inmediato</p>
            </div>
          </div>
        </div>

        <p className="text-center text-xs text-slate-400">
          Protegido con cifrado BCrypt de grado bancario, validación telefónica IFT y verificación en dos pasos (2FA)
        </p>
      </div>
    </div>
  );
};
