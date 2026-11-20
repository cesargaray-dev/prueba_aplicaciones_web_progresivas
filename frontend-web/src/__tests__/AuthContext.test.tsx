import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AuthProvider } from '../context/AuthContext';
import { useAuth } from '../context/useAuth';
import { authApi } from '../api/authApi';
import { setAuthToken, getAuthToken, apiClient } from '../api/client';
import { mockLoginResponse, mockUserProfile } from './testUtils';

const ConsumerHarness: React.FC = () => {
  const auth = useAuth();
  return (
    <div>
      <div data-testid="init">{String(auth.isInitializing)}</div>
      <div data-testid="authed">{String(auth.isAuthenticated)}</div>
      <div data-testid="saldo">{auth.user?.saldo ?? 'none'}</div>
      <div data-testid="seconds">{auth.secondsRemaining}</div>
      <div data-testid="expired">{auth.sessionExpiredMessage ?? 'none'}</div>
      <button
        type="button"
        onClick={() => auth.login({ usuario: 'cesar123', contrasena: 'Password@123' })}
      >
        login
      </button>
      <button
        type="button"
        onClick={() =>
          auth.register({
            nombre: 'Cesar',
            apellidoPaterno: 'Garay',
            apellidoMaterno: 'Garcia',
            fechaNacimiento: '1998-01-15',
            correo: 'cesar@gmail.com',
            telefonoMovil: '5512345678',
            usuario: 'cesar123',
            contrasena: 'Password@123',
          })
        }
      >
        register
      </button>
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          auth.updateBalance(999);
        }}
      >
        updateBalance
      </button>
      <button type="button" onClick={() => auth.refreshBalance()}>
        refreshBalance
      </button>
      <button
        type="button"
        onClick={() =>
          auth.updateContacto({ correo: 'new@gmail.com', telefonoMovil: '5512345678' })
        }
      >
        updateContacto
      </button>
      <button
        type="button"
        onClick={() =>
          auth.cambiarContrasena({ contrasenaActual: 'Old12345', contrasenaNueva: 'New12345' })
        }
      >
        cambiarPass
      </button>
      <button type="button" onClick={() => auth.abonarSaldo({ monto: 100 })}>
        abonar
      </button>
      <button type="button" onClick={() => auth.logout()}>
        logout
      </button>
      <button type="button" onClick={() => auth.clearSessionExpired()}>
        clearExpired
      </button>
    </div>
  );
};

