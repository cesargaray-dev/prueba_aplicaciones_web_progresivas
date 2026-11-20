import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ReferenceModal } from '../components/ReferenceModal';
import { paymentApi } from '../api/paymentApi';
import {
  MockAuthProvider,
  createMockAuthContext,
  mockCfeProduct,
  mockTelcelProduct,
  mockValidationSuccess,
} from './testUtils';

describe('ReferenceModal Component (F12 / F13)', () => {
  it('returns null when closed or product is null', () => {
    const { container } = render(
      <MockAuthProvider>
        <ReferenceModal
          product={null}
          isOpen={true}
          onClose={vi.fn()}
          onProceedTo2FA={vi.fn()}
        />
      </MockAuthProvider>
    );
    expect(container.firstChild).toBeNull();
  });

  it('validates recarga phone, supports saved phone quick-fill, handles invalid API response, and proceeds', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const onProceedTo2FA = vi.fn();

    render(
      <MockAuthProvider>
        <ReferenceModal
          product={mockTelcelProduct}
          isOpen={true}
          onClose={onClose}
          onProceedTo2FA={onProceedTo2FA}
        />
      </MockAuthProvider>
    );

    expect(screen.getByText('Tiempo aire y megas nacionales')).toBeInTheDocument();

    // Quick-fill saved phone
    await user.click(screen.getByRole('button', { name: /Usar mi celular/i }));
    const refInput = screen.getByPlaceholderText('5512345678') as HTMLInputElement;
    expect(refInput.value).toBe('5512345678');

    // Invalid API validation response
    vi.spyOn(paymentApi, 'validarReferencia').mockResolvedValueOnce({
      esValida: false,
      mensaje: 'Línea no registrada',
    });
    await user.click(screen.getByRole('button', { name: 'Validar' }));
    await waitFor(() => expect(screen.getByText('Línea no registrada')).toBeInTheDocument());

    // Valid API validation response
    vi.spyOn(paymentApi, 'validarReferencia').mockResolvedValueOnce(mockValidationSuccess);
    await user.click(screen.getByRole('button', { name: 'Validar' }));
    await waitFor(() =>
      expect(screen.getByText('Referencia Verificada Exitosamente')).toBeInTheDocument()
    );

    await user.click(screen.getByRole('button', { name: /Continuar a Confirmación 2FA/i }));
    expect(onProceedTo2FA).toHaveBeenCalledWith(
      expect.objectContaining({
        product: mockTelcelProduct,
        referencia: '5512345678',
        monto: 20,
      })
    );

    // Escape closes modal
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(onClose).toHaveBeenCalled();
  });

  it('enforces >= $10.00 MXN minimum on open-amount service and renders hasVerificationDigit & showAyuda', async () => {
    const user = userEvent.setup();
    const onProceedTo2FA = vi.fn();
    const ctx = createMockAuthContext();

    render(
      <MockAuthProvider value={ctx}>
        <ReferenceModal
          product={mockCfeProduct}
          isOpen={true}
          onClose={vi.fn()}
          onProceedTo2FA={onProceedTo2FA}
        />
      </MockAuthProvider>
    );

    expect(screen.getByText(/Dígito verificador requerido/i)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Ayuda para localizar tu referencia/i }));
    expect(screen.getByText(/estado de cuenta o recibo físico/i)).toBeInTheDocument();

    const refInput = screen.getByPlaceholderText('Ingresa tu referencia...');
    await user.type(refInput, '123456789012');

    vi.spyOn(paymentApi, 'validarReferencia').mockResolvedValueOnce({
      esValida: true,
      mensaje: 'Contrato CFE válido',
      requiereMonto: true,
      servicio: 'CFE',
    });

    await user.click(screen.getByRole('button', { name: 'Validar' }));
    await waitFor(() =>
      expect(screen.getByText('Referencia Verificada Exitosamente')).toBeInTheDocument()
    );

    const montoInput = screen.getByPlaceholderText('0.00');
    await user.type(montoInput, '5');
    await user.click(screen.getByRole('button', { name: /Continuar a Confirmación 2FA/i }));
    expect(
      screen.getByText('El importe mínimo para pago de monto abierto es de $10.00 MXN.')
    ).toBeInTheDocument();
    expect(onProceedTo2FA).not.toHaveBeenCalled();

    await user.clear(montoInput);
    await user.type(montoInput, '60000');
    await user.click(screen.getByRole('button', { name: /Continuar a Confirmación 2FA/i }));
    expect(
      screen.getByText('El importe máximo por transacción es de $50,000.00 MXN.')
    ).toBeInTheDocument();

    await user.clear(montoInput);
    await user.type(montoInput, '150');
    await user.click(screen.getByRole('button', { name: /Continuar a Confirmación 2FA/i }));
    expect(onProceedTo2FA).toHaveBeenCalledWith(
      expect.objectContaining({
        referencia: '123456789012',
        monto: 150,
      })
    );
  });
});
