import React, { useState, useEffect } from 'react';
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
  ArrowLeft,
  ArrowRight,
  RefreshCw,
  KeyRound,
  Clock,
  Sparkles,
  FileBadge,
  MapPin,
  Briefcase,
  Calendar,
  Sun,
  Moon,
  Award,
  CreditCard,
} from 'lucide-react';
import { useAuth } from '../context/useAuth';
import {
  ESTADOS_MEXICO,
  diagnosticarCurp,
  diagnosticarRfc,
  diagnosticarTelefono,
  esCodigoPostalValido,
  esContrasenaSegura,
  esCorreoPlaceholder,
  esCorreoValido,
  esCurpValida,
  esMayorDeEdad,
  esNombreValido,
  esRfcValido,
  esTelefonoMexicanoValido,
  esUsuarioValido,
  generarCurpYRfcSugeridos,
  getMaxBirthDateIso,
} from '../utils/validation';

type AuthMode = 'login' | 'register';
type RegisterStep = 1 | 2 | 3 | 4;

const STEP_META: { step: RegisterStep; shortLabel: string; title: string; subtitle: string }[] = [
  {
    step: 1,
    shortLabel: 'Identidad',
    title: 'Datos Personales del Titular',
    subtitle: 'Ingresa tu nombre legal completo y fecha de nacimiento tal como aparecen en tu identificación oficial.',
  },
  {
    step: 2,
    shortLabel: 'Fiscal y Domicilio',
    title: 'Expediente Fiscal (CURP / RFC) y Domicilio',
    subtitle: 'Valida tu clave RENAPO, RFC con homoclave, perfil económico y domicilio en México.',
  },
  {
    step: 3,
    shortLabel: 'Acceso',
    title: 'Contacto Verificado y Credenciales',
    subtitle: 'Configura tu correo real para recibir códigos 2FA vía Brevo y tus credenciales únicas Web + Móvil.',
  },
  {
    step: 4,
    shortLabel: 'Código 2FA',
    title: 'Verifica tu Correo Electrónico (2FA)',
    subtitle: 'Ingresa el código de seguridad de 6 dígitos enviado a tu bandeja para aperturar tu cuenta digital.',
  },
];

