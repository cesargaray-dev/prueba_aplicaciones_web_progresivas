import React, { useState, useEffect, useCallback } from 'react';
import {
  X,
  ShieldCheck,
  KeyRound,
  AlertCircle,
  Loader2,
  RefreshCw,
  Clock,
  CheckCircle2,
  AlertTriangle,
  Wallet,
  Mail,
  PlusCircle,
} from 'lucide-react';
import type { ProductDTO } from '../types/catalog';
import type {
  ValidarReferenciaResponse,
  Generar2FAResponse,
  PagoServicioResponse,
} from '../types/payment';
import type { LoginResponse } from '../types/auth';
import { paymentApi } from '../api/paymentApi';
import { ApiError } from '../api/client';
import { useAuth } from '../context/useAuth';
import { BrandLogo } from './BrandLogo';
import { ProfileModal } from './ProfileModal';
import {
  esCorreoPlaceholder,
  esRecargaTelefonica,
  esTelefonoMexicanoValido,
} from '../utils/validation';

interface Payment2FAModalProps {
  isOpen: boolean;
  onClose: () => void;
  product: ProductDTO;
  referencia: string;
  monto: number;
  validation: ValidarReferenciaResponse;
  onPaymentSuccess: (receipt: PagoServicioResponse) => void;
}

interface Payment2FAModalInnerProps {
  user: LoginResponse;
  onClose: () => void;
  product: ProductDTO;
  referencia: string;
  monto: number;
  validation: ValidarReferenciaResponse;
  onPaymentSuccess: (receipt: PagoServicioResponse) => void;
}

const buildStructuredFailureResponse = (
  err: ApiError,
  product: ProductDTO,
  monto: number,
  currentBalance: number
): PagoServicioResponse | null => {
  const raw = err.data as (Partial<PagoServicioResponse> & { mensaje?: string }) | undefined;
  const estadoRaw = typeof raw?.estadoOperacion === 'string' ? raw.estadoOperacion.toUpperCase() : '';
  const hasProviderCode = Boolean(raw?.codigoProveedor);
  const isPendingStatus = err.status === 504 || err.status === 202 || estadoRaw === 'PENDIENTE';
  const isRejectedStatus =
    estadoRaw === 'RECHAZADA' ||
    ((err.status === 409 || err.status === 422) && (hasProviderCode || Boolean(raw?.folioTicket)));

  if (!isPendingStatus && !isRejectedStatus) {
    return null;
  }

  const normalizedEstado = isPendingStatus ? 'PENDIENTE' : 'RECHAZADA';

  return {
    codigo: raw?.codigo ?? (isPendingStatus ? 202 : 1),
    estadoOperacion: normalizedEstado,
    codigoProveedor: raw?.codigoProveedor,
    mensajeOperacion: raw?.mensajeOperacion || raw?.mensaje || err.message,
    mensaje: raw?.mensajeOperacion || raw?.mensaje || err.message,
    idTransaccion: raw?.idTransaccion,
    numeroAutorizacion: raw?.numeroAutorizacion,
    folioTicket: raw?.folioTicket,
    upc: raw?.upc,
    montoPagado: raw?.montoPagado ?? monto,
    comision: raw?.comision ?? 0,
    saldoRestanteCuenta: raw?.saldoRestanteCuenta ?? currentBalance,
    fechaHoraOperacion: raw?.fechaHoraOperacion,
    concepto: raw?.concepto,
    servicio: raw?.servicio || product.service,
    producto: raw?.producto || product.name,
    logoUrl: raw?.logoUrl || product.logoUrl || undefined,
    correoEnviado: raw?.correoEnviado,
    correoDestino: raw?.correoDestino,
    mensajeCorreo: raw?.mensajeCorreo,
    brevoMessageId: raw?.brevoMessageId,
  };
};

