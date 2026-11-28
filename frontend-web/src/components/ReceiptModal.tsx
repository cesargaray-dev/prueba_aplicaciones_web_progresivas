import React, { useEffect, useState } from 'react';
import {
  CheckCircle2,
  XCircle,
  Clock,
  Mail,
  Printer,
  Copy,
  Check,
  X,
  ArrowRight,
  AlertTriangle,
  Send,
  Loader2,
  FileDown,
  History,
} from 'lucide-react';
import type { PagoServicioResponse } from '../types/payment';
import type { ProductDTO } from '../types/catalog';
import { useAuth } from '../context/useAuth';
import { paymentApi } from '../api/paymentApi';
import { BrandLogo } from './BrandLogo';
import {
  esCorreoPlaceholder,
  esCorreoValido,
  esRecargaTelefonica,
  obtenerLeyendaComprobante,
} from '../utils/validation';

interface ReceiptModalProps {
  isOpen: boolean;
  onClose: () => void;
  receipt: PagoServicioResponse | null;
  product: ProductDTO | null;
  referencia: string;
  onGoToHistory?: () => void;
}

interface ReceiptModalContentProps {
  onClose: () => void;
  receipt: PagoServicioResponse;
  product: ProductDTO | null;
  referencia: string;
  onGoToHistory?: () => void;
}

type NormalizedResultState = 'EXITOSA' | 'RECHAZADA' | 'PENDIENTE';

const normalizeOperationState = (receipt: PagoServicioResponse): NormalizedResultState => {
  const raw = (receipt.estadoOperacion || '').toUpperCase();
  if (raw === 'RECHAZADA' || raw === 'FALLIDA' || raw === 'ERROR') {
    return 'RECHAZADA';
  }
  if (raw === 'PENDIENTE' || raw === 'NO_CONFIRMADA' || raw === 'EN_PROCESO' || receipt.codigo === 202) {
    return 'PENDIENTE';
  }
  return 'EXITOSA';
};