export const LoginPage: React.FC = () => {
  const {
    login,
    enviarCodigoRegistro,
    register,
    isLoading,
    sessionExpiredMessage,
    clearSessionExpired,
  } = useAuth();

  const [mode, setMode] = useState<AuthMode>('login');
  const [isDark, setIsDark] = useState<boolean>(() => {
    const saved = window.localStorage.getItem('theme_mode');
    return saved ? saved === 'dark' : document.documentElement.classList.contains('dark');
  });

  useEffect(() => {
    document.documentElement.classList.toggle('dark', isDark);
    window.localStorage.setItem('theme_mode', isDark ? 'dark' : 'light');
  }, [isDark]);

  // Login state
  const [usuario, setUsuario] = useState('');
  const [contrasena, setContrasena] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  // Register Step 1: Identity
  const [regStep, setRegStep] = useState<RegisterStep>(1);
  const [nombre, setNombre] = useState('');
  const [segundoNombre, setSegundoNombre] = useState('');
  const [apellidoPaterno, setApellidoPaterno] = useState('');
  const [apellidoMaterno, setApellidoMaterno] = useState('');
  const [fechaNacimiento, setFechaNacimiento] = useState('1998-05-15');
  const [sexo, setSexo] = useState<'HOMBRE' | 'MUJER'>('HOMBRE');
  const [nacionalidad, setNacionalidad] = useState('MEXICANA');
  const [estadoCivil, setEstadoCivil] = useState('SOLTERO');

  // Register Step 2: Fiscal (CURP / RFC), Economic & Address
  const [entidadCodigo, setEntidadCodigo] = useState('DF');
  const [curp, setCurp] = useState('');
  const [rfc, setRfc] = useState('');
  const [ocupacion, setOcupacion] = useState('Profesionista');
  const [empresa, setEmpresa] = useState('Independiente');
  const [ingresoMensual, setIngresoMensual] = useState('18000');
  const [calle, setCalle] = useState('');
  const [numeroExterior, setNumeroExterior] = useState('');
  const [numeroInterior, setNumeroInterior] = useState('');
  const [colonia, setColonia] = useState('');
  const [codigoPostal, setCodigoPostal] = useState('');
  const [municipio, setMunicipio] = useState('');

  // Register Step 3: Contact & Credentials
  const [correo, setCorreo] = useState('');
  const [telefonoMovil, setTelefonoMovil] = useState('');
  const [telefonoAlternativo, setTelefonoAlternativo] = useState('');
  const [regUsuario, setRegUsuario] = useState('');
  const [regContrasena, setRegContrasena] = useState('');
  const [confirmarContrasena, setConfirmarContrasena] = useState('');
  const [showRegPassword, setShowRegPassword] = useState(false);

  // Register Step 4: 2FA OTP
  const [codigoVerificacion, setCodigoVerificacion] = useState('');
  const [otpCountdown, setOtpCountdown] = useState(0);
  const [otpSentTo, setOtpSentTo] = useState('');
  const [otpFeedback, setOtpFeedback] = useState<string | null>(null);

  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (regStep !== 4 || otpCountdown <= 0) return;
    const timer = setInterval(() => {
      setOtpCountdown((prev) => (prev > 1 ? prev - 1 : 0));
    }, 1000);
    return () => clearInterval(timer);
  }, [regStep, otpCountdown]);

  const selectedStateName =
    ESTADOS_MEXICO.find((s) => s.code === entidadCodigo)?.name || 'Ciudad de México';

  const phoneDiag = telefonoMovil ? diagnosticarTelefono(telefonoMovil) : null;
  const phoneOk = esTelefonoMexicanoValido(telefonoMovil);
  const curpDiag = curp ? diagnosticarCurp(curp) : null;
  const curpOk = esCurpValida(curp);
  const rfcDiag = rfc ? diagnosticarRfc(rfc) : null;
  const rfcOk = esRfcValido(rfc);

  const pwdRules = [
    { ok: regContrasena.length >= 8, label: '8+ caracteres' },
    { ok: /[A-Z]/.test(regContrasena), label: 'Mayúscula' },
    { ok: /[a-z]/.test(regContrasena), label: 'Minúscula' },
    { ok: /\d/.test(regContrasena), label: 'Número' },
  ];
  const pwdStrength = pwdRules.filter((r) => r.ok).length;

  const formatCountdown = (secs: number) => {
    const mins = Math.floor(secs / 60)
      .toString()
      .padStart(2, '0');
    const rem = (secs % 60).toString().padStart(2, '0');
    return `${mins}:${rem}`;
  };

  const handleModeChange = (nextMode: AuthMode) => {
    setMode(nextMode);
    setError(null);
    setRegStep(1);
    setCodigoVerificacion('');
    setOtpCountdown(0);
    setOtpFeedback(null);
  };

  const handleAutoGenerateFiscal = () => {
    setError(null);
    const generated = generarCurpYRfcSugeridos({
      nombre,
      segundoNombre,
      apellidoPaterno,
      apellidoMaterno,
      fechaNacimiento,
      sexo,
      estadoCodigo: entidadCodigo,
    });
    if (!generated) {
      setError('Completa primero tu nombre, apellido paterno y fecha de nacimiento en el Paso 1.');
      return;
    }
    setCurp(generated.curp);
    setRfc(generated.rfc);
  };

  const validateStep1 = (): string | null => {
    if (!esNombreValido(nombre, true)) {
      return 'El nombre debe tener entre 2 y 50 caracteres y contener solo letras y espacios.';
    }
    if (!esNombreValido(segundoNombre, false)) {
      return 'El segundo nombre solo debe contener letras y espacios (máx. 50 caracteres).';
    }
    if (!esNombreValido(apellidoPaterno, true)) {
      return 'El apellido paterno debe tener entre 2 y 50 caracteres y contener solo letras y espacios.';
    }
    if (!esNombreValido(apellidoMaterno, true)) {
      return 'El apellido materno debe tener entre 2 y 50 caracteres y contener solo letras y espacios.';
    }
    if (!esMayorDeEdad(fechaNacimiento)) {
      return 'Debes ingresar una fecha de nacimiento válida y ser mayor de 18 años.';
    }
    return null;
  };

  const validateStep2 = (): string | null => {
    const curpErr = diagnosticarCurp(curp);
    if (curpErr) return curpErr;
    const rfcErr = diagnosticarRfc(rfc);
    if (rfcErr) return rfcErr;
    if (ocupacion.trim().length < 2) {
      return 'Ingresa tu ocupación o actividad económica.';
    }
    if (empresa.trim().length < 2) {
      return 'Ingresa tu empresa, institución o actividad independiente.';
    }
    const ingresoNum = parseFloat(ingresoMensual);
    if (Number.isNaN(ingresoNum) || ingresoNum <= 0) {
      return 'Ingresa un ingreso mensual estimado válido mayor a $0.00 MXN.';
    }
    if (calle.trim().length < 3) {
      return 'Ingresa la calle de tu domicilio (mínimo 3 caracteres).';
    }
    if (!numeroExterior.trim()) {
      return 'Ingresa el número exterior de tu domicilio (usa S/N si no aplica).';
    }
    if (colonia.trim().length < 2) {
      return 'Ingresa la colonia o fraccionamiento de tu domicilio.';
    }
    if (!esCodigoPostalValido(codigoPostal)) {
      return 'El código postal debe contener 5 dígitos válidos (rango 01000 a 99999).';
    }
    if (municipio.trim().length < 2) {
      return 'Ingresa tu municipio o alcaldía.';
    }
    return null;
  };

  const validateStep3 = (): string | null => {
    const emailTrim = correo.trim();
    if (!esCorreoValido(emailTrim)) {
      return 'Ingresa un correo electrónico con formato válido.';
    }
    if (esCorreoPlaceholder(emailTrim)) {
      return 'Ingresa un correo electrónico real (Gmail, Outlook, iCloud, etc.) para recibir tus códigos 2FA y comprobantes.';
    }
    const phoneErr = diagnosticarTelefono(telefonoMovil);
    if (phoneErr) return phoneErr;
    if (telefonoAlternativo.trim()) {
      const altErr = diagnosticarTelefono(telefonoAlternativo);
      if (altErr) return `Teléfono alternativo: ${altErr}`;
    }
    if (!esUsuarioValido(regUsuario)) {
      return 'El usuario debe tener entre 4 y 50 caracteres (solo letras, números, puntos, guiones y guion bajo).';
    }
    if (!esContrasenaSegura(regContrasena)) {
      return 'La contraseña debe tener al menos 8 caracteres, una mayúscula, una minúscula y un número.';
    }
    if (regContrasena !== confirmarContrasena) {
      return 'Las contraseñas no coinciden. Verifica la confirmación.';
    }
    return null;
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

  const handleNextStep = () => {
    setError(null);
    clearSessionExpired();
    if (regStep === 1) {
      const err1 = validateStep1();
      if (err1) {
        setError(err1);
        return;
      }
      // Pre-suggest CURP & RFC if empty so the user sees them ready to verify/adjust
      if (!curp && !rfc) {
        const suggested = generarCurpYRfcSugeridos({
          nombre,
          segundoNombre,
          apellidoPaterno,
          apellidoMaterno,
          fechaNacimiento,
          sexo,
          estadoCodigo: entidadCodigo,
        });
        if (suggested) {
          setCurp(suggested.curp);
          setRfc(suggested.rfc);
        }
      }
      setRegStep(2);
      return;
    }
    if (regStep === 2) {
      const err2 = validateStep2();
      if (err2) {
        setError(err2);
        return;
      }
      setRegStep(3);
    }
  };

  const handleSendRegCode = async (isResend = false) => {
    setError(null);
    setOtpFeedback(null);
    clearSessionExpired();

    const err1 = validateStep1();
    if (err1) {
      setRegStep(1);
      setError(err1);
      return;
    }
    const err2 = validateStep2();
    if (err2) {
      setRegStep(2);
      setError(err2);
      return;
    }
    const err3 = validateStep3();
    if (err3) {
      setRegStep(3);
      setError(err3);
      return;
    }

    const emailTrim = correo.trim();
    try {
      const resp = await enviarCodigoRegistro({
        correo: emailTrim,
        nombre: nombre.trim(),
        usuario: regUsuario.trim(),
        curp: curp.trim().toUpperCase(),
        rfc: rfc.trim().toUpperCase(),
      });
      setOtpSentTo(resp.correoDestino || emailTrim);
      setOtpCountdown(resp.expiraEnSegundos || 300);
      setCodigoVerificacion('');
      setRegStep(4);
      if (isResend) {
        setOtpFeedback('Se envió un nuevo código 2FA de 6 dígitos a tu correo.');
      }
    } catch (err: unknown) {
      const errorMsg =
        err instanceof Error
          ? err.message
          : 'No fue posible enviar el código de verificación a tu correo.';
      setError(errorMsg);
    }
  };

  const handleRegisterSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (regStep === 1 || regStep === 2) {
      handleNextStep();
      return;
    }
    if (regStep === 3) {
      await handleSendRegCode(false);
      return;
    }

    setError(null);
    setOtpFeedback(null);
    clearSessionExpired();

    const cleanCode = codigoVerificacion.trim();
    if (!/^\d{6}$/.test(cleanCode)) {
      setError('Ingresa el código de verificación de 6 dígitos enviado a tu correo.');
      return;
    }

    try {
      await register({
        nombre: nombre.trim(),
        segundoNombre: segundoNombre.trim() || undefined,
        apellidoPaterno: apellidoPaterno.trim(),
        apellidoMaterno: apellidoMaterno.trim(),
        fechaNacimiento,
        sexo,
        nacionalidad,
        estadoCivil,
        curp: curp.trim().toUpperCase(),
        rfc: rfc.trim().toUpperCase(),
        ocupacion: ocupacion.trim(),
        empresa: empresa.trim(),
        ingresoMensual: parseFloat(ingresoMensual) || 15000,
        calle: calle.trim(),
        numeroExterior: numeroExterior.trim(),
        numeroInterior: numeroInterior.trim() || undefined,
        colonia: colonia.trim(),
        codigoPostal: codigoPostal.trim(),
        municipio: municipio.trim(),
        estado: selectedStateName,
        pais: 'México',
        correo: correo.trim(),
        telefonoMovil: telefonoMovil.trim(),
        telefonoAlternativo: telefonoAlternativo.trim() || undefined,
        usuario: regUsuario.trim(),
        contrasena: regContrasena,
        codigoVerificacion: cleanCode,
      });
    } catch (err: unknown) {
      const errorMsg =
        err instanceof Error ? err.message : 'No fue posible completar el registro.';
      setError(errorMsg);
    }
  };

  const currentMeta = STEP_META[regStep - 1];

  return (
    <div className="min-h-screen bg-gradient-to-br from-[#040814] via-[#0A193B] to-[#060D20] flex flex-col justify-between p-4 sm:p-6 lg:p-8 relative overflow-hidden">
      {/* Ambient Executive Light Orbs */}
      <div className="fixed inset-0 overflow-hidden pointer-events-none">
        <div className="absolute -top-[12%] -left-[8%] w-[45%] h-[45%] bg-blue-600/20 blur-[130px] rounded-full" />
        <div className="absolute top-[25%] -right-[10%] w-[38%] h-[38%] bg-amber-500/12 blur-[130px] rounded-full" />
        <div className="absolute -bottom-[12%] left-[20%] w-[40%] h-[40%] bg-indigo-600/20 blur-[130px] rounded-full" />
      </div>

      {/* Top Executive Utility Bar */}
      <div className="w-full max-w-6xl mx-auto z-10 flex items-center justify-between gap-3 pb-4">
        <div className="inline-flex items-center gap-2.5 px-3.5 py-1.5 rounded-full bg-white/10 backdrop-blur-md border border-white/15 text-xs font-semibold text-blue-100">
          <span className="w-2 h-2 rounded-full bg-emerald-400 shadow-[0_0_8px_#34d399]" />
          <span>Red Bancaria Certificada &bull; GestoPago Direct</span>
        </div>

        <button
          type="button"
          onClick={() => setIsDark((prev) => !prev)}
          aria-label="Cambiar tema"
          title={isDark ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
          className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-white/10 hover:bg-white/20 backdrop-blur-md border border-white/20 text-xs font-bold text-white transition-all"
        >
          {isDark ? (
            <>
              <Sun className="w-3.5 h-3.5 text-amber-300" />
              <span className="hidden sm:inline">Modo Claro</span>
            </>
          ) : (
            <>
              <Moon className="w-3.5 h-3.5 text-blue-200" />
              <span className="hidden sm:inline">Modo Oscuro</span>
            </>
          )}
        </button>
      </div>

      {/* Main Split Portal */}
      <div className="w-full max-w-6xl mx-auto z-10 my-auto">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 lg:gap-8 items-stretch">
          {/* Left Showcase Column */}
          <div className="lg:col-span-5 rounded-[30px] bg-gradient-to-br from-white/[0.09] via-blue-950/40 to-slate-950/70 backdrop-blur-2xl border border-white/15 p-6 sm:p-8 text-white flex flex-col justify-between shadow-[0_25px_60px_-15px_rgba(0,0,0,0.7)]">
            <div className="space-y-6">
              {/* Brand Emblem */}
              <div className="flex items-center gap-3.5">
                <div className="p-3.5 rounded-2xl bg-gradient-to-br from-amber-300/25 via-blue-500/20 to-indigo-600/20 border border-amber-300/40 shadow-xl">
                  <Landmark className="w-8 h-8 text-amber-200" />
                </div>
                <div>
                  <span className="text-[10px] font-black uppercase tracking-[0.2em] text-amber-300 block">
                    BANCA PRIVADA DIGITAL
                  </span>
                  <h1 className="text-2xl sm:text-3xl font-black tracking-tight text-white">
                    Banco Digital
                  </h1>
                </div>
              </div>

              <div className="space-y-2">
                <h2 className="text-xl sm:text-2xl font-extrabold leading-snug text-white">
                  Pagos de servicios, recargas al instante y seguridad bancaria 2FA.
                </h2>
                <p className="text-xs sm:text-sm text-blue-100/80 leading-relaxed">
                  Opera tus recibos CFE, agua, internet, TAG y tiempo aire con comprobantes oficiales PDF, folio de autorización y validación en tiempo real.
                </p>
              </div>

              {/* Virtual Platinum Card Preview */}
              <div className="rounded-2xl bg-gradient-to-br from-[#061029] via-[#102A6B] to-[#08122B] p-4 border border-amber-300/30 shadow-xl space-y-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <CreditCard className="w-4 h-4 text-amber-300" />
                    <span className="text-[10px] font-black tracking-[0.16em] text-amber-200 uppercase">
                      CUENTA PLATINUM SIN COMISIÓN
                    </span>
                  </div>
                  <span className="px-2 py-0.5 rounded-full bg-emerald-500/20 border border-emerald-400/30 text-[10px] font-bold text-emerald-300">
                    Bono Apertura
                  </span>
                </div>
                <div className="grid grid-cols-2 gap-2 pt-1 text-xs">
                  <div className="p-2.5 rounded-xl bg-white/5 border border-white/10">
                    <p className="text-[10px] text-blue-200/75">Saldo de Bienvenida</p>
                    <p className="text-sm font-black text-white mt-0.5">$200.00 MXN</p>
                  </div>
                  <div className="p-2.5 rounded-xl bg-white/5 border border-white/10">
                    <p className="text-[10px] text-blue-200/75">Comisión por Pago</p>
                    <p className="text-sm font-black text-emerald-300 mt-0.5">$0.00 MXN</p>
                  </div>
                </div>
              </div>

              {/* Security & Compliance Pillars */}
              <div className="space-y-2.5 pt-1">
                {[
                  {
                    icon: FileBadge,
                    title: 'Expediente KYC Completo (CURP y RFC)',
                    desc: 'Validación normativa RENAPO, SAT e IFT con apertura inmediata de cuenta de 16 dígitos.',
                  },
                  {
                    icon: ShieldCheck,
                    title: 'Doble Factor 2FA por Correo (Brevo)',
                    desc: 'Código OTP único de 6 dígitos para validar tu registro y autorizar cada operación.',
                  },
                  {
                    icon: Award,
                    title: 'Trazabilidad Oficial GestoPago',
                    desc: 'Cada recibo incluye ID_TX, Número de Autorización, Folio UPC y descarga PDF.',
                  },
                ].map((item) => {
                  const Icon = item.icon;
                  return (
                    <div
                      key={item.title}
                      className="flex items-start gap-3 p-3 rounded-2xl bg-white/[0.05] border border-white/10"
                    >
                      <div className="p-2 rounded-xl bg-blue-500/20 border border-blue-300/25 shrink-0 mt-0.5">
                        <Icon className="w-4 h-4 text-amber-200" />
                      </div>
                      <div>
                        <p className="text-xs font-extrabold text-white">{item.title}</p>
                        <p className="text-[11px] text-blue-100/75 leading-relaxed">{item.desc}</p>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            <div className="pt-6 mt-6 border-t border-white/10 flex items-center justify-between text-[11px] text-blue-200/70">
              <span>Cifrado BCrypt &bull; Sesión Única Activa</span>
              <span className="font-mono text-amber-200/90">Web + Android APK</span>
            </div>
          </div>

          {/* Right Form Card */}
          <div className="lg:col-span-7 bg-white/95 backdrop-blur-xl rounded-[30px] shadow-[0_25px_60px_-15px_rgba(0,0,0,0.65)] border border-white/25 p-6 sm:p-8 flex flex-col justify-between space-y-5">
            <div className="space-y-5">
              {sessionExpiredMessage && (
                <div
                  role="alert"
                  className="p-3.5 rounded-2xl bg-amber-50 border border-amber-200 flex items-start justify-between gap-2.5 text-amber-900 text-xs animate-in fade-in"
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

              {/* Mode Selector Tabs */}
              <div className="grid grid-cols-2 p-1.5 bg-slate-100 rounded-2xl border border-slate-200">
                <button
                  type="button"
                  onClick={() => handleModeChange('login')}
                  className={`py-2.5 text-xs font-extrabold rounded-xl transition-all flex items-center justify-center gap-2 ${
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
                  className={`py-2.5 text-xs font-extrabold rounded-xl transition-all flex items-center justify-center gap-2 ${
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
                <div className="p-3.5 rounded-2xl bg-red-50 border border-red-200 flex items-start gap-2.5 text-red-700 text-xs animate-in fade-in">
                  <AlertCircle className="w-4 h-4 shrink-0 text-red-600 mt-0.5" />
                  <p className="font-semibold leading-relaxed">{error}</p>
                </div>
              )}

              {mode === 'login' ? (
                <form onSubmit={handleLoginSubmit} noValidate className="space-y-5 pt-2">
                  <div className="space-y-1">
                    <h2 className="text-xl font-black text-slate-900">Accede a tu banca</h2>
                    <p className="text-xs text-slate-500">
                      Ingresa con tus credenciales únicas sincronizadas entre el portal Web y la aplicación móvil.
                    </p>
                  </div>

                  <div className="space-y-1.5">
                    <label
                      htmlFor="login-usuario"
                      className="text-xs font-bold text-slate-700 uppercase tracking-wider"
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
                        placeholder="Ingresa tu nombre de usuario"
                        autoComplete="username"
                        className="w-full pl-10 pr-4 py-3.5 bg-slate-50 border border-slate-200 rounded-xl text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-600 focus:bg-white transition-all"
                        disabled={isLoading}
                      />
                    </div>
                  </div>

                  <div className="space-y-1.5">
                    <label
                      htmlFor="login-contrasena"
                      className="text-xs font-bold text-slate-700 uppercase tracking-wider"
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
                        className="w-full pl-10 pr-11 py-3.5 bg-slate-50 border border-slate-200 rounded-xl text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-600 focus:bg-white transition-all"
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
                    className="w-full py-3.5 bg-gradient-to-r from-blue-950 via-blue-800 to-indigo-800 hover:from-blue-900 hover:to-indigo-700 text-white font-extrabold text-sm rounded-2xl shadow-lg shadow-blue-950/25 flex items-center justify-center gap-2 transition-all active:scale-[0.99] disabled:opacity-60"
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

                  <div className="p-3.5 rounded-2xl bg-slate-50 border border-slate-200/80 flex items-center justify-between gap-3 text-xs">
                    <span className="text-slate-600 font-medium">¿Aún no eres titular en Banco Digital?</span>
                    <button
                      type="button"
                      onClick={() => handleModeChange('register')}
                      className="font-extrabold text-blue-800 hover:text-blue-950 inline-flex items-center gap-1 shrink-0"
                    >
                      <span>Abrir cuenta digital</span>
                      <ArrowRight className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </form>
              ) : (
                <form onSubmit={handleRegisterSubmit} noValidate className="space-y-4">
                  {/* 4-Step Interactive Progress Header */}
                  <div className="space-y-3">
                    <div className="grid grid-cols-4 gap-1.5">
                      {STEP_META.map((item) => {
                        const isActive = regStep === item.step;
                        const isDone = regStep > item.step;
                        return (
                          <button
                            key={item.step}
                            type="button"
                            disabled={item.step > regStep || isLoading}
                            onClick={() => {
                              if (item.step < regStep) {
                                setError(null);
                                setRegStep(item.step);
                              }
                            }}
                            className={`p-2 rounded-xl border text-left transition-all ${
                              isActive
                                ? 'bg-blue-50 border-blue-400 text-blue-950 shadow-sm'
                                : isDone
                                ? 'bg-emerald-50 border-emerald-300 text-emerald-900 cursor-pointer hover:bg-emerald-100/70'
                                : 'bg-slate-50 border-slate-200 text-slate-400 cursor-default'
                            }`}
                          >
                            <div className="flex items-center justify-between">
                              <span className="text-[10px] font-black uppercase">
                                Paso {item.step}
                              </span>
                              {isDone && <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600" />}
                            </div>
                            <p className="text-[11px] font-bold truncate mt-0.5">
                              {item.shortLabel}
                            </p>
                          </button>
                        );
                      })}
                    </div>

                    <div className="flex items-start justify-between gap-2 pt-1">
                      <div>
                        <h2 className="text-lg font-black text-slate-900">{currentMeta.title}</h2>
                        <p className="text-xs text-slate-500">{currentMeta.subtitle}</p>
                      </div>
                      <span className="px-2.5 py-1 rounded-full text-[10px] font-extrabold bg-blue-50 text-blue-800 border border-blue-200 shrink-0">
                        Paso {regStep} de 4
                      </span>
                    </div>
                  </div>

                  {/* STEP 1: PERSONAL IDENTITY */}
                  {regStep === 1 && (
                    <div className="space-y-3.5 animate-in fade-in">
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
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
                            placeholder="Ej. César"
                            className="w-full px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                          />
                        </div>
                        <div className="space-y-1">
                          <label
                            htmlFor="reg-segundo-nombre"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Segundo nombre (opcional)
                          </label>
                          <input
                            id="reg-segundo-nombre"
                            type="text"
                            maxLength={50}
                            value={segundoNombre}
                            onChange={(e) => setSegundoNombre(e.target.value)}
                            placeholder="Ej. Enrique"
                            className="w-full px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                          />
                        </div>
                        <div className="space-y-1">
                          <label
                            htmlFor="reg-ap-paterno"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Apellido paterno *
                          </label>
                          <input
                            id="reg-ap-paterno"
                            type="text"
                            maxLength={50}
                            value={apellidoPaterno}
                            onChange={(e) => setApellidoPaterno(e.target.value)}
                            placeholder="Ej. Garay"
                            className="w-full px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                          />
                        </div>
                        <div className="space-y-1">
                          <label
                            htmlFor="reg-ap-materno"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Apellido materno *
                          </label>
                          <input
                            id="reg-ap-materno"
                            type="text"
                            maxLength={50}
                            value={apellidoMaterno}
                            onChange={(e) => setApellidoMaterno(e.target.value)}
                            placeholder="Ej. García"
                            className="w-full px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                          />
                        </div>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <div className="space-y-1">
                          <label
                            htmlFor="reg-fecha-nac"
                            className="text-[11px] font-bold text-slate-700 flex items-center gap-1"
                          >
                            <Calendar className="w-3 h-3 text-blue-700" />
                            <span>Fecha de nacimiento (+18 años) *</span>
                          </label>
                          <input
                            id="reg-fecha-nac"
                            type="date"
                            max={getMaxBirthDateIso()}
                            value={fechaNacimiento}
                            onChange={(e) => setFechaNacimiento(e.target.value)}
                            className="w-full px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                          />
                        </div>
                        <div className="space-y-1">
                          <label htmlFor="reg-sexo" className="text-[11px] font-bold text-slate-700">
                            Sexo *
                          </label>
                          <select
                            id="reg-sexo"
                            value={sexo}
                            onChange={(e) => setSexo(e.target.value as 'HOMBRE' | 'MUJER')}
                            className="w-full px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                          >
                            <option value="HOMBRE">Hombre (H)</option>
                            <option value="MUJER">Mujer (M)</option>
                          </select>
                        </div>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <div className="space-y-1">
                          <label
                            htmlFor="reg-nacionalidad"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Nacionalidad *
                          </label>
                          <select
                            id="reg-nacionalidad"
                            value={nacionalidad}
                            onChange={(e) => setNacionalidad(e.target.value)}
                            className="w-full px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                          >
                            <option value="MEXICANA">Mexicana</option>
                            <option value="EXTRANJERA">Extranjera con residencia</option>
                          </select>
                        </div>
                        <div className="space-y-1">
                          <label
                            htmlFor="reg-estado-civil"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Estado civil *
                          </label>
                          <select
                            id="reg-estado-civil"
                            value={estadoCivil}
                            onChange={(e) => setEstadoCivil(e.target.value)}
                            className="w-full px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                          >
                            <option value="SOLTERO">Soltero(a)</option>
                            <option value="CASADO">Casado(a)</option>
                            <option value="UNION_LIBRE">Unión libre</option>
                            <option value="DIVORCIADO">Divorciado(a)</option>
                            <option value="VIUDO">Viudo(a)</option>
                          </select>
                        </div>
                      </div>

                      <button
                        type="submit"
                        className="w-full py-3 bg-gradient-to-r from-blue-950 to-blue-700 hover:from-blue-900 hover:to-blue-600 text-white font-extrabold text-xs sm:text-sm rounded-xl shadow-md flex items-center justify-center gap-2 transition-all"
                      >
                        <span>Continuar a Datos Fiscales y Domicilio</span>
                        <ArrowRight className="w-4 h-4" />
                      </button>
                    </div>
                  )}

                  {/* STEP 2: FISCAL (CURP & RFC), ECONOMIC & ADDRESS */}
                  {regStep === 2 && (
                    <div className="space-y-3.5 animate-in fade-in">
                      <div className="p-3 rounded-2xl bg-indigo-50/80 border border-indigo-200 flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
                        <div className="text-xs text-indigo-950">
                          <p className="font-extrabold flex items-center gap-1.5">
                            <FileBadge className="w-3.5 h-3.5 text-indigo-700" />
                            <span>Asistente Oficial RENAPO / SAT</span>
                          </p>
                          <p className="text-[11px] text-indigo-800/85">
                            Genera tu estructura oficial de CURP y RFC a partir de tus datos y edita tu homoclave si lo requieres.
                          </p>
                        </div>
                        <button
                          type="button"
                          onClick={handleAutoGenerateFiscal}
                          className="inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded-xl bg-indigo-900 hover:bg-indigo-800 text-white text-[11px] font-extrabold shrink-0 shadow-sm transition-colors"
                        >
                          <Sparkles className="w-3.5 h-3.5 text-amber-300" />
                          <span>Calcular CURP / RFC</span>
                        </button>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                        <div className="space-y-1">
                          <label
                            htmlFor="reg-entidad"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Entidad Federativa *
                          </label>
                          <select
                            id="reg-entidad"
                            value={entidadCodigo}
                            onChange={(e) => setEntidadCodigo(e.target.value)}
                            className="w-full px-2.5 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                          >
                            {ESTADOS_MEXICO.map((st) => (
                              <option key={st.code} value={st.code}>
                                {st.name} ({st.code})
                              </option>
                            ))}
                          </select>
                        </div>

                        <div className="space-y-1">
                          <label htmlFor="reg-curp" className="text-[11px] font-bold text-slate-700">
                            CURP (18 caracteres) *
                          </label>
                          <div className="relative">
                            <input
                              id="reg-curp"
                              type="text"
                              maxLength={18}
                              value={curp}
                              onChange={(e) =>
                                setCurp(
                                  e.target.value
                                    .toUpperCase()
                                    .replace(/[^A-Z0-9]/g, '')
                                    .slice(0, 18)
                                )
                              }
                              placeholder="XXXX000000HDFXXX00"
                              className={`w-full pl-3 pr-7 py-2 bg-slate-50 border rounded-xl text-[11px] font-mono font-bold text-slate-900 focus:outline-none focus:ring-2 ${
                                curpOk
                                  ? 'border-emerald-400 focus:ring-emerald-500'
                                  : curp
                                  ? 'border-amber-300 focus:ring-amber-400'
                                  : 'border-slate-200 focus:ring-blue-600'
                              }`}
                            />
                            {curpOk && (
                              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600 absolute right-2 top-2.5" />
                            )}
                          </div>
                        </div>

                        <div className="space-y-1">
                          <label htmlFor="reg-rfc" className="text-[11px] font-bold text-slate-700">
                            RFC con Homoclave (13) *
                          </label>
                          <div className="relative">
                            <input
                              id="reg-rfc"
                              type="text"
                              maxLength={13}
                              value={rfc}
                              onChange={(e) =>
                                setRfc(
                                  e.target.value
                                    .toUpperCase()
                                    .replace(/[^A-ZÑ&0-9]/g, '')
                                    .slice(0, 13)
                                )
                              }
                              placeholder="XXXX000000XXX"
                              className={`w-full pl-3 pr-7 py-2 bg-slate-50 border rounded-xl text-[11px] font-mono font-bold text-slate-900 focus:outline-none focus:ring-2 ${
                                rfcOk
                                  ? 'border-emerald-400 focus:ring-emerald-500'
                                  : rfc
                                  ? 'border-amber-300 focus:ring-amber-400'
                                  : 'border-slate-200 focus:ring-blue-600'
                              }`}
                            />
                            {rfcOk && (
                              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-600 absolute right-2 top-2.5" />
                            )}
                          </div>
                        </div>
                      </div>
                      {(curp && curpDiag) || (rfc && rfcDiag) ? (
                        <p className="text-[11px] text-amber-700 font-medium">
                          {curpDiag || rfcDiag}
                        </p>
                      ) : null}

                      {/* Economic Profile */}
                      <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5 pt-1 border-t border-slate-100">
                        <div className="space-y-1">
                          <label
                            htmlFor="reg-ocupacion"
                            className="text-[11px] font-bold text-slate-700 flex items-center gap-1"
                          >
                            <Briefcase className="w-3 h-3 text-slate-400" />
                            <span>Ocupación *</span>
                          </label>
                          <input
                            id="reg-ocupacion"
                            type="text"
                            maxLength={80}
                            value={ocupacion}
                            onChange={(e) => setOcupacion(e.target.value)}
                            placeholder="Ingeniero, Estudiante..."
                            className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                          />
                        </div>
                        <div className="space-y-1">
                          <label
                            htmlFor="reg-empresa"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Empresa / Institución *
                          </label>
                          <input
                            id="reg-empresa"
                            type="text"
                            maxLength={100}
                            value={empresa}
                            onChange={(e) => setEmpresa(e.target.value)}
                            placeholder="Nombre de empresa"
                            className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                          />
                        </div>
                        <div className="space-y-1">
                          <label
                            htmlFor="reg-ingreso"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Ingreso mensual MXN *
                          </label>
                          <input
                            id="reg-ingreso"
                            type="number"
                            min={1000}
                            step="500"
                            value={ingresoMensual}
                            onChange={(e) => setIngresoMensual(e.target.value)}
                            placeholder="18000"
                            className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-mono font-bold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                          />
                        </div>
                      </div>

                      {/* Address */}
                      <div className="space-y-2.5 pt-1 border-t border-slate-100">
                        <p className="text-[11px] font-extrabold uppercase tracking-wider text-slate-500 flex items-center gap-1">
                          <MapPin className="w-3.5 h-3.5 text-blue-700" />
                          <span>Domicilio del Titular</span>
                        </p>
                        <div className="grid grid-cols-1 sm:grid-cols-12 gap-2.5">
                          <div className="sm:col-span-6 space-y-1">
                            <label
                              htmlFor="reg-calle"
                              className="text-[11px] font-bold text-slate-700"
                            >
                              Calle y Avenida *
                            </label>
                            <input
                              id="reg-calle"
                              type="text"
                              maxLength={150}
                              value={calle}
                              onChange={(e) => setCalle(e.target.value)}
                              placeholder="Av. Paseo de la Reforma"
                              className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                            />
                          </div>
                          <div className="sm:col-span-3 space-y-1">
                            <label
                              htmlFor="reg-num-ext"
                              className="text-[11px] font-bold text-slate-700"
                            >
                              Núm. Ext. *
                            </label>
                            <input
                              id="reg-num-ext"
                              type="text"
                              maxLength={15}
                              value={numeroExterior}
                              onChange={(e) => setNumeroExterior(e.target.value)}
                              placeholder="250"
                              className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                            />
                          </div>
                          <div className="sm:col-span-3 space-y-1">
                            <label
                              htmlFor="reg-num-int"
                              className="text-[11px] font-bold text-slate-700"
                            >
                              Núm. Int.
                            </label>
                            <input
                              id="reg-num-int"
                              type="text"
                              maxLength={15}
                              value={numeroInterior}
                              onChange={(e) => setNumeroInterior(e.target.value)}
                              placeholder="Opcional"
                              className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                            />
                          </div>
                        </div>

                        <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                          <div className="space-y-1">
                            <label
                              htmlFor="reg-colonia"
                              className="text-[11px] font-bold text-slate-700"
                            >
                              Colonia *
                            </label>
                            <input
                              id="reg-colonia"
                              type="text"
                              maxLength={80}
                              value={colonia}
                              onChange={(e) => setColonia(e.target.value)}
                              placeholder="Juárez"
                              className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                            />
                          </div>
                          <div className="space-y-1">
                            <label
                              htmlFor="reg-cp"
                              className="text-[11px] font-bold text-slate-700"
                            >
                              Código Postal (5 díg) *
                            </label>
                            <input
                              id="reg-cp"
                              type="text"
                              inputMode="numeric"
                              maxLength={5}
                              value={codigoPostal}
                              onChange={(e) =>
                                setCodigoPostal(e.target.value.replace(/\D/g, '').slice(0, 5))
                              }
                              placeholder="06600"
                              className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-mono font-bold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                            />
                          </div>
                          <div className="space-y-1">
                            <label
                              htmlFor="reg-municipio"
                              className="text-[11px] font-bold text-slate-700"
                            >
                              Municipio / Alcaldía *
                            </label>
                            <input
                              id="reg-municipio"
                              type="text"
                              maxLength={80}
                              value={municipio}
                              onChange={(e) => setMunicipio(e.target.value)}
                              placeholder="Cuauhtémoc"
                              className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                            />
                          </div>
                        </div>
                      </div>

                      <div className="flex items-center justify-between gap-3 pt-1">
                        <button
                          type="button"
                          onClick={() => {
                            setError(null);
                            setRegStep(1);
                          }}
                          className="px-4 py-2.5 rounded-xl border border-slate-200 text-slate-700 hover:bg-slate-100 text-xs font-bold inline-flex items-center gap-1.5"
                        >
                          <ArrowLeft className="w-3.5 h-3.5" />
                          <span>Anterior</span>
                        </button>
                        <button
                          type="submit"
                          className="flex-1 py-2.5 px-4 bg-gradient-to-r from-blue-950 to-blue-700 hover:from-blue-900 hover:to-blue-600 text-white font-extrabold text-xs sm:text-sm rounded-xl shadow-md flex items-center justify-center gap-2 transition-all"
                        >
                          <span>Continuar a Contacto y Acceso</span>
                          <ArrowRight className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                  )}

                  {/* STEP 3: CONTACT & CREDENTIALS */}
                  {regStep === 3 && (
                    <div className="space-y-3.5 animate-in fade-in">
                      <div className="space-y-1">
                        <label htmlFor="reg-correo" className="text-[11px] font-bold text-slate-700">
                          Correo electrónico real (para 2FA y comprobantes) *
                        </label>
                        <div className="relative">
                          <Mail className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-3 pointer-events-none" />
                          <input
                            id="reg-correo"
                            type="email"
                            value={correo}
                            onChange={(e) => setCorreo(e.target.value)}
                            placeholder="tucorreo@gmail.com"
                            className="w-full pl-9 pr-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                            disabled={isLoading}
                          />
                        </div>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <div className="space-y-1">
                          <label
                            htmlFor="reg-telefono"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Teléfono móvil México (10 dígitos) *
                          </label>
                          <div className="relative">
                            <Phone className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-3 pointer-events-none" />
                            <input
                              id="reg-telefono"
                              type="tel"
                              inputMode="numeric"
                              maxLength={10}
                              value={telefonoMovil}
                              onChange={(e) =>
                                setTelefonoMovil(e.target.value.replace(/\D/g, '').slice(0, 10))
                              }
                              placeholder="5512345678"
                              className={`w-full pl-9 pr-8 py-2.5 bg-slate-50 border rounded-xl text-xs font-mono text-slate-900 focus:outline-none focus:ring-2 ${
                                phoneOk
                                  ? 'border-emerald-400 focus:ring-emerald-500'
                                  : telefonoMovil
                                  ? 'border-red-300 focus:ring-red-400'
                                  : 'border-slate-200 focus:ring-blue-600'
                              }`}
                              disabled={isLoading}
                            />
                            {phoneOk && (
                              <CheckCircle2 className="w-4 h-4 text-emerald-600 absolute right-2.5 top-2.5" />
                            )}
                          </div>
                          {telefonoMovil && phoneDiag && (
                            <p className="text-[11px] text-red-600 font-medium">{phoneDiag}</p>
                          )}
                        </div>

                        <div className="space-y-1">
                          <label
                            htmlFor="reg-tel-alt"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Teléfono alternativo (opcional)
                          </label>
                          <input
                            id="reg-tel-alt"
                            type="tel"
                            inputMode="numeric"
                            maxLength={10}
                            value={telefonoAlternativo}
                            onChange={(e) =>
                              setTelefonoAlternativo(e.target.value.replace(/\D/g, '').slice(0, 10))
                            }
                            placeholder="Opcional 10 dígitos"
                            className="w-full px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-mono text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                            disabled={isLoading}
                          />
                        </div>
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
                          className="w-full px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-mono text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                          disabled={isLoading}
                        />
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <div className="space-y-1">
                          <label
                            htmlFor="reg-contrasena"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Contraseña *
                          </label>
                          <div className="relative">
                            <input
                              id="reg-contrasena"
                              type={showRegPassword ? 'text' : 'password'}
                              value={regContrasena}
                              onChange={(e) => setRegContrasena(e.target.value)}
                              placeholder="Mayús, minús y número"
                              className="w-full pl-3 pr-9 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
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
                            className="w-full px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                            disabled={isLoading}
                          />
                        </div>
                      </div>

                      {regContrasena && (
                        <div className="space-y-1.5">
                          <div className="grid grid-cols-4 gap-1.5 h-1.5">
                            {[1, 2, 3, 4].map((lvl) => (
                              <div
                                key={lvl}
                                className={`rounded-full transition-colors ${
                                  pwdStrength >= lvl
                                    ? pwdStrength === 4
                                      ? 'bg-emerald-500'
                                      : 'bg-amber-400'
                                    : 'bg-slate-200'
                                }`}
                              />
                            ))}
                          </div>
                          <div className="grid grid-cols-2 sm:grid-cols-4 gap-1.5">
                            {pwdRules.map((rule) => (
                              <div
                                key={rule.label}
                                className={`px-2 py-1 rounded-lg text-[10px] font-bold flex items-center gap-1 border ${
                                  rule.ok
                                    ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                                    : 'bg-slate-50 text-slate-400 border-slate-200'
                                }`}
                              >
                                <CheckCircle2
                                  className={`w-3 h-3 ${
                                    rule.ok ? 'text-emerald-600' : 'text-slate-300'
                                  }`}
                                />
                                <span>{rule.label}</span>
                              </div>
                            ))}
                          </div>
                        </div>
                      )}

                      <div className="flex items-center justify-between gap-3 pt-1">
                        <button
                          type="button"
                          disabled={isLoading}
                          onClick={() => {
                            setError(null);
                            setRegStep(2);
                          }}
                          className="px-4 py-2.5 rounded-xl border border-slate-200 text-slate-700 hover:bg-slate-100 text-xs font-bold inline-flex items-center gap-1.5 disabled:opacity-50"
                        >
                          <ArrowLeft className="w-3.5 h-3.5" />
                          <span>Anterior</span>
                        </button>
                        <button
                          type="submit"
                          disabled={isLoading}
                          className="flex-1 py-3 px-4 bg-gradient-to-r from-emerald-700 to-teal-600 hover:from-emerald-600 hover:to-teal-500 text-white font-extrabold text-xs sm:text-sm rounded-xl shadow-lg shadow-emerald-900/20 flex items-center justify-center gap-2 transition-all disabled:opacity-60"
                        >
                          {isLoading ? (
                            <>
                              <Loader2 className="w-4 h-4 animate-spin" />
                              <span>Enviando código 2FA...</span>
                            </>
                          ) : (
                            <>
                              <Mail className="w-4 h-4" />
                              <span>Enviar Código 2FA al Correo</span>
                            </>
                          )}
                        </button>
                      </div>
                    </div>
                  )}

                  {/* STEP 4: 2FA EMAIL OTP VERIFICATION + DOSSIER SUMMARY */}
                  {regStep === 4 && (
                    <div className="space-y-4 animate-in fade-in">
                      {/* Executive KYC Dossier Summary */}
                      <div className="p-3.5 rounded-2xl bg-slate-50 border border-slate-200 grid grid-cols-2 sm:grid-cols-3 gap-2 text-[11px]">
                        <div>
                          <span className="text-slate-400 font-bold block">Titular</span>
                          <span className="font-extrabold text-slate-900 truncate block">
                            {[nombre, segundoNombre, apellidoPaterno, apellidoMaterno]
                              .filter(Boolean)
                              .join(' ')}
                          </span>
                        </div>
                        <div>
                          <span className="text-slate-400 font-bold block">CURP</span>
                          <span className="font-mono font-bold text-blue-900">{curp}</span>
                        </div>
                        <div>
                          <span className="text-slate-400 font-bold block">RFC</span>
                          <span className="font-mono font-bold text-blue-900">{rfc}</span>
                        </div>
                      </div>

                      <div className="p-3.5 rounded-2xl bg-blue-50 border border-blue-200 space-y-2">
                        <div className="flex items-start justify-between gap-2">
                          <div className="flex items-start gap-2.5">
                            <Mail className="w-4 h-4 text-blue-700 shrink-0 mt-0.5" />
                            <div className="text-xs text-blue-950">
                              <p className="font-bold">Código 2FA enviado a:</p>
                              <p className="font-mono font-semibold text-blue-700 break-all">
                                {otpSentTo || correo.trim()}
                              </p>
                            </div>
                          </div>
                          <span
                            className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-mono font-bold shrink-0 ${
                              otpCountdown > 0
                                ? 'bg-white text-blue-800 border border-blue-200'
                                : 'bg-amber-100 text-amber-800 border border-amber-300'
                            }`}
                          >
                            <Clock className="w-3 h-3" />
                            {otpCountdown > 0 ? formatCountdown(otpCountdown) : 'Expirado'}
                          </span>
                        </div>
                        <p className="text-[11px] text-blue-800/80">
                          Revisa tu bandeja de entrada o carpeta de spam. Nunca compartas este código.
                        </p>
                      </div>

                      {otpFeedback && (
                        <div className="p-3 rounded-xl bg-emerald-50 border border-emerald-200 flex items-center gap-2 text-emerald-800 text-xs font-medium">
                          <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                          <span>{otpFeedback}</span>
                        </div>
                      )}

                      <div className="space-y-1.5">
                        <label
                          htmlFor="reg-codigo-2fa"
                          className="text-xs font-bold text-slate-700 uppercase tracking-wider flex items-center gap-1.5"
                        >
                          <KeyRound className="w-3.5 h-3.5 text-blue-700" />
                          <span>Código de verificación (6 dígitos) *</span>
                        </label>
                        <input
                          id="reg-codigo-2fa"
                          type="text"
                          inputMode="numeric"
                          maxLength={6}
                          value={codigoVerificacion}
                          onChange={(e) =>
                            setCodigoVerificacion(e.target.value.replace(/\D/g, '').slice(0, 6))
                          }
                          placeholder="000000"
                          autoComplete="one-time-code"
                          className="w-full px-4 py-3 bg-slate-50 border border-slate-300 rounded-xl text-center text-xl font-mono font-extrabold tracking-[0.45em] text-slate-900 focus:outline-none focus:ring-2 focus:ring-emerald-600 focus:bg-white transition-all"
                          disabled={isLoading}
                        />
                      </div>

                      <button
                        type="submit"
                        disabled={isLoading || codigoVerificacion.length < 6}
                        className="w-full py-3 bg-gradient-to-r from-emerald-700 to-teal-600 hover:from-emerald-600 hover:to-teal-500 text-white font-extrabold text-sm rounded-xl shadow-lg shadow-emerald-900/20 flex items-center justify-center gap-2 transition-all active:scale-[0.99] disabled:opacity-60"
                      >
                        {isLoading ? (
                          <>
                            <Loader2 className="w-5 h-5 animate-spin" />
                            <span>Verificando y aperturando cuenta...</span>
                          </>
                        ) : (
                          <>
                            <UserPlus className="w-5 h-5" />
                            <span>Verificar y Crear Cuenta</span>
                          </>
                        )}
                      </button>

                      <div className="flex items-center justify-between gap-2 pt-1">
                        <button
                          type="button"
                          disabled={isLoading}
                          onClick={() => {
                            setRegStep(3);
                            setCodigoVerificacion('');
                            setError(null);
                            setOtpFeedback(null);
                          }}
                          className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold text-slate-600 hover:text-slate-900 hover:bg-slate-100 rounded-xl transition-colors disabled:opacity-50"
                        >
                          <ArrowLeft className="w-3.5 h-3.5" />
                          <span>Editar datos</span>
                        </button>
                        <button
                          type="button"
                          disabled={isLoading}
                          onClick={() => void handleSendRegCode(true)}
                          className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-bold text-blue-700 hover:text-blue-900 hover:bg-blue-50 rounded-xl transition-colors disabled:opacity-50"
                        >
                          <RefreshCw className="w-3.5 h-3.5" />
                          <span>Reenviar código</span>
                        </button>
                      </div>
                    </div>
                  )}
                </form>
              )}
            </div>

            {/* Footer Trust Pills */}
            <div className="pt-4 border-t border-slate-100 grid grid-cols-3 gap-2 text-center">
              <div className="p-2 rounded-xl bg-slate-50 border border-slate-100">
                <p className="text-[10px] font-extrabold text-slate-700">KYC Oficial</p>
                <p className="text-[10px] text-slate-500">CURP &amp; RFC</p>
              </div>
              <div className="p-2 rounded-xl bg-slate-50 border border-slate-100">
                <p className="text-[10px] font-extrabold text-slate-700">2FA por Correo</p>
                <p className="text-[10px] text-slate-500">Token OTP 5 min</p>
              </div>
              <div className="p-2 rounded-xl bg-slate-50 border border-slate-100">
                <p className="text-[10px] font-extrabold text-slate-700">GestoPago</p>
                <p className="text-[10px] text-slate-500">Folio ID_TX Real</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <p className="text-center text-[11px] text-blue-200/55 pt-4 z-10">
        Protegido con cifrado BCrypt de grado bancario, expediente KYC RENAPO/SAT, validación IFT y verificación en dos pasos (2FA)
      </p>
    </div>
  );
};
