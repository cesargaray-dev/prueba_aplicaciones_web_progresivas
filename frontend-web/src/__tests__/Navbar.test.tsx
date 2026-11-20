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
      user: { ...mockLoginResponse, email: 'demo@ejemplo.com' },
    });

    render(
      <MockAuthProvider value={ctx}>
        <Navbar activeTab="catalog" setActiveTab={setActiveTab} />
      </MockAuthProvider>
    );

    // Timer is visible (not hidden on mobile) and formats 0:45
    expect(screen.getByText('0:45')).toBeInTheDocument();
    expect(screen.getAllByText(/\$500\.00/).length).toBeGreaterThan(0);

    // Switch tab
    await user.click(screen.getByRole('button', { name: /Historial de Pagos/i }));
    expect(setActiveTab).toHaveBeenCalledWith('history');

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
  });
});
