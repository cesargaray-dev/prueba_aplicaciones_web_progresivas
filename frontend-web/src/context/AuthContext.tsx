import React, { useState, useEffect, useCallback, useRef } from 'react';
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
import { authApi } from '../api/authApi';
import { getAuthToken, setAuthToken, setOnUnauthorizedHandler } from '../api/client';
import { AuthContext } from './authContextInstance';

const INACTIVITY_TIMEOUT_SECONDS = 300; // 5 minutes
const SESSION_POLL_INTERVAL_MS = 10_000; // 10 seconds (does not extend inactivity TTL)
const KEEP_ALIVE_THROTTLE_MS = 60_000; // Refresca TTL en backend máx. 1 vez por minuto si hay actividad real

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
  telefonoAlternativo: profile.telefonoAlternativo,
  curp: profile.curp,
  rfc: profile.rfc,
  fechaNacimiento: profile.fechaNacimiento,
  sexo: profile.sexo,
  nacionalidad: profile.nacionalidad,
  estadoCivil: profile.estadoCivil,
  ocupacion: profile.ocupacion,
  empresa: profile.empresa,
  ingresoMensual: profile.ingresoMensual,
  calle: profile.calle,
  numeroExterior: profile.numeroExterior,
  numeroInterior: profile.numeroInterior,
  colonia: profile.colonia,
  municipio: profile.municipio,
  estado: profile.estado,
  codigoPostal: profile.codigoPostal,
  pais: profile.pais,
  numeroCuenta: profile.numeroCuenta,
  saldo: profile.saldo,
  ultimoAcceso: profile.ultimoAcceso,
  esAdmin: profile.esAdmin ?? profile.usuario?.toLowerCase() === 'admin',
  rol: profile.rol ?? (profile.usuario?.toLowerCase() === 'admin' ? 'ADMIN' : 'CLIENTE'),
});

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<LoginResponse | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [isInitializing, setIsInitializing] = useState<boolean>(() => Boolean(getAuthToken()));
  const [secondsRemaining, setSecondsRemaining] = useState<number>(INACTIVITY_TIMEOUT_SECONDS);
  const [sessionExpiredMessage, setSessionExpiredMessage] = useState<string | null>(null);
  const [sessionNotice, setSessionNotice] = useState<string | null>(null);

  const lastActivityRef = useRef<number>(0);
  const lastKeepAliveRef = useRef<number>(0);
  const userRef = useRef<LoginResponse | null>(null);

  useEffect(() => {
    userRef.current = user;
  }, [user]);

  const clearSessionExpired = useCallback(() => {
    setSessionExpiredMessage(null);
  }, []);

  const clearSessionNotice = useCallback(() => {
    setSessionNotice(null);
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
        telefonoAlternativo: profile.telefonoAlternativo ?? prev.telefonoAlternativo,
        curp: profile.curp ?? prev.curp,
        rfc: profile.rfc ?? prev.rfc,
        fechaNacimiento: profile.fechaNacimiento ?? prev.fechaNacimiento,
        sexo: profile.sexo ?? prev.sexo,
        nacionalidad: profile.nacionalidad ?? prev.nacionalidad,
        estadoCivil: profile.estadoCivil ?? prev.estadoCivil,
        ocupacion: profile.ocupacion ?? prev.ocupacion,
        empresa: profile.empresa ?? prev.empresa,
        ingresoMensual: profile.ingresoMensual ?? prev.ingresoMensual,
        calle: profile.calle ?? prev.calle,
        numeroExterior: profile.numeroExterior ?? prev.numeroExterior,
        numeroInterior: profile.numeroInterior ?? prev.numeroInterior,
        colonia: profile.colonia ?? prev.colonia,
        municipio: profile.municipio ?? prev.municipio,
        estado: profile.estado ?? prev.estado,
        codigoPostal: profile.codigoPostal ?? prev.codigoPostal,
        pais: profile.pais ?? prev.pais,
        nombre: profile.nombre ?? prev.nombre,
        segundoNombre: profile.segundoNombre ?? prev.segundoNombre ?? '',
        apellidoPaterno: profile.apellidoPaterno ?? prev.apellidoPaterno,
        apellidoMaterno: profile.apellidoMaterno ?? prev.apellidoMaterno,
        nombreCompleto: profile.nombreCompleto || prev.nombreCompleto,
        ultimoAcceso: profile.ultimoAcceso ?? prev.ultimoAcceso,
        esAdmin: profile.esAdmin ?? prev.esAdmin ?? profile.usuario?.toLowerCase() === 'admin',
        rol: profile.rol ?? prev.rol,
      };
    });
  }, []);

  const logout = useCallback(async () => {
    const hadSession = Boolean(userRef.current || getAuthToken());
    userRef.current = null;
    setUser(null);
    setAuthToken(null);
    setSessionNotice(null);
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
    setOnUnauthorizedHandler((serverMessage?: string) => {
      userRef.current = null;
      setUser(null);
      setAuthToken(null);
      setSessionNotice(null);
      setSecondsRemaining(INACTIVITY_TIMEOUT_SECONDS);
      const isDisplaced = Boolean(serverMessage && /otro dispositivo/i.test(serverMessage));
      setSessionExpiredMessage(
        isDisplaced
          ? serverMessage!
          : 'Tu sesión ha expirado o fue cerrada por seguridad. Inicia sesión nuevamente.'
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
    setSessionNotice(null);
    try {
      const response = await authApi.login(credentials);
      userRef.current = response;
      setUser(response);
      if (response.sesionPreviaCerrada) {
        setSessionNotice(
          response.avisoSesion ||
            'Se cerró automáticamente una sesión activa previa en otro dispositivo por seguridad.'
        );
      }
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

  const enviarCodigoRegistro = async (
    payload: EnviarCodigoRegistroRequest
  ): Promise<EnviarCodigoRegistroResponse> => {
    setIsLoading(true);
    setSessionExpiredMessage(null);
    try {
      return await authApi.enviarCodigoRegistro(payload);
    } finally {
      setIsLoading(false);
    }
  };

  const register = async (payload: RegisterRequest) => {
    setIsLoading(true);
    setSessionExpiredMessage(null);
    setSessionNotice(null);
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
    const nowInit = Date.now();
    lastActivityRef.current = nowInit;
    lastKeepAliveRef.current = nowInit;

    const handleUserActivity = () => {
      const now = Date.now();
      if (now - lastActivityRef.current > 1000) {
        lastActivityRef.current = now;
        setSecondsRemaining(INACTIVITY_TIMEOUT_SECONDS);
        if (now - lastKeepAliveRef.current >= KEEP_ALIVE_THROTTLE_MS && getAuthToken()) {
          lastKeepAliveRef.current = now;
          void authApi.keepAlive().catch(() => {
            // 401 es manejado globalmente por apiClient; errores transitorios se ignoran
          });
        }
      }
    };

    const scrollOptions: AddEventListenerOptions = { capture: true, passive: true };
    window.addEventListener('mousemove', handleUserActivity, { passive: true });
    window.addEventListener('mousedown', handleUserActivity, { passive: true });
    window.addEventListener('pointerdown', handleUserActivity, { passive: true });
    window.addEventListener('keydown', handleUserActivity);
    window.addEventListener('input', handleUserActivity, { passive: true });
    window.addEventListener('click', handleUserActivity);
    window.addEventListener('scroll', handleUserActivity, scrollOptions);
    window.addEventListener('touchstart', handleUserActivity, { passive: true });
    window.addEventListener('focusin', handleUserActivity, { passive: true });

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
      window.removeEventListener('mousedown', handleUserActivity);
      window.removeEventListener('pointerdown', handleUserActivity);
      window.removeEventListener('keydown', handleUserActivity);
      window.removeEventListener('input', handleUserActivity);
      window.removeEventListener('click', handleUserActivity);
      window.removeEventListener('scroll', handleUserActivity, scrollOptions);
      window.removeEventListener('touchstart', handleUserActivity);
      window.removeEventListener('focusin', handleUserActivity);
      clearInterval(interval);
    };
  }, [isAuthenticated]);

  // Multi-device single-session watchdog: detects quickly if another device took over the account
  useEffect(() => {
    if (!isAuthenticated) return;

    const verifySession = () => {
      if (!getAuthToken()) return;
      void authApi.checkSessionStatus().catch(() => {
        // 401 is handled globally by apiClient -> onUnauthorizedHandler with exact displacement reason
      });
    };

    const pollInterval = setInterval(verifySession, SESSION_POLL_INTERVAL_MS);
    window.addEventListener('focus', verifySession);

    return () => {
      clearInterval(pollInterval);
      window.removeEventListener('focus', verifySession);
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
        sessionNotice,
        clearSessionNotice,
        login,
        enviarCodigoRegistro,
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
