import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, waitFor, act, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Payment2FAModal } from '../components/Payment2FAModal';
import { authApi } from '../api/authApi';
import { paymentApi } from '../api/paymentApi';
import { ApiError } from '../api/client';
import {
  MockAuthProvider,
  createMockAuthContext,
  mockLoginResponse,
  mockPagoSuccess,
  mockTelcelProduct,
  mockTotpSetup,
  mockValidationSuccess,
} from './testUtils';

describe('Payment2FAModal Component (TOTP & EMAIL 2FA)', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('supports TOTP mode (Google Authenticator 30s), hides email banner & resend button, and processes payment', async () => {
    const user = userEvent.setup();
    const onPaymentSuccess = vi.fn();
    const ctx = createMockAuthContext({
      user: { ...mockLoginResponse, totpHabilitado: true },
    });

    vi.spyOn(paymentApi, 'generar2FA').mockResolvedValueOnce({
      codigo: 0,
      metodo2FA: 'TOTP',
      periodoTotpSegundos: 30,
      mensaje: 'Ingresa el código actual de Google Authenticator',
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
      expect(
        screen.getByText(/Autenticación con Google Authenticator activa/i)
      ).toBeInTheDocument()
    );
    expect(screen.getByText(/Código dinámico cada 30s/i)).toBeInTheDocument();
    expect(screen.queryByText(/Código 2FA enviado vía correo/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Reenviar/i })).not.toBeInTheDocument();

    await user.type(screen.getByPlaceholderText('000000'), '654321');
    await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: /Autorizar y Pagar/i }));

    await waitFor(() => expect(onPaymentSuccess).toHaveBeenCalled());
    expect(ctx.updateBalance).toHaveBeenCalledWith(480);
  });

  it('supports EMAIL mode, NEVER renders Soft-Token bypass, and allows inline Vincular Google Authenticator', async () => {
    const user = userEvent.setup();
    const onPaymentSuccess = vi.fn();
    const ctx = createMockAuthContext();

    vi.spyOn(paymentApi, 'generar2FA')
      .mockResolvedValueOnce({
        codigo: 0,
        metodo2FA: 'EMAIL',
        correoEnviado: true,
        correoDestino: 'cesar@gmail.com',
        expiraEnSegundos: 300,
        codigo2FA: '999888',
        mensajeCorreo: 'Enviado vía Brevo',
      })
      .mockResolvedValueOnce({
        codigo: 0,
        metodo2FA: 'TOTP',
        periodoTotpSegundos: 30,
      });

    vi.spyOn(authApi, 'iniciarTotpPerfil').mockResolvedValueOnce(mockTotpSetup);
    vi.spyOn(authApi, 'activarTotpPerfil').mockResolvedValueOnce({
      totpHabilitado: true,
      mensaje: 'Activado',
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

    expect(screen.queryByText(/Respaldo Soft-Token/i)).not.toBeInTheDocument();
    expect(screen.queryByText('999888')).not.toBeInTheDocument();

    // Link Google Authenticator inline right from EMAIL mode (test cancel then activate)
    await user.click(screen.getByRole('button', { name: /Vincular Google Authenticator/i }));
    await waitFor(() => expect(screen.getByAltText('QR Google Authenticator')).toBeInTheDocument());
    await user.click(screen.getAllByRole('button', { name: 'Cancelar' })[0]);
    expect(screen.queryByAltText('QR Google Authenticator')).not.toBeInTheDocument();

    vi.spyOn(authApi, 'iniciarTotpPerfil').mockResolvedValueOnce(mockTotpSetup);
    await user.click(screen.getByRole('button', { name: /Vincular Google Authenticator/i }));
    await waitFor(() => expect(screen.getByAltText('QR Google Authenticator')).toBeInTheDocument());
    await user.type(
      screen.getByLabelText(/Código de Google Authenticator \(6 dígitos\)/i),
      '123456'
    );
    await user.click(screen.getByRole('button', { name: /Activar y Usar TOTP/i }));

    await waitFor(() =>
      expect(
        screen.getByText(/Autenticación con Google Authenticator activa/i)
      ).toBeInTheDocument()
    );

    // Submit form directly via fireEvent to exercise form guard branches (unchecked, short code)
    const form = screen.getByPlaceholderText('000000').closest('form')!;
    fireEvent.submit(form);
    expect(screen.getByText(/casilla de confirmación explícita/i)).toBeInTheDocument();

    await user.click(screen.getByRole('checkbox'));
    fireEvent.submit(form);
    expect(
      screen.getByText(/Ingresa el código de 6 dígitos de Google Authenticator/i)
    ).toBeInTheDocument();

    await user.type(screen.getByPlaceholderText('000000'), '654321');
    await user.click(screen.getByRole('button', { name: /Autorizar y Pagar/i }));

    await waitFor(() => expect(onPaymentSuccess).toHaveBeenCalled());
  });

  it('shows Admin funding notice for regular clients and Abonar Saldo CTA for admin when balance is insufficient', async () => {
    const user = userEvent.setup();
    const clientCtx = createMockAuthContext({
      user: { ...mockLoginResponse, saldo: 0, esAdmin: false },
    });

    vi.spyOn(paymentApi, 'generar2FA').mockResolvedValue({
      metodo2FA: 'EMAIL',
      correoEnviado: true,
      correoDestino: 'cesar@gmail.com',
      expiraEnSegundos: 300,
    });

    const { unmount } = render(
      <MockAuthProvider value={clientCtx}>
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
    await waitFor(() =>
      expect(
        screen.getByText(/Solicita a un Administrador el ingreso de dinero/i)
      ).toBeInTheDocument()
    );
    unmount();

    const ctx = createMockAuthContext({
      user: { ...mockLoginResponse, saldo: 10, esAdmin: true, usuario: 'admin' },
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
    await waitFor(() =>
      expect(screen.getByText('Abonar Fondos (Administrador)')).toBeInTheDocument()
    );
  });

  it('keeps 401 OTP errors inline and forwards structured RECHAZADA and PENDIENTE responses', async () => {
    const user = userEvent.setup();
    const onPaymentSuccess = vi.fn();
    const ctx = createMockAuthContext();

    vi.spyOn(paymentApi, 'generar2FA').mockResolvedValue({
      metodo2FA: 'EMAIL',
      correoEnviado: true,
      correoDestino: 'cesar@gmail.com',
      expiraEnSegundos: 300,
    });

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

    vi.spyOn(paymentApi, 'pagarServicio').mockRejectedValueOnce(
      new ApiError('Referencia rechazada por operador', 422, {
        estadoOperacion: 'RECHAZADA',
        codigoProveedor: '-1',
        mensajeOperacion: 'Origen no valido',
        leyenda: 'Leyenda rechazo oficial',
      })
    );

    await user.click(screen.getByRole('button', { name: /Autorizar y Pagar/i }));
    await waitFor(() =>
      expect(onPaymentSuccess).toHaveBeenCalledWith(
        expect.objectContaining({
          estadoOperacion: 'RECHAZADA',
          codigoProveedor: '-1',
          leyenda: 'Leyenda rechazo oficial',
        })
      )
    );

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

  it('counts down EMAIL OTP TTL to Expirado and supports resend', async () => {
    vi.useFakeTimers();
    vi.spyOn(paymentApi, 'generar2FA').mockResolvedValue({
      metodo2FA: 'EMAIL',
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
