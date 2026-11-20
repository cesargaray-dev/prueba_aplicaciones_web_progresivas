import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProfileModal } from '../components/ProfileModal';
import { MockAuthProvider, createMockAuthContext, mockLoginResponse } from './testUtils';

describe('ProfileModal Component', () => {
  it('validates names (letters only), email, phone, and submits profile update', async () => {
    const user = userEvent.setup();
    const ctx = createMockAuthContext({
      user: { ...mockLoginResponse, email: 'demo@ejemplo.com' },
    });

    render(
      <MockAuthProvider value={ctx}>
        <ProfileModal isOpen={true} onClose={vi.fn()} />
      </MockAuthProvider>
    );

    await waitFor(() => expect(ctx.refreshBalance).toHaveBeenCalled());
    expect(screen.getByText(/Actualízalo por un correo real/i)).toBeInTheDocument();

    const nombreInput = screen.getByLabelText('Nombre *');
    await user.clear(nombreInput);
    await user.type(nombreInput, 'Cesar123');
    await user.click(screen.getByRole('button', { name: /Guardar Cambios de Perfil/i }));
    expect(screen.getByText(/contener solo letras y espacios/i)).toBeInTheDocument();

    await user.clear(nombreInput);
    await user.type(nombreInput, 'Cesar');
    const correoInput = screen.getByLabelText(/Correo electrónico real/i);
    await user.clear(correoInput);
    await user.type(correoInput, 'cesar@gmail.com');
    await user.click(screen.getByRole('button', { name: /Guardar Cambios de Perfil/i }));

    await waitFor(() =>
      expect(ctx.updateContacto).toHaveBeenCalledWith(
        expect.objectContaining({
          nombre: 'Cesar',
          correo: 'cesar@gmail.com',
        })
      )
    );
  });

  it('handles Abonar Saldo tab and Seguridad password change tab', async () => {
    const user = userEvent.setup();
    const ctx = createMockAuthContext();

    render(
      <MockAuthProvider value={ctx}>
        <ProfileModal isOpen={true} onClose={vi.fn()} initialTab="abono" />
      </MockAuthProvider>
    );

    // Quick chip $500
    await user.click(screen.getByRole('button', { name: '$500' }));
    await user.click(screen.getByRole('button', { name: /Abonar a Cuenta/i }));
    await waitFor(() => expect(ctx.abonarSaldo).toHaveBeenCalledWith({ monto: 500, concepto: 'Abono a billetera digital' }));

    // Seguridad tab
    await user.click(screen.getByRole('button', { name: 'Seguridad' }));
    await user.type(screen.getByLabelText('Contraseña actual'), 'OldPass123');
    await user.type(screen.getByLabelText('Nueva contraseña'), 'NewPass123');
    await user.type(screen.getByLabelText('Confirmar nueva contraseña'), 'Mismatch1');
    await user.click(screen.getByRole('button', { name: /Actualizar Contraseña/i }));
    expect(screen.getByText(/confirmación no coincide/i)).toBeInTheDocument();

    const confirmInput = screen.getByLabelText('Confirmar nueva contraseña');
    await user.clear(confirmInput);
    await user.type(confirmInput, 'NewPass123');
    await user.click(screen.getByRole('button', { name: /Actualizar Contraseña/i }));
    await waitFor(() =>
      expect(ctx.cambiarContrasena).toHaveBeenCalledWith({
        contrasenaActual: 'OldPass123',
        contrasenaNueva: 'NewPass123',
      })
    );
  });
});
