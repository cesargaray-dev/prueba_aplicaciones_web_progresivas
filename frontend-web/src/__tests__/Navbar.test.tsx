import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Navbar } from '../components/Navbar';
import { MockAuthProvider, createMockAuthContext, mockLoginResponse } from './testUtils';

describe('Navbar Component', () => {
  it('switches tabs, refreshes balance, shows visible inactivity timer, opens ProfileModal, and logs out', async () => {
    const user = userEvent.setup();
    const setActiveTab = vi.fn();
    const ctx = createMockAuthContext({
      secondsRemaining: 45,
      sessionNotice: 'Se cerró una sesión activa previa en otro dispositivo por seguridad.',
      user: { ...mockLoginResponse, email: 'demo@ejemplo.com' },
    });

    const { rerender } = render(
      <MockAuthProvider value={ctx}>
        <Navbar activeTab="catalog" setActiveTab={setActiveTab} />
      </MockAuthProvider>
    );

    // Multi-device notice banner renders and can be dismissed
    expect(screen.getByText(/otro dispositivo por seguridad/i)).toBeInTheDocument();
    await user.click(screen.getByLabelText('Cerrar aviso de seguridad'));
    expect(ctx.clearSessionNotice).toHaveBeenCalled();

    // Timer is visible (not hidden on mobile) and formats 0:45
    expect(screen.getByText('0:45')).toBeInTheDocument();
    expect(screen.getAllByText(/\$500\.00/).length).toBeGreaterThan(0);

    // Switch tab to History
    await user.click(screen.getByRole('button', { name: /Historial de Pagos/i }));
    expect(setActiveTab).toHaveBeenCalledWith('history');

    // Toggle Dark / Light Mode
    await user.click(screen.getByLabelText('Cambiar tema'));
    expect(document.documentElement.classList.contains('dark')).toBe(true);
    await user.click(screen.getByLabelText('Cambiar tema'));
    expect(document.documentElement.classList.contains('dark')).toBe(false);

    // Refresh balance
    await user.click(screen.getByTitle('Actualizar saldo'));
    expect(ctx.refreshBalance).toHaveBeenCalled();

    // Open ProfileModal
    await user.click(screen.getByTitle('Mi Perfil, Correo 2FA y Abonos'));
    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: 'Cerrar' }));

    // Logout
    await user.click(screen.getByTitle('Cerrar sesión'));
    expect(ctx.logout).toHaveBeenCalled();

    // Admin view renders dedicated Admin Console badge without customer balance/catalog tabs
    const adminCtx = createMockAuthContext({
      user: { ...mockLoginResponse, usuario: 'admin', esAdmin: true },
    });
    rerender(
      <MockAuthProvider value={adminCtx}>
        <Navbar activeTab="admin" setActiveTab={setActiveTab} />
      </MockAuthProvider>
    );
    expect(screen.getByText(/Consola de Administración de Usuarios/i)).toBeInTheDocument();
  });
});