const Payment2FAModalInner: React.FC<Payment2FAModalInnerProps> = ({
  user,
  onClose,
  product,
  referencia,
  monto,
  validation,
  onPaymentSuccess,
}) => {
  const { updateBalance, refreshBalance } = useAuth();

  const [codigo2FA, setCodigo2FA] = useState('');
  const [twoFAResponse, setTwoFAResponse] = useState<Generar2FAResponse | null>(null);
  const [isGenerating2FA, setIsGenerating2FA] = useState(true);
  const [isProcessing, setIsProcessing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [countdown, setCountdown] = useState<number>(300);
  const [explicitConfirm, setExplicitConfirm] = useState<boolean>(false);
  const [isAbonarOpen, setIsAbonarOpen] = useState<boolean>(false);

  const isRecarga = esRecargaTelefonica(product);
  const numeroCuenta = user.numeroCuenta;

  // Initial 2FA OTP generation on mount without synchronous setState in effect body
  useEffect(() => {
    let cancelled = false;
    paymentApi
      .generar2FA(numeroCuenta)
      .then((resp) => {
        if (cancelled) return;
        setTwoFAResponse(resp);
        setCountdown(resp.expiraEnSegundos ?? resp.vigenciaSegundos ?? 300);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        const msg = err instanceof Error ? err.message : 'Error al generar el código 2FA.';
        setError(msg);
      })
      .finally(() => {
        if (!cancelled) {
          setIsGenerating2FA(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [numeroCuenta]);

  const handleResendOTP = useCallback(async () => {
    setIsGenerating2FA(true);
    setError(null);
    setCodigo2FA('');

    try {
      const resp = await paymentApi.generar2FA(numeroCuenta);
      setTwoFAResponse(resp);
      setCountdown(resp.expiraEnSegundos ?? resp.vigenciaSegundos ?? 300);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error al generar el código 2FA.';
      setError(msg);
    } finally {
      setIsGenerating2FA(false);
    }
  }, [numeroCuenta]);

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !isProcessing && !isAbonarOpen) onClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [isProcessing, isAbonarOpen, onClose]);

  useEffect(() => {
    if (!twoFAResponse || countdown <= 0) return;
    const timer = setInterval(() => {
      setCountdown((prev) => Math.max(0, prev - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, [twoFAResponse, countdown]);

  const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(amount);

  const formatTimer = (sec: number) => {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  const insufficientBalance = user.saldo < monto;
  const emailIsPlaceholder = esCorreoPlaceholder(user.email || '');

  const handleConfirmPayment = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!explicitConfirm) {
      setError('Debes marcar la casilla de confirmación explícita para autorizar el pago.');
      return;
    }

    if (countdown === 0) {
      setError('El código 2FA ha expirado. Solicita uno nuevo presionando "Reenviar".');
      return;
    }

    if (codigo2FA.length !== 6) {
      setError('Ingresa el código 2FA de 6 dígitos enviado a tu correo.');
      return;
    }

    if (insufficientBalance) {
      setError('Saldo insuficiente en tu cuenta para realizar esta operación.');
      return;
    }

    setIsProcessing(true);
    try {
      const profilePhone =
        user.telefonoMovil && esTelefonoMexicanoValido(user.telefonoMovil)
          ? user.telefonoMovil
          : undefined;

      const resp = await paymentApi.pagarServicio({
        numeroCuenta: user.numeroCuenta,
        idServicio: parseInt(product.serviceId, 10),
        idProducto: parseInt(product.id, 10),
        monto,
        telefono: isRecarga ? referencia.trim() : profilePhone,
        referencia: referencia.trim(),
        codigo2FA: codigo2FA.trim(),
      });

      const normalizedEstado = (
        resp.estadoOperacion || (resp.codigo === 202 ? 'PENDIENTE' : 'EXITOSA')
      ).toUpperCase();

      if (typeof resp.saldoRestanteCuenta === 'number') {
        updateBalance(resp.saldoRestanteCuenta);
      } else if (normalizedEstado === 'EXITOSA' || normalizedEstado === 'PENDIENTE') {
        await refreshBalance();
      }

      onPaymentSuccess({
        ...resp,
        estadoOperacion: normalizedEstado,
        servicio: resp.servicio || product.service,
        producto: resp.producto || product.name,
        logoUrl: resp.logoUrl || product.logoUrl || undefined,
      });
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        if (err.status === 401) {
          setError(err.message);
          return;
        }
        const structured = buildStructuredFailureResponse(err, product, monto, user.saldo);
        if (structured) {
          await refreshBalance();
          onPaymentSuccess(structured);
          return;
        }
      }

      const msg =
        err instanceof Error
          ? err.message
          : 'No fue posible procesar el pago con el proveedor.';
      setError(msg);
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <>
      <div
        className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in"
        role="dialog"
        aria-modal="true"
        aria-label="Confirmación y Seguridad 2FA"
      >
        <div className="bg-white rounded-3xl shadow-2xl border border-slate-200 w-full max-w-lg max-h-[92vh] overflow-y-auto animate-in zoom-in-95">
          {/* Header */}
          <div className="bg-gradient-to-r from-blue-950 to-blue-900 px-6 py-4 text-white flex items-center justify-between sticky top-0 z-10">
            <div className="flex items-center gap-3">
              <div className="p-2 bg-white/10 rounded-xl">
                <ShieldCheck className="w-6 h-6 text-emerald-400" />
              </div>
              <div>
                <p className="text-[11px] font-bold uppercase tracking-wider text-blue-300">
                  Paso 2 de 2: Confirmación y Seguridad 2FA
                </p>
                <h3 className="text-base font-extrabold leading-tight">Resumen de Operación</h3>
              </div>
            </div>

            <button
              type="button"
              onClick={onClose}
              disabled={isProcessing}
              aria-label="Cerrar"
              className="p-1.5 rounded-full hover:bg-white/10 text-blue-200 hover:text-white transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          <form onSubmit={handleConfirmPayment} className="p-6 space-y-5">
            {/* Operation Summary Card */}
            <div className="bg-slate-50 rounded-2xl border border-slate-200 p-4 space-y-3">
              <div className="flex items-center gap-3 pb-3 border-b border-slate-200/80">
                <BrandLogo
                  serviceId={product.serviceId}
                  serviceName={product.service}
                  logoUrl={product.logoUrl}
                  size="sm"
                />
                <div className="min-w-0">
                  <p className="text-xs font-bold text-slate-500 uppercase truncate">
                    {validation.servicio || product.service}
                  </p>
                  <p className="text-sm font-extrabold text-slate-900 truncate">{product.name}</p>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-y-2 text-xs">
                <span className="text-slate-500 font-medium">
                  {isRecarga ? 'Teléfono Celular:' : 'Referencia:'}
                </span>
                <span className="text-right font-mono font-bold text-slate-900 break-all">
                  {referencia}
                </span>

                <span className="text-slate-500 font-medium">Cuenta Cargo:</span>
                <span className="text-right font-mono font-semibold text-slate-800">
                  •••• {user.numeroCuenta.slice(-4)}
                </span>

                <span className="text-slate-500 font-medium">Importe a Pagar:</span>
                <span className="text-right text-sm font-black text-blue-950">
                  {formatCurrency(monto)}
                </span>

                <span className="text-slate-500 font-medium">Comisión Bancaria:</span>
                <span className="text-right font-bold text-emerald-700">$0.00 MXN</span>
              </div>

              <div
                className={`pt-2 border-t border-slate-200/80 flex flex-wrap items-center justify-between gap-2 text-xs ${
                  insufficientBalance ? 'text-red-600' : 'text-slate-600'
                }`}
              >
                <span className="flex items-center gap-1.5 font-semibold">
                  <Wallet className="w-3.5 h-3.5" />
                  Saldo Disponible:
                </span>
                <span className="font-bold">
                  {formatCurrency(user.saldo)}
                  {!insufficientBalance && ` → Restante: ${formatCurrency(user.saldo - monto)}`}
                </span>
              </div>
            </div>

            {insufficientBalance && (
              <div className="p-3.5 rounded-2xl bg-red-50 border border-red-200 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-red-700 text-xs font-semibold">
                <div className="flex items-start gap-2.5">
                  <AlertTriangle className="w-4 h-4 shrink-0 text-red-600 mt-0.5" />
                  <span>
                    Saldo insuficiente ({formatCurrency(user.saldo)}). Abona fondos para completar tu pago sin perder tus datos.
                  </span>
                </div>
                <button
                  type="button"
                  onClick={() => setIsAbonarOpen(true)}
                  className="inline-flex items-center justify-center gap-1.5 px-3 py-1.5 rounded-xl bg-emerald-700 hover:bg-emerald-600 text-white text-[11px] font-bold shrink-0 shadow-sm transition-colors"
                >
                  <PlusCircle className="w-3.5 h-3.5" />
                  <span>Abonar Saldo</span>
                </button>
              </div>
            )}

            {/* 2FA Code Delivery Status & Input */}
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <label htmlFor="otp-input" className="text-xs font-bold text-slate-700 flex items-center gap-1.5">
                  <KeyRound className="w-4 h-4 text-blue-700" />
                  <span>Código de Verificación 2FA (6 dígitos)</span>
                </label>

                {twoFAResponse && (
                  <span
                    className={`inline-flex items-center gap-1 text-[11px] font-bold px-2 py-0.5 rounded-full ${
                      countdown > 0
                        ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                        : 'bg-red-50 text-red-700 border border-red-200'
                    }`}
                  >
                    <Clock className="w-3 h-3" />
                    {countdown > 0 ? formatTimer(countdown) : 'Expirado'}
                  </span>
                )}
              </div>

              {isGenerating2FA ? (
                <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 flex items-center justify-center gap-2 text-xs text-slate-600">
                  <Loader2 className="w-4 h-4 animate-spin text-blue-700" />
                  <span>Generando y enviando código de seguridad a tu correo...</span>
                </div>
              ) : twoFAResponse ? (
                <div
                  className={`p-3 rounded-xl border text-xs flex items-start gap-2.5 ${
                    twoFAResponse.correoEnviado && !emailIsPlaceholder
                      ? 'bg-emerald-50 border-emerald-200 text-emerald-900'
                      : 'bg-amber-50 border-amber-200 text-amber-900'
                  }`}
                >
                  <Mail
                    className={`w-4 h-4 shrink-0 mt-0.5 ${
                      twoFAResponse.correoEnviado && !emailIsPlaceholder
                        ? 'text-emerald-700'
                        : 'text-amber-700'
                    }`}
                  />
                  <div className="space-y-0.5 flex-1">
                    <p className="font-bold">
                      {twoFAResponse.correoEnviado && !emailIsPlaceholder
                        ? `Código 2FA enviado vía correo a ${twoFAResponse.correoDestino}`
                        : 'No se pudo confirmar el envío por correo. Verifica tu correo en Perfil o presiona Reenviar.'}
                    </p>
                    <p className="text-[11px] opacity-90">
                      {twoFAResponse.mensajeCorreo ||
                        twoFAResponse.mensaje ||
                        'Revisa tu bandeja de entrada o carpeta de spam.'}
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={handleResendOTP}
                    disabled={isGenerating2FA || isProcessing}
                    className="px-2.5 py-1.5 rounded-lg bg-white border border-slate-300 text-slate-700 font-bold text-[11px] hover:bg-slate-50 flex items-center gap-1 shrink-0"
                  >
                    <RefreshCw className="w-3 h-3" />
                    <span>Reenviar</span>
                  </button>
                </div>
              ) : (
                <div className="flex justify-end">
                  <button
                    type="button"
                    onClick={handleResendOTP}
                    disabled={isGenerating2FA || isProcessing}
                    className="px-3 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 font-bold text-[11px] inline-flex items-center gap-1"
                  >
                    <RefreshCw className="w-3 h-3" />
                    <span>Solicitar código 2FA</span>
                  </button>
                </div>
              )}

              <input
                id="otp-input"
                type="text"
                inputMode="numeric"
                maxLength={6}
                value={codigo2FA}
                onChange={(e) => setCodigo2FA(e.target.value.replace(/\D/g, '').slice(0, 6))}
                placeholder="000000"
                className="w-full text-center tracking-[0.6em] text-xl font-black font-mono py-3 bg-slate-50 border border-slate-300 rounded-xl text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600 focus:bg-white transition-all"
                disabled={isProcessing}
              />
            </div>

            {/* Explicit Confirmation Checkbox */}
            <label className="flex items-start gap-2.5 p-3 rounded-xl bg-slate-50 border border-slate-200 cursor-pointer select-none hover:bg-slate-100/70 transition-colors">
              <input
                id="confirm-checkbox"
                type="checkbox"
                checked={explicitConfirm}
                onChange={(e) => setExplicitConfirm(e.target.checked)}
                className="mt-0.5 w-4 h-4 rounded text-blue-900 focus:ring-blue-700"
                disabled={isProcessing}
              />
              <span className="text-xs text-slate-700 leading-relaxed">
                Confirmo explícitamente los datos anteriores y autorizo el cargo irrevocable de{' '}
                <strong>{formatCurrency(monto)}</strong> a mi cuenta para pagar{' '}
                <strong>{product.name}</strong>.
              </span>
            </label>

            {error && (
              <div className="p-3.5 rounded-2xl bg-red-50 border border-red-200 flex items-start gap-2.5 text-red-700 text-xs animate-in fade-in">
                <AlertCircle className="w-5 h-5 text-red-600 shrink-0" />
                <p className="font-medium leading-relaxed">{error}</p>
              </div>
            )}

            {/* Footer Buttons */}
            <div className="pt-2 flex items-center justify-end gap-3 border-t border-slate-100">
              <button
                type="button"
                onClick={onClose}
                disabled={isProcessing}
                className="px-5 py-2.5 rounded-2xl text-xs font-bold text-slate-600 hover:bg-slate-100 transition-colors disabled:opacity-50"
              >
                Cancelar
              </button>

              <button
                type="submit"
                disabled={
                  isProcessing ||
                  !explicitConfirm ||
                  codigo2FA.length !== 6 ||
                  insufficientBalance ||
                  countdown === 0
                }
                className="px-6 py-2.5 rounded-2xl bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white text-xs font-bold flex items-center gap-2 transition-all shadow-md shadow-emerald-900/20 disabled:opacity-40 disabled:cursor-not-allowed active:scale-95"
              >
                {isProcessing ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    <span>Procesando Pago...</span>
                  </>
                ) : (
                  <>
                    <CheckCircle2 className="w-4 h-4" />
                    <span>Autorizar y Pagar {formatCurrency(monto)}</span>
                  </>
                )}
              </button>
            </div>
          </form>
        </div>
      </div>

      <ProfileModal
        isOpen={isAbonarOpen}
        onClose={() => setIsAbonarOpen(false)}
        initialTab="abono"
      />
    </>
  );
};

export const Payment2FAModal: React.FC<Payment2FAModalProps> = ({
  isOpen,
  onClose,
  product,
  referencia,
  monto,
  validation,
  onPaymentSuccess,
}) => {
  const { user } = useAuth();
  if (!isOpen || !user) return null;

  return (
    <Payment2FAModalInner
      key={`${user.numeroCuenta}-${product.serviceId}-${product.id}-${referencia}-${monto}`}
      user={user}
      onClose={onClose}
      product={product}
      referencia={referencia}
      monto={monto}
      validation={validation}
      onPaymentSuccess={onPaymentSuccess}
    />
  );
};
