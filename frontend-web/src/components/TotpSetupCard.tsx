import React, { useEffect, useState } from 'react';
import { Check, Clock, Copy, KeyRound, Smartphone } from 'lucide-react';
import type { TotpSetupResponse } from '../types/auth';

export interface TotpSetupCardProps {
  totpSetup: TotpSetupResponse;
  codigo: string;
  onCodigoChange: (val: string) => void;
  inputId?: string;
  disabled?: boolean;
  compact?: boolean;
}

const calcRemainingSeconds = (period: number): number => {
  const safePeriod = period > 0 ? period : 30;
  return safePeriod - (Math.floor(Date.now() / 1000) % safePeriod);
};

export const TotpSetupCard: React.FC<TotpSetupCardProps> = ({
  totpSetup,
  codigo,
  onCodigoChange,
  inputId = 'totp-code-input',
  disabled = false,
  compact = false,
}) => {
  const period = totpSetup.periodoSegundos || 30;
  const [secondsLeft, setSecondsLeft] = useState<number>(() => calcRemainingSeconds(period));
  const [copiedSecret, setCopiedSecret] = useState<boolean>(false);

  useEffect(() => {
    const tick = () => setSecondsLeft(calcRemainingSeconds(period));
    tick();
    const timer = setInterval(tick, 1000);
    return () => clearInterval(timer);
  }, [period]);

  const displaySecret = totpSetup.secretFormateado || totpSetup.secret;

  const handleCopySecret = async () => {
    try {
      await navigator.clipboard.writeText(totpSetup.secret);
      setCopiedSecret(true);
      setTimeout(() => setCopiedSecret(false), 1800);
    } catch {
      // Ignore clipboard unavailability
    }
  };

  return (
    <div className="rounded-2xl bg-gradient-to-br from-slate-50 via-blue-50/50 to-indigo-50/60 border border-blue-200/80 p-4 space-y-4 shadow-sm">
      {/* Header & Live 30s Cycle Indicator */}
      <div className="space-y-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <div className="p-2 rounded-xl bg-blue-900 text-amber-300 shadow-sm">
              <Smartphone className="w-4 h-4" />
            </div>
            <div>
              <p className="text-xs font-black text-slate-900">
                Google Authenticator (Código dinámico {period}s)
              </p>
              <p className="text-[11px] text-slate-600">
                {totpSetup.emisor || 'Banco Digital'} &bull; {totpSetup.cuenta}
              </p>
            </div>
          </div>

          <span
            className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-mono font-extrabold border ${
              secondsLeft <= 5
                ? 'bg-amber-100 text-amber-900 border-amber-300'
                : 'bg-emerald-50 text-emerald-800 border-emerald-200'
            }`}
          >
            <Clock className="w-3.5 h-3.5" />
            <span>Cambio en {secondsLeft}s</span>
          </span>
        </div>

        <div className="w-full h-1.5 bg-slate-200/80 rounded-full overflow-hidden">
          <div
            className={`h-full transition-all duration-700 rounded-full ${
              secondsLeft <= 5 ? 'bg-amber-500' : 'bg-emerald-500'
            }`}
            style={{ width: `${Math.max(5, (secondsLeft / period) * 100)}%` }}
          />
        </div>
      </div>

      {/* QR + Manual Secret + Instructions */}
      <div className="grid grid-cols-1 sm:grid-cols-12 gap-4 items-center">
        <div className="sm:col-span-5 flex flex-col items-center justify-center">
          <div
            className="totp-qr-frame p-3 rounded-2xl border-2 border-slate-200 shadow-md flex items-center justify-center"
            style={{ backgroundColor: '#ffffff' }}
          >
            <img
              src={`data:image/png;base64,${totpSetup.qrPngBase64}`}
              alt="QR Google Authenticator"
              className={compact ? 'w-32 h-32 object-contain' : 'w-40 h-40 object-contain'}
            />
          </div>
          <span className="text-[10px] font-bold text-slate-500 mt-1.5 text-center">
            Escanea desde Google Authenticator
          </span>
        </div>

        <div className="sm:col-span-7 space-y-2.5">
          <ol className="space-y-1.5 text-[11px] text-slate-700 font-medium">
            <li className="flex items-start gap-2">
              <span className="w-4 h-4 rounded-full bg-blue-900 text-white text-[10px] font-black flex items-center justify-center shrink-0 mt-0.5">
                1
              </span>
              <span>Abre la app <strong>Google Authenticator</strong> en tu celular.</span>
            </li>
            <li className="flex items-start gap-2">
              <span className="w-4 h-4 rounded-full bg-blue-900 text-white text-[10px] font-black flex items-center justify-center shrink-0 mt-0.5">
                2
              </span>
              <span>Escanea el código QR o ingresa la clave secreta manual.</span>
            </li>
            <li className="flex items-start gap-2">
              <span className="w-4 h-4 rounded-full bg-blue-900 text-white text-[10px] font-black flex items-center justify-center shrink-0 mt-0.5">
                3
              </span>
              <span>
                Escribe el código de <strong>{totpSetup.digitos || 6} dígitos</strong> que se actualiza cada{' '}
                <strong>{period} segundos</strong>.
              </span>
            </li>
          </ol>

          <div className="p-2.5 rounded-xl bg-white border border-slate-200 space-y-1">
            <div className="flex items-center justify-between gap-2">
              <span className="text-[10px] font-extrabold uppercase tracking-wider text-slate-500">
                Clave secreta manual
              </span>
              <button
                type="button"
                onClick={handleCopySecret}
                aria-label="Copiar clave secreta"
                className="inline-flex items-center gap-1 px-2 py-0.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-800 text-[10px] font-extrabold transition-colors"
              >
                {copiedSecret ? (
                  <>
                    <Check className="w-3 h-3 text-emerald-600" />
                    <span>¡Copiada!</span>
                  </>
                ) : (
                  <>
                    <Copy className="w-3 h-3" />
                    <span>Copiar</span>
                  </>
                )}
              </button>
            </div>
            <p className="font-mono text-xs font-black tracking-wider text-blue-950 break-all select-all">
              {displaySecret}
            </p>
          </div>
        </div>
      </div>

      {/* 6-digit TOTP verification input */}
      <div className="space-y-1.5 pt-1">
        <label
          htmlFor={inputId}
          className="text-xs font-extrabold text-slate-800 flex items-center gap-1.5"
        >
          <KeyRound className="w-3.5 h-3.5 text-blue-700" />
          <span>Código de Google Authenticator (6 dígitos) *</span>
        </label>
        <input
          id={inputId}
          type="text"
          inputMode="numeric"
          maxLength={6}
          value={codigo}
          onChange={(e) => onCodigoChange(e.target.value.replace(/\D/g, '').slice(0, 6))}
          placeholder="000000"
          autoComplete="one-time-code"
          disabled={disabled}
          className="w-full px-4 py-3 bg-white border border-slate-300 rounded-xl text-center text-xl font-mono font-black tracking-[0.5em] text-slate-900 focus:outline-none focus:ring-2 focus:ring-emerald-600 transition-all disabled:opacity-60"
        />
      </div>
    </div>
  );
};
