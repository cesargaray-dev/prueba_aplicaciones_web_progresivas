import { apiClient } from './client';
import type { AdminUpdateUserRequest, AdminUserItem, RegisterRequest } from '../types/auth';

export const adminApi = {
  listarUsuarios: async (): Promise<AdminUserItem[]> => {
    return apiClient.get<AdminUserItem[]>('/admin/usuarios');
  },

  crearUsuario: async (payload: RegisterRequest): Promise<AdminUserItem> => {
    return apiClient.post<AdminUserItem>('/admin/usuarios', payload);
  },

  actualizarUsuario: async (
    clienteId: number,
    payload: AdminUpdateUserRequest
  ): Promise<AdminUserItem> => {
    return apiClient.put<AdminUserItem>(`/admin/usuarios/${clienteId}`, payload);
  },

  eliminarUsuario: async (clienteId: number): Promise<{ codigo: number; mensaje: string }> => {
    return apiClient.delete<{ codigo: number; mensaje: string }>(`/admin/usuarios/${clienteId}`);
  },
};
