import React, { useEffect, useState } from 'react';
import {
  X,
  CheckCircle2,
  XCircle,
  Clock,
  Printer,
  Hash,
  Calendar,
  Mail,
  Send,
  Loader2,
  Copy,
  Check,
  FileDown,
} from 'lucide-react';
import type { ItemHistorialPago } from '../types/history';
import { useAuth } from '../context/useAuth';
import { paymentApi } from '../api/paymentApi';
import { BrandLogo } from './BrandLogo';
import { esCorreoPlaceholder, esCorreoValido, esItemHistorialRecarga } from '../utils/validation';

interface TransactionDetailModalProps {
  item: ItemHistorialPago | null;
  isOpen: boolean;
  onClose: () => void;
}

interface TransactionDetailContentProps {
  item: ItemHistorialPago;
  onClose: () => void;
}

const TransactionDetailContent: React.FC<TransactionDetailContentProps> = ({ item, onClose }) => {
  const { user } = useAuth();
  const [resendEmail, setResendEmail] = useState(
    user?.email && !esCorreoPlaceholder(user.email) ? user.email : ''
  );
  const [resending, setResending] = useState(false);
  const [downloadingPdf, setDownloadingPdf] = useState(false);
  const [pdfNotice, setPdfNotice] = useState<string | null>(null);
  const [copiedFolio, setCopiedFolio] = useState(false);
  const [resendFeedback, setResendFeedback] = useState<{ ok: boolean; msg: string } | null>(null);

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose]);

  const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(amount);

  const getStatusDisplay = (estado?: string) => {
    const norm = (estado || '').toUpperCase();
    if (norm === 'EXITOSA' || norm === 'COMPLETADA') {
      return {
        label: 'EXITOSA',
        color: 'bg-emerald-100 text-emerald-800 border-emerald-200',
        icon: <CheckCircle2 className="w-4 h-4 text-emerald-600" />,
      };
    }
    if (norm === 'PENDIENTE' || norm === 'EN_PROCESO') {
      return {
        label: 'PENDIENTE',
        color: 'bg-amber-100 text-amber-800 border-amber-200',
        icon: <Clock className="w-4 h-4 text-amber-600" />,
      };
    }
    return {
      label: norm || 'RECHAZADA',
      color: 'bg-red-100 text-red-800 border-red-200',
      icon: <XCircle className="w-4 h-4 text-red-600" />,
    };
  };

  const statusInfo = getStatusDisplay(item.estado);
  const isRecargaMovil = esItemHistorialRecarga(item);

  const handleCopyFolio = async () => {
    try {
      await navigator.clipboard.writeText(item.folioTicket);
      setCopiedFolio(true);
      setTimeout(() => setCopiedFolio(false), 1800);
    } catch {
      // Ignore clipboard error
    }
  };

  const handleDownloadPdf = async () => {
    if (!item.folioTicket) {
      window.print();
      return;
    }
    setDownloadingPdf(true);
    setPdfNotice(null);
    try {
      const blob = await paymentApi.descargarComprobantePdf(item.folioTicket);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `comprobante-${item.folioTicket}.pdf`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
    } catch {
      setPdfNotice('Descarga directa no disponible; se abrió la vista de impresión para guardar en PDF.');
      window.print();
    } finally {
      setDownloadingPdf(false);
    }
  };

  const handleResend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user?.numeroCuenta) return;

    const target = resendEmail.trim();
    if (target && (!esCorreoValido(target) || esCorreoPlaceholder(target))) {
      setResendFeedback({
        ok: false,
        msg: 'Ingresa un correo electrónico real para enviar el comprobante.',
      });
      return;
    }

    setResending(true);
    setResendFeedback(null);
    try {
      const res = await paymentApi.reenviarComprobante({
        numeroCuenta: user.numeroCuenta,
        folioTicket: item.folioTicket,
        correoDestino: target || undefined,
      });
      setResendFeedback({
        ok: res.enviado,
        msg:
          res.mensaje ||
          (res.enviado ? 'Comprobante enviado correctamente.' : 'No se pudo enviar el correo.'),
      });
    } catch (err: unknown) {
      setResendFeedback({
        ok: false,
        msg: err instanceof Error ? err.message : 'Error al reenviar comprobante.',
      });
    } finally {
      setResending(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-sm animate-in fade-in"
      role="dialog"
      aria-modal="true"
      aria-label={`Detalle de movimiento ${item.servicio}`}
    >
      <div className="print-voucher bg-white rounded-3xl shadow-2xl border border-slate-200 w-full max-w-md max-h-[90vh] overflow-y-auto animate-in zoom-in-95">
        {/* Header */}
        <div className="bg-slate-50 px-6 py-4 border-b border-slate-100 flex items-center justify-between sticky top-0 z-10">
          <div className="flex items-center gap-3 min-w-0">
            <BrandLogo
              serviceId={item.idServicio}
              serviceName={item.servicio}
              logoUrl={item.logoUrl}
              size="sm"
            />
            <div className="min-w-0">
              <p className="text-[11px] font-bold uppercase tracking-wider text-slate-400">
                Detalle de Movimiento
              </p>
              <h3 className="text-base font-extrabold text-slate-900 leading-tight truncate">
                {item.servicio}
              </h3>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar"
            className="no-print p-1.5 rounded-full text-slate-400 hover:text-slate-600 hover:bg-slate-200/60 transition-colors shrink-0"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="p-6 space-y-5">
          {/* Status & Product Title */}
          <div className="flex items-center justify-between gap-2 bg-slate-50 p-4 rounded-2xl border border-slate-100">
            <div className="min-w-0">
              <p className="text-xs font-bold text-slate-900 truncate">{item.producto}</p>
              <p className="text-[11px] text-slate-500 mt-0.5 line-clamp-2">{item.concepto}</p>
            </div>
            <span
              className={`px-3 py-1 rounded-full text-xs font-bold border inline-flex items-center gap-1.5 shrink-0 ${statusInfo.color}`}
            >
              {statusInfo.icon}
              <span>{statusInfo.label}</span>
            </span>
          </div>

          {/* Details List */}
          <div className="space-y-2.5 text-xs">
            <div className="flex justify-between items-center gap-3 py-1 border-b border-slate-100">
              <span className="text-slate-500 font-medium flex items-center gap-1">
                <Hash className="w-3 h-3 text-slate-400" />
                Folio de Ticket:
              </span>
              <span className="inline-flex items-center gap-1.5 font-mono font-bold text-slate-900 break-all">
                <span>{item.folioTicket}</span>
                <button
                  type="button"
                  onClick={handleCopyFolio}
                  title="Copiar folio"
                  aria-label="Copiar folio"
                  className="no-print p-1 rounded hover:bg-slate-100 text-slate-400 hover:text-blue-700"
                >
                  {copiedFolio ? (
                    <Check className="w-3.5 h-3.5 text-emerald-600" />
                  ) : (
                    <Copy className="w-3.5 h-3.5" />
                  )}
                </button>
              </span>
            </div>

            {item.idTxGestopago !== undefined && item.idTxGestopago !== null && (
              <div className="flex justify-between gap-3 py-1 border-b border-slate-100">
                <span className="text-slate-500 font-medium">ID Transacción GestoPago:</span>
                <span className="font-mono font-black text-emerald-800 bg-emerald-50 px-2 py-0.5 rounded break-all">
                  #{item.idTxGestopago}
                </span>
              </div>
            )}

            <div className="flex justify-between gap-3 py-1 border-b border-slate-100">
              <span className="text-slate-500 font-medium">No. Autorización GestoPago:</span>
              <span className="font-mono font-bold text-blue-900 bg-blue-50 px-2 py-0.5 rounded break-all">
                {item.numeroAutorizacion || 'N/A'}
              </span>
            </div>

            {isRecargaMovil ? (
              <div className="flex justify-between gap-3 py-1 border-b border-slate-100">
                <span className="text-slate-500 font-medium">Teléfono Recargado:</span>
                <span className="font-mono font-bold text-slate-900">{item.telefono}</span>
              </div>
            ) : (
              item.referencia && (
                <div className="flex justify-between gap-3 py-1 border-b border-slate-100">
                  <span className="text-slate-500 font-medium">Referencia de Pago:</span>
                  <span className="font-mono font-bold text-slate-900 break-all">
                    {item.referencia}
                  </span>
                </div>
              )
            )}

            <div className="flex justify-between gap-3 py-1 border-b border-slate-100">
              <span className="text-slate-500 font-medium flex items-center gap-1">
                <Calendar className="w-3 h-3 text-slate-400" />
                Fecha de Operación:
              </span>
              <span className="font-mono text-slate-800">{item.fechaOperacion}</span>
            </div>

            {item.saldoAnterior !== undefined && (
              <div className="flex justify-between gap-3 py-1 border-b border-slate-100">
                <span className="text-slate-500 font-medium">Saldo Anterior:</span>
                <span className="text-slate-700">{formatCurrency(item.saldoAnterior)}</span>
              </div>
            )}

            <div className="flex justify-between gap-3 py-1 border-b border-slate-100">
              <span className="text-slate-500 font-medium">Monto Pagado:</span>
              <span className="font-bold text-slate-900">{formatCurrency(item.monto)}</span>
            </div>

            <div className="flex justify-between gap-3 py-1 border-b border-slate-100">
              <span className="text-slate-500 font-medium">Comisión:</span>
              <span className="text-slate-700">{formatCurrency(item.comision || 0)}</span>
            </div>

            {item.saldoRestante !== undefined && (
              <div className="flex justify-between gap-3 py-1">
                <span className="text-slate-500 font-medium">Saldo Posterior:</span>
                <span className="font-bold text-blue-900">{formatCurrency(item.saldoRestante)}</span>
              </div>
            )}
          </div>

          {/* Resend Brevo receipt from History */}
          <form
            onSubmit={handleResend}
            className="no-print p-3.5 rounded-2xl bg-blue-50/70 border border-blue-100 space-y-2"
          >
            <label className="text-[11px] font-bold text-blue-950 flex items-center gap-1.5">
              <Mail className="w-3.5 h-3.5 text-blue-700" />
              <span>Enviar comprobante oficial por correo (Brevo)</span>
            </label>
            <div className="flex gap-2">
              <input
                type="email"
                value={resendEmail}
                onChange={(e) => setResendEmail(e.target.value)}
                placeholder="tucorreo@gmail.com"
                className="flex-1 px-3 py-1.5 bg-white border border-slate-300 rounded-xl text-xs text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-600"
              />
              <button
                type="submit"
                disabled={resending}
                className="px-3 py-1.5 rounded-xl bg-blue-900 hover:bg-blue-800 text-white text-xs font-bold inline-flex items-center gap-1 disabled:opacity-50"
              >
                {resending ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <Send className="w-3.5 h-3.5" />
                )}
                <span>Enviar</span>
              </button>
            </div>
            {resendFeedback && (
              <p
                className={`text-[11px] font-semibold ${
                  resendFeedback.ok ? 'text-emerald-700' : 'text-red-700'
                }`}
              >
                {resendFeedback.msg}
              </p>
            )}
          </form>

          {pdfNotice && (
            <p className="no-print text-[11px] text-amber-700 bg-amber-50 border border-amber-200 rounded-xl px-3 py-2">
              {pdfNotice}
            </p>
          )}
        </div>

        {/* Footer */}
        <div className="no-print bg-slate-50 px-6 py-4 border-t border-slate-100 flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={handleDownloadPdf}
              disabled={downloadingPdf}
              className="flex items-center gap-1.5 px-3 py-2 rounded-2xl border border-blue-200 bg-blue-50 text-blue-900 hover:bg-blue-100 text-xs font-bold transition-colors disabled:opacity-50"
            >
              {downloadingPdf ? (
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
              ) : (
                <FileDown className="w-3.5 h-3.5" />
              )}
              <span>PDF</span>
            </button>

            <button
              type="button"
              onClick={() => window.print()}
              className="flex items-center gap-1.5 px-3 py-2 rounded-2xl border border-slate-300 text-slate-700 hover:bg-white text-xs font-bold transition-colors"
            >
              <Printer className="w-3.5 h-3.5" />
              <span>Imprimir</span>
            </button>
          </div>

          <button
            type="button"
            onClick={onClose}
            className="px-5 py-2 rounded-2xl bg-slate-900 hover:bg-blue-900 text-white text-xs font-bold transition-colors"
          >
            Cerrar
          </button>
        </div>
      </div>
    </div>
  );
};

export const TransactionDetailModal: React.FC<TransactionDetailModalProps> = ({
  item,
  isOpen,
  onClose,
}) => {
  if (!isOpen || !item) return null;
  return (
    <TransactionDetailContent
      key={item.folioTicket || item.idPago}
      item={item}
      onClose={onClose}
    />
  );
};
