import React, { useEffect, useState } from 'react';
import {
  X,
  CheckCircle2,
  AlertCircle,
  Loader2,
  Smartphone,
  FileText,
  ArrowRight,
  Gift,
  HelpCircle,
  ShieldAlert,
} from 'lucide-react';
import type { ProductDTO } from '../types/catalog';
import type { ValidarReferenciaResponse } from '../types/payment';
import { paymentApi } from '../api/paymentApi';
import { useAuth } from '../context/useAuth';
import { BrandLogo } from './BrandLogo';
import {
  diagnosticarTelefono,
  esRecargaTelefonica,
  esTelefonoMexicanoValido,
} from '../utils/validation';

interface ReferenceModalProps {
  product: ProductDTO | null;
  isOpen: boolean;
  onClose: () => void;
  onProceedTo2FA: (data: {
    product: ProductDTO;
    referencia: string;
    monto: number;
    validation: ValidarReferenciaResponse;
  }) => void;
}

interface ReferenceModalInnerProps {
  product: ProductDTO;
  onClose: () => void;
  onProceedTo2FA: (data: {
    product: ProductDTO;
    referencia: string;
    monto: number;
    validation: ValidarReferenciaResponse;
  }) => void;
}

const ReferenceModalInner: React.FC<ReferenceModalInnerProps> = ({
  product,
  onClose,
  onProceedTo2FA,
}) => {
  const { user } = useAuth();
  const isRecarga = esRecargaTelefonica(product);
  const fixedPrice = parseFloat(product.price || '0');
  const isFixedPrice = product.frontType === '1' && !isNaN(fixedPrice) && fixedPrice > 0;
  const suggestedMinPrice =
    !isFixedPrice && !isNaN(fixedPrice) && fixedPrice > 0 ? fixedPrice : null;

  const [referencia, setReferencia] = useState('');
  const [monto, setMonto] = useState<string>(isFixedPrice ? fixedPrice.toFixed(2) : '');
  const [isValidating, setIsValidating] = useState(false);
  const [validationResult, setValidationResult] = useState<ValidarReferenciaResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showAyudaBox, setShowAyudaBox] = useState(false);

  const hasSavedPhone = Boolean(user?.telefonoMovil && esTelefonoMexicanoValido(user.telefonoMovil));
  const phoneDiag = isRecarga && referencia ? diagnosticarTelefono(referencia) : null;
  const phoneValid = isRecarga && esTelefonoMexicanoValido(referencia);

  const requiresVerificationDigit =
    String(
      validationResult?.hasVerificationDigit ?? product.hasVerificationDigit ?? ''
    ).toLowerCase() === 'true';

  const hasAyudaInfo =
    product.showAyuda === true || String(product.showAyuda ?? '').toLowerCase() === 'true';

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !isValidating) onClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose, isValidating]);

  const handleValidateReference = async () => {
    setError(null);
    setValidationResult(null);

    if (!referencia.trim()) {
      setError(
        isRecarga
          ? 'Ingresa el número telefónico de 10 dígitos.'
          : 'Ingresa el número de referencia, contrato o identificador del servicio.'
      );
      return;
    }

    if (isRecarga) {
      const diag = diagnosticarTelefono(referencia);
      if (diag) {
        setError(diag);
        return;
      }
    }

    setIsValidating(true);
    try {
      const resp = await paymentApi.validarReferencia({
        idServicio: parseInt(product.serviceId, 10),
        idProducto: parseInt(product.id, 10),
        referencia: referencia.trim(),
        monto: monto ? parseFloat(monto) : undefined,
      });

      setValidationResult(resp);

      if (!resp.esValida) {
        setError(resp.mensaje || 'La referencia ingresada no cumple con el formato requerido.');
      } else if (resp.montoSugerido && !monto) {
        setMonto(resp.montoSugerido.toString());
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error al validar la referencia.';
      setError(msg);
    } finally {
      setIsValidating(false);
    }
  };

  const handleContinue = (e: React.FormEvent) => {
    e.preventDefault();
    if (!validationResult?.esValida) {
      setError('Debes validar la referencia antes de continuar con el pago.');
      return;
    }

    const numMonto = parseFloat(monto);
    if (isNaN(numMonto) || numMonto <= 0) {
      setError('Ingresa un monto válido mayor a $0.00 MXN.');
      return;
    }

    if (!isFixedPrice && numMonto < 10) {
      setError('El importe mínimo para pago de monto abierto es de $10.00 MXN.');
      return;
    }

    if (numMonto > 50000) {
      setError('El importe máximo por transacción es de $50,000.00 MXN.');
      return;
    }

    onProceedTo2FA({
      product,
      referencia: referencia.trim(),
      monto: numMonto,
      validation: validationResult,
    });
  };

  const getReferenceHint = () => {
    if (isRecarga) return 'Número celular México de 10 dígitos (ej. 5512345678)';
    if (product.frontType === '1') {
      return 'Ingresa el número de cuenta, TAG o identificador de tarjeta';
    }
    const refType = (product.referenceType || '').toLowerCase();
    switch (refType) {
      case 'b':
        return 'Código de barras numérico del recibo (8 a 30 dígitos)';
      case 'c':
        return 'Número de contrato / cuenta numérica (6 a 16 dígitos)';
      case 'bc':
        return 'Contrato (10 a 12 dígitos) o código de barras (20 a 30 dígitos)';
      case 'a':
      default:
        return 'Referencia alfanumérica o cuenta de cliente';
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in"
      role="dialog"
      aria-modal="true"
      aria-label={`Validación de referencia para ${product.name}`}
    >
      <div className="bg-white rounded-3xl shadow-2xl border border-slate-200 w-full max-w-lg max-h-[92vh] overflow-y-auto animate-in zoom-in-95">
        {/* Modal Header */}
        <div className="bg-slate-50 px-6 py-4 border-b border-slate-100 flex items-center justify-between sticky top-0 z-10">
          <div className="flex items-center gap-3 min-w-0">
            <BrandLogo
              serviceId={product.serviceId}
              serviceName={product.service}
              logoUrl={product.logoUrl}
              size="sm"
            />
            <div className="min-w-0">
              <p className="text-[11px] font-bold uppercase tracking-wider text-blue-700">
                Paso 1 de 2: Validación de {isRecarga ? 'Línea Celular' : 'Referencia'}
              </p>
              <h3 className="text-base font-extrabold text-slate-900 leading-tight truncate">
                {product.name}
              </h3>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar"
            className="p-1.5 rounded-full text-slate-400 hover:text-slate-600 hover:bg-slate-200/60 transition-colors shrink-0"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleContinue} noValidate className="p-6 space-y-5">
          {/* Selected Product Info Card */}
          <div className="p-3.5 rounded-2xl bg-blue-50/70 border border-blue-100 space-y-2 text-xs">
            <div className="flex items-center justify-between gap-2">
              <span className="font-semibold text-blue-950">
                Proveedor: <strong>{product.service}</strong>
              </span>
              <span className="font-bold text-blue-800 shrink-0">
                {isFixedPrice ? `Importe Fijo: $${fixedPrice.toFixed(2)}` : 'Monto Abierto'}
              </span>
            </div>
            {product.legend && product.legend.trim() && (
              <p className="text-[11px] text-blue-900/80 leading-relaxed border-t border-blue-100 pt-2">
                {product.legend.trim()}
              </p>
            )}
            <div className="flex flex-wrap items-center gap-2">
              {requiresVerificationDigit && (
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-indigo-100 text-indigo-800 border border-indigo-200 text-[10px] font-bold">
                  <ShieldAlert className="w-3 h-3" />
                  <span>Dígito verificador requerido (Módulo 10/11)</span>
                </span>
              )}
              {hasAyudaInfo && (
                <button
                  type="button"
                  onClick={() => setShowAyudaBox((prev) => !prev)}
                  className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-white text-blue-700 border border-blue-200 hover:bg-blue-100 text-[10px] font-bold transition-colors"
                >
                  <HelpCircle className="w-3 h-3" />
                  <span>{showAyudaBox ? 'Ocultar ayuda' : 'Ayuda para localizar tu referencia'}</span>
                </button>
              )}
            </div>
            {hasAyudaInfo && showAyudaBox && (
              <div className="p-2.5 rounded-xl bg-white border border-blue-200 text-[11px] text-slate-700 leading-relaxed">
                Localiza el número de servicio, contrato o código de barras en la parte superior o inferior de tu estado de cuenta o recibo físico. Captura todos los dígitos sin guiones ni espacios.
              </div>
            )}
          </div>

          {/* Reference Input + Validate Button */}
          <div className="space-y-1.5">
            <div className="flex items-center justify-between gap-2">
              <label htmlFor="ref-input" className="text-xs font-bold text-slate-700 flex items-center gap-1.5">
                {isRecarga ? (
                  <Smartphone className="w-4 h-4 text-emerald-600" />
                ) : product.frontType === '1' ? (
                  <Gift className="w-4 h-4 text-indigo-600" />
                ) : (
                  <FileText className="w-4 h-4 text-blue-600" />
                )}
                <span>
                  {isRecarga ? 'Número Telefónico (10 dígitos)' : 'Referencia / Contrato'}
                </span>
              </label>
              {isRecarga && hasSavedPhone && (
                <button
                  type="button"
                  onClick={() => {
                    setReferencia(user!.telefonoMovil!);
                    setValidationResult(null);
                    setError(null);
                  }}
                  className="text-[11px] font-bold text-blue-700 hover:text-blue-900 underline"
                >
                  Usar mi celular ({user!.telefonoMovil})
                </button>
              )}
            </div>

            <div className="flex gap-2">
              <input
                id="ref-input"
                type="text"
                maxLength={isRecarga ? 10 : 35}
                inputMode={isRecarga ? 'numeric' : 'text'}
                value={referencia}
                onChange={(e) => {
                  const val = isRecarga
                    ? e.target.value.replace(/\D/g, '').slice(0, 10)
                    : e.target.value;
                  setReferencia(val);
                  if (validationResult) setValidationResult(null);
                  if (error) setError(null);
                }}
                placeholder={isRecarga ? '5512345678' : 'Ingresa tu referencia...'}
                className="flex-1 px-4 py-2.5 bg-slate-50 border border-slate-200 rounded-xl text-sm font-mono text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-600 focus:bg-white transition-all"
              />

              <button
                type="button"
                onClick={handleValidateReference}
                disabled={isValidating || !referencia.trim()}
                className="px-4 py-2.5 rounded-xl bg-slate-900 hover:bg-blue-900 text-white text-xs font-bold flex items-center gap-1.5 transition-all disabled:opacity-50 shrink-0 shadow-sm"
              >
                {isValidating ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <span>Validar</span>
                )}
              </button>
            </div>
            <p className="text-[11px] text-slate-500">{getReferenceHint()}</p>
            {isRecarga && referencia && phoneDiag && !error && (
              <p className="text-[11px] text-amber-700 font-medium">{phoneDiag}</p>
            )}
            {phoneValid && !validationResult && (
              <p className="text-[11px] text-emerald-700 font-semibold">
                Formato telefónico IFT válido. Pulsa &quot;Validar&quot; para verificar con el proveedor.
              </p>
            )}
          </div>

          {/* Validation Result Banner */}
          {validationResult && validationResult.esValida && (
            <div className="p-3.5 rounded-2xl bg-emerald-50 border border-emerald-200 flex items-start gap-2.5 text-emerald-800 text-xs animate-in fade-in">
              <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0" />
              <div className="space-y-0.5">
                <p className="font-bold">Referencia Verificada Exitosamente</p>
                <p>{validationResult.mensaje}</p>
                {validationResult.servicio && (
                  <p className="text-[11px] text-emerald-700">
                    Servicio confirmado: <strong>{validationResult.servicio}</strong>
                    {validationResult.producto ? ` (${validationResult.producto})` : ''}
                  </p>
                )}
              </div>
            </div>
          )}

          {error && (
            <div className="p-3.5 rounded-2xl bg-red-50 border border-red-200 flex items-start gap-2.5 text-red-700 text-xs animate-in fade-in">
              <AlertCircle className="w-5 h-5 text-red-600 shrink-0" />
              <p className="font-medium leading-relaxed">{error}</p>
            </div>
          )}

          {/* Amount Input */}
          <div className="space-y-1.5">
            <label htmlFor="monto-input" className="text-xs font-bold text-slate-700">
              Importe a Pagar (MXN)
            </label>
            <div className="relative">
              <span className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400 font-bold text-sm">
                $
              </span>
              <input
                id="monto-input"
                type="number"
                step="0.01"
                min={isFixedPrice ? '1' : '10'}
                max="50000"
                value={monto}
                readOnly={isFixedPrice}
                onChange={(e) => setMonto(e.target.value)}
                placeholder="0.00"
                className={`w-full pl-8 pr-14 py-2.5 border rounded-xl text-sm font-bold text-slate-900 focus:outline-none transition-all ${
                  isFixedPrice
                    ? 'bg-slate-100 border-slate-200 text-slate-600 cursor-not-allowed'
                    : 'bg-slate-50 border-slate-200 focus:ring-2 focus:ring-blue-600 focus:bg-white'
                }`}
              />
              <span className="absolute right-4 top-1/2 -translate-y-1/2 text-slate-400 font-bold text-xs">
                MXN
              </span>
            </div>
            {!isFixedPrice && (
              <p className="text-[11px] text-slate-500">
                {suggestedMinPrice
                  ? `Importe mínimo $10.00 MXN (referencia sugerida del proveedor: $${suggestedMinPrice.toFixed(2)} MXN).`
                  : 'Ingresa el importe exacto de tu recibo o monto deseado (mínimo $10.00 MXN).'}
              </p>
            )}
          </div>

          {/* Footer Buttons */}
          <div className="pt-2 flex items-center justify-end gap-3 border-t border-slate-100">
            <button
              type="button"
              onClick={onClose}
              className="px-5 py-2.5 rounded-2xl text-xs font-bold text-slate-600 hover:bg-slate-100 transition-colors"
            >
              Cancelar
            </button>

            <button
              type="submit"
              disabled={!validationResult?.esValida}
              className="px-6 py-2.5 rounded-2xl bg-gradient-to-r from-blue-900 to-blue-700 hover:from-blue-800 hover:to-blue-600 text-white text-xs font-bold flex items-center gap-2 transition-all shadow-md shadow-blue-900/20 disabled:opacity-40 disabled:cursor-not-allowed active:scale-95"
            >
              <span>Continuar a Confirmación 2FA</span>
              <ArrowRight className="w-4 h-4" />
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export const ReferenceModal: React.FC<ReferenceModalProps> = (props) => {
  if (!props.isOpen || !props.product) return null;
  return (
    <ReferenceModalInner
      key={`${props.product.serviceId}-${props.product.id}`}
      product={props.product}
      onClose={props.onClose}
      onProceedTo2FA={props.onProceedTo2FA}
    />
  );
};
