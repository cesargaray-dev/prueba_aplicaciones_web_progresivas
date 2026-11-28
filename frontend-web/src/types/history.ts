export interface ItemHistorialPago {
  idPago: number;
  folioTicket: string;
  numeroAutorizacion: string;
  idTxGestopago?: number;
  idServicio: number;
  servicio: string;
  idProducto: number;
  producto: string;
  telefono?: string;
  referencia?: string;
  monto: number;
  comision?: number;
  saldoAnterior?: number;
  saldoRestante?: number;
  concepto?: string;
  fechaOperacion?: string;
  estado: string;
  logoUrl?: string;
  leyenda?: string;
}

export interface HistorialPagosResponse {
  codigo?: number;
  mensaje?: string;
  numeroCuenta: string;
  totalMovimientos: number;
  pagos: ItemHistorialPago[];
}

export interface HistorialPagosRequest {
  numeroCuenta: string;
}
