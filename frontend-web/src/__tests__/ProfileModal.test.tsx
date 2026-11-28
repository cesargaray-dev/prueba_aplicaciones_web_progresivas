import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
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
    expect(screen.getByText(/solo debe contener letras y espacios/i)).toBeInTheDocument();

    await user.clear(nombreInput);
    await user.type(nombreInput, 'Cesar');

    // Validate segundo nombre, apellido paterno, apellido materno, invalid & placeholder email
    const segInput = screen.getByLabelText(/Segundo nombre/i);
    fireEvent.change(segInput, { target: { value: 'Enrique99' } });
    await user.click(screen.getByRole('button', { name: /Guardar Cambios de Perfil/i }));
    expect(screen.getByText(/El segundo nombre solo debe contener letras/i)).toBeInTheDocument();
    fireEvent.change(segInput, { target: { value: 'Enrique' } });

    const patInput = screen.getByLabelText('Apellido paterno *');
    fireEvent.change(patInput, { target: { value: 'Ga' } });
    await user.click(screen.getByRole('button', { name: /Guardar Cambios de Perfil/i }));
    expect(screen.getByText(/El apellido paterno debe tener al menos 3 letras/i)).toBeInTheDocument();
    fireEvent.change(patInput, { target: { value: 'Garay' } });

    const matInput = screen.getByLabelText('Apellido materno *');
    fireEvent.change(matInput, { target: { value: 'Lo' } });
    await user.click(screen.getByRole('button', { name: /Guardar Cambios de Perfil/i }));
    expect(screen.getByText(/El apellido materno debe tener al menos 3 letras/i)).toBeInTheDocument();
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

  it('handles Abonar Saldo tab and Seguridad password change tab', async () => {
    const user = userEvent.setup();
    const ctx = createMockAuthContext();

    render(
      <MockAuthProvider value={ctx}>
        <ProfileModal isOpen={true} onClose={vi.fn()} initialTab="abono" />
      </MockAuthProvider>
    );

    // Quick invalid amount check ($0) then Quick chip $500
    const montoInput = screen.getByLabelText('Monto MXN');
    fireEvent.change(montoInput, { target: { value: '0' } });
    fireEvent.submit(montoInput.closest('form')!);
    expect(screen.getByText(/entre \$1\.00 y \$50,000\.00 MXN/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '$500' }));
    await user.click(screen.getByRole('button', { name: /Abonar a Cuenta/i }));
    await waitFor(() => expect(ctx.abonarSaldo).toHaveBeenCalledWith({ monto: 500, concepto: 'Abono a billetera digital' }));

    // Copy account number in header
    await user.click(screen.getByTitle('Copiar número de cuenta'));

    // Seguridad tab + validation branches (empty current, weak new, mismatch, same as current)
    await user.click(screen.getByRole('button', { name: 'Seguridad' }));
    await user.click(screen.getByRole('button', { name: /Actualizar Contraseña/i }));
    expect(screen.getByText(/Ingresa tu contraseña actual/i)).toBeInTheDocument();

    await user.type(screen.getByLabelText('Contraseña actual'), 'OldPass123');
    await user.type(screen.getByLabelText('Nueva contraseña'), 'weak');
    await user.click(screen.getByRole('button', { name: /Actualizar Contraseña/i }));
    expect(screen.getByText(/La nueva contraseña debe tener mínimo 8 caracteres/i)).toBeInTheDocument();

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

    // Switch back to Mis Datos and validate phone/email error branches
    await user.click(screen.getByRole('button', { name: 'Mis Datos' }));
    expect(screen.getByText(/Expediente Bancario KYC Verificado/i)).toBeInTheDocument();
    const telInput = screen.getByLabelText(/Teléfono móvil/i);
    await user.clear(telInput);
    await user.type(telInput, '0000000000');
    await user.click(screen.getByRole('button', { name: /Guardar Cambios de Perfil/i }));
    expect(screen.getAllByText(/LADA válida|10 dígitos/i).length).toBeGreaterThan(0);
  });
});
