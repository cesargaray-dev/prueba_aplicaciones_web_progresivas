import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, waitFor, act, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Payment2FAModal } from '../components/Payment2FAModal';
import { paymentApi } from '../api/paymentApi';
import { ApiError } from '../api/client';
import {
  MockAuthProvider,
  createMockAuthContext,
  mockLoginResponse,
  mockPagoSuccess,
  mockTelcelProduct,
  mockValidationSuccess,
} from './testUtils';

describe('Payment2FAModal Component (F11 / F12 / F13)', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('requests 2FA on open, NEVER renders Soft-Token bypass, and handles EXITOSA payment', async () => {
    const user = userEvent.setup();
    const onPaymentSuccess = vi.fn();
    const ctx = createMockAuthContext();

    vi.spyOn(paymentApi, 'generar2FA').mockResolvedValueOnce({
      codigo: 0,
      correoEnviado: true,
      correoDestino: 'cesar@gmail.com',
      expiraEnSegundos: 300,
      codigo2FA: '999888', // Even if backend returns it in dev, UI must NOT expose Soft-Token bypass
      mensajeCorreo: 'Enviado vía Brevo',
    });

    vi.spyOn(paymentApi, 'pagarServicio').mockResolvedValueOnce(mockPagoSuccess);

    render(
      <MockAuthProvider value={ctx}>
        <Payment2FAModal
          isOpen={true}
          onClose={vi.fn()}
          product={mockTelcelProduct}
          referencia="5512345678"
          monto={20}
          validation={mockValidationSuccess}
          onPaymentSuccess={onPaymentSuccess}
        />
      </MockAuthProvider>
    );

    await waitFor(() =>
      expect(screen.getByText(/Código 2FA enviado vía correo/i)).toBeInTheDocument()
    );

    // Ensure Soft-Token bypass is completely absent
    expect(screen.queryByText(/Respaldo Soft-Token/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/Usar código/i)).not.toBeInTheDocument();
    expect(screen.queryByText('999888')).not.toBeInTheDocument();

    await user.type(screen.getByPlaceholderText('000000'), '654321');
    await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: /Autorizar y Pagar/i }));

    await waitFor(() => expect(onPaymentSuccess).toHaveBeenCalled());
    expect(ctx.updateBalance).toHaveBeenCalledWith(480);
  });

  it('shows Abonar Saldo CTA when balance is insufficient and opens ProfileModal on abono tab', async () => {
    const user = userEvent.setup();
    const ctx = createMockAuthContext({
      user: { ...mockLoginResponse, saldo: 10 },
    });

    vi.spyOn(paymentApi, 'generar2FA').mockResolvedValueOnce({
      correoEnviado: true,
      correoDestino: 'cesar@gmail.com',
      expiraEnSegundos: 300,
    });

    render(
      <MockAuthProvider value={ctx}>
        <Payment2FAModal
          isOpen={true}
          onClose={vi.fn()}
          product={mockTelcelProduct}
          referencia="5512345678"
          monto={20}
          validation={mockValidationSuccess}
          onPaymentSuccess={vi.fn()}
        />
      </MockAuthProvider>
    );

    await waitFor(() => expect(screen.getByText(/Saldo insuficiente/i)).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: 'Abonar Saldo' }));
    await waitFor(() => expect(screen.getByText('Abonar Fondos a mi Cuenta')).toBeInTheDocument());
  });

  it('keeps 401 OTP errors inline and forwards structured RECHAZADA and PENDIENTE responses', async () => {
    const user = userEvent.setup();
    const onPaymentSuccess = vi.fn();
    const ctx = createMockAuthContext();

    vi.spyOn(paymentApi, 'generar2FA').mockResolvedValue({
      correoEnviado: true,
      correoDestino: 'cesar@gmail.com',
      expiraEnSegundos: 300,
    });

    // 1. 401 wrong OTP stays inline
    vi.spyOn(paymentApi, 'pagarServicio').mockRejectedValueOnce(
      new ApiError('Código 2FA incorrecto', 401, { mensaje: 'Código 2FA incorrecto' })
    );

    render(
      <MockAuthProvider value={ctx}>
        <Payment2FAModal
          isOpen={true}
          onClose={vi.fn()}
          product={mockTelcelProduct}
          referencia="5512345678"
          monto={20}
          validation={mockValidationSuccess}
          onPaymentSuccess={onPaymentSuccess}
        />
      </MockAuthProvider>
    );

    await waitFor(() => expect(screen.getByPlaceholderText('000000')).toBeInTheDocument());
    await user.type(screen.getByPlaceholderText('000000'), '111111');
    await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: /Autorizar y Pagar/i }));

    await waitFor(() => expect(screen.getByText('Código 2FA incorrecto')).toBeInTheDocument());
    expect(onPaymentSuccess).not.toHaveBeenCalled();

    // 2. 422 structured RECHAZADA forwards to result view
    vi.spyOn(paymentApi, 'pagarServicio').mockRejectedValueOnce(
      new ApiError('Referencia rechazada por operador', 422, {
        estadoOperacion: 'RECHAZADA',
        codigoProveedor: '06',
        mensajeOperacion: 'Número inválido en proveedor',
      })
    );

    await user.click(screen.getByRole('button', { name: /Autorizar y Pagar/i }));
    await waitFor(() =>
      expect(onPaymentSuccess).toHaveBeenCalledWith(
        expect.objectContaining({
          estadoOperacion: 'RECHAZADA',
          codigoProveedor: '06',
        })
      )
    );

    // 3. 504 timeout forwards as PENDIENTE
    vi.spyOn(paymentApi, 'pagarServicio').mockRejectedValueOnce(
      new ApiError('Tiempo de espera agotado', 504, {
        estadoOperacion: 'PENDIENTE',
        upc: 'UPC-777',
      })
    );

    await user.click(screen.getByRole('button', { name: /Autorizar y Pagar/i }));
    await waitFor(() =>
      expect(onPaymentSuccess).toHaveBeenCalledWith(
        expect.objectContaining({
          estadoOperacion: 'PENDIENTE',
          upc: 'UPC-777',
        })
      )
    );
  });

  it('counts down OTP TTL to Expirado and supports resend', async () => {
    vi.useFakeTimers();
    vi.spyOn(paymentApi, 'generar2FA').mockResolvedValue({
      correoEnviado: false,
      expiraEnSegundos: 2,
      mensaje: 'Revisa tu correo',
    });

    render(
      <MockAuthProvider>
        <Payment2FAModal
          isOpen={true}
          onClose={vi.fn()}
          product={mockTelcelProduct}
          referencia="5512345678"
          monto={20}
          validation={mockValidationSuccess}
          onPaymentSuccess={vi.fn()}
        />
      </MockAuthProvider>
    );

    await act(async () => {
      await Promise.resolve();
    });

    expect(screen.getByText('0:02')).toBeInTheDocument();

    await act(async () => {
      vi.advanceTimersByTime(3000);
    });
    expect(screen.getByText('Expirado')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /Reenviar/i }));
    await act(async () => {
      await Promise.resolve();
    });
    expect(paymentApi.generar2FA).toHaveBeenCalledTimes(2);
  });
});
