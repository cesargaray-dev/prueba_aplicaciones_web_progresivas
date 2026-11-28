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
  Sparkles,
  FileBadge,
  MapPin,
  Briefcase,
  Calendar,
  Sun,
  Moon,
  Award,
  CreditCard,
  Smartphone,
} from 'lucide-react';
import { useAuth } from '../context/useAuth';
import { authApi } from '../api/authApi';
import { catalogApi } from '../api/catalogApi';
import { ApiError } from '../api/client';
import type { TotpSetupResponse } from '../types/auth';
import { TotpSetupCard } from '../components/TotpSetupCard';
import {
  CATALOGO_ESTADOS_SEPOMEX,
  CATALOGO_OCUPACIONES,
  TABULADOR_PROFESIONES,
  TABULADOR_SUELDOS,
  TIPOS_VIALIDAD,
  resolverCodigoPostalMexicano,
} from '../data/mexicoCatalogs';
import {
  ESTADOS_MEXICO,
  diagnosticarApellido,
  diagnosticarCalle,
  diagnosticarCodigoPostal,
  diagnosticarColonia,
  diagnosticarCurp,
  diagnosticarEmpresa,
  diagnosticarNombre,
  diagnosticarNumeroDomicilio,
  diagnosticarRfc,
  diagnosticarTelefono,
  esContrasenaSegura,
  esCorreoPlaceholder,
  esCorreoValido,
  esCurpValida,
  esMayorDeEdad,
  esRfcValido,
  esTelefonoMexicanoValido,
  esUsuarioValido,
  generarCurpYRfcSugeridos,
  getMaxBirthDateIso,
} from '../utils/validation';

type AuthMode = 'login' | 'register';
type RegisterStep = 1 | 2 | 3 | 4 | 5 | 6;

const DUPLICATE_EMAIL_MESSAGE =
  'Este correo electrónico ya está en uso en otra cuenta. Inicia sesión o utiliza otro correo.';
const DUPLICATE_USUARIO_MESSAGE =
  'Este nombre de usuario ya está en uso en otra cuenta. Elige uno distinto.';

const STEP_META: { step: RegisterStep; shortLabel: string; title: string; subtitle: string }[] = [
  {
    step: 1,
    shortLabel: 'Personales',
    title: 'Datos Personales',
    subtitle:
      'Ingresa tu nombre legal completo, fecha y entidad de nacimiento tal como aparecen en tu identificación oficial.',
  },
  {
    step: 2,
    shortLabel: 'Fiscales',
    title: 'Datos Fiscales',
    subtitle:
      'Valida tu clave CURP de 18 caracteres y tu RFC con homoclave de 13 caracteres ante RENAPO y SAT.',
  },
  {
    step: 3,
    shortLabel: 'Ocupación',
    title: 'Ocupación e Ingresos',
    subtitle:
      'Selecciona tu ocupación del catálogo oficial, profesión del tabulador CNBV, institución e ingreso mensual.',
  },
  {
    step: 4,
    shortLabel: 'Domicilio',
    title: 'Domicilio SEPOMEX',
    subtitle:
      'Ingresa tu código postal de 5 dígitos para autocompletar estado, municipio y catálogo oficial de colonias.',
  },
  {
    step: 5,
    shortLabel: 'Contacto',
    title: 'Contacto y Acceso',
    subtitle:
      'Configura tu correo electrónico único, teléfono móvil de 10 dígitos y credenciales seguras de acceso.',
  },
  {
    step: 6,
    shortLabel: 'Autenticador',
    title: 'Autenticador de Google (TOTP 30s)',
    subtitle:
      'Escanea el código QR en Google Authenticator e ingresa el código dinámico de 6 dígitos para activar tu cuenta.',
  },
];

const OCUPACION_EMPRESA_SUGERIDA: Record<string, string> = {
  'Profesionista independiente': 'INDEPENDIENTE',
  'Trabajador(a) por cuenta propia / Oficio': 'ACTIVIDAD PROPIA',
  Estudiante: 'INSTITUCIÓN EDUCATIVA',
  'Labores del hogar': 'HOGAR',
  'Jubilado(a) / Pensionado(a)': 'PENSIONADO',
  'Desempleado(a) / En búsqueda de empleo': 'SIN EMPLEO ACTUAL',
};