const ReceiptModalContent: React.FC<ReceiptModalContentProps> = ({
  onClose,
  receipt,
  product,
  referencia,
  onGoToHistory,
}) => {
  const { user } = useAuth();
  const [fallbackDate] = useState(() => new Date().toLocaleString('es-MX'));
  const [copied, setCopied] = useState(false);
  const [downloadingPdf, setDownloadingPdf] = useState(false);
  const [pdfNotice, setPdfNotice] = useState<string | null>(null);

  const [showResend, setShowResend] = useState(false);
  const [resendEmail, setResendEmail] = useState(
    user?.email && !esCorreoPlaceholder(user.email) ? user.email : ''
  );
  const [resending, setResending] = useState(false);
  const [resendStatus, setResendStatus] = useState<{
    ok: boolean;
    msg: string;
    destino?: string;
  } | null>(null);

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose]);

  const resultState = normalizeOperationState(receipt);
  const isSuccess = resultState === 'EXITOSA';
  const isPending = resultState === 'PENDIENTE';
  const isRejected = resultState === 'RECHAZADA';

  const isRecarga = product ? esRecargaTelefonica(product) : false;
  const serviceNameClean = (receipt.servicio || product?.service || 'Servicio').trim();
  const rawProductClean = (receipt.producto || product?.name || '').trim();
  const displayTicketProduct = (() => {
    if (isRecarga) {
      const lowProd = rawProductClean.toLowerCase();
      const lowServ = serviceNameClean.toLowerCase();
      if (lowProd.includes('recarga') && lowProd.includes(lowServ)) return rawProductClean;
      if (lowProd.startsWith('recarga')) {
        const rest = rawProductClean.slice(7).trim();
        return rest ? `Recarga ${serviceNameClean} ${rest}` : `Recarga ${serviceNameClean}`;
      }
      return rawProductClean
        ? `Recarga ${serviceNameClean} - ${rawProductClean}`
        : `Recarga ${serviceNameClean}`;
    }
    return rawProductClean || serviceNameClean;
  })();

  const motivoRechazoGestoPago = (() => {
    const rawMotivo = (receipt.motivoProveedor || '').trim();
    if (rawMotivo) return rawMotivo;
    const msg = (receipt.mensajeOperacion || receipt.mensaje || '').trim();
    if (/origen\s+(no\s+)?valido/i.test(msg)) return 'Origen no valido';
    return msg || 'Origen no valido';
  })();

  const numeroRespuestaGestoPago =
    receipt.numeroAutorizacion ||
    (receipt.codigoProveedor === '-1' ? '-1' : undefined) ||
    (isRejected ? '-1' : undefined);

  const leyendaTexto = obtenerLeyendaComprobante({
    leyenda: receipt.leyenda,
    esRechazada: isRejected,
    numAuth: isRejected ? numeroRespuestaGestoPago : receipt.numeroAutorizacion,
    folio: receipt.folioTicket,
  });

  const formatCurrency = (amount?: number) =>
    new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(amount ?? 0);

  const handlePrint = () => {
    window.print();
  };

  const handleDownloadPdf = async () => {
    if (!receipt.folioTicket) {
      window.print();
      return;
    }
    setDownloadingPdf(true);
    setPdfNotice(null);
    try {
      const blob = await paymentApi.descargarComprobantePdf(receipt.folioTicket);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `comprobante-${receipt.folioTicket}.pdf`;
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

  const handleCopy = async () => {
    const summary =
      `TICKET DE OPERACIÓN GESTOPAGO - BANCO DIGITAL\n` +
      `Operación / Producto: ${displayTicketProduct}\n` +
      `Resultado: ${isRejected ? 'Operación rechazada' : resultState}\n` +
      (isRejected
        ? `Motivo GestoPago: ${motivoRechazoGestoPago}\n` +
          `Número Respuesta GestoPago: ${numeroRespuestaGestoPago || '-1'}${
            receipt.codigoProveedor && receipt.codigoProveedor !== numeroRespuestaGestoPago
              ? ` (Código: ${receipt.codigoProveedor})`
              : ''
          }\n`
        : `ID Transacción GestoPago (ID_TX): ${receipt.idTransaccion ?? 'N/A'}\n` +
          `No. Autorización GestoPago: ${receipt.numeroAutorizacion || 'N/A'}\n`) +
      `Folio UPC GestoPago: ${receipt.upc || 'N/A'}\n` +
      `Folio Ticket: ${receipt.folioTicket || 'N/A'}\n` +
      `Referencia: ${referencia}\n` +
      `Monto: ${formatCurrency(receipt.montoPagado)}\n` +
      `Fecha: ${receipt.fechaHoraOperacion || fallbackDate}\n\n` +
      `LEYENDA OFICIAL Y ACLARACIONES:\n${leyendaTexto}`;
    try {
      await navigator.clipboard.writeText(summary);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Ignore clipboard failure
    }
  };

  const handleResend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user?.numeroCuenta || !receipt.folioTicket) return;

    const target = resendEmail.trim();
    if (target) {
      if (!esCorreoValido(target) || esCorreoPlaceholder(target)) {
        setResendStatus({
          ok: false,
          msg: 'Ingresa un correo electrónico real (no de ejemplo) para enviar el comprobante.',
        });
        return;
      }
    }

    setResending(true);
    setResendStatus(null);
    try {
      const res = await paymentApi.reenviarComprobante({
        numeroCuenta: user.numeroCuenta,
        folioTicket: receipt.folioTicket,
        correoDestino: target || undefined,
      });
      setResendStatus({
        ok: res.enviado,
        msg:
          res.mensaje ||
          (res.enviado ? 'Comprobante reenviado.' : 'No se pudo enviar el correo.'),
        destino: res.correoDestino,
      });
    } catch (err: unknown) {
      setResendStatus({
        ok: false,
        msg: err instanceof Error ? err.message : 'Error al reenviar el comprobante.',
      });
    } finally {
      setResending(false);
    }
  };

  const efectivoEnviado = resendStatus ? resendStatus.ok : Boolean(receipt.correoEnviado);
  const efectivoDestino = resendStatus?.destino || receipt.correoDestino || user?.email;
  const efectivoMensaje =
    resendStatus?.msg ||
    receipt.mensajeCorreo ||
    (efectivoEnviado
      ? `Comprobante oficial enviado a ${efectivoDestino || 'tu correo registrado'}.`
      : 'No se pudo confirmar la entrega por correo. Puedes reenviarlo a otro correo abajo.');

  const heroConfig = isSuccess
    ? {
        gradient: 'from-emerald-600 to-teal-700',
        subtextClass: 'text-emerald-100',
        icon: <CheckCircle2 className="w-10 h-10 text-white" />,
        title: '¡Operación Exitosa!',
        subtitle: receipt.mensajeOperacion || receipt.mensaje || 'Tu pago ha sido confirmado por el proveedor',
      }
    : isPending
    ? {
        gradient: 'from-amber-500 to-orange-600',
        subtextClass: 'text-amber-50',
        icon: <Clock className="w-10 h-10 text-white" />,
        title: 'Operación Pendiente / No Confirmada',
        subtitle:
          receipt.mensajeOperacion ||
          receipt.mensaje ||
          'La transacción está en conciliación con el proveedor.',
      }
    : {
        gradient: 'from-red-600 to-rose-700',
        subtextClass: 'text-red-100',
        icon: <XCircle className="w-10 h-10 text-white" />,
        title: 'Operación rechazada',
        subtitle: `${displayTicketProduct} · ${motivoRechazoGestoPago} (${numeroRespuestaGestoPago || '-1'})`,
      };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/70 backdrop-blur-sm animate-in fade-in"
      role="dialog"
      aria-modal="true"
      aria-label={heroConfig.title}
    >
      <div className="print-voucher bg-white rounded-3xl shadow-2xl border border-slate-200 w-full max-w-md max-h-[92vh] overflow-y-auto animate-in zoom-in-95">
        {/* 3-State Hero Banner */}
        <div
          className={`bg-gradient-to-br ${heroConfig.gradient} px-6 py-7 text-center text-white relative`}
        >
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar"
            className="no-print absolute top-4 right-4 p-1.5 rounded-full bg-white/10 hover:bg-white/20 text-white transition-colors"
          >
            <X className="w-4 h-4" />
          </button>

          <div className="w-16 h-16 bg-white/20 backdrop-blur-md rounded-full flex items-center justify-center mx-auto mb-3 border border-white/30 shadow-inner">
            {heroConfig.icon}
          </div>

          <h2 className="text-xl font-black tracking-tight">{heroConfig.title}</h2>
          <p className={`${heroConfig.subtextClass} text-xs font-medium mt-1`}>
            {heroConfig.subtitle}
          </p>

          {(receipt.folioTicket || receipt.upc) && (
            <div className="mt-4 flex flex-wrap justify-center gap-2">
              {receipt.folioTicket && (
                <div className="inline-block px-4 py-1.5 rounded-full bg-black/20 backdrop-blur-sm text-xs font-bold tracking-wider uppercase border border-white/10">
                  Folio: {receipt.folioTicket}
                </div>
              )}
              {receipt.upc && (
                <div className="inline-block px-3 py-1.5 rounded-full bg-black/20 backdrop-blur-sm text-[11px] font-mono font-bold tracking-wider uppercase border border-white/10">
                  UPC: {receipt.upc}
                </div>
              )}
            </div>
          )}
        </div>

        {/* Amount & Provider Details */}
        <div className="p-6 space-y-5">
          <div className="text-center pb-4 border-b border-dashed border-slate-200">
            <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400">
              {isRejected ? 'Importe Solicitado (No Cobrado)' : isPending ? 'Importe en Conciliación' : 'Importe Pagado'}
            </span>
            <div className="text-3xl font-black text-slate-900 mt-1">
              {formatCurrency(receipt.montoPagado)}
            </div>
            {(product || receipt.producto) && (
              <div className="inline-flex items-center gap-2 mt-2 px-3 py-1 bg-slate-50 rounded-full border border-slate-100">
                <BrandLogo
                  serviceId={product?.serviceId}
                  serviceName={receipt.servicio || product?.service || 'Servicio'}
                  logoUrl={receipt.logoUrl || product?.logoUrl}
                  size="sm"
                />
                <span className="text-xs font-bold text-slate-700">
                  {displayTicketProduct}
                </span>
              </div>
            )}
          </div>

          {/* Pending Advisory */}
          {isPending && (
            <div className="p-3.5 rounded-2xl bg-amber-50 border border-amber-200 flex items-start gap-2.5 text-amber-900 text-xs">
              <AlertTriangle className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
              <div className="space-y-1">
                <p className="font-bold">No confirmada por el proveedor</p>
                <p className="text-[11px] leading-relaxed">
                  Nunca trates esta operación como exitosa ni realices un pago duplicado de inmediato. Revisa tu historial en unos minutos para conocer el resultado final de conciliación.
                </p>
              </div>
            </div>
          )}

          {/* Rejected Advisory */}
          {isRejected && (
            <div className="p-3.5 rounded-2xl bg-red-50 border border-red-200 flex items-start gap-2.5 text-red-900 text-xs">
              <XCircle className="w-5 h-5 text-red-600 shrink-0 mt-0.5" />
              <div className="space-y-1">
                <p className="font-bold">
                  {displayTicketProduct} — Operación rechazada ({motivoRechazoGestoPago})
                </p>
                <p className="text-[11px] leading-relaxed">
                  Se generó y firmó el payload AES-256 hacia GestoPago, respondiendo{' '}
                  <strong>{numeroRespuestaGestoPago || '-1'}</strong> ({motivoRechazoGestoPago}). No se realizó ningún cargo a tu cuenta.
                </p>
              </div>
            </div>
          )}

          {/* Official GestoPago Proof of Purchase OR Rejected Signed Request Ticket */}
          {(receipt.idTransaccion ||
            numeroRespuestaGestoPago ||
            receipt.upc ||
            receipt.codigoProveedor) && (
            <div
              className={`p-3.5 rounded-2xl bg-gradient-to-br ${
                isSuccess
                  ? 'from-blue-950 via-slate-900 to-indigo-950 border-blue-400/30'
                  : 'from-slate-950 via-rose-950/90 to-slate-900 border-rose-400/35'
              } text-white border shadow-md space-y-2`}
            >
              <div className="flex items-center justify-between gap-2">
                <span className="text-[10px] font-black uppercase tracking-[0.12em] text-amber-300">
                  {isSuccess
                    ? '✓ Compra Confirmada por GestoPago'
                    : '⚡ Respuesta GestoPago · Operación Rechazada'}
                </span>
                <span
                  className={`px-2 py-0.5 rounded-full font-mono text-[10px] font-bold border ${
                    isSuccess
                      ? 'bg-emerald-500/20 border-emerald-400/40 text-emerald-300'
                      : 'bg-rose-500/25 border-rose-300/40 text-rose-200'
                  }`}
                >
                  {isSuccess
                    ? `COD ${receipt.codigoProveedor || '01'}`
                    : `GESTOPAGO: ${numeroRespuestaGestoPago || '-1'}`}
                </span>
              </div>
              <div className="grid grid-cols-1 gap-1.5 text-xs">
                {!isSuccess && (
                  <>
                    <div className="flex items-center justify-between gap-2 bg-white/10 rounded-xl px-3 py-1.5">
                      <span className="text-rose-200 font-semibold">Operación:</span>
                      <span className="font-bold text-white text-xs text-right">
                        {displayTicketProduct}
                      </span>
                    </div>
                    <div className="flex items-center justify-between gap-2 bg-white/10 rounded-xl px-3 py-1.5">
                      <span className="text-rose-200 font-semibold">Estado:</span>
                      <span className="font-mono font-black text-rose-200 text-xs">
                        Operación rechazada
                      </span>
                    </div>
                    <div className="flex items-center justify-between gap-2 bg-white/10 rounded-xl px-3 py-1.5">
                      <span className="text-rose-200 font-semibold">Motivo GestoPago:</span>
                      <span className="font-mono font-black text-amber-200 text-xs">
                        {motivoRechazoGestoPago}
                      </span>
                    </div>
                    <div className="flex items-center justify-between gap-2 bg-white/10 rounded-xl px-3 py-1.5">
                      <span className="text-rose-200 font-semibold">Número GestoPago:</span>
                      <span className="font-mono font-black text-white text-sm tracking-wider">
                        {numeroRespuestaGestoPago || '-1'}
                      </span>
                    </div>
                  </>
                )}
                {receipt.idTransaccion !== undefined && receipt.idTransaccion !== null && (
                  <div className="flex items-center justify-between gap-2 bg-white/10 rounded-xl px-3 py-1.5">
                    <span className="text-blue-200 font-semibold">ID Transacción GestoPago:</span>
                    <span className="font-mono font-black text-amber-200 text-sm tracking-wider">
                      #{receipt.idTransaccion}
                    </span>
                  </div>
                )}
                {isSuccess && receipt.numeroAutorizacion && (
                  <div className="flex items-center justify-between gap-2 bg-white/10 rounded-xl px-3 py-1.5">
                    <span className="text-blue-200 font-semibold">No. Autorización GestoPago:</span>
                    <span className="font-mono font-black text-white text-sm tracking-wider break-all">
                      {receipt.numeroAutorizacion}
                    </span>
                  </div>
                )}
                {receipt.upc && (
                  <div className="flex items-center justify-between gap-2 bg-white/5 rounded-xl px-3 py-1">
                    <span className="text-blue-200/85 font-medium">Folio UPC GestoPago:</span>
                    <span className="font-mono font-bold text-sky-200 break-all">
                      {receipt.upc}
                    </span>
                  </div>
                )}
                {receipt.firmaDigital && (
                  <div className="flex items-center justify-between gap-2 bg-white/5 rounded-xl px-3 py-1">
                    <span className="text-blue-200/85 font-medium">Firma AES-256 (signed):</span>
                    <span className="font-mono text-[10px] text-slate-200 break-all">
                      {receipt.firmaDigital}
                    </span>
                  </div>
                )}
              </div>
            </div>
          )}

          <div className="space-y-2.5 text-xs">
            {receipt.idTransaccion !== undefined && receipt.idTransaccion !== null && (
              <div className="flex justify-between items-center gap-3 py-1 border-b border-slate-50">
                <span className="text-slate-500 font-medium">ID TX GestoPago:</span>
                <span className="font-mono font-black text-blue-900 bg-blue-50 px-2 py-0.5 rounded">
                  {receipt.idTransaccion}
                </span>
              </div>
            )}

            {receipt.numeroAutorizacion && (
              <div className="flex justify-between items-center gap-3 py-1 border-b border-slate-50">
                <span className="text-slate-500 font-medium">No. Autorización Proveedor:</span>
                <span className="font-mono font-black text-blue-900 bg-blue-50 px-2 py-0.5 rounded break-all">
                  {receipt.numeroAutorizacion}
                </span>
              </div>
            )}

            {receipt.codigoProveedor && (
              <div className="flex justify-between items-center gap-3 py-1 border-b border-slate-50">
                <span className="text-slate-500 font-medium">Código Proveedor:</span>
                <span className="font-mono font-bold text-slate-800">{receipt.codigoProveedor}</span>
              </div>
            )}

            <div className="flex justify-between items-center gap-3 py-1 border-b border-slate-50">
              <span className="text-slate-500 font-medium">
                {isRecarga ? 'Teléfono Recargado:' : 'Referencia / Contrato:'}
              </span>
              <span className="font-mono font-bold text-slate-900 break-all">{referencia}</span>
            </div>

            <div className="flex justify-between items-center gap-3 py-1 border-b border-slate-50">
              <span className="text-slate-500 font-medium">Fecha y Hora:</span>
              <span className="font-mono text-slate-700">
                {receipt.fechaHoraOperacion || fallbackDate}
              </span>
            </div>

            {!isRejected && (
              <div className="flex justify-between items-center gap-3 py-1 border-b border-slate-50">
                <span className="text-slate-500 font-medium">Comisión:</span>
                <span className="font-bold text-emerald-700">
                  {formatCurrency(receipt.comision || 0)}
                </span>
              </div>
            )}

            <div className="flex justify-between items-center gap-3 py-1">
              <span className="text-slate-500 font-medium">
                {isRejected ? 'Saldo Intacto en Cuenta:' : 'Saldo Restante en Cuenta:'}
              </span>
              <span className="font-black text-slate-900">
                {formatCurrency(receipt.saldoRestanteCuenta ?? user?.saldo ?? 0)}
              </span>
            </div>
          </div>

          {/* Official Regulatory Legend & Clarifications (Visible in UI and Print) */}
          <div className="p-3.5 rounded-2xl bg-amber-50 border border-amber-200 text-amber-900 space-y-1">
            <p className="text-[10px] font-black uppercase tracking-[0.12em] text-amber-800">
              LEYENDA OFICIAL Y ACLARACIONES
            </p>
            <p className="text-[11px] leading-relaxed font-medium">{leyendaTexto}</p>
          </div>

          {/* Real Email Delivery Status & Resend (ONLY on EXITOSA) */}
          {isSuccess && (
            <div
              className={`no-print p-3.5 rounded-2xl border space-y-2.5 ${
                efectivoEnviado
                  ? 'bg-blue-50 border-blue-100 text-blue-950'
                  : 'bg-amber-50 border-amber-200 text-amber-900'
              }`}
            >
              <div className="flex items-start gap-2.5">
                {efectivoEnviado ? (
                  <Mail className="w-5 h-5 text-blue-700 shrink-0 mt-0.5" />
                ) : (
                  <AlertTriangle className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
                )}
                <div className="text-[11px] leading-relaxed flex-1">
                  <p className="font-bold">
                    {efectivoEnviado
                      ? 'Comprobante enviado vía Brevo'
                      : 'Entrega de correo pendiente'}
                  </p>
                  <p>{efectivoMensaje}</p>
                </div>
                <button
                  type="button"
                  onClick={() => setShowResend((s) => !s)}
                  className="text-[11px] font-bold underline shrink-0"
                >
                  {showResend ? 'Ocultar' : 'Reenviar'}
                </button>
              </div>

              {showResend && (
                <form onSubmit={handleResend} className="pt-2 border-t border-black/10 flex gap-2">
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
                </form>
              )}
            </div>
          )}

          {pdfNotice && (
            <p className="no-print text-[11px] text-amber-700 bg-amber-50 border border-amber-200 rounded-xl px-3 py-2">
              {pdfNotice}
            </p>
          )}

          {/* Actions */}
          <div className="no-print pt-2 flex flex-col gap-2">
            <div className={`grid ${!isPending && Boolean(isSuccess || receipt.folioTicket) ? 'grid-cols-3' : 'grid-cols-2'} gap-2`}>
              {!isPending && Boolean(isSuccess || receipt.folioTicket) && (
                <button
                  type="button"
                  onClick={handleDownloadPdf}
                  disabled={downloadingPdf}
                  className="py-2.5 px-2 rounded-2xl border border-blue-200 bg-blue-50 hover:bg-blue-100 text-blue-900 text-xs font-bold flex items-center justify-center gap-1.5 transition-colors disabled:opacity-50"
                >
                  {downloadingPdf ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <FileDown className="w-4 h-4" />
                  )}
                  <span>Descargar PDF</span>
                </button>
              )}

              <button
                type="button"
                onClick={handlePrint}
                className="py-2.5 px-2 rounded-2xl border border-slate-200 hover:bg-slate-50 text-slate-700 text-xs font-bold flex items-center justify-center gap-1.5 transition-colors"
              >
                <Printer className="w-4 h-4 text-slate-500" />
                <span>Imprimir Ticket</span>
              </button>

              <button
                type="button"
                onClick={handleCopy}
                className={`py-2.5 px-2 rounded-2xl border text-xs font-bold flex items-center justify-center gap-1.5 transition-colors ${
                  copied
                    ? 'border-emerald-300 bg-emerald-50 text-emerald-700'
                    : 'border-slate-200 hover:bg-slate-50 text-slate-700'
                }`}
              >
                {copied ? (
                  <>
                    <Check className="w-4 h-4 text-emerald-600" />
                    <span>¡Copiado!</span>
                  </>
                ) : (
                  <>
                    <Copy className="w-4 h-4 text-slate-500" />
                    <span>Copiar</span>
                  </>
                )}
              </button>
            </div>

            {isPending && onGoToHistory && (
              <button
                type="button"
                onClick={() => {
                  onClose();
                  onGoToHistory();
                }}
                className="w-full py-2.5 rounded-2xl border border-amber-300 bg-amber-50 hover:bg-amber-100 text-amber-900 text-xs font-bold flex items-center justify-center gap-2 transition-colors"
              >
                <History className="w-4 h-4" />
                <span>Ir al Historial para Conciliar</span>
              </button>
            )}

            <button
              type="button"
              onClick={onClose}
              className="w-full py-3 rounded-2xl bg-slate-900 hover:bg-blue-900 text-white text-xs font-bold flex items-center justify-center gap-2 transition-all shadow-md"
            >
              <span>
                {isRejected ? 'Cerrar y Volver al Catálogo' : 'Finalizar y Volver al Catálogo'}
              </span>
              <ArrowRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export const ReceiptModal: React.FC<ReceiptModalProps> = ({
  isOpen,
  onClose,
  receipt,
  product,
  referencia,
  onGoToHistory,
}) => {
  if (!isOpen || !receipt) return null;
  return (
    <ReceiptModalContent
      key={receipt.folioTicket || receipt.upc || receipt.idTransaccion || 'payment-result'}
      onClose={onClose}
      receipt={receipt}
      product={product}
      referencia={referencia}
      onGoToHistory={onGoToHistory}
    />
  );
};
