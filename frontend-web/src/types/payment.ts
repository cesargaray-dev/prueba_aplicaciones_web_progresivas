export interface ValidarReferenciaRequest {
  idServicio: number;
  idProducto?: number;
  referencia: string;
  monto?: number;
}

export interface ValidarReferenciaResponse {
  esValida: boolean;
  mensaje: string;
  tipoFront?: number;
  tipoReferencia?: string;
  requiereMonto?: boolean;
  montoSugerido?: number;
  servicio?: string;
  producto?: string;
  hasVerificationDigit?: boolean | string;
}

export interface Generar2FARequest {
  numeroCuenta: string;
}

export interface Generar2FAResponse {
  codigo?: number;
  mensaje?: string;
  numeroCuenta?: string;
  codigo2FA?: string | null;
  vigenciaSegundos?: number;
  expiraEnSegundos?: number;
  fechaExpiracion?: string;
  correoEnviado?: boolean;
  correoDestino?: string;
  mensajeCorreo?: string;
  messageId?: string;
  brevoMessageId?: string;
}

export interface PagoServicioRequest {
  numeroCuenta: string;
  idServicio: number;
  idProducto: number;
  monto: number;
  telefono?: string;
  referencia?: string;
  codigo2FA: string;
}

export type EstadoOperacionPago = 'EXITOSA' | 'RECHAZADA' | 'PENDIENTE';

export interface PagoServicioResponse {
  codigo?: number;
  mensaje?: string;
  estadoOperacion?: EstadoOperacionPago | string;
  codigoProveedor?: string;
  mensajeOperacion?: string;
  idTransaccion?: number;
  numeroAutorizacion?: string;
  folioTicket?: string;
  upc?: string;
  montoPagado?: number;
  comision?: number;
  saldoRestanteCuenta?: number;
  fechaHoraOperacion?: string;
  concepto?: string;
  servicio?: string;
  producto?: string;
  logoUrl?: string;
  correoEnviado?: boolean;
  correoDestino?: string;
  mensajeCorreo?: string;
  brevoMessageId?: string;
}

export interface ReenviarComprobanteRequest {
  numeroCuenta: string;
  folioTicket: string;
  correoDestino?: string;
}

export interface EmailEnvioResultado {
  enviado: boolean;
  correoDestino?: string;
  messageId?: string;
  mensaje?: string;
  correoPlaceholder?: boolean;
}