export const LoginPage: React.FC = () => {
  const { login, register, isLoading, sessionExpiredMessage, clearSessionExpired } = useAuth();

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

  // Register Step 1: Datos Personales
  const [regStep, setRegStep] = useState<RegisterStep>(1);
  const [nombre, setNombre] = useState('');
  const [segundoNombre, setSegundoNombre] = useState('');
  const [apellidoPaterno, setApellidoPaterno] = useState('');
  const [apellidoMaterno, setApellidoMaterno] = useState('');
  const [fechaNacimiento, setFechaNacimiento] = useState('1998-05-15');
  const [sexo, setSexo] = useState<'HOMBRE' | 'MUJER'>('HOMBRE');
  const [nacionalidad, setNacionalidad] = useState('MEXICANA');
  const [estadoCivil, setEstadoCivil] = useState('SOLTERO');
  const [entidadCodigo, setEntidadCodigo] = useState('DF');

  // Register Step 2: Datos Fiscales (CURP & RFC + live availability)
  const [curp, setCurp] = useState('');
  const [rfc, setRfc] = useState('');
  const [curpTakenMsg, setCurpTakenMsg] = useState<string | null>(null);
  const [rfcTakenMsg, setRfcTakenMsg] = useState<string | null>(null);
  const [checkingFiscal, setCheckingFiscal] = useState(false);

  // Register Step 3: Ocupación e Ingresos
  const [ocupacion, setOcupacion] = useState<string>(CATALOGO_OCUPACIONES[0]);
  const [profesion, setProfesion] = useState<string>(TABULADOR_PROFESIONES[0].label);
  const [empresa, setEmpresa] = useState('');
  const [ingresoMensual, setIngresoMensual] = useState(
    String(TABULADOR_SUELDOS[2].montoRepresentativo)
  );

  // Register Step 4: Domicilio SEPOMEX
  const [tipoVialidad, setTipoVialidad] = useState('Calle');
  const [calle, setCalle] = useState('');
  const [numeroExterior, setNumeroExterior] = useState('');
  const [numeroInterior, setNumeroInterior] = useState('');
  const [codigoPostal, setCodigoPostal] = useState('');
  const [estadoDomicilioCodigo, setEstadoDomicilioCodigo] = useState('');
  const [estadoDomicilioNombre, setEstadoDomicilioNombre] = useState('');
  const [municipio, setMunicipio] = useState('');
  const [coloniasDelCp, setColoniasDelCp] = useState<string[]>([]);
  const [colonia, setColonia] = useState('');
  const [cpLoading, setCpLoading] = useState(false);
  const [cpApiError, setCpApiError] = useState<string | null>(null);

  // Register Step 5: Contacto y Acceso
  const [correo, setCorreo] = useState('');
  const [correoTakenMsg, setCorreoTakenMsg] = useState<string | null>(null);
  const [checkingCorreo, setCheckingCorreo] = useState(false);
  const [telefonoMovil, setTelefonoMovil] = useState('');
  const [telefonoAlternativo, setTelefonoAlternativo] = useState('');
  const [regUsuario, setRegUsuario] = useState('');
  const [usuarioTakenMsg, setUsuarioTakenMsg] = useState<string | null>(null);
  const [checkingUsuario, setCheckingUsuario] = useState(false);
  const [regContrasena, setRegContrasena] = useState('');
  const [confirmarContrasena, setConfirmarContrasena] = useState('');
  const [showRegPassword, setShowRegPassword] = useState(false);

  // Register Step 6: Autenticador de Google (TOTP 30s)
  const [totpSetup, setTotpSetup] = useState<TotpSetupResponse | null>(null);
  const [totpSetupLoading, setTotpSetupLoading] = useState(false);
  const [codigoTotp, setCodigoTotp] = useState('');
  const [totpFeedback, setTotpFeedback] = useState<string | null>(null);

  // Per-field touched state for immediate onBlur & live validation
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const markTouched = (field: string) =>
    setTouched((prev) => (prev[field] ? prev : { ...prev, [field]: true }));
  const markManyTouched = (fields: string[]) =>
    setTouched((prev) => {
      const next = { ...prev };
      for (const f of fields) next[f] = true;
      return next;
    });

  const [error, setError] = useState<string | null>(null);

  const selectedStateName =
    estadoDomicilioNombre ||
    CATALOGO_ESTADOS_SEPOMEX.find((e) => e.codigoRenapo === estadoDomicilioCodigo)?.nombre ||
    '';

  // Live per-field diagnostics
  const nombreErr = diagnosticarNombre(nombre, 'El nombre', true);
  const segundoNombreErr = diagnosticarNombre(segundoNombre, 'El segundo nombre', false);
  const apPaternoErr = diagnosticarApellido(apellidoPaterno, 'El apellido paterno', true);
  const apMaternoErr = diagnosticarApellido(apellidoMaterno, 'El apellido materno', true);
  const fechaNacErr = esMayorDeEdad(fechaNacimiento)
    ? null
    : 'Debes ingresar una fecha válida y ser mayor de 18 años.';

  const curpDiag = diagnosticarCurp(curp);
  const curpOk = esCurpValida(curp) && !curpTakenMsg;
  const rfcDiag = diagnosticarRfc(rfc);
  const rfcOk = esRfcValido(rfc) && !rfcTakenMsg;

  const ocupacionErr = !ocupacion.trim()
    ? 'Selecciona tu ocupación del catálogo oficial.'
    : null;
  const profesionErr = !profesion.trim()
    ? 'Selecciona tu profesión del tabulador.'
    : null;
  const empresaErr = diagnosticarEmpresa(empresa);

  const cpDiag = cpApiError || diagnosticarCodigoPostal(codigoPostal);
  const calleErr = diagnosticarCalle(calle);
  const numExtErr = diagnosticarNumeroDomicilio(numeroExterior, true, 'El número exterior');
  const numIntErr = diagnosticarNumeroDomicilio(numeroInterior, false, 'El número interior');
  const coloniaErr = !colonia.trim()
    ? 'Selecciona una colonia perteneciente a tu código postal.'
    : diagnosticarColonia(colonia);
  const municipioErr =
    municipio.trim().length < 2
      ? 'Ingresa un código postal válido para autocompletar el municipio.'
      : null;

  const correoTrim = correo.trim();
  const correoFormatErr = !correoTrim
    ? 'El correo electrónico es obligatorio.'
    : !esCorreoValido(correoTrim)
    ? 'Ingresa un correo electrónico con formato válido (ej. nombre@gmail.com).'
    : esCorreoPlaceholder(correoTrim)
    ? 'Usa un correo electrónico real (no dominios de ejemplo).'
    : null;
  const phoneDiag = diagnosticarTelefono(telefonoMovil);
  const phoneOk = esTelefonoMexicanoValido(telefonoMovil);
  const telAltErr = telefonoAlternativo.trim() ? diagnosticarTelefono(telefonoAlternativo) : null;
  const regUsuarioFormatErr = !regUsuario.trim()
    ? 'El nombre de usuario es obligatorio (4 a 50 caracteres).'
    : !esUsuarioValido(regUsuario)
    ? 'El usuario debe tener entre 4 y 50 caracteres (letras, números, . _ -).'
    : null;
  const regContrasenaErr = !regContrasena
    ? 'Ingresa una contraseña segura.'
    : !esContrasenaSegura(regContrasena)
    ? 'Mínimo 8 caracteres con al menos 1 mayúscula, 1 minúscula y 1 número.'
    : null;
  const confirmarContrasenaErr = !confirmarContrasena
    ? 'Confirma tu contraseña.'
    : regContrasena !== confirmarContrasena
    ? 'Las contraseñas no coinciden.'
    : null;

  const hasInvalidNameChars = (v: string) => /[^A-Za-zÁÉÍÓÚÜáéíóúüÑñ\s]/.test(v);
  const showFieldError = (field: string, errMsg: string | null, immediateTrigger = false) =>
    Boolean(errMsg && (touched[field] || immediateTrigger));

  const fieldInputClass = (hasErr: boolean, isValid = false, extra = '') =>
    `w-full px-3 py-2.5 bg-slate-50 border rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 transition-colors ${
      hasErr
        ? 'border-red-400 focus:ring-red-400 bg-red-50/30'
        : isValid
        ? 'border-emerald-400 focus:ring-emerald-500'
        : 'border-slate-200 focus:ring-blue-600'
    } ${extra}`;

  const pwdRules = [
    { ok: regContrasena.length >= 8, label: '8+ caracteres' },
    { ok: /[A-Z]/.test(regContrasena), label: 'Mayúscula' },
    { ok: /[a-z]/.test(regContrasena), label: 'Minúscula' },
    { ok: /\d/.test(regContrasena), label: 'Número' },
  ];
  const pwdStrength = pwdRules.filter((r) => r.ok).length;

  const handleModeChange = (nextMode: AuthMode) => {
    setMode(nextMode);
    setError(null);
    setTouched({});
    setRegStep(1);
    setTotpSetup(null);
    setCodigoTotp('');
    setTotpFeedback(null);
  };

  // Availability check helpers (CURP, RFC, Correo, Usuario)
  const checkFiscalAvailability = async (params: {
    curp?: string;
    rfc?: string;
  }): Promise<boolean> => {
    const cleanCurp = params.curp?.trim().toUpperCase();
    const cleanRfc = params.rfc?.trim().toUpperCase();
    if (!cleanCurp && !cleanRfc) return true;

    setCheckingFiscal(true);
    try {
      const res = await authApi.verificarDisponibilidadRegistro({
        curp: cleanCurp,
        rfc: cleanRfc,
      });
      let available = true;
      if (cleanCurp && res.curpDisponible === false) {
        setCurpTakenMsg(
          res.mensajes?.curp || 'Esta CURP ya está registrada en otra cuenta.'
        );
        available = false;
      }
      if (cleanRfc && res.rfcDisponible === false) {
        setRfcTakenMsg(
          res.mensajes?.rfc || 'Este RFC ya está registrado en otra cuenta.'
        );
        available = false;
      }
      return available;
    } catch (err: unknown) {
      if (err instanceof ApiError && err.status === 0) {
        return true;
      }
      if (err instanceof ApiError && (err.status === 409 || err.status === 400)) {
        if (/curp/i.test(err.message)) {
          setCurpTakenMsg(err.message);
          return false;
        }
        if (/rfc/i.test(err.message)) {
          setRfcTakenMsg(err.message);
          return false;
        }
      }
      return true;
    } finally {
      setCheckingFiscal(false);
    }
  };

  const checkContactAvailability = async (params: {
    correo?: string;
    usuario?: string;
  }): Promise<boolean> => {
    const cleanCorreo = params.correo?.trim();
    const cleanUsr = params.usuario?.trim();
    if (!cleanCorreo && !cleanUsr) return true;

    if (cleanCorreo) setCheckingCorreo(true);
    if (cleanUsr) setCheckingUsuario(true);
    try {
      const res = await authApi.verificarDisponibilidadRegistro({
        correo: cleanCorreo,
        usuario: cleanUsr,
      });
      let available = true;
      if (cleanCorreo && res.correoDisponible === false) {
        setCorreoTakenMsg(DUPLICATE_EMAIL_MESSAGE);
        available = false;
      }
      if (cleanUsr && res.usuarioDisponible === false) {
        setUsuarioTakenMsg(res.mensajes?.usuario || DUPLICATE_USUARIO_MESSAGE);
        available = false;
      }
      return available;
    } catch (err: unknown) {
      if (err instanceof ApiError && err.status === 0) {
        return true;
      }
      if (err instanceof ApiError && err.status === 409) {
        setCorreoTakenMsg(DUPLICATE_EMAIL_MESSAGE);
        return false;
      }
      return true;
    } finally {
      if (cleanCorreo) setCheckingCorreo(false);
      if (cleanUsr) setCheckingUsuario(false);
    }
  };

  const handleCodigoPostalChange = (rawVal: string) => {
    const clean = rawVal.replace(/\D/g, '').slice(0, 5);
    setCodigoPostal(clean);
    setColonia('');
    setCpApiError(null);

    if (clean.length < 5) {
      setEstadoDomicilioCodigo('');
      setEstadoDomicilioNombre('');
      setMunicipio('');
      setColoniasDelCp([]);
      setCpLoading(false);
      return;
    }

    markTouched('codigoPostal');
    const localRes = resolverCodigoPostalMexicano(clean);
    if (!localRes.valido) {
      setEstadoDomicilioCodigo('');
      setEstadoDomicilioNombre('');
      setMunicipio('');
      setColoniasDelCp([]);
      return;
    }

    setEstadoDomicilioCodigo(localRes.codigoRenapo || 'DF');
    setEstadoDomicilioNombre(localRes.estadoNombre || '');
    setMunicipio(localRes.municipioSugerido || '');
    setColoniasDelCp(localRes.coloniasSugeridas);
    setCpLoading(true);

    catalogApi
      .consultarCodigoPostal(clean)
      .then((sepomex) => {
        if (!sepomex.valido) {
          setEstadoDomicilioCodigo('');
          setEstadoDomicilioNombre('');
          setMunicipio('');
          setColoniasDelCp([]);
          setCpApiError(
            sepomex.mensaje ||
              `El código postal ${clean} no existe en el catálogo oficial SEPOMEX.`
          );
          return;
        }
        setEstadoDomicilioCodigo(sepomex.codigoEstado || localRes.codigoRenapo || 'DF');
        setEstadoDomicilioNombre(sepomex.estado || localRes.estadoNombre || '');
        setMunicipio(sepomex.municipio || localRes.municipioSugerido || '');
        setColoniasDelCp(sepomex.colonias);
        setColonia('');
      })
      .catch(() => {
        // Offline SEPOMEX fallback preserved
      })
      .finally(() => {
        setCpLoading(false);
      });
  };

  const handleOcupacionCatalogChange = (nuevaOcupacion: string) => {
    setOcupacion(nuevaOcupacion);
    const sugerida = OCUPACION_EMPRESA_SUGERIDA[nuevaOcupacion];
    if (sugerida && !empresa.trim()) {
      setEmpresa(sugerida);
    }
  };

  const handleProfesionChange = (nuevaProfesionLabel: string) => {
    setProfesion(nuevaProfesionLabel);
    const item = TABULADOR_PROFESIONES.find((p) => p.label === nuevaProfesionLabel);
    if (item?.empresaSugerida && !empresa.trim()) {
      setEmpresa(item.empresaSugerida);
    }
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
    setCurpTakenMsg(null);
    setRfcTakenMsg(null);
    void checkFiscalAvailability({ curp: generated.curp, rfc: generated.rfc });
  };

  const validateStep1 = (): string | null => {
    if (nombreErr) return nombreErr;
    if (segundoNombreErr) return segundoNombreErr;
    if (apPaternoErr) return apPaternoErr;
    if (apMaternoErr) return apMaternoErr;
    if (fechaNacErr) return fechaNacErr;
    return null;
  };

  const validateStep2 = (): string | null => {
    if (curpDiag) return curpDiag;
    if (curpTakenMsg) return curpTakenMsg;
    if (rfcDiag) return rfcDiag;
    if (rfcTakenMsg) return rfcTakenMsg;
    return null;
  };

  const validateStep3 = (): string | null => {
    if (ocupacionErr) return ocupacionErr;
    if (profesionErr) return profesionErr;
    if (empresaErr) return empresaErr;
    const ingresoNum = parseFloat(ingresoMensual);
    if (Number.isNaN(ingresoNum) || ingresoNum <= 0) {
      return 'Selecciona tu rango de ingreso mensual del tabulador.';
    }
    return null;
  };

  const validateStep4 = (): string | null => {
    if (cpDiag) return cpDiag;
    if (municipioErr) return municipioErr;
    if (coloniaErr) return coloniaErr;
    if (calleErr) return calleErr;
    if (numExtErr) return numExtErr;
    if (numIntErr) return numIntErr;
    return null;
  };

  const validateStep5 = (): string | null => {
    if (correoFormatErr) return correoFormatErr;
    if (correoTakenMsg) return correoTakenMsg;
    if (phoneDiag) return phoneDiag;
    if (telAltErr) return `Teléfono alternativo: ${telAltErr}`;
    if (regUsuarioFormatErr) return regUsuarioFormatErr;
    if (usuarioTakenMsg) return usuarioTakenMsg;
    if (regContrasenaErr) return regContrasenaErr;
    if (confirmarContrasenaErr) return confirmarContrasenaErr;
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

  const handleNextStep = async () => {
    setError(null);
    clearSessionExpired();

    if (regStep === 1) {
      markManyTouched([
        'nombre',
        'segundoNombre',
        'apellidoPaterno',
        'apellidoMaterno',
        'fechaNacimiento',
      ]);
      const err1 = validateStep1();
      if (err1) {
        setError(err1);
        return;
      }
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
          void checkFiscalAvailability({ curp: suggested.curp, rfc: suggested.rfc });
        }
      }
      setRegStep(2);
      return;
    }

    if (regStep === 2) {
      markManyTouched(['curp', 'rfc']);
      const err2 = validateStep2();
      if (err2) {
        if (err2 !== curpTakenMsg && err2 !== rfcTakenMsg) {
          setError(err2);
        }
        return;
      }
      const fiscalOk = await checkFiscalAvailability({ curp, rfc });
      if (!fiscalOk) {
        return;
      }
      setRegStep(3);
      return;
    }

    if (regStep === 3) {
      markManyTouched(['ocupacion', 'profesion', 'empresa', 'ingresoMensual']);
      const err3 = validateStep3();
      if (err3) {
        setError(err3);
        return;
      }
      setRegStep(4);
      return;
    }

    if (regStep === 4) {
      markManyTouched([
        'codigoPostal',
        'municipio',
        'colonia',
        'calle',
        'numeroExterior',
        'numeroInterior',
      ]);
      const err4 = validateStep4();
      if (err4) {
        setError(err4);
        return;
      }
      setRegStep(5);
    }
  };

  const handleProceedToTotp = async (isRegenerate = false) => {
    setError(null);
    setTotpFeedback(null);
    clearSessionExpired();

    const err1 = validateStep1();
    if (err1) {
      markManyTouched([
        'nombre',
        'segundoNombre',
        'apellidoPaterno',
        'apellidoMaterno',
        'fechaNacimiento',
      ]);
      setRegStep(1);
      setError(err1);
      return;
    }
    const err2 = validateStep2();
    if (err2) {
      markManyTouched(['curp', 'rfc']);
      setRegStep(2);
      if (err2 !== curpTakenMsg && err2 !== rfcTakenMsg) setError(err2);
      return;
    }
    const err3 = validateStep3();
    if (err3) {
      markManyTouched(['ocupacion', 'profesion', 'empresa', 'ingresoMensual']);
      setRegStep(3);
      setError(err3);
      return;
    }
    const err4 = validateStep4();
    if (err4) {
      markManyTouched([
        'codigoPostal',
        'municipio',
        'colonia',
        'calle',
        'numeroExterior',
        'numeroInterior',
      ]);
      setRegStep(4);
      setError(err4);
      return;
    }

    markManyTouched([
      'correo',
      'telefonoMovil',
      'telefonoAlternativo',
      'regUsuario',
      'regContrasena',
      'confirmarContrasena',
    ]);
    const err5 = validateStep5();
    if (err5) {
      setRegStep(5);
      if (err5 !== correoTakenMsg && err5 !== usuarioTakenMsg) {
        setError(err5);
      }
      return;
    }

    setTotpSetupLoading(true);
    try {
      const contactOk = await checkContactAvailability({
        correo: correoTrim,
        usuario: regUsuario.trim(),
      });
      if (!contactOk) {
        setRegStep(5);
        return;
      }

      const setup = await authApi.iniciarTotpRegistro({
        correo: correoTrim,
        usuario: regUsuario.trim(),
      });
      setTotpSetup(setup);
      setCodigoTotp('');
      setRegStep(6);
      if (isRegenerate) {
        setTotpFeedback('Se generó una nueva llave QR para Google Authenticator.');
      }
    } catch (err: unknown) {
      if (
        (err instanceof ApiError && err.status === 409) ||
        (err instanceof Error && /correo.*ya.*uso/i.test(err.message))
      ) {
        setCorreoTakenMsg(DUPLICATE_EMAIL_MESSAGE);
        setRegStep(5);
        return;
      }
      if (err instanceof ApiError && err.status === 400 && /usuario/i.test(err.message)) {
        setUsuarioTakenMsg(err.message);
        setRegStep(5);
        return;
      }
      const errorMsg =
        err instanceof Error
          ? err.message
          : 'No fue posible generar la configuración de Google Authenticator.';
      setError(errorMsg);
    } finally {
      setTotpSetupLoading(false);
    }
  };

  const buildCalleCompleta = (): string => {
    const cleanCalle = calle.trim();
    const startsWithPrefix =
      /^(calle|av\.?|avenida|blvd\.?|boulevard|calz\.?|calzada|priv\.?|privada|cerr\.?|cerrada|prol\.?|prolongaci[oó]n|cto\.?|circuito|andador|carr\.?|carretera|viaducto|retorno)\b/i.test(
        cleanCalle
      );
    return startsWithPrefix ? cleanCalle : `${tipoVialidad} ${cleanCalle}`.trim();
  };

  const handleRegisterSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (regStep === 1 || regStep === 2 || regStep === 3 || regStep === 4) {
      await handleNextStep();
      return;
    }
    if (regStep === 5) {
      await handleProceedToTotp(false);
      return;
    }

    setError(null);
    setTotpFeedback(null);
    clearSessionExpired();

    const cleanCode = codigoTotp.trim();
    if (!/^\d{6}$/.test(cleanCode)) {
      setError('Ingresa el código de 6 dígitos generado en Google Authenticator.');
      return;
    }
    if (!totpSetup?.secret) {
      setError('Genera primero tu código QR de Google Authenticator.');
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
        profesion: profesion.trim(),
        empresa: empresa.trim(),
        ingresoMensual: parseFloat(ingresoMensual) || 20000,
        calle: buildCalleCompleta(),
        numeroExterior: numeroExterior.trim(),
        numeroInterior: numeroInterior.trim() || undefined,
        colonia: colonia.trim(),
        codigoPostal: codigoPostal.trim(),
        municipio: municipio.trim(),
        estado: selectedStateName,
        pais: 'México',
        correo: correoTrim,
        telefonoMovil: telefonoMovil.trim(),
        telefonoAlternativo: telefonoAlternativo.trim() || undefined,
        usuario: regUsuario.trim(),
        contrasena: regContrasena,
        totpSecret: totpSetup.secret,
        codigoTotp: cleanCode,
      });
    } catch (err: unknown) {
      if (
        (err instanceof ApiError && err.status === 409) ||
        (err instanceof Error && /correo.*ya.*uso/i.test(err.message))
      ) {
        setCorreoTakenMsg(DUPLICATE_EMAIL_MESSAGE);
        setRegStep(5);
        return;
      }
      const errorMsg =
        err instanceof Error ? err.message : 'No fue posible completar el registro.';
      setError(errorMsg);
    }
  };

  const currentMeta = STEP_META[regStep - 1];
  const isBusy = isLoading || totpSetupLoading;

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
                  Pagos de servicios, recargas al instante y seguridad Google Authenticator.
                </h2>
                <p className="text-xs sm:text-sm text-blue-100/80 leading-relaxed">
                  Opera tus recibos CFE, agua, internet, TAG y tiempo aire con comprobantes oficiales PDF, leyenda regulatoria y validación TOTP en tiempo real.
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
                    TOTP 30s Activo
                  </span>
                </div>
                <div className="grid grid-cols-2 gap-2 pt-1 text-xs">
                  <div className="p-2.5 rounded-xl bg-white/5 border border-white/10">
                    <p className="text-[10px] text-blue-200/75">Apertura Digital</p>
                    <p className="text-sm font-black text-white mt-0.5">6 Pasos Guiados</p>
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
                    desc: 'Validación normativa RENAPO, SAT, SEPOMEX y catálogo oficial de ocupaciones.',
                  },
                  {
                    icon: Smartphone,
                    title: 'Google Authenticator (TOTP cada 30s)',
                    desc: 'Código dinámico de 6 dígitos sincronizado con tu celular para registro y pagos.',
                  },
                  {
                    icon: Award,
                    title: 'Trazabilidad Oficial GestoPago',
                    desc: 'Cada recibo incluye ID_TX, Número de Autorización, Leyenda Oficial y descarga PDF.',
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
                    <span className="text-slate-600 font-medium">
                      ¿Aún no eres titular en Banco Digital?
                    </span>
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
                  {/* 6-Step Visual Stepper: Title, Paso X de 6, Progress Bar & Step Pills */}
                  <div className="space-y-2.5">
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <h2 className="text-lg font-black text-slate-900">{currentMeta.title}</h2>
                        <p className="text-xs text-slate-500">{currentMeta.subtitle}</p>
                      </div>
                      <span className="px-2.5 py-1 rounded-full text-[10px] font-extrabold bg-blue-50 text-blue-800 border border-blue-200 shrink-0">
                        Paso {regStep} de 6
                      </span>
                    </div>

                    <div
                      role="progressbar"
                      aria-label="Progreso de creación de cuenta"
                      aria-valuemin={1}
                      aria-valuemax={6}
                      aria-valuenow={regStep}
                      className="w-full h-2 bg-slate-100 rounded-full overflow-hidden border border-slate-200/70"
                    >
                      <div
                        className="h-full bg-gradient-to-r from-blue-900 via-blue-600 to-emerald-500 transition-all duration-300 rounded-full"
                        style={{ width: `${(regStep / 6) * 100}%` }}
                      />
                    </div>

                    <div className="grid grid-cols-3 sm:grid-cols-6 gap-1.5">
                      {STEP_META.map((item) => {
                        const isActive = regStep === item.step;
                        const isDone = regStep > item.step;
                        return (
                          <button
                            key={item.step}
                            type="button"
                            disabled={item.step > regStep || isBusy}
                            onClick={() => {
                              if (item.step < regStep) {
                                setError(null);
                                setRegStep(item.step);
                              }
                            }}
                            className={`p-1.5 rounded-xl border text-left transition-all ${
                              isActive
                                ? 'bg-blue-50 border-blue-400 text-blue-950 shadow-sm'
                                : isDone
                                ? 'bg-emerald-50 border-emerald-300 text-emerald-900 cursor-pointer hover:bg-emerald-100/70'
                                : 'bg-slate-50 border-slate-200 text-slate-400 cursor-default'
                            }`}
                          >
                            <div className="flex items-center justify-between">
                              <span className="text-[9.5px] font-black uppercase">
                                Paso {item.step}
                              </span>
                              {isDone && <CheckCircle2 className="w-3 h-3 text-emerald-600" />}
                            </div>
                            <p className="text-[10.5px] font-bold truncate mt-0.5">
                              {item.shortLabel}
                            </p>
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  {/* PASO 1: DATOS PERSONALES */}
                  {regStep === 1 && (
                    <div className="space-y-3.5 animate-in fade-in">
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <div className="space-y-1">
                          <div className="flex items-center justify-between">
                            <label
                              htmlFor="reg-nombre"
                              className="text-[11px] font-bold text-slate-700"
                            >
                              Nombre (2–60 letras) *
                            </label>
                            <span className="text-[10px] font-mono text-slate-400">
                              {nombre.trim().length}/60
                            </span>
                          </div>
                          <input
                            id="reg-nombre"
                            type="text"
                            maxLength={60}
                            value={nombre}
                            onChange={(e) => setNombre(e.target.value)}
                            onBlur={() => markTouched('nombre')}
                            aria-invalid={showFieldError(
                              'nombre',
                              nombreErr,
                              nombre.length > 0 && Boolean(nombreErr)
                            )}
                            placeholder="Ej. César"
                            className={fieldInputClass(
                              showFieldError(
                                'nombre',
                                nombreErr,
                                nombre.length > 0 && Boolean(nombreErr)
                              ),
                              Boolean(nombre.trim() && !nombreErr)
                            )}
                          />
                          {showFieldError(
                            'nombre',
                            nombreErr,
                            nombre.length > 0 && Boolean(nombreErr)
                          ) && (
                            <p className="text-[11px] text-red-600 font-semibold">{nombreErr}</p>
                          )}
                        </div>

                        <div className="space-y-1">
                          <div className="flex items-center justify-between">
                            <label
                              htmlFor="reg-segundo-nombre"
                              className="text-[11px] font-bold text-slate-700"
                            >
                              Segundo nombre (opcional)
                            </label>
                            {segundoNombre && (
                              <span className="text-[10px] font-mono text-slate-400">
                                {segundoNombre.trim().length}/60
                              </span>
                            )}
                          </div>
                          <input
                            id="reg-segundo-nombre"
                            type="text"
                            maxLength={60}
                            value={segundoNombre}
                            onChange={(e) => setSegundoNombre(e.target.value)}
                            onBlur={() => markTouched('segundoNombre')}
                            aria-invalid={showFieldError(
                              'segundoNombre',
                              segundoNombreErr,
                              hasInvalidNameChars(segundoNombre)
                            )}
                            placeholder="Ej. Enrique"
                            className={fieldInputClass(
                              showFieldError(
                                'segundoNombre',
                                segundoNombreErr,
                                hasInvalidNameChars(segundoNombre)
                              ),
                              Boolean(segundoNombre.trim() && !segundoNombreErr)
                            )}
                          />
                          {showFieldError(
                            'segundoNombre',
                            segundoNombreErr,
                            hasInvalidNameChars(segundoNombre)
                          ) && (
                            <p className="text-[11px] text-red-600 font-semibold">
                              {segundoNombreErr}
                            </p>
                          )}
                        </div>

                        <div className="space-y-1">
                          <div className="flex items-center justify-between">
                            <label
                              htmlFor="reg-ap-paterno"
                              className="text-[11px] font-bold text-slate-700"
                            >
                              Apellido paterno (3–40 letras) *
                            </label>
                            <span className="text-[10px] font-mono text-slate-400">
                              {apellidoPaterno.trim().length}/40
                            </span>
                          </div>
                          <input
                            id="reg-ap-paterno"
                            type="text"
                            maxLength={40}
                            value={apellidoPaterno}
                            onChange={(e) => setApellidoPaterno(e.target.value)}
                            onBlur={() => markTouched('apellidoPaterno')}
                            aria-invalid={showFieldError(
                              'apellidoPaterno',
                              apPaternoErr,
                              apellidoPaterno.length > 0 && Boolean(apPaternoErr)
                            )}
                            placeholder="Ej. Garay"
                            className={fieldInputClass(
                              showFieldError(
                                'apellidoPaterno',
                                apPaternoErr,
                                apellidoPaterno.length > 0 && Boolean(apPaternoErr)
                              ),
                              Boolean(apellidoPaterno.trim() && !apPaternoErr)
                            )}
                          />
                          {showFieldError(
                            'apellidoPaterno',
                            apPaternoErr,
                            apellidoPaterno.length > 0 && Boolean(apPaternoErr)
                          ) && (
                            <p className="text-[11px] text-red-600 font-semibold">{apPaternoErr}</p>
                          )}
                        </div>

                        <div className="space-y-1">
                          <div className="flex items-center justify-between">
                            <label
                              htmlFor="reg-ap-materno"
                              className="text-[11px] font-bold text-slate-700"
                            >
                              Apellido materno (3–40 letras) *
                            </label>
                            <span className="text-[10px] font-mono text-slate-400">
                              {apellidoMaterno.trim().length}/40
                            </span>
                          </div>
                          <input
                            id="reg-ap-materno"
                            type="text"
                            maxLength={40}
                            value={apellidoMaterno}
                            onChange={(e) => setApellidoMaterno(e.target.value)}
                            onBlur={() => markTouched('apellidoMaterno')}
                            aria-invalid={showFieldError(
                              'apellidoMaterno',
                              apMaternoErr,
                              apellidoMaterno.length > 0 && Boolean(apMaternoErr)
                            )}
                            placeholder="Ej. García"
                            className={fieldInputClass(
                              showFieldError(
                                'apellidoMaterno',
                                apMaternoErr,
                                apellidoMaterno.length > 0 && Boolean(apMaternoErr)
                              ),
                              Boolean(apellidoMaterno.trim() && !apMaternoErr)
                            )}
                          />
                          {showFieldError(
                            'apellidoMaterno',
                            apMaternoErr,
                            apellidoMaterno.length > 0 && Boolean(apMaternoErr)
                          ) && (
                            <p className="text-[11px] text-red-600 font-semibold">{apMaternoErr}</p>
                          )}
                        </div>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
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
                            onBlur={() => markTouched('fechaNacimiento')}
                            className={fieldInputClass(
                              showFieldError('fechaNacimiento', fechaNacErr),
                              Boolean(fechaNacimiento && !fechaNacErr)
                            )}
                          />
                          {showFieldError('fechaNacimiento', fechaNacErr) && (
                            <p className="text-[11px] text-red-600 font-semibold">{fechaNacErr}</p>
                          )}
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

                        <div className="space-y-1">
                          <label
                            htmlFor="reg-entidad"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Entidad de nacimiento *
                          </label>
                          <select
                            id="reg-entidad"
                            value={entidadCodigo}
                            onChange={(e) => setEntidadCodigo(e.target.value)}
                            className="w-full px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                          >
                            {ESTADOS_MEXICO.map((st) => (
                              <option key={st.code} value={st.code}>
                                {st.name} ({st.code})
                              </option>
                            ))}
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
                        <span>Continuar a Datos Fiscales</span>
                        <ArrowRight className="w-4 h-4" />
                      </button>
                    </div>
                  )}

                  {/* PASO 2: DATOS FISCALES (CURP & RFC + DISPONIBILIDAD) */}
                  {regStep === 2 && (
                    <div className="space-y-4 animate-in fade-in">
                      <div className="p-3.5 rounded-2xl bg-indigo-50/80 border border-indigo-200 flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
                        <div className="text-xs text-indigo-950">
                          <p className="font-extrabold flex items-center gap-1.5">
                            <FileBadge className="w-4 h-4 text-indigo-700" />
                            <span>Asistente Oficial RENAPO / SAT</span>
                          </p>
                          <p className="text-[11px] text-indigo-800/85">
                            Autocalcula tu estructura oficial de CURP y RFC a partir de tus datos personales y edita tu homoclave si lo requieres.
                          </p>
                        </div>
                        <button
                          type="button"
                          onClick={handleAutoGenerateFiscal}
                          className="inline-flex items-center justify-center gap-1.5 px-3.5 py-2 rounded-xl bg-indigo-900 hover:bg-indigo-800 text-white text-xs font-extrabold shrink-0 shadow-sm transition-colors"
                        >
                          <Sparkles className="w-3.5 h-3.5 text-amber-300" />
                          <span>Calcular CURP / RFC</span>
                        </button>
                      </div>

                      {checkingFiscal && (
                        <div className="flex items-center gap-2 text-[11px] font-semibold text-blue-700">
                          <Loader2 className="w-3.5 h-3.5 animate-spin" />
                          <span>Verificando disponibilidad de CURP y RFC...</span>
                        </div>
                      )}

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
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
                              onBlur={() => {
                                markTouched('curp');
                                if (esCurpValida(curp)) {
                                  void checkFiscalAvailability({ curp });
                                }
                              }}
                              onChange={(e) => {
                                const val = e.target.value
                                  .toUpperCase()
                                  .replace(/[^A-Z0-9]/g, '')
                                  .slice(0, 18);
                                setCurp(val);
                                if (curpTakenMsg) setCurpTakenMsg(null);
                                if (esCurpValida(val)) {
                                  void checkFiscalAvailability({ curp: val });
                                }
                              }}
                              placeholder="XXXX000000HDFXXX00"
                              className={`w-full pl-3 pr-8 py-2.5 bg-slate-50 border rounded-xl text-xs font-mono font-bold text-slate-900 focus:outline-none focus:ring-2 ${
                                curpTakenMsg || showFieldError('curp', curpDiag)
                                  ? 'border-red-400 focus:ring-red-400 bg-red-50/30'
                                  : curpOk
                                  ? 'border-emerald-400 focus:ring-emerald-500'
                                  : curp
                                  ? 'border-amber-300 focus:ring-amber-400'
                                  : 'border-slate-200 focus:ring-blue-600'
                              }`}
                            />
                            {curpOk && (
                              <CheckCircle2 className="w-4 h-4 text-emerald-600 absolute right-2.5 top-2.5" />
                            )}
                          </div>
                          {curpTakenMsg ? (
                            <div
                              role="alert"
                              className="p-2 rounded-xl bg-red-50 border border-red-200 flex items-start gap-1.5 text-[11px] text-red-700 font-bold"
                            >
                              <AlertCircle className="w-3.5 h-3.5 text-red-600 shrink-0 mt-0.5" />
                              <span>{curpTakenMsg}</span>
                            </div>
                          ) : (
                            showFieldError('curp', curpDiag, Boolean(curp && curp.length === 18)) && (
                              <p className="text-[11px] text-red-600 font-semibold">{curpDiag}</p>
                            )
                          )}
                        </div>

                        <div className="space-y-1">
                          <label htmlFor="reg-rfc" className="text-[11px] font-bold text-slate-700">
                            RFC con Homoclave (13 caracteres) *
                          </label>
                          <div className="relative">
                            <input
                              id="reg-rfc"
                              type="text"
                              maxLength={13}
                              value={rfc}
                              onBlur={() => {
                                markTouched('rfc');
                                if (esRfcValido(rfc)) {
                                  void checkFiscalAvailability({ rfc });
                                }
                              }}
                              onChange={(e) => {
                                const val = e.target.value
                                  .toUpperCase()
                                  .replace(/[^A-ZÑ&0-9]/g, '')
                                  .slice(0, 13);
                                setRfc(val);
                                if (rfcTakenMsg) setRfcTakenMsg(null);
                                if (esRfcValido(val)) {
                                  void checkFiscalAvailability({ rfc: val });
                                }
                              }}
                              placeholder="XXXX000000XXX"
                              className={`w-full pl-3 pr-8 py-2.5 bg-slate-50 border rounded-xl text-xs font-mono font-bold text-slate-900 focus:outline-none focus:ring-2 ${
                                rfcTakenMsg || showFieldError('rfc', rfcDiag)
                                  ? 'border-red-400 focus:ring-red-400 bg-red-50/30'
                                  : rfcOk
                                  ? 'border-emerald-400 focus:ring-emerald-500'
                                  : rfc
                                  ? 'border-amber-300 focus:ring-amber-400'
                                  : 'border-slate-200 focus:ring-blue-600'
                              }`}
                            />
                            {rfcOk && (
                              <CheckCircle2 className="w-4 h-4 text-emerald-600 absolute right-2.5 top-2.5" />
                            )}
                          </div>
                          {rfcTakenMsg ? (
                            <div
                              role="alert"
                              className="p-2 rounded-xl bg-red-50 border border-red-200 flex items-start gap-1.5 text-[11px] text-red-700 font-bold"
                            >
                              <AlertCircle className="w-3.5 h-3.5 text-red-600 shrink-0 mt-0.5" />
                              <span>{rfcTakenMsg}</span>
                            </div>
                          ) : (
                            showFieldError('rfc', rfcDiag, Boolean(rfc && rfc.length === 13)) && (
                              <p className="text-[11px] text-red-600 font-semibold">{rfcDiag}</p>
                            )
                          )}
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
                          disabled={isBusy}
                          className="flex-1 py-2.5 px-4 bg-gradient-to-r from-blue-950 to-blue-700 hover:from-blue-900 hover:to-blue-600 text-white font-extrabold text-xs sm:text-sm rounded-xl shadow-md flex items-center justify-center gap-2 transition-all disabled:opacity-60"
                        >
                          <span>Continuar a Ocupación e Ingresos</span>
                          <ArrowRight className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                  )}

                  {/* PASO 3: OCUPACIÓN E INGRESOS */}
                  {regStep === 3 && (
                    <div className="space-y-3.5 animate-in fade-in">
                      <div className="p-3 rounded-2xl bg-slate-50 border border-slate-200 flex items-center gap-2.5 text-xs text-slate-700">
                        <Briefcase className="w-4 h-4 text-blue-800 shrink-0" />
                        <span>
                          Perfil económico normativo CNBV: selecciona tu ocupación oficial, tu profesión específica y tu rango de ingresos mensuales.
                        </span>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <div className="space-y-1">
                          <label
                            htmlFor="reg-ocupacion"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Catálogo de Ocupación *
                          </label>
                          <select
                            id="reg-ocupacion"
                            value={ocupacion}
                            onChange={(e) => handleOcupacionCatalogChange(e.target.value)}
                            onBlur={() => markTouched('ocupacion')}
                            className={fieldInputClass(
                              showFieldError('ocupacion', ocupacionErr),
                              Boolean(ocupacion.trim() && !ocupacionErr),
                              'font-semibold'
                            )}
                          >
                            <option value="">-- Selecciona una ocupación --</option>
                            {CATALOGO_OCUPACIONES.map((oc) => (
                              <option key={oc} value={oc}>
                                {oc}
                              </option>
                            ))}
                          </select>
                          {showFieldError('ocupacion', ocupacionErr) && (
                            <p className="text-[11px] text-red-600 font-semibold">{ocupacionErr}</p>
                          )}
                        </div>

                        <div className="space-y-1">
                          <label
                            htmlFor="reg-profesion"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Tabulador de Profesión *
                          </label>
                          <select
                            id="reg-profesion"
                            value={profesion}
                            onChange={(e) => handleProfesionChange(e.target.value)}
                            onBlur={() => markTouched('profesion')}
                            className="w-full px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                          >
                            {Array.from(new Set(TABULADOR_PROFESIONES.map((p) => p.sector))).map(
                              (sector) => (
                                <optgroup key={sector} label={sector}>
                                  {TABULADOR_PROFESIONES.filter((p) => p.sector === sector).map(
                                    (prof) => (
                                      <option key={prof.clave} value={prof.label}>
                                        {prof.label}
                                      </option>
                                    )
                                  )}
                                </optgroup>
                              )
                            )}
                          </select>
                        </div>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <div className="space-y-1">
                          <label
                            htmlFor="reg-empresa"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Empresa / Institución / Actividad *
                          </label>
                          <input
                            id="reg-empresa"
                            type="text"
                            maxLength={80}
                            value={empresa}
                            onChange={(e) => setEmpresa(e.target.value)}
                            onBlur={() => markTouched('empresa')}
                            placeholder="Ej. Grupo Bimbo, UNAM, Independiente..."
                            className={fieldInputClass(
                              showFieldError('empresa', empresaErr),
                              Boolean(empresa.trim() && !empresaErr)
                            )}
                          />
                          {showFieldError('empresa', empresaErr) && (
                            <p className="text-[11px] text-red-600 font-semibold">{empresaErr}</p>
                          )}
                        </div>

                        <div className="space-y-1">
                          <label
                            htmlFor="reg-ingreso"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Tabulador de Sueldo Mensual *
                          </label>
                          <select
                            id="reg-ingreso"
                            value={ingresoMensual}
                            onChange={(e) => setIngresoMensual(e.target.value)}
                            onBlur={() => markTouched('ingresoMensual')}
                            className="w-full px-3 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                          >
                            {TABULADOR_SUELDOS.map((s) => (
                              <option key={s.clave} value={String(s.montoRepresentativo)}>
                                {s.label}
                              </option>
                            ))}
                          </select>
                        </div>
                      </div>

                      <div className="flex items-center justify-between gap-3 pt-1">
                        <button
                          type="button"
                          onClick={() => {
                            setError(null);
                            setRegStep(2);
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
                          <span>Continuar a Domicilio</span>
                          <ArrowRight className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                  )}

                  {/* PASO 4: DOMICILIO SEPOMEX */}
                  {regStep === 4 && (
                    <div className="space-y-3.5 animate-in fade-in">
                      <div className="flex items-center justify-between gap-2">
                        <p className="text-[11px] font-extrabold uppercase tracking-wider text-slate-500 flex items-center gap-1">
                          <MapPin className="w-3.5 h-3.5 text-blue-700" />
                          <span>Domicilio en México (Autocompletado por C.P. SEPOMEX)</span>
                        </p>
                        {cpLoading ? (
                          <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-blue-50 text-blue-700 border border-blue-200 inline-flex items-center gap-1">
                            <Loader2 className="w-3 h-3 animate-spin" />
                            Consultando SEPOMEX...
                          </span>
                        ) : estadoDomicilioNombre && !cpDiag ? (
                          <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200">
                            ✓ C.P. {codigoPostal} · {estadoDomicilioNombre}
                          </span>
                        ) : null}
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                        <div className="space-y-1">
                          <label htmlFor="reg-cp" className="text-[11px] font-bold text-slate-700">
                            Código Postal (5 díg) *
                          </label>
                          <input
                            id="reg-cp"
                            type="text"
                            inputMode="numeric"
                            maxLength={5}
                            value={codigoPostal}
                            onChange={(e) => handleCodigoPostalChange(e.target.value)}
                            onBlur={() => markTouched('codigoPostal')}
                            placeholder="Ej. 37800 o 06600"
                            className={fieldInputClass(
                              showFieldError('codigoPostal', cpDiag, codigoPostal.length === 5),
                              Boolean(estadoDomicilioNombre && !cpDiag),
                              'py-2 font-mono font-bold'
                            )}
                          />
                          {showFieldError('codigoPostal', cpDiag, codigoPostal.length === 5) && (
                            <p className="text-[10.5px] text-red-600 font-semibold">{cpDiag}</p>
                          )}
                        </div>

                        <div className="space-y-1">
                          <label
                            htmlFor="reg-estado-dom"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Estado (Automático) *
                          </label>
                          <input
                            id="reg-estado-dom"
                            type="text"
                            readOnly
                            value={estadoDomicilioNombre}
                            placeholder="Se llena con tu C.P."
                            className="w-full px-2.5 py-2 bg-slate-100 border border-slate-200 rounded-xl text-xs font-semibold text-slate-800 cursor-not-allowed"
                          />
                        </div>

                        <div className="space-y-1">
                          <label
                            htmlFor="reg-municipio"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Municipio / Alcaldía (Auto) *
                          </label>
                          <input
                            id="reg-municipio"
                            type="text"
                            readOnly
                            value={municipio}
                            placeholder="Se llena con tu C.P."
                            className="w-full px-2.5 py-2 bg-slate-100 border border-slate-200 rounded-xl text-xs font-semibold text-slate-800 cursor-not-allowed"
                          />
                        </div>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-12 gap-2.5">
                        <div className="sm:col-span-5 space-y-1">
                          <label
                            htmlFor="reg-colonia-select"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Colonia (Catálogo del C.P.) *
                          </label>
                          <select
                            id="reg-colonia-select"
                            disabled={coloniasDelCp.length === 0}
                            value={colonia}
                            onChange={(e) => {
                              setColonia(e.target.value);
                              markTouched('colonia');
                            }}
                            onBlur={() => markTouched('colonia')}
                            className={fieldInputClass(
                              showFieldError('colonia', coloniaErr),
                              Boolean(colonia.trim() && !coloniaErr),
                              'py-2 font-semibold disabled:opacity-60 disabled:cursor-not-allowed'
                            )}
                          >
                            <option value="">
                              {coloniasDelCp.length === 0
                                ? 'Ingresa primero un C.P. válido...'
                                : `-- Selecciona colonia (${coloniasDelCp.length}) --`}
                            </option>
                            {coloniasDelCp.map((col) => (
                              <option key={col} value={col}>
                                {col}
                              </option>
                            ))}
                          </select>
                          {showFieldError('colonia', coloniaErr) && (
                            <p className="text-[10.5px] text-red-600 font-semibold">{coloniaErr}</p>
                          )}
                        </div>

                        <div className="sm:col-span-3 space-y-1">
                          <label
                            htmlFor="reg-tipo-vialidad"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Vialidad *
                          </label>
                          <select
                            id="reg-tipo-vialidad"
                            value={tipoVialidad}
                            onChange={(e) => setTipoVialidad(e.target.value)}
                            className="w-full px-2.5 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                          >
                            {TIPOS_VIALIDAD.map((v) => (
                              <option key={v.clave} value={v.prefijo}>
                                {v.label}
                              </option>
                            ))}
                          </select>
                        </div>

                        <div className="sm:col-span-4 space-y-1">
                          <label
                            htmlFor="reg-calle"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Nombre de Calle (3–80) *
                          </label>
                          <input
                            id="reg-calle"
                            type="text"
                            maxLength={80}
                            value={calle}
                            onChange={(e) => setCalle(e.target.value)}
                            onBlur={() => markTouched('calle')}
                            placeholder="Paseo de la Reforma"
                            className={fieldInputClass(
                              showFieldError('calle', calleErr),
                              Boolean(calle.trim() && !calleErr),
                              'py-2'
                            )}
                          />
                          {showFieldError('calle', calleErr) && (
                            <p className="text-[10.5px] text-red-600 font-semibold">{calleErr}</p>
                          )}
                        </div>
                      </div>

                      <div className="grid grid-cols-2 gap-2.5">
                        <div className="space-y-1">
                          <div className="flex items-center justify-between">
                            <label
                              htmlFor="reg-num-ext"
                              className="text-[11px] font-bold text-slate-700"
                            >
                              Núm. Exterior *
                            </label>
                            <button
                              type="button"
                              onClick={() => {
                                setNumeroExterior('S/N');
                                markTouched('numeroExterior');
                              }}
                              className="text-[10px] font-extrabold text-blue-700 hover:underline"
                            >
                              Usar S/N
                            </button>
                          </div>
                          <input
                            id="reg-num-ext"
                            type="text"
                            maxLength={10}
                            value={numeroExterior}
                            onChange={(e) => setNumeroExterior(e.target.value)}
                            onBlur={() => markTouched('numeroExterior')}
                            placeholder="222 o S/N"
                            className={fieldInputClass(
                              showFieldError('numeroExterior', numExtErr),
                              Boolean(numeroExterior.trim() && !numExtErr),
                              'py-2'
                            )}
                          />
                          {showFieldError('numeroExterior', numExtErr) && (
                            <p className="text-[10.5px] text-red-600 font-semibold">{numExtErr}</p>
                          )}
                        </div>

                        <div className="space-y-1">
                          <label
                            htmlFor="reg-num-int"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Núm. Interior (opcional)
                          </label>
                          <input
                            id="reg-num-int"
                            type="text"
                            maxLength={10}
                            value={numeroInterior}
                            onChange={(e) => setNumeroInterior(e.target.value)}
                            onBlur={() => markTouched('numeroInterior')}
                            placeholder="Ej. 4B"
                            className={fieldInputClass(
                              showFieldError('numeroInterior', numIntErr),
                              Boolean(numeroInterior.trim() && !numIntErr),
                              'py-2'
                            )}
                          />
                          {showFieldError('numeroInterior', numIntErr) && (
                            <p className="text-[10.5px] text-red-600 font-semibold">{numIntErr}</p>
                          )}
                        </div>
                      </div>

                      <div className="flex items-center justify-between gap-3 pt-1">
                        <button
                          type="button"
                          onClick={() => {
                            setError(null);
                            setRegStep(3);
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

                  {/* PASO 5: CONTACTO Y ACCESO (CON VALIDACIÓN INMEDIATA DE CORREO/USUARIO DUPLICADO) */}
                  {regStep === 5 && (
                    <div className="space-y-3.5 animate-in fade-in">
                      <div className="space-y-1">
                        <div className="flex items-center justify-between gap-2">
                          <label
                            htmlFor="reg-correo"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Correo electrónico real *
                          </label>
                          {checkingCorreo && (
                            <span className="text-[10px] font-semibold text-blue-700 inline-flex items-center gap-1">
                              <Loader2 className="w-3 h-3 animate-spin" />
                              Verificando correo...
                            </span>
                          )}
                        </div>
                        <div className="relative">
                          <Mail className="w-3.5 h-3.5 text-slate-400 absolute left-3 top-3 pointer-events-none" />
                          <input
                            id="reg-correo"
                            type="email"
                            value={correo}
                            onChange={(e) => {
                              setCorreo(e.target.value);
                              if (correoTakenMsg) setCorreoTakenMsg(null);
                            }}
                            onBlur={() => {
                              markTouched('correo');
                              const trimmed = correo.trim();
                              if (trimmed && esCorreoValido(trimmed) && !esCorreoPlaceholder(trimmed)) {
                                void checkContactAvailability({ correo: trimmed });
                              }
                            }}
                            aria-invalid={
                              Boolean(correoTakenMsg) || showFieldError('correo', correoFormatErr)
                            }
                            placeholder="tucorreo@gmail.com"
                            className={fieldInputClass(
                              Boolean(correoTakenMsg) || showFieldError('correo', correoFormatErr),
                              Boolean(correoTrim && !correoFormatErr && !correoTakenMsg),
                              'pl-9 pr-3'
                            )}
                            disabled={isBusy}
                          />
                        </div>
                        {correoTakenMsg ? (
                          <div
                            role="alert"
                            className="p-2.5 rounded-xl bg-red-50 border border-red-300 flex items-start justify-between gap-2 text-red-700 text-[11px]"
                          >
                            <div className="flex items-start gap-1.5">
                              <AlertCircle className="w-4 h-4 text-red-600 shrink-0 mt-0.5" />
                              <span className="font-bold leading-snug">{correoTakenMsg}</span>
                            </div>
                            <button
                              type="button"
                              onClick={() => handleModeChange('login')}
                              className="text-[10.5px] font-extrabold underline text-red-800 hover:text-red-950 shrink-0"
                            >
                              Iniciar sesión
                            </button>
                          </div>
                        ) : (
                          showFieldError('correo', correoFormatErr) && (
                            <p className="text-[11px] text-red-600 font-semibold">
                              {correoFormatErr}
                            </p>
                          )
                        )}
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
                              onBlur={() => markTouched('telefonoMovil')}
                              onChange={(e) =>
                                setTelefonoMovil(e.target.value.replace(/\D/g, '').slice(0, 10))
                              }
                              placeholder="5512345678"
                              className={`w-full pl-9 pr-8 py-2.5 bg-slate-50 border rounded-xl text-xs font-mono text-slate-900 focus:outline-none focus:ring-2 ${
                                phoneOk
                                  ? 'border-emerald-400 focus:ring-emerald-500'
                                  : showFieldError(
                                      'telefonoMovil',
                                      phoneDiag,
                                      telefonoMovil.length === 10
                                    )
                                  ? 'border-red-400 focus:ring-red-400'
                                  : 'border-slate-200 focus:ring-blue-600'
                              }`}
                              disabled={isBusy}
                            />
                            {phoneOk && (
                              <CheckCircle2 className="w-4 h-4 text-emerald-600 absolute right-2.5 top-2.5" />
                            )}
                          </div>
                          {showFieldError(
                            'telefonoMovil',
                            phoneDiag,
                            Boolean(telefonoMovil && telefonoMovil.length === 10)
                          ) && (
                            <p className="text-[11px] text-red-600 font-semibold">{phoneDiag}</p>
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
                            onBlur={() => markTouched('telefonoAlternativo')}
                            onChange={(e) =>
                              setTelefonoAlternativo(e.target.value.replace(/\D/g, '').slice(0, 10))
                            }
                            placeholder="Opcional 10 dígitos"
                            className={fieldInputClass(
                              showFieldError('telefonoAlternativo', telAltErr),
                              Boolean(telefonoAlternativo && !telAltErr),
                              'font-mono'
                            )}
                            disabled={isBusy}
                          />
                          {showFieldError('telefonoAlternativo', telAltErr) && (
                            <p className="text-[11px] text-red-600 font-semibold">{telAltErr}</p>
                          )}
                        </div>
                      </div>

                      <div className="space-y-1">
                        <div className="flex items-center justify-between gap-2">
                          <label
                            htmlFor="reg-usuario"
                            className="text-[11px] font-bold text-slate-700"
                          >
                            Usuario (4–50 caracteres) *
                          </label>
                          {checkingUsuario && (
                            <span className="text-[10px] font-semibold text-blue-700 inline-flex items-center gap-1">
                              <Loader2 className="w-3 h-3 animate-spin" />
                              Verificando usuario...
                            </span>
                          )}
                        </div>
                        <input
                          id="reg-usuario"
                          type="text"
                          maxLength={50}
                          value={regUsuario}
                          onChange={(e) => {
                            setRegUsuario(e.target.value);
                            if (usuarioTakenMsg) setUsuarioTakenMsg(null);
                          }}
                          onBlur={() => {
                            markTouched('regUsuario');
                            const trimmedUsr = regUsuario.trim();
                            if (trimmedUsr && esUsuarioValido(trimmedUsr)) {
                              void checkContactAvailability({ usuario: trimmedUsr });
                            }
                          }}
                          aria-invalid={
                            Boolean(usuarioTakenMsg) ||
                            showFieldError('regUsuario', regUsuarioFormatErr)
                          }
                          placeholder="min. 4 caracteres (letras, números, ._-)"
                          className={fieldInputClass(
                            Boolean(usuarioTakenMsg) ||
                              showFieldError('regUsuario', regUsuarioFormatErr),
                            Boolean(regUsuario.trim() && !regUsuarioFormatErr && !usuarioTakenMsg),
                            'font-mono'
                          )}
                          disabled={isBusy}
                        />
                        {usuarioTakenMsg ? (
                          <div
                            role="alert"
                            className="p-2 rounded-xl bg-red-50 border border-red-300 flex items-start gap-1.5 text-red-700 text-[11px] font-bold"
                          >
                            <AlertCircle className="w-3.5 h-3.5 text-red-600 shrink-0 mt-0.5" />
                            <span>{usuarioTakenMsg}</span>
                          </div>
                        ) : (
                          showFieldError('regUsuario', regUsuarioFormatErr) && (
                            <p className="text-[11px] text-red-600 font-semibold">
                              {regUsuarioFormatErr}
                            </p>
                          )
                        )}
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
                              onBlur={() => markTouched('regContrasena')}
                              placeholder="Mayús, minús y número"
                              className={fieldInputClass(
                                showFieldError('regContrasena', regContrasenaErr),
                                Boolean(regContrasena && !regContrasenaErr),
                                'pl-3 pr-9'
                              )}
                              disabled={isBusy}
                            />
                            <button
                              type="button"
                              onClick={() => setShowRegPassword(!showRegPassword)}
                              aria-label={
                                showRegPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'
                              }
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
                          {showFieldError('regContrasena', regContrasenaErr) && (
                            <p className="text-[10.5px] text-red-600 font-semibold">
                              {regContrasenaErr}
                            </p>
                          )}
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
                            onBlur={() => markTouched('confirmarContrasena')}
                            placeholder="Repite tu contraseña"
                            className={fieldInputClass(
                              showFieldError('confirmarContrasena', confirmarContrasenaErr),
                              Boolean(confirmarContrasena && !confirmarContrasenaErr)
                            )}
                            disabled={isBusy}
                          />
                          {showFieldError('confirmarContrasena', confirmarContrasenaErr) && (
                            <p className="text-[10.5px] text-red-600 font-semibold">
                              {confirmarContrasenaErr}
                            </p>
                          )}
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
                          disabled={isBusy}
                          onClick={() => {
                            setError(null);
                            setRegStep(4);
                          }}
                          className="px-4 py-2.5 rounded-xl border border-slate-200 text-slate-700 hover:bg-slate-100 text-xs font-bold inline-flex items-center gap-1.5 disabled:opacity-50"
                        >
                          <ArrowLeft className="w-3.5 h-3.5" />
                          <span>Anterior</span>
                        </button>
                        <button
                          type="submit"
                          disabled={
                            isBusy ||
                            Boolean(correoTakenMsg) ||
                            Boolean(usuarioTakenMsg)
                          }
                          className="flex-1 py-3 px-4 bg-gradient-to-r from-emerald-700 to-teal-600 hover:from-emerald-600 hover:to-teal-500 text-white font-extrabold text-xs sm:text-sm rounded-xl shadow-lg shadow-emerald-900/20 flex items-center justify-center gap-2 transition-all disabled:opacity-60"
                        >
                          {totpSetupLoading ? (
                            <>
                              <Loader2 className="w-4 h-4 animate-spin" />
                              <span>Configurando Google Authenticator...</span>
                            </>
                          ) : (
                            <>
                              <Smartphone className="w-4 h-4" />
                              <span>Continuar a Google Authenticator</span>
                            </>
                          )}
                        </button>
                      </div>
                    </div>
                  )}

                  {/* PASO 6: AUTENTICADOR DE GOOGLE (TOTP 30s) */}
                  {regStep === 6 && totpSetup && (
                    <div className="space-y-4 animate-in fade-in">
                      {/* Executive KYC Dossier Summary */}
                      <div className="p-3 rounded-2xl bg-slate-50 border border-slate-200 grid grid-cols-2 sm:grid-cols-4 gap-2 text-[11px]">
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
                          <span className="font-mono font-bold text-blue-900 truncate block">
                            {curp}
                          </span>
                        </div>
                        <div>
                          <span className="text-slate-400 font-bold block">RFC</span>
                          <span className="font-mono font-bold text-blue-900 truncate block">
                            {rfc}
                          </span>
                        </div>
                        <div>
                          <span className="text-slate-400 font-bold block">Ocupación</span>
                          <span className="font-bold text-slate-800 truncate block">
                            {ocupacion}
                          </span>
                        </div>
                      </div>

                      {totpFeedback && (
                        <div className="p-3 rounded-xl bg-emerald-50 border border-emerald-200 flex items-center gap-2 text-emerald-800 text-xs font-medium">
                          <CheckCircle2 className="w-4 h-4 text-emerald-600 shrink-0" />
                          <span>{totpFeedback}</span>
                        </div>
                      )}

                      <TotpSetupCard
                        totpSetup={totpSetup}
                        codigo={codigoTotp}
                        onCodigoChange={setCodigoTotp}
                        inputId="reg-codigo-totp"
                        disabled={isBusy}
                      />

                      <button
                        type="submit"
                        disabled={isBusy || codigoTotp.length < 6}
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
                          disabled={isBusy}
                          onClick={() => {
                            setRegStep(5);
                            setCodigoTotp('');
                            setError(null);
                            setTotpFeedback(null);
                          }}
                          className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-semibold text-slate-600 hover:text-slate-900 hover:bg-slate-100 rounded-xl transition-colors disabled:opacity-50"
                        >
                          <ArrowLeft className="w-3.5 h-3.5" />
                          <span>Editar datos</span>
                        </button>
                        <button
                          type="button"
                          disabled={isBusy}
                          onClick={() => void handleProceedToTotp(true)}
                          className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-bold text-blue-700 hover:text-blue-900 hover:bg-blue-50 rounded-xl transition-colors disabled:opacity-50"
                        >
                          <RefreshCw className="w-3.5 h-3.5" />
                          <span>Regenerar QR</span>
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
                <p className="text-[10px] font-extrabold text-slate-700">Google Auth</p>
                <p className="text-[10px] text-slate-500">TOTP cada 30s</p>
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
        Protegido con cifrado BCrypt de grado bancario, expediente KYC RENAPO/SAT, validación IFT y autenticador dinámico TOTP (2FA)
      </p>
    </div>
  );
};
