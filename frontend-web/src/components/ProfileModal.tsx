import React, { useEffect, useRef, useState } from 'react';
import {
  X,
  UserCheck,
  Mail,
  Phone,
  Wallet,
  CheckCircle2,
  AlertCircle,
  Loader2,
  PlusCircle,
  AlertTriangle,
  Copy,
  Check,
  User,
  ShieldCheck,
  Lock,
  Eye,
  EyeOff,
  FileBadge,
  MapPin,
} from 'lucide-react';
import { useAuth } from '../context/useAuth';
import type { LoginResponse } from '../types/auth';
import {
  diagnosticarTelefono,
  esContrasenaSegura,
  esCorreoPlaceholder,
  esCorreoValido,
  esNombreValido,
  esTelefonoMexicanoValido,
} from '../utils/validation';

interface ProfileModalProps {
  isOpen: boolean;
  onClose: () => void;
  initialTab?: ProfileTab;
}

interface ProfileModalContentProps {
  user: LoginResponse;
  onClose: () => void;
  initialTab?: ProfileTab;
}

export type ProfileTab = 'datos' | 'abono' | 'seguridad';

const ProfileModalContent: React.FC<ProfileModalContentProps> = ({
  user,
  onClose,
  initialTab = 'datos',
}) => {
  const { refreshBalance, updateContacto, cambiarContrasena, abonarSaldo } = useAuth();
  const [activeTab, setActiveTab] = useState<ProfileTab>(initialTab);

  // Personal + Contact fields
  const [nombre, setNombre] = useState(user.nombre || '');
  const [segundoNombre, setSegundoNombre] = useState(user.segundoNombre || '');
  const [apellidoPaterno, setApellidoPaterno] = useState(user.apellidoPaterno || '');
  const [apellidoMaterno, setApellidoMaterno] = useState(user.apellidoMaterno || '');
  const [correo, setCorreo] = useState(user.email || '');
  const [telefono, setTelefono] = useState(user.telefonoMovil || '');
  const [loadingProfile, setLoadingProfile] = useState(false);
  const [savingContact, setSavingContact] = useState(false);

  // Funds fields
  const [montoAbono, setMontoAbono] = useState('250');
  const [conceptoAbono, setConceptoAbono] = useState('Abono a billetera digital');
  const [savingAbono, setSavingAbono] = useState(false);

  // Password fields
  const [contrasenaActual, setContrasenaActual] = useState('');
  const [contrasenaNueva, setContrasenaNueva] = useState('');
  const [confirmarContrasena, setConfirmarContrasena] = useState('');
  const [showCurrentPass, setShowCurrentPass] = useState(false);
  const [showNewPass, setShowNewPass] = useState(false);
  const [savingPassword, setSavingPassword] = useState(false);

  const [copiedAccount, setCopiedAccount] = useState(false);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; msg: string } | null>(null);
  const isDirtyRef = useRef(false);

  // Fetch fresh /auth/me on modal open so split name fields are always pre-populated
  useEffect(() => {
    let cancelled = false;

    const loadProfile = async () => {
      setLoadingProfile(true);
      try {
        const fresh = await refreshBalance();
        if (!cancelled && fresh && !isDirtyRef.current) {
          setNombre(fresh.nombre || '');
          setSegundoNombre(fresh.segundoNombre || '');
          setApellidoPaterno(fresh.apellidoPaterno || '');
          setApellidoMaterno(fresh.apellidoMaterno || '');
          setCorreo(fresh.email || '');
          setTelefono(fresh.telefonoMovil || '');
        }
      } finally {
        if (!cancelled) {
          setLoadingProfile(false);
        }
      }
    };

    loadProfile();
    return () => {
      cancelled = true;
    };
  }, [refreshBalance]);

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !savingContact && !savingAbono && !savingPassword) onClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose, savingContact, savingAbono, savingPassword]);

  const isPlaceholderMail = esCorreoPlaceholder(user.email || '');
  const phoneDiag = telefono ? diagnosticarTelefono(telefono) : null;
  const phoneValid = esTelefonoMexicanoValido(telefono);

  const passRules = {
    length: contrasenaNueva.length >= 8,
    upper: /[A-Z]/.test(contrasenaNueva),
    lower: /[a-z]/.test(contrasenaNueva),
    digit: /\d/.test(contrasenaNueva),
  };

  const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(amount);

  const handleCopyAccount = async () => {
    if (!user.numeroCuenta) return;
    try {
      await navigator.clipboard.writeText(user.numeroCuenta);
      setCopiedAccount(true);
      setTimeout(() => setCopiedAccount(false), 1800);
    } catch {
      // Clipboard fallback ignored
    }
  };

  const handleGuardarPerfil = async (e: React.FormEvent) => {
    e.preventDefault();
    setFeedback(null);

    const nombreClean = nombre.trim();
    const segundoClean = segundoNombre.trim();
    const paternoClean = apellidoPaterno.trim();
    const maternoClean = apellidoMaterno.trim();
    const correoClean = correo.trim();
    const telClean = telefono.trim();

    if (!esNombreValido(nombreClean, true)) {
      setFeedback({
        type: 'error',
        msg: 'El nombre debe tener entre 2 y 50 caracteres y contener solo letras y espacios.',
      });
      return;
    }
    if (!esNombreValido(segundoClean, false)) {
      setFeedback({
        type: 'error',
        msg: 'El segundo nombre solo debe contener letras y espacios (máximo 50 caracteres).',
      });
      return;
    }
    if (!esNombreValido(paternoClean, true)) {
      setFeedback({
        type: 'error',
        msg: 'El apellido paterno debe tener entre 2 y 50 caracteres y contener solo letras y espacios.',
      });
      return;
    }
    if (!esNombreValido(maternoClean, true)) {
      setFeedback({
        type: 'error',
        msg: 'El apellido materno debe tener entre 2 y 50 caracteres y contener solo letras y espacios.',
      });
      return;
    }
    if (!esCorreoValido(correoClean)) {
      setFeedback({ type: 'error', msg: 'Ingresa un correo electrónico válido.' });
      return;
    }
    if (esCorreoPlaceholder(correoClean)) {
      setFeedback({
        type: 'error',
        msg: 'No se permiten dominios de ejemplo (@ejemplo.com). Usa un correo electrónico real para recibir tus comprobantes y códigos 2FA.',
      });
      return;
    }
    const telErr = diagnosticarTelefono(telClean);
    if (telErr) {
      setFeedback({ type: 'error', msg: telErr });
      return;
    }

    setSavingContact(true);
    try {
      const updated = await updateContacto({
        nombre: nombreClean,
        segundoNombre: segundoClean,
        apellidoPaterno: paternoClean,
        apellidoMaterno: maternoClean,
        correo: correoClean,
        telefonoMovil: telClean,
      });
      setNombre(updated.nombre || nombreClean);
      setSegundoNombre(updated.segundoNombre || '');
      setApellidoPaterno(updated.apellidoPaterno || paternoClean);
      setApellidoMaterno(updated.apellidoMaterno || maternoClean);
      setFeedback({
        type: 'success',
        msg: `Perfil actualizado correctamente (${updated.nombreCompleto}). Tus códigos 2FA y recibos llegarán a ${correoClean}.`,
      });
    } catch (err: unknown) {
      setFeedback({
        type: 'error',
        msg: err instanceof Error ? err.message : 'No se pudieron actualizar tus datos.',
      });
    } finally {
      setSavingContact(false);
    }
  };

  const handleAbonar = async (e: React.FormEvent) => {
    e.preventDefault();
    setFeedback(null);
    const val = parseFloat(montoAbono);
    if (isNaN(val) || val < 1 || val > 50000) {
      setFeedback({ type: 'error', msg: 'El monto del abono debe estar entre $1.00 y $50,000.00 MXN.' });
      return;
    }

    setSavingAbono(true);
    try {
      const updated = await abonarSaldo({ monto: val, concepto: conceptoAbono.trim() || undefined });
      setFeedback({
        type: 'success',
        msg: `Abono aplicado exitosamente. Nuevo saldo disponible: ${formatCurrency(updated.saldo)}.`,
      });
    } catch (err: unknown) {
      setFeedback({
        type: 'error',
        msg: err instanceof Error ? err.message : 'No fue posible abonar fondos.',
      });
    } finally {
      setSavingAbono(false);
    }
  };

  const handleCambiarContrasena = async (e: React.FormEvent) => {
    e.preventDefault();
    setFeedback(null);

    if (!contrasenaActual) {
      setFeedback({ type: 'error', msg: 'Ingresa tu contraseña actual.' });
      return;
    }
    if (!esContrasenaSegura(contrasenaNueva)) {
      setFeedback({
        type: 'error',
        msg: 'La nueva contraseña debe tener mínimo 8 caracteres, al menos una mayúscula, una minúscula y un número.',
      });
      return;
    }
    if (contrasenaNueva !== confirmarContrasena) {
      setFeedback({ type: 'error', msg: 'La confirmación no coincide con la nueva contraseña.' });
      return;
    }
    if (contrasenaActual === contrasenaNueva) {
      setFeedback({ type: 'error', msg: 'La nueva contraseña debe ser diferente a la actual.' });
      return;
    }

    setSavingPassword(true);
    try {
      await cambiarContrasena({ contrasenaActual, contrasenaNueva });
      setContrasenaActual('');
      setContrasenaNueva('');
      setConfirmarContrasena('');
      setFeedback({
        type: 'success',
        msg: 'Tu contraseña se actualizó exitosamente. Úsala en tu próximo inicio de sesión en Web y Móvil.',
      });
    } catch (err: unknown) {
      setFeedback({
        type: 'error',
        msg: err instanceof Error ? err.message : 'No fue posible actualizar la contraseña.',
      });
    } finally {
      setSavingPassword(false);
    }
  };

  const switchTab = (tab: ProfileTab) => {
    setActiveTab(tab);
    setFeedback(null);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in"
      role="dialog"
      aria-modal="true"
      aria-label="Mi Perfil y Cuenta"
    >
      <div className="bg-white rounded-3xl shadow-2xl border border-slate-200 w-full max-w-lg max-h-[92vh] overflow-y-auto animate-in zoom-in-95">
        {/* Header */}
        <div className="bg-gradient-to-r from-slate-900 via-blue-950 to-blue-900 px-6 py-5 text-white flex items-center justify-between sticky top-0 z-10">
          <div className="flex items-center gap-3 min-w-0">
            <div className="p-2.5 bg-white/10 rounded-xl shrink-0">
              <UserCheck className="w-5 h-5 text-blue-300" />
            </div>
            <div className="min-w-0">
              <h3 className="text-base font-extrabold truncate">{user.nombreCompleto}</h3>
              <div className="flex items-center gap-2 text-xs text-blue-200 font-mono">
                <span className="truncate">
                  @{user.usuario} &bull; {user.numeroCuenta}
                </span>
                <button
                  type="button"
                  onClick={handleCopyAccount}
                  title="Copiar número de cuenta"
                  className="p-1 rounded hover:bg-white/15 text-blue-200 hover:text-white transition-colors shrink-0"
                >
                  {copiedAccount ? <Check className="w-3.5 h-3.5 text-emerald-300" /> : <Copy className="w-3.5 h-3.5" />}
                </button>
              </div>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar"
            className="p-1.5 rounded-full hover:bg-white/15 text-blue-200 hover:text-white transition-colors shrink-0"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Navigation Tabs */}
        <div className="px-6 pt-4 pb-2 bg-slate-50 border-b border-slate-200">
          <div className="grid grid-cols-3 gap-1.5 bg-slate-200/70 p-1 rounded-2xl">
            <button
              type="button"
              onClick={() => switchTab('datos')}
              className={`py-2 px-2 rounded-xl text-xs font-bold flex items-center justify-center gap-1.5 transition-all ${
                activeTab === 'datos'
                  ? 'bg-white text-blue-950 shadow-sm'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <User className="w-3.5 h-3.5" />
              <span>Mis Datos</span>
            </button>
            <button
              type="button"
              onClick={() => switchTab('abono')}
              className={`py-2 px-2 rounded-xl text-xs font-bold flex items-center justify-center gap-1.5 transition-all ${
                activeTab === 'abono'
                  ? 'bg-white text-emerald-800 shadow-sm'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <Wallet className="w-3.5 h-3.5" />
              <span>Abonar Saldo</span>
            </button>
            <button
              type="button"
              onClick={() => switchTab('seguridad')}
              className={`py-2 px-2 rounded-xl text-xs font-bold flex items-center justify-center gap-1.5 transition-all ${
                activeTab === 'seguridad'
                  ? 'bg-white text-indigo-900 shadow-sm'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <ShieldCheck className="w-3.5 h-3.5" />
              <span>Seguridad</span>
            </button>
          </div>
        </div>

        <div className="p-6 space-y-4">
          {isPlaceholderMail && (
            <div className="p-3.5 rounded-2xl bg-amber-50 border border-amber-200 flex items-start gap-2.5 text-xs text-amber-900">
              <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
              <p>
                Tu cuenta actual usa el correo de prueba <strong>{user.email}</strong> (sin buzón real). Actualízalo por un correo real para recibir tus códigos 2FA y recibos vía Brevo.
              </p>
            </div>
          )}

          {feedback && (
            <div
              className={`p-3.5 rounded-2xl border flex items-start gap-2.5 text-xs ${
                feedback.type === 'success'
                  ? 'bg-emerald-50 border-emerald-200 text-emerald-800'
                  : 'bg-red-50 border-red-200 text-red-700'
              }`}
            >
              {feedback.type === 'success' ? (
                <CheckCircle2 className="w-4 h-4 shrink-0 text-emerald-600 mt-0.5" />
              ) : (
                <AlertCircle className="w-4 h-4 shrink-0 text-red-600 mt-0.5" />
              )}
              <span className="font-medium leading-relaxed">{feedback.msg}</span>
            </div>
          )}

          {/* TAB 1: MIS DATOS (Personal + Contact) */}
          {activeTab === 'datos' && (
            <form onSubmit={handleGuardarPerfil} className="space-y-4">
              {loadingProfile && (
                <div className="flex items-center gap-2 text-xs text-blue-700 bg-blue-50 border border-blue-100 rounded-xl px-3 py-2">
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                  <span>Sincronizando datos de tu perfil...</span>
                </div>
              )}

              {(user.curp || user.rfc || user.fechaNacimiento || user.calle) && (
                <div className="p-4 rounded-2xl bg-gradient-to-br from-slate-900 via-blue-950 to-indigo-950 text-white border border-blue-400/25 space-y-2.5">
                  <div className="flex items-center justify-between">
                    <span className="text-[11px] font-extrabold uppercase tracking-wider text-amber-300 flex items-center gap-1.5">
                      <FileBadge className="w-3.5 h-3.5" />
                      <span>Expediente Bancario KYC Verificado</span>
                    </span>
                    <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-emerald-500/20 border border-emerald-400/35 text-emerald-300">
                      {user.nacionalidad || 'MEXICANA'}
                    </span>
                  </div>
                  <div className="grid grid-cols-2 gap-2 text-xs">
                    {user.curp && (
                      <div className="bg-white/10 rounded-xl px-3 py-1.5">
                        <span className="text-[10px] text-blue-200/80 block">CURP Oficial</span>
                        <span className="font-mono font-bold text-white text-[11px] break-all">
                          {user.curp}
                        </span>
                      </div>
                    )}
                    {user.rfc && (
                      <div className="bg-white/10 rounded-xl px-3 py-1.5">
                        <span className="text-[10px] text-blue-200/80 block">RFC con Homoclave</span>
                        <span className="font-mono font-bold text-amber-200 text-[11px] break-all">
                          {user.rfc}
                        </span>
                      </div>
                    )}
                  </div>
                  {(user.calle || user.municipio || user.estado) && (
                    <p className="text-[11px] text-blue-100/85 flex items-start gap-1.5 pt-1 border-t border-white/10">
                      <MapPin className="w-3.5 h-3.5 text-amber-300 shrink-0 mt-0.5" />
                      <span>
                        {[
                          user.calle && `${user.calle} #${user.numeroExterior || 'S/N'}`,
                          user.colonia && `Col. ${user.colonia}`,
                          user.codigoPostal && `C.P. ${user.codigoPostal}`,
                          user.municipio,
                          user.estado,
                        ]
                          .filter(Boolean)
                          .join(', ')}
                      </span>
                    </p>
                  )}
                </div>
              )}

              <div className="space-y-3 bg-slate-50 p-4 rounded-2xl border border-slate-200">
                <h4 className="text-xs font-extrabold uppercase tracking-wider text-slate-600 flex items-center gap-1.5">
                  <User className="w-3.5 h-3.5 text-blue-800" />
                  <span>Información Personal</span>
                </h4>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div className="space-y-1">
                    <label htmlFor="perfil-nombre" className="text-xs font-semibold text-slate-700">
                      Nombre *
                    </label>
                    <input
                      id="perfil-nombre"
                      type="text"
                      maxLength={80}
                      value={nombre}
                      onChange={(e) => {
                        isDirtyRef.current = true;
                        setNombre(e.target.value);
                      }}
                      placeholder="Tu nombre"
                      className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                    />
                  </div>
                  <div className="space-y-1">
                    <label htmlFor="perfil-segundo-nombre" className="text-xs font-semibold text-slate-700">
                      Segundo nombre (opcional)
                    </label>
                    <input
                      id="perfil-segundo-nombre"
                      type="text"
                      maxLength={80}
                      value={segundoNombre}
                      onChange={(e) => {
                        isDirtyRef.current = true;
                        setSegundoNombre(e.target.value);
                      }}
                      placeholder="Opcional"
                      className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                    />
                  </div>
                  <div className="space-y-1">
                    <label htmlFor="perfil-apellido-paterno" className="text-xs font-semibold text-slate-700">
                      Apellido paterno *
                    </label>
                    <input
                      id="perfil-apellido-paterno"
                      type="text"
                      maxLength={80}
                      value={apellidoPaterno}
                      onChange={(e) => {
                        isDirtyRef.current = true;
                        setApellidoPaterno(e.target.value);
                      }}
                      placeholder="Apellido paterno"
                      className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                    />
                  </div>
                  <div className="space-y-1">
                    <label htmlFor="perfil-apellido-materno" className="text-xs font-semibold text-slate-700">
                      Apellido materno *
                    </label>
                    <input
                      id="perfil-apellido-materno"
                      type="text"
                      maxLength={80}
                      value={apellidoMaterno}
                      onChange={(e) => {
                        isDirtyRef.current = true;
                        setApellidoMaterno(e.target.value);
                      }}
                      placeholder="Apellido materno"
                      className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                    />
                  </div>
                </div>
              </div>

              <div className="space-y-3 bg-slate-50 p-4 rounded-2xl border border-slate-200">
                <div className="flex items-center justify-between">
                  <h4 className="text-xs font-extrabold uppercase tracking-wider text-slate-600">
                    Contacto y Notificaciones 2FA
                  </h4>
                  <span
                    className={`text-[10px] font-bold px-2 py-0.5 rounded-full border ${
                      isPlaceholderMail
                        ? 'bg-amber-50 text-amber-700 border-amber-200'
                        : 'bg-emerald-50 text-emerald-700 border-emerald-200'
                    }`}
                  >
                    {isPlaceholderMail ? 'Actualizar correo' : 'Brevo activo'}
                  </span>
                </div>

                <div className="space-y-1">
                  <label htmlFor="perfil-correo" className="text-xs font-semibold text-slate-700">
                    Correo electrónico real *
                  </label>
                  <div className="relative">
                    <Mail className="w-4 h-4 text-slate-400 absolute left-3 top-2.5 pointer-events-none" />
                    <input
                      id="perfil-correo"
                      type="email"
                      value={correo}
                      onChange={(e) => {
                        isDirtyRef.current = true;
                        setCorreo(e.target.value);
                      }}
                      placeholder="tucorreo@gmail.com"
                      className="w-full pl-9 pr-3 py-2 bg-white border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
                    />
                  </div>
                </div>

                <div className="space-y-1">
                  <label htmlFor="perfil-telefono" className="text-xs font-semibold text-slate-700">
                    Teléfono móvil (10 dígitos México) *
                  </label>
                  <div className="relative">
                    <Phone className="w-4 h-4 text-slate-400 absolute left-3 top-2.5 pointer-events-none" />
                    <input
                      id="perfil-telefono"
                      type="tel"
                      inputMode="numeric"
                      maxLength={10}
                      value={telefono}
                      onChange={(e) => {
                        isDirtyRef.current = true;
                        setTelefono(e.target.value.replace(/\D/g, '').slice(0, 10));
                      }}
                      placeholder="5512345678"
                      className={`w-full pl-9 pr-9 py-2 bg-white border rounded-xl text-xs font-mono text-slate-900 focus:outline-none focus:ring-2 ${
                        phoneValid
                          ? 'border-emerald-400 focus:ring-emerald-500'
                          : telefono
                          ? 'border-red-300 focus:ring-red-400'
                          : 'border-slate-200 focus:ring-blue-600'
                      }`}
                    />
                    {phoneValid && <CheckCircle2 className="w-4 h-4 text-emerald-600 absolute right-2.5 top-2" />}
                  </div>
                  {telefono && phoneDiag && <p className="text-[11px] text-red-600 font-medium">{phoneDiag}</p>}
                </div>
              </div>

              <button
                type="submit"
                disabled={savingContact}
                className="w-full py-2.5 rounded-xl bg-blue-900 hover:bg-blue-800 text-white font-bold text-xs flex items-center justify-center gap-2 transition-colors disabled:opacity-60 shadow-sm"
              >
                {savingContact ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCircle2 className="w-4 h-4" />}
                <span>Guardar Cambios de Perfil</span>
              </button>
            </form>
          )}

          {/* TAB 2: ABONAR SALDO */}
          {activeTab === 'abono' && (
            <form onSubmit={handleAbonar} className="space-y-4 bg-emerald-50/60 p-4 rounded-2xl border border-emerald-200">
              <div className="flex items-center justify-between">
                <h4 className="text-xs font-extrabold uppercase tracking-wider text-emerald-900 flex items-center gap-1.5">
                  <Wallet className="w-4 h-4 text-emerald-700" />
                  <span>Abonar Fondos a mi Cuenta</span>
                </h4>
                <span className="text-xs font-black text-emerald-800">
                  Actual: {formatCurrency(user.saldo)}
                </span>
              </div>

              <div className="grid grid-cols-4 gap-2">
                {['100', '250', '500', '1000'].map((amt) => (
                  <button
                    key={amt}
                    type="button"
                    onClick={() => setMontoAbono(amt)}
                    className={`py-2 rounded-xl text-xs font-bold border transition-colors ${
                      montoAbono === amt
                        ? 'bg-emerald-700 text-white border-emerald-700 shadow-sm'
                        : 'bg-white text-emerald-800 border-emerald-200 hover:bg-emerald-100'
                    }`}
                  >
                    ${amt}
                  </button>
                ))}
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label htmlFor="abono-monto" className="text-[11px] font-bold text-emerald-900">
                    Monto MXN
                  </label>
                  <input
                    id="abono-monto"
                    type="number"
                    min={1}
                    max={50000}
                    step="0.01"
                    value={montoAbono}
                    onChange={(e) => setMontoAbono(e.target.value)}
                    className="w-full px-3 py-2 bg-white border border-emerald-200 rounded-xl text-xs font-bold text-slate-900 focus:outline-none focus:ring-2 focus:ring-emerald-600"
                  />
                </div>
                <div className="space-y-1">
                  <label htmlFor="abono-concepto" className="text-[11px] font-bold text-emerald-900">
                    Concepto
                  </label>
                  <input
                    id="abono-concepto"
                    type="text"
                    value={conceptoAbono}
                    onChange={(e) => setConceptoAbono(e.target.value)}
                    placeholder="Transferencia SPEI"
                    className="w-full px-3 py-2 bg-white border border-emerald-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-emerald-600"
                  />
                </div>
              </div>

              <button
                type="submit"
                disabled={savingAbono}
                className="w-full py-2.5 rounded-xl bg-emerald-700 hover:bg-emerald-600 text-white font-bold text-xs flex items-center justify-center gap-2 transition-colors disabled:opacity-60 shadow-sm"
              >
                {savingAbono ? <Loader2 className="w-4 h-4 animate-spin" /> : <PlusCircle className="w-4 h-4" />}
                <span>Abonar a Cuenta</span>
              </button>
            </form>
          )}

          {/* TAB 3: SEGURIDAD (Cambiar Contraseña + Resumen) */}
          {activeTab === 'seguridad' && (
            <div className="space-y-4">
              <div className="p-3.5 rounded-2xl bg-indigo-50/70 border border-indigo-100 flex items-start gap-2.5 text-xs text-indigo-950">
                <ShieldCheck className="w-4 h-4 text-indigo-700 shrink-0 mt-0.5" />
                <div className="space-y-0.5">
                  <p className="font-bold">Protección Multiplataforma Activa</p>
                  <p className="text-[11px] text-indigo-800/90 leading-relaxed">
                    Tus credenciales sincronizan Web y App Móvil. Cada pago requiere confirmación 2FA enviada a tu correo real y la sesión se bloquea tras 5 minutos de inactividad.
                  </p>
                </div>
              </div>

              <form onSubmit={handleCambiarContrasena} className="space-y-3 bg-slate-50 p-4 rounded-2xl border border-slate-200">
                <h4 className="text-xs font-extrabold uppercase tracking-wider text-slate-600 flex items-center gap-1.5">
                  <Lock className="w-3.5 h-3.5 text-indigo-700" />
                  <span>Cambiar Contraseña</span>
                </h4>

                <div className="space-y-1">
                  <label htmlFor="perfil-pass-actual" className="text-xs font-semibold text-slate-700">
                    Contraseña actual
                  </label>
                  <div className="relative">
                    <Lock className="w-4 h-4 text-slate-400 absolute left-3 top-2.5 pointer-events-none" />
                    <input
                      id="perfil-pass-actual"
                      type={showCurrentPass ? 'text' : 'password'}
                      value={contrasenaActual}
                      onChange={(e) => setContrasenaActual(e.target.value)}
                      placeholder="Ingresa tu contraseña actual"
                      className="w-full pl-9 pr-9 py-2 bg-white border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-indigo-600"
                    />
                    <button
                      type="button"
                      onClick={() => setShowCurrentPass((v) => !v)}
                      className="absolute right-2.5 top-2 text-slate-400 hover:text-slate-600"
                    >
                      {showCurrentPass ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                <div className="space-y-1">
                  <label htmlFor="perfil-pass-nueva" className="text-xs font-semibold text-slate-700">
                    Nueva contraseña
                  </label>
                  <div className="relative">
                    <Lock className="w-4 h-4 text-slate-400 absolute left-3 top-2.5 pointer-events-none" />
                    <input
                      id="perfil-pass-nueva"
                      type={showNewPass ? 'text' : 'password'}
                      value={contrasenaNueva}
                      onChange={(e) => setContrasenaNueva(e.target.value)}
                      placeholder="Mínimo 8 caracteres, Aa1"
                      className="w-full pl-9 pr-9 py-2 bg-white border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-indigo-600"
                    />
                    <button
                      type="button"
                      onClick={() => setShowNewPass((v) => !v)}
                      className="absolute right-2.5 top-2 text-slate-400 hover:text-slate-600"
                    >
                      {showNewPass ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                {/* Live Password Strength Checklist */}
                <div className="grid grid-cols-2 gap-1.5 text-[11px]">
                  {[
                    { ok: passRules.length, label: 'Mínimo 8 caracteres' },
                    { ok: passRules.upper, label: '1 letra mayúscula' },
                    { ok: passRules.lower, label: '1 letra minúscula' },
                    { ok: passRules.digit, label: '1 número (0-9)' },
                  ].map((r) => (
                    <div
                      key={r.label}
                      className={`flex items-center gap-1.5 font-medium ${
                        r.ok ? 'text-emerald-700' : 'text-slate-400'
                      }`}
                    >
                      <CheckCircle2 className="w-3.5 h-3.5 shrink-0" />
                      <span>{r.label}</span>
                    </div>
                  ))}
                </div>

                <div className="space-y-1">
                  <label htmlFor="perfil-pass-confirmar" className="text-xs font-semibold text-slate-700">
                    Confirmar nueva contraseña
                  </label>
                  <input
                    id="perfil-pass-confirmar"
                    type={showNewPass ? 'text' : 'password'}
                    value={confirmarContrasena}
                    onChange={(e) => setConfirmarContrasena(e.target.value)}
                    placeholder="Repite la nueva contraseña"
                    className="w-full px-3 py-2 bg-white border border-slate-200 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-indigo-600"
                  />
                </div>

                <button
                  type="submit"
                  disabled={savingPassword}
                  className="w-full py-2.5 rounded-xl bg-indigo-900 hover:bg-indigo-800 text-white font-bold text-xs flex items-center justify-center gap-2 transition-colors disabled:opacity-60 shadow-sm"
                >
                  {savingPassword ? <Loader2 className="w-4 h-4 animate-spin" /> : <ShieldCheck className="w-4 h-4" />}
                  <span>Actualizar Contraseña</span>
                </button>
              </form>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export const ProfileModal: React.FC<ProfileModalProps> = ({ isOpen, onClose, initialTab = 'datos' }) => {
  const { user } = useAuth();
  if (!isOpen || !user) return null;
  return (
    <ProfileModalContent
      key={`${user.usuario}-${initialTab}`}
      user={user}
      onClose={onClose}
      initialTab={initialTab}
    />
  );
};
