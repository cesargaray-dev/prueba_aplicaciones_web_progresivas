import { apiClient } from './client';
import type { HistorialPagosResponse } from '../types/history';

export const historyApi = {
  getHistorial: async (numeroCuenta: string): Promise<HistorialPagosResponse> => {
    return apiClient.post<HistorialPagosResponse>('/pagos/historial', { numeroCuenta });
  },
};
