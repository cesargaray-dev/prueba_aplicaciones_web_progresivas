export interface ProductDTO {
  id: string; // idProducto
  name: string; // producto
  service: string; // servicio
  serviceId: string; // idServicio
  price: string; // precio
  referenceType: string; // tipoReferencia: a, b, c, bc
  frontType: string; // tipoFront: 1 (recargas/telefonía) or 2 (servicios/recibos)
  hasVerificationDigit?: boolean | string; // hasDigitoVerificador
  categoryId?: string; // idCatTipoServicio
  legend?: string | null;
  showAyuda?: boolean | string;
  logoUrl?: string | null;
}

export interface ProductListResponse {
  message?: string;
  codigoCache?: number;
  origenCache?: string;
  data: ProductDTO[];
  status?: string;
}
