import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ProfileModal } from '../components/ProfileModal';
import { authApi } from '../api/authApi';
import {
  MockAuthProvider,
  createMockAuthContext,
  mockLoginResponse,
  mockTotpSetup,
  mockUserProfile,
} from './testUtils';

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
    expect(screen.getByText(/solo debe contener letras y espacios/i)).toBeInTheDocument();

    await user.clear(nombreInput);
    await user.type(nombreInput, 'Cesar');

    const segInput = screen.getByLabelText(/Segundo nombre/i);
    fireEvent.change(segInput, { target: { value: 'Enrique99' } });
    await user.click(screen.getByRole('button', { name: /Guardar Cambios de Perfil/i }));
    expect(
      screen.getByText(/El segundo nombre solo debe contener letras/i)
    ).toBeInTheDocument();
    fireEvent.change(segInput, { target: { value: 'Enrique' } });

    const patInput = screen.getByLabelText('Apellido paterno *');
    fireEvent.change(patInput, { target: { value: 'Ga' } });
    await user.click(screen.getByRole('button', { name: /Guardar Cambios de Perfil/i }));
    expect(
      screen.getByText(/El apellido paterno debe tener al menos 3 letras/i)
    ).toBeInTheDocument();
    fireEvent.change(patInput, { target: { value: 'Garay' } });

    const matInput = screen.getByLabelText('Apellido materno *');
    fireEvent.change(matInput, { target: { value: 'Lo' } });
    await user.click(screen.getByRole('button', { name: /Guardar Cambios de Perfil/i }));
    expect(
      screen.getByText(/El apellido materno debe tener al menos 3 letras/i)
    ).toBeInTheDocument();
    fireEvent.change(matInput, { target: { value: 'Garcia' } });

    const correoInput = screen.getByLabelText(/Correo electrónico real/i);
    fireEvent.change(correoInput, { target: { value: 'invalido' } });
    fireEvent.submit(correoInput.closest('form')!);
    expect(screen.getByText(/Ingresa un correo electrónico válido/i)).toBeInTheDocument();

    fireEvent.change(correoInput, { target: { value: 'demo@ejemplo.com' } });
    await user.click(screen.getByRole('button', { name: /Guardar Cambios de Perfil/i }));
    expect(screen.getByText(/No se permiten dominios de ejemplo/i)).toBeInTheDocument();

    fireEvent.change(correoInput, { target: { value: 'cesar@gmail.com' } });
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

  it('handles Abonar Saldo tab, Google Authenticator activation/deactivation, and Seguridad password change', async () => {
    const user = userEvent.setup();
    const clientCtx = createMockAuthContext();

    const { unmount } = render(
      <MockAuthProvider value={clientCtx}>
        <ProfileModal isOpen={true} onClose={vi.fn()} initialTab="abono" />
      </MockAuthProvider>
    );
    expect(screen.getByText(/Fondeo Autorizado por Administrador/i)).toBeInTheDocument();
    expect(screen.getByText(/\$0\.00 MXN/i)).toBeInTheDocument();
    unmount();

    vi.spyOn(authApi, 'obtenerEstadoTotp').mockResolvedValueOnce({
      totpHabilitado: false,
      mensaje: 'No configurado',
    });
    vi.spyOn(authApi, 'iniciarTotpPerfil').mockResolvedValue(mockTotpSetup);
    vi.spyOn(authApi, 'activarTotpPerfil')
      .mockRejectedValueOnce(new Error('Código TOTP incorrecto'))
      .mockResolvedValueOnce({
        totpHabilitado: true,
        mensaje: 'Google Authenticator activado exitosamente.',
      });
    vi.spyOn(authApi, 'desactivarTotpPerfil')
      .mockRejectedValueOnce(new Error('Código inválido al desactivar'))
      .mockResolvedValueOnce({
        totpHabilitado: false,
        mensaje: 'Google Authenticator desactivado.',
      });

    const ctx = createMockAuthContext({
      user: { ...clientCtx.user!, esAdmin: true, rol: 'ADMIN', usuario: 'admin' },
      refreshBalance: vi
        .fn()
        .mockResolvedValueOnce(mockUserProfile)
        .mockResolvedValueOnce({ ...mockUserProfile, totpHabilitado: true })
        .mockResolvedValue({ ...mockUserProfile, totpHabilitado: false }),
    });

    render(
      <MockAuthProvider value={ctx}>
        <ProfileModal isOpen={true} onClose={vi.fn()} initialTab="abono" />
      </MockAuthProvider>
    );

    const montoInput = screen.getByLabelText('Monto MXN');
    fireEvent.change(montoInput, { target: { value: '0' } });
    fireEvent.submit(montoInput.closest('form')!);
    expect(screen.getByText(/entre \$1\.00 y \$50,000\.00 MXN/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '$500' }));
    await user.click(screen.getByRole('button', { name: /Abonar a Cuenta/i }));
    await waitFor(() =>
      expect(ctx.abonarSaldo).toHaveBeenCalledWith({
        monto: 500,
        concepto: 'Abono a billetera digital',
      })
    );

    await user.click(screen.getByTitle('Copiar número de cuenta'));

    // Seguridad tab -> Activate & Deactivate Google Authenticator
    await user.click(screen.getByRole('button', { name: 'Seguridad' }));
    await waitFor(() => expect(screen.getByText('No configurado')).toBeInTheDocument());

    await user.click(screen.getByRole('button', { name: /Activar Google Authenticator/i }));
    await waitFor(() => expect(screen.getByAltText('QR Google Authenticator')).toBeInTheDocument());

    // Cancel setup & reopen
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(screen.queryByAltText('QR Google Authenticator')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Activar Google Authenticator/i }));
    await waitFor(() => expect(screen.getByAltText('QR Google Authenticator')).toBeInTheDocument());

    // Submit short code first -> validation error
    await user.click(screen.getByRole('button', { name: /Confirmar y Activar/i }));
    expect(
      screen.getByText(/Ingresa el código de 6 dígitos generado en Google Authenticator/i)
    ).toBeInTheDocument();

    await user.type(
      screen.getByLabelText(/Código de Google Authenticator \(6 dígitos\)/i),
      '123456'
    );
    await user.click(screen.getByRole('button', { name: /Confirmar y Activar/i }));
    await waitFor(() => expect(screen.getByText('Código TOTP incorrecto')).toBeInTheDocument());

    await user.click(screen.getByRole('button', { name: /Confirmar y Activar/i }));
    await waitFor(() => expect(screen.getByText('Activo')).toBeInTheDocument());

    // Deactivate Google Authenticator (cancel, error, then success)
    await user.click(screen.getByRole('button', { name: /Desactivar Google Authenticator/i }));
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));
    await user.click(screen.getByRole('button', { name: /Desactivar Google Authenticator/i }));
    await user.click(screen.getByRole('button', { name: /Confirmar Desactivación/i }));
    expect(screen.getByText(/Ingresa tu código actual de 6 dígitos/i)).toBeInTheDocument();

    await user.type(
      screen.getByLabelText(/Código actual de Google Authenticator/i),
      '654321'
    );
    await user.click(screen.getByRole('button', { name: /Confirmar Desactivación/i }));
    await waitFor(() =>
      expect(screen.getByText('Código inválido al desactivar')).toBeInTheDocument()
    );

    await user.click(screen.getByRole('button', { name: /Confirmar Desactivación/i }));
    await waitFor(() => expect(screen.getByText('No configurado')).toBeInTheDocument());

    // Password validations & update
    await user.click(screen.getByRole('button', { name: /Actualizar Contraseña/i }));
    expect(screen.getByText(/Ingresa tu contraseña actual/i)).toBeInTheDocument();

    await user.type(screen.getByLabelText('Contraseña actual'), 'OldPass123');
    await user.type(screen.getByLabelText('Nueva contraseña'), 'weak');
    await user.click(screen.getByRole('button', { name: /Actualizar Contraseña/i }));
    expect(
      screen.getByText(/La nueva contraseña debe tener mínimo 8 caracteres/i)
    ).toBeInTheDocument();

    const newPassInput = screen.getByLabelText('Nueva contraseña');
    await user.clear(newPassInput);
    await user.type(newPassInput, 'OldPass123');
    const confirmInput = screen.getByLabelText('Confirmar nueva contraseña');
    await user.type(confirmInput, 'OldPass123');
    await user.click(screen.getByRole('button', { name: /Actualizar Contraseña/i }));
    expect(screen.getByText(/diferente a la actual/i)).toBeInTheDocument();

    await user.clear(newPassInput);
    await user.type(newPassInput, 'NewPass123');
    await user.clear(confirmInput);
    await user.type(confirmInput, 'Mismatch1');
    await user.click(screen.getByRole('button', { name: /Actualizar Contraseña/i }));
    expect(screen.getByText(/confirmación no coincide/i)).toBeInTheDocument();

    await user.clear(confirmInput);
    await user.type(confirmInput, 'NewPass123');
    await user.click(screen.getByRole('button', { name: /Actualizar Contraseña/i }));
    await waitFor(() =>
      expect(ctx.cambiarContrasena).toHaveBeenCalledWith({
        contrasenaActual: 'OldPass123',
        contrasenaNueva: 'NewPass123',
      })
    );

    await user.click(screen.getByRole('button', { name: 'Mis Datos' }));
    expect(screen.getByText(/Expediente Bancario KYC Verificado/i)).toBeInTheDocument();
    const telInput = screen.getByLabelText(/Teléfono móvil/i);
    await user.clear(telInput);
    await user.type(telInput, '0000000000');
    await user.click(screen.getByRole('button', { name: /Guardar Cambios de Perfil/i }));
    expect(screen.getAllByText(/LADA válida|10 dígitos/i).length).toBeGreaterThan(0);
  });
});
