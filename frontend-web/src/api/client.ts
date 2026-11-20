// Base API client for interacting with the Spring Boot backend

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1';

export interface ApiRequestOptions extends RequestInit {
  skipUnauthorizedHandler?: boolean;
}

type UnauthorizedHandler = () => void;

let onUnauthorizedHandler: UnauthorizedHandler | null = null;

export const setOnUnauthorizedHandler = (handler: UnauthorizedHandler | null): void => {
  onUnauthorizedHandler = handler;
};

const clearLegacyLocalStorage = (): void => {
  if (typeof window !== 'undefined' && typeof window.localStorage !== 'undefined') {
    window.localStorage.removeItem('auth_token');
    window.localStorage.removeItem('auth_user');
  }
};

const getStorageItem = (key: string): string | null => {
  if (typeof window !== 'undefined' && typeof window.sessionStorage !== 'undefined') {
    return window.sessionStorage.getItem(key);
  }
  return null;
};

const setStorageItem = (key: string, value: string | null): void => {
  if (typeof window !== 'undefined' && typeof window.sessionStorage !== 'undefined') {
    if (value) {
      window.sessionStorage.setItem(key, value);
    } else {
      window.sessionStorage.removeItem(key);
    }
  }
  if (!value) {
    clearLegacyLocalStorage();
  }
};

clearLegacyLocalStorage();

let authToken: string | null = getStorageItem('auth_token');

export const setAuthToken = (token: string | null) => {
  authToken = token;
  setStorageItem('auth_token', token);
};

export const getAuthToken = (): string | null => {
  if (!authToken) {
    authToken = getStorageItem('auth_token');
  }
  return authToken;
};

export class ApiError extends Error {
  public status: number;
  public data: unknown;

  constructor(message: string, status: number, data?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.data = data;
  }
}

const extractErrorMessage = (status: number, data: unknown): string => {
  let errorMessage = `Error HTTP ${status}`;
  if (typeof data === 'object' && data !== null) {
    const errorObj = data as Record<string, unknown>;
    errorMessage =
      (errorObj.mensajeOperacion as string) ||
      (errorObj.mensaje as string) ||
      (errorObj.message as string) ||
      (errorObj.error as string) ||
      errorMessage;
  } else if (typeof data === 'string' && data.trim()) {
    errorMessage = data.trim();
  }
  return errorMessage;
};

const isSessionUnauthorized = (
  endpoint: string,
  status: number,
  errorMessage: string,
  hadToken: boolean,
  skipUnauthorizedHandler?: boolean
): boolean => {
  if (status !== 401 || !hadToken || skipUnauthorizedHandler) {
    return false;
  }

  const normalizedPath = (endpoint.startsWith('/') ? endpoint : `/${endpoint}`)
    .split('?')[0]
    .toLowerCase();

  if (
    normalizedPath === '/auth/login' ||
    normalizedPath === '/auth/register' ||
    normalizedPath === '/auth/login/biometrico'
  ) {
    return false;
  }

  // Backend Autenticacion2FAException returns HTTP 401 on wrong/expired OTP during payment.
  // Keep wrong 2FA code errors inline in Payment2FAModal without kicking the user out.
  if (normalizedPath === '/pagos/servicios') {
    const mentionsOtp = /\b2fa\b|\botp\b|c[oó]digo/i.test(errorMessage);
    const mentionsSession = /sesi[oó]n|token|no\s+autenticad|expirado\s+su\s+acceso/i.test(
      errorMessage
    );
    if (mentionsOtp && !mentionsSession) {
      return false;
    }
  }

  return true;
};

async function parseResponsePayload(response: Response): Promise<unknown> {
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    return response.json();
  }
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

async function request<T>(endpoint: string, options: ApiRequestOptions = {}): Promise<T> {
  const { skipUnauthorizedHandler, headers: optionHeaders, ...fetchOptions } = options;
  const url = `${API_BASE_URL}${endpoint.startsWith('/') ? endpoint : `/${endpoint}`}`;

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
    ...((optionHeaders as Record<string, string>) || {}),
  };

  const token = getAuthToken();
  const hadToken = Boolean(token || headers['Authorization']);
  if (token && !headers['Authorization']) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  try {
    const response = await fetch(url, {
      ...fetchOptions,
      headers,
    });

    const data = await parseResponsePayload(response);

    if (!response.ok) {
      const errorMessage = extractErrorMessage(response.status, data);
      if (
        isSessionUnauthorized(
          endpoint,
          response.status,
          errorMessage,
          hadToken,
          skipUnauthorizedHandler
        )
      ) {
        setAuthToken(null);
        onUnauthorizedHandler?.();
      }
      throw new ApiError(errorMessage, response.status, data);
    }

    return data as T;
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    const message = error instanceof Error ? error.message : 'Error desconocido de conexión';
    throw new ApiError(message, 0);
  }
}

async function requestBlob(endpoint: string, options: ApiRequestOptions = {}): Promise<Blob> {
  const { skipUnauthorizedHandler, headers: optionHeaders, ...fetchOptions } = options;
  const url = `${API_BASE_URL}${endpoint.startsWith('/') ? endpoint : `/${endpoint}`}`;

  const headers: Record<string, string> = {
    Accept: 'application/pdf, */*',
    ...((optionHeaders as Record<string, string>) || {}),
  };

  const token = getAuthToken();
  const hadToken = Boolean(token || headers['Authorization']);
  if (token && !headers['Authorization']) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  try {
    const response = await fetch(url, {
      ...fetchOptions,
      headers,
    });

    if (!response.ok) {
      const data = await parseResponsePayload(response);
      const errorMessage = extractErrorMessage(response.status, data);
      if (
        isSessionUnauthorized(
          endpoint,
          response.status,
          errorMessage,
          hadToken,
          skipUnauthorizedHandler
        )
      ) {
        setAuthToken(null);
        onUnauthorizedHandler?.();
      }
      throw new ApiError(errorMessage, response.status, data);
    }

    return await response.blob();
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    const message = error instanceof Error ? error.message : 'Error desconocido de conexión';
    throw new ApiError(message, 0);
  }
}

export const apiClient = {
  get: <T>(endpoint: string, options?: ApiRequestOptions) =>
    request<T>(endpoint, { ...options, method: 'GET' }),

  getBlob: (endpoint: string, options?: ApiRequestOptions) =>
    requestBlob(endpoint, { ...options, method: 'GET' }),

  post: <T>(endpoint: string, body?: unknown, options?: ApiRequestOptions) =>
    request<T>(endpoint, {
      ...options,
      method: 'POST',
      body: body !== undefined ? JSON.stringify(body) : undefined,
    }),

  put: <T>(endpoint: string, body?: unknown, options?: ApiRequestOptions) =>
    request<T>(endpoint, {
      ...options,
      method: 'PUT',
      body: body !== undefined ? JSON.stringify(body) : undefined,
    }),

  delete: <T>(endpoint: string, options?: ApiRequestOptions) =>
    request<T>(endpoint, {
      ...options,
      method: 'DELETE',
    }),
};
