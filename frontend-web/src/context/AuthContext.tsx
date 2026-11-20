import React, { useState, useEffect, useCallback, useRef } from 'react';
import type {
  AbonoSaldoRequest,
  ActualizarContactoRequest,
  CambiarContrasenaRequest,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  UserProfile,
} from '../types/auth';
import { authApi } from '../api/authApi';
import { getAuthToken, setAuthToken, setOnUnauthorizedHandler } from '../api/client';
import { AuthContext } from './authContextInstance';

const INACTIVITY_TIMEOUT_SECONDS = 300; // 5 minutes

const profileToLoginResponse = (profile: UserProfile, token: string): LoginResponse => ({
  token,
  usuario: profile.usuario,
  clienteId: profile.clienteId,
  nombre: profile.nombre,
  segundoNombre: profile.segundoNombre,
  apellidoPaterno: profile.apellidoPaterno,
  apellidoMaterno: profile.apellidoMaterno,
  nombreCompleto: profile.nombreCompleto,
  email: profile.email,
  telefonoMovil: profile.telefonoMovil,
  numeroCuenta: profile.numeroCuenta,
  saldo: profile.saldo,
  ultimoAcceso: profile.ultimoAcceso,
});

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<LoginResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [isInitializing, setIsInitializing] = useState<boolean>(() => Boolean(getAuthToken()));
  const [secondsRemaining, setSecondsRemaining] = useState<number>(INACTIVITY_TIMEOUT_SECONDS);
  const [sessionExpiredMessage, setSessionExpiredMessage] = useState<string | null>(null);

  const lastActivityRef = useRef<number>(0);
  const userRef = useRef<LoginResponse | null>(null);

  useEffect(() => {
    userRef.current = user;
  }, [user]);

  const clearSessionExpired = useCallback(() => {
    setSessionExpiredMessage(null);
  }, []);

  const resetInactivityTimer = useCallback(() => {
    lastActivityRef.current = Date.now();
    setSecondsRemaining(INACTIVITY_TIMEOUT_SECONDS);
  }, []);

  const syncFromProfile = useCallback((profile: UserProfile) => {
    setUser((prev) => {
      if (!prev) {
        return profileToLoginResponse(profile, getAuthToken() || '');
      }
      return {
        ...prev,
        saldo: typeof profile.saldo === 'number' ? profile.saldo : prev.saldo,
        email: profile.email || prev.email,
        telefonoMovil: profile.telefonoMovil ?? prev.telefonoMovil,
        nombre: profile.nombre ?? prev.nombre,
        segundoNombre: profile.segundoNombre ?? prev.segundoNombre ?? '',
        apellidoPaterno: profile.apellidoPaterno ?? prev.apellidoPaterno,
        apellidoMaterno: profile.apellidoMaterno ?? prev.apellidoMaterno,
        nombreCompleto: profile.nombreCompleto || prev.nombreCompleto,
        ultimoAcceso: profile.ultimoAcceso ?? prev.ultimoAcceso,
      };
    });
  }, []);

  const logout = useCallback(async () => {
    const hadSession = Boolean(userRef.current || getAuthToken());
    userRef.current = null;
    setUser(null);
    setAuthToken(null);
    setSecondsRemaining(INACTIVITY_TIMEOUT_SECONDS);

    if (hadSession) {
      try {
        await authApi.logout();
      } catch (err) {
        console.warn('Error during backend logout notification:', err);
      }
    }
  }, []);

  const logoutRef = useRef(logout);
  useEffect(() => {
    logoutRef.current = logout;
  }, [logout]);

  // Global 401 unauthorized handler & initial boot hydration from sessionStorage token
  useEffect(() => {
    setOnUnauthorizedHandler(() => {
      userRef.current = null;
      setUser(null);
      setAuthToken(null);
      setSecondsRemaining(INACTIVITY_TIMEOUT_SECONDS);
      setSessionExpiredMessage(
        'Tu sesión ha expirado o fue cerrada por seguridad. Inicia sesión nuevamente.'
      );
    });

    let cancelled = false;
    const existingToken = getAuthToken();

    if (existingToken) {
      authApi
        .getMe({ skipUnauthorizedHandler: true })
        .then((profile) => {
          if (cancelled) return;
          const hydrated = profileToLoginResponse(profile, existingToken);
          userRef.current = hydrated;
          setUser(hydrated);
          lastActivityRef.current = Date.now();
          setSecondsRemaining(INACTIVITY_TIMEOUT_SECONDS);
        })
        .catch(() => {
          if (cancelled) return;
          setAuthToken(null);
          userRef.current = null;
          setUser(null);
        })
        .finally(() => {
          if (!cancelled) {
            setIsInitializing(false);
          }
        });
    }

    return () => {
      cancelled = true;
      setOnUnauthorizedHandler(null);
    };
  }, []);

  const login = async (credentials: LoginRequest) => {
    setIsLoading(true);
    setSessionExpiredMessage(null);
    try {
      const response = await authApi.login(credentials);
      userRef.current = response;
      setUser(response);
      resetInactivityTimer();
      try {
        const profile = await authApi.getMe();
        syncFromProfile(profile);
      } catch {
        // Non-fatal profile sync fallback
      }
    } finally {
      setIsLoading(false);
    }
  };

  const register = async (payload: RegisterRequest) => {
    setIsLoading(true);
    setSessionExpiredMessage(null);
    try {
      const response = await authApi.register(payload);
      userRef.current = response;
      setUser(response);
      resetInactivityTimer();
      try {
        const profile = await authApi.getMe();
        syncFromProfile(profile);
      } catch {
        // Non-fatal profile sync fallback
      }
    } finally {
      setIsLoading(false);
    }
  };

  const updateBalance = useCallback((newBalance: number) => {
    setUser((prev) => {
      if (!prev) return null;
      return { ...prev, saldo: newBalance };
    });
  }, []);

  const refreshBalance = useCallback(async (): Promise<UserProfile | null> => {
    if (!getAuthToken() && !userRef.current) return null;
    try {
      const profile = await authApi.getMe();
      if (profile) {
        syncFromProfile(profile);
        return profile;
      }
      return null;
    } catch (err) {
      console.warn('Could not refresh profile via /auth/me:', err);
      return null;
    }
  }, [syncFromProfile]);

  const updateContacto = async (payload: ActualizarContactoRequest): Promise<UserProfile> => {
    const profile = await authApi.updateContacto(payload);
    syncFromProfile(profile);
    return profile;
  };

  const cambiarContrasena = async (payload: CambiarContrasenaRequest): Promise<void> => {
    await authApi.cambiarContrasena(payload);
  };

  const abonarSaldo = async (payload: AbonoSaldoRequest): Promise<UserProfile> => {
    const profile = await authApi.abonarSaldo(payload);
    syncFromProfile(profile);
    return profile;
  };

  const isAuthenticated = Boolean(user);

  // Inactivity tracking: bound strictly to session lifecycle so balance/profile updates do NOT reset timer
  useEffect(() => {
    if (!isAuthenticated) return;
    lastActivityRef.current = Date.now();

    const handleUserActivity = () => {
      const now = Date.now();
      if (now - lastActivityRef.current > 1000) {
        lastActivityRef.current = now;
        setSecondsRemaining(INACTIVITY_TIMEOUT_SECONDS);
      }
    };

    window.addEventListener('mousemove', handleUserActivity);
    window.addEventListener('keydown', handleUserActivity);
    window.addEventListener('click', handleUserActivity);
    window.addEventListener('scroll', handleUserActivity);
    window.addEventListener('touchstart', handleUserActivity);

    const interval = setInterval(() => {
      const elapsedSeconds = Math.floor((Date.now() - lastActivityRef.current) / 1000);
      const remaining = Math.max(0, INACTIVITY_TIMEOUT_SECONDS - elapsedSeconds);
      setSecondsRemaining(remaining);

      if (remaining === 0) {
        console.warn('Sesión cerrada por inactividad de 5 minutos');
        setSessionExpiredMessage(
          'Tu sesión se cerró automáticamente tras 5 minutos de inactividad.'
        );
        void logoutRef.current();
      }
    }, 1000);

    return () => {
      window.removeEventListener('mousemove', handleUserActivity);
      window.removeEventListener('keydown', handleUserActivity);
      window.removeEventListener('click', handleUserActivity);
      window.removeEventListener('scroll', handleUserActivity);
      window.removeEventListener('touchstart', handleUserActivity);
      clearInterval(interval);
    };
  }, [isAuthenticated]);

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated,
        isLoading,
        isInitializing,
        secondsRemaining,
        sessionExpiredMessage,
        clearSessionExpired,
        login,
        register,
        logout,
        updateBalance,
        refreshBalance,
        updateContacto,
        cambiarContrasena,
        abonarSaldo,
        resetInactivityTimer,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};