describe('AuthContext & Session Hardening (F11 / F13)', () => {
  beforeEach(() => {
    setAuthToken(null);
    window.sessionStorage.clear();
    window.localStorage.clear();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('throws when useAuth is used outside AuthProvider', () => {
    const Broken: React.FC = () => {
      useAuth();
      return null;
    };
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
    expect(() => render(<Broken />)).toThrow('useAuth must be used within an AuthProvider');
    spy.mockRestore();
  });

  it('hydrates in-memory user from sessionStorage token via /auth/me on mount and cleans up failed hydration', async () => {
    setAuthToken('boot-jwt');
    vi.spyOn(authApi, 'getMe').mockResolvedValueOnce(mockUserProfile);

    render(
      <AuthProvider>
        <ConsumerHarness />
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByTestId('init').textContent).toBe('false');
    });
    expect(screen.getByTestId('authed').textContent).toBe('true');
    expect(screen.getByTestId('saldo').textContent).toBe('500');

    // Failed hydration clears token
    setAuthToken('expired-boot-jwt');
    vi.spyOn(authApi, 'getMe').mockRejectedValueOnce(new Error('401'));
    render(
      <AuthProvider>
        <ConsumerHarness />
      </AuthProvider>
    );
    await waitFor(() => {
      expect(getAuthToken()).toBeNull();
    });
  });

  it('handles login, register, profile updates, deposit, password change, and logout', async () => {
    const user = userEvent.setup();
    vi.spyOn(authApi, 'login').mockResolvedValue(mockLoginResponse);
    vi.spyOn(authApi, 'register').mockResolvedValue(mockLoginResponse);
    vi.spyOn(authApi, 'getMe').mockResolvedValue({ ...mockUserProfile, saldo: 550 });
    vi.spyOn(authApi, 'updateContacto').mockResolvedValue({ ...mockUserProfile, saldo: 550 });
    vi.spyOn(authApi, 'cambiarContrasena').mockResolvedValue({ codigo: 0, mensaje: 'OK' });
    vi.spyOn(authApi, 'abonarSaldo').mockResolvedValue({ ...mockUserProfile, saldo: 650 });
    vi.spyOn(authApi, 'logout').mockResolvedValue(undefined);

    render(
      <AuthProvider>
        <ConsumerHarness />
      </AuthProvider>
    );

    await user.click(screen.getByText('login'));
    await waitFor(() => expect(screen.getByTestId('authed').textContent).toBe('true'));
    expect(screen.getByTestId('saldo').textContent).toBe('550');

    await user.click(screen.getByText('abonar'));
    await waitFor(() => expect(screen.getByTestId('saldo').textContent).toBe('650'));

    await user.click(screen.getByText('updateContacto'));
    await user.click(screen.getByText('cambiarPass'));
    await user.click(screen.getByText('refreshBalance'));
    await user.click(screen.getByText('logout'));
    await waitFor(() => expect(screen.getByTestId('authed').textContent).toBe('false'));

    await user.click(screen.getByText('register'));
    await waitFor(() => expect(screen.getByTestId('authed').textContent).toBe('true'));
  });

  it('triggers global 401 session expiration message and clears user state', async () => {
    const user = userEvent.setup();
    vi.spyOn(authApi, 'login').mockResolvedValue(mockLoginResponse);
    vi.spyOn(authApi, 'getMe').mockResolvedValue(mockUserProfile);

    render(
      <AuthProvider>
        <ConsumerHarness />
      </AuthProvider>
    );

    await user.click(screen.getByText('login'));
    await waitFor(() => expect(screen.getByTestId('authed').textContent).toBe('true'));

    setAuthToken('jwt-active');
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: false,
      status: 401,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({ mensaje: 'Token inválido' }),
    } as Response);

    await expect(apiClient.get('/pagos/historial')).rejects.toThrow('Token inválido');

    await waitFor(() => expect(screen.getByTestId('authed').textContent).toBe('false'));
    expect(screen.getByTestId('expired').textContent).toContain('sesión ha expirado');

    await user.click(screen.getByText('clearExpired'));
    expect(screen.getByTestId('expired').textContent).toBe('none');
  });

  it('does NOT reset inactivity countdown on balance update and logs out at 300s inactivity', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval', 'Date'] });
    vi.spyOn(authApi, 'login').mockResolvedValue(mockLoginResponse);
    vi.spyOn(authApi, 'getMe').mockResolvedValue(mockUserProfile);
    vi.spyOn(authApi, 'logout').mockResolvedValue(undefined);

    render(
      <AuthProvider>
        <ConsumerHarness />
      </AuthProvider>
    );

    await act(async () => {
      screen.getByText('login').click();
    });

    expect(screen.getByTestId('authed').textContent).toBe('true');

    // Advance 200s
    await act(async () => {
      vi.advanceTimersByTime(200_000);
    });
    expect(Number(screen.getByTestId('seconds').textContent)).toBeLessThanOrEqual(100);

    // Trigger balance update -> must NOT reset seconds back to 300
    await act(async () => {
      screen.getByText('updateBalance').click();
    });
    expect(Number(screen.getByTestId('seconds').textContent)).toBeLessThanOrEqual(100);

    // Advance remaining 105s -> auto-logout
    await act(async () => {
      vi.advanceTimersByTime(105_000);
    });
    expect(screen.getByTestId('authed').textContent).toBe('false');
    expect(screen.getByTestId('expired').textContent).toContain('inactividad');
  });
});
