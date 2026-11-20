import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ReceiptModal } from '../components/ReceiptModal';
import { paymentApi } from '../api/paymentApi';
import { MockAuthProvider, mockPagoSuccess, mockTelcelProduct } from './testUtils';

describe('ReceiptModal 3-State Result View (F12)', () => {
  it('renders EXITOSA state with Brevo resend, PDF download (and print fallback), print, and copy', async () => {
    const user = userEvent.setup();
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true, writable: true });

    vi.spyOn(paymentApi, 'descargarComprobantePdf').mockResolvedValueOnce(
      new Blob(['pdf'], { type: 'application/pdf' })
    );
    vi.spyOn(paymentApi, 'reenviarComprobante').mockResolvedValueOnce({
      enviado: true,
      correoDestino: 'destino@gmail.com',
      mensaje: 'Enviado OK',
    });

    render(
      <MockAuthProvider>
        <ReceiptModal
          isOpen={true}
          onClose={vi.fn()}
          receipt={mockPagoSuccess}
          product={mockTelcelProduct}
          referencia="5512345678"
        />
      </MockAuthProvider>
    );

    expect(screen.getByText('¡Operación Exitosa!')).toBeInTheDocument();
    expect(screen.getByText('Folio: FT-20261120-1010')).toBeInTheDocument();

    // PDF download
    await user.click(screen.getByRole('button', { name: /Descargar PDF/i }));
    await waitFor(() => expect(paymentApi.descargarComprobantePdf).toHaveBeenCalledWith('FT-20261120-1010'));

    // PDF fallback to window.print()
    vi.spyOn(paymentApi, 'descargarComprobantePdf').mockRejectedValueOnce(new Error('404'));
    await user.click(screen.getByRole('button', { name: /Descargar PDF/i }));
    await waitFor(() =>
      expect(screen.getByText(/vista de impresión para guardar en PDF/i)).toBeInTheDocument()
    );
    expect(window.print).toHaveBeenCalled();

    // Copy
    await user.click(screen.getByRole('button', { name: /Copiar/i }));
    expect(writeText).toHaveBeenCalled();

    // Resend Brevo
    await user.click(screen.getByRole('button', { name: 'Reenviar' }));
    await user.click(screen.getByRole('button', { name: 'Enviar' }));
    await waitFor(() => expect(screen.getByText('Enviado OK')).toBeInTheDocument());
  });

  it('renders PENDIENTE state with UPC warning and Ir al Historial button', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const onGoToHistory = vi.fn();

    render(
      <MockAuthProvider>
        <ReceiptModal
          isOpen={true}
          onClose={onClose}
          receipt={{
            ...mockPagoSuccess,
            estadoOperacion: 'PENDIENTE',
            upc: 'UPC-900',
            mensajeOperacion: 'En espera de conciliación',
          }}
          product={mockTelcelProduct}
          referencia="5512345678"
          onGoToHistory={onGoToHistory}
        />
      </MockAuthProvider>
    );

    expect(screen.getByText('Operación Pendiente / No Confirmada')).toBeInTheDocument();
    expect(screen.getByText('UPC: UPC-900')).toBeInTheDocument();
    expect(screen.getByText(/Nunca trates esta operación como exitosa/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Descargar PDF/i })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Ir al Historial/i }));
    expect(onClose).toHaveBeenCalled();
    expect(onGoToHistory).toHaveBeenCalled();
  });

  it('renders RECHAZADA state with untouched balance notice and hides success/Brevo actions', () => {
    const onClose = vi.fn();

    render(
      <MockAuthProvider>
        <ReceiptModal
          isOpen={true}
          onClose={onClose}
          receipt={{
            estadoOperacion: 'RECHAZADA',
            codigoProveedor: '06',
            mensajeOperacion: 'Servicio no disponible temporalmente',
            montoPagado: 20,
            saldoRestanteCuenta: 500,
          }}
          product={mockTelcelProduct}
          referencia="5512345678"
        />
      </MockAuthProvider>
    );

    expect(screen.getByText('Operación Rechazada')).toBeInTheDocument();
    expect(screen.getByText('Tu saldo no fue descontado')).toBeInTheDocument();
    expect(screen.getByText(/Código del proveedor: 06/i)).toBeInTheDocument();
    expect(screen.queryByText(/Comprobante enviado vía Brevo/i)).not.toBeInTheDocument();

    fireEvent.keyDown(window, { key: 'Escape' });
    expect(onClose).toHaveBeenCalled();
  });
});
