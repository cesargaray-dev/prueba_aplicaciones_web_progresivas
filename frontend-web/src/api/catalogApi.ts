import { apiClient } from './client';
import type { ProductDTO, ProductListResponse } from '../types/catalog';

export const catalogApi = {
  getProductos: async (): Promise<ProductDTO[]> => {
    const response = await apiClient.get<ProductListResponse>('/gestopago/productos');
    return response.data || [];
  },
};
