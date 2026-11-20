import { apiClient } from './client';
import type {
  ValidarReferenciaRequest,
  ValidarReferenciaResponse,
  Generar2FAResponse,
  PagoServicioRequest,
  PagoServicioResponse,
  ReenviarComprobanteRequest,
  EmailEnvioResultado,
} from '../types/payment';

export const paymentApi = {
  validarReferencia: async (
    request: ValidarReferenciaRequest
  ): Promise<ValidarReferenciaResponse> => {
    return apiClient.post<ValidarReferenciaResponse>('/pagos/validar-referencia', request);
  },

  generar2FA: async (numeroCuenta: string): Promise<Generar2FAResponse> => {
    return apiClient.post<Generar2FAResponse>('/pagos/2fa/generar', { numeroCuenta });
  },

  pagarServicio: async (
    request: PagoServicioRequest
  ): Promise<PagoServicioResponse> => {
    return apiClient.post<PagoServicioResponse>('/pagos/servicios', request);
  },

  reenviarComprobante: async (
    request: ReenviarComprobanteRequest
  ): Promise<EmailEnvioResultado> => {
    return apiClient.post<EmailEnvioResultado>('/pagos/comprobante/reenviar', request);
  },

  descargarComprobantePdf: async (folioTicket: string): Promise<Blob> => {
    return apiClient.getBlob(`/pagos/comprobante/${encodeURIComponent(folioTicket.trim())}/pdf`);
  },
};
