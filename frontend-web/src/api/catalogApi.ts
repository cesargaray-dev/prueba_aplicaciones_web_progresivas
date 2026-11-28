import { apiClient } from './client';
import type { ProductDTO, ProductListResponse } from '../types/catalog';

export interface CodigoPostalSepomexResponse {
  codigoPostal: string;
  valido: boolean;
  pais?: string;
  codigoEstado?: string;
  estado?: string;
  municipio?: string;
  colonias: string[];
  mensaje?: string;
}

export const catalogApi = {
  getProductos: async (): Promise<ProductDTO[]> => {
    const response = await apiClient.get<ProductListResponse>('/gestopago/productos');
    return response.data || [];
  },

  consultarCodigoPostal: async (cp: string): Promise<CodigoPostalSepomexResponse> => {
    return apiClient.get<CodigoPostalSepomexResponse>(
      `/catalogos/cp/${encodeURIComponent(cp.trim())}`,
      { skipUnauthorizedHandler: true }
    );
  },
};
