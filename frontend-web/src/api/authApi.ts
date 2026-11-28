import { apiClient, setAuthToken } from './client';
import type { ApiRequestOptions } from './client';
import type {
  AbonoSaldoRequest,
  ActualizarContactoRequest,
  CambiarContrasenaRequest,
  DisponibilidadRegistroParams,
  DisponibilidadRegistroResponse,
  EnviarCodigoRegistroRequest,
  EnviarCodigoRegistroResponse,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterTotpRequest,
  TotpEstadoResponse,
  TotpSetupResponse,
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

  verificarDisponibilidadRegistro: async (
    params: DisponibilidadRegistroParams
  ): Promise<DisponibilidadRegistroResponse> => {
    const query = new URLSearchParams();
    if (params.correo?.trim()) query.set('correo', params.correo.trim());
    if (params.usuario?.trim()) query.set('usuario', params.usuario.trim());
    if (params.curp?.trim()) query.set('curp', params.curp.trim().toUpperCase());
    if (params.rfc?.trim()) query.set('rfc', params.rfc.trim().toUpperCase());
    const qs = query.toString();
    if (!qs) {
      return {};
    }
    return apiClient.get<DisponibilidadRegistroResponse>(
      `/auth/register/disponibilidad?${qs}`,
      { skipUnauthorizedHandler: true }
    );
  },

  iniciarTotpRegistro: async (payload: RegisterTotpRequest): Promise<TotpSetupResponse> => {
    return apiClient.post<TotpSetupResponse>('/auth/register/totp', payload, {
      skipUnauthorizedHandler: true,
    });
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

  obtenerEstadoTotp: async (): Promise<TotpEstadoResponse> => {
    return apiClient.get<TotpEstadoResponse>('/auth/me/totp/estado');
  },

  iniciarTotpPerfil: async (): Promise<TotpSetupResponse> => {
    return apiClient.post<TotpSetupResponse>('/auth/me/totp/iniciar', {});
  },

  activarTotpPerfil: async (payload: {
    secret: string;
    codigo: string;
  }): Promise<TotpEstadoResponse> => {
    return apiClient.post<TotpEstadoResponse>('/auth/me/totp/activar', payload);
  },

  desactivarTotpPerfil: async (payload: { codigo: string }): Promise<TotpEstadoResponse> => {
    return apiClient.post<TotpEstadoResponse>('/auth/me/totp/desactivar', payload);
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
