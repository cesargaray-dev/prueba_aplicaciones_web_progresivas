import { createContext } from 'react';
import type {
  AbonoSaldoRequest,
  ActualizarContactoRequest,
  CambiarContrasenaRequest,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  UserProfile,
} from '../types/auth';

export interface AuthContextType {
  user: LoginResponse | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  isInitializing: boolean;
  secondsRemaining: number;
  sessionExpiredMessage: string | null;
  clearSessionExpired: () => void;
  sessionNotice: string | null;
  clearSessionNotice: () => void;
  login: (credentials: LoginRequest) => Promise<void>;
  register: (payload: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
  updateBalance: (newBalance: number) => void;
  refreshBalance: () => Promise<UserProfile | null>;
  updateContacto: (payload: ActualizarContactoRequest) => Promise<UserProfile>;
  cambiarContrasena: (payload: CambiarContrasenaRequest) => Promise<void>;
  abonarSaldo: (payload: AbonoSaldoRequest) => Promise<UserProfile>;
  resetInactivityTimer: () => void;
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined);
