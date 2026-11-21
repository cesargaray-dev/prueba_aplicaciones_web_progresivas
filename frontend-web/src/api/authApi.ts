import { apiClient, setAuthToken } from './client';
import type { ApiRequestOptions } from './client';
import type {
  AbonoSaldoRequest,
  ActualizarContactoRequest,
  CambiarContrasenaRequest,
  EnviarCodigoRegistroRequest,
  EnviarCodigoRegistroResponse,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  UserProfile,
} from '../types/auth';

export const authApi = {
  login: async (credentials: LoginRequest): Promise<LoginResponse> => {
    const response = await apiClient.post<LoginResponse>('/auth/login', credentials, {
      skipUnauthorizedHandler: true,
    });
    if (response?.token) {
      setAuthToken(response.token);
    }
    return response;
  },

  enviarCodigoRegistro: async (
    payload: EnviarCodigoRegistroRequest
  ): Promise<EnviarCodigoRegistroResponse> => {
    return apiClient.post<EnviarCodigoRegistroResponse>('/auth/register/enviar-codigo', payload, {
      skipUnauthorizedHandler: true,
    });
  },

  register: async (payload: RegisterRequest): Promise<LoginResponse> => {
    const response = await apiClient.post<LoginResponse>('/auth/register', payload, {
      skipUnauthorizedHandler: true,
    });
    if (response?.token) {
      setAuthToken(response.token);
    }
    return response;
  },

  keepAlive: async (): Promise<{ activa: boolean }> => {
    return apiClient.post<{ activa: boolean }>('/auth/keep-alive', {});
  },

  logout: async (): Promise<void> => {
    try {
      await apiClient.post('/auth/logout', {}, { skipUnauthorizedHandler: true });
    } catch {
      // El cierre de sesión local siempre debe completarse aunque el servidor no responda.
    } finally {
      setAuthToken(null);
    }
  },

  getMe: async (options?: ApiRequestOptions): Promise<UserProfile> => {
    return apiClient.get<UserProfile>('/auth/me', options);
  },

  checkSessionStatus: async (): Promise<{ activa: boolean }> => {
    return apiClient.get<{ activa: boolean }>('/auth/session-status');
  },

  updateContacto: async (payload: ActualizarContactoRequest): Promise<UserProfile> => {
    return apiClient.put<UserProfile>('/auth/me/perfil', payload);
  },

  cambiarContrasena: async (
    payload: CambiarContrasenaRequest
  ): Promise<{ codigo?: number; mensaje?: string }> => {
    return apiClient.put<{ codigo?: number; mensaje?: string }>('/auth/me/contrasena', payload);
  },

  abonarSaldo: async (payload: AbonoSaldoRequest): Promise<UserProfile> => {
    return apiClient.post<UserProfile>('/auth/me/abonar', payload);
  },
};
