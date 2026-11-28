import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ReceiptModal } from '../components/ReceiptModal';
import { paymentApi } from '../api/paymentApi';
import { MockAuthProvider, mockPagoSuccess, mockTelcelProduct } from './testUtils';

describe('ReceiptModal 3-State Result View & Official Leyenda', () => {
  it('renders EXITOSA state with official Leyenda, Brevo resend, PDF download, print, and copy including leyenda', async () => {
    const user = userEvent.setup();
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
      writable: true,
    });

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
    expect(screen.getByText('LEYENDA OFICIAL Y ACLARACIONES')).toBeInTheDocument();
    expect(
      screen.getByText(/Conserve este comprobante digital como constancia oficial de pago/i)
    ).toBeInTheDocument();

    // PDF download
    await user.click(screen.getByRole('button', { name: /Descargar PDF/i }));
    await waitFor(() =>
      expect(paymentApi.descargarComprobantePdf).toHaveBeenCalledWith('FT-20261120-1010')
    );

    // PDF fallback to window.print()
    vi.spyOn(paymentApi, 'descargarComprobantePdf').mockRejectedValueOnce(new Error('404'));
    await user.click(screen.getByRole('button', { name: /Descargar PDF/i }));
    await waitFor(() =>
      expect(screen.getByText(/vista de impresión para guardar en PDF/i)).toBeInTheDocument()
    );
    expect(window.print).toHaveBeenCalled();

    // Copy includes LEYENDA OFICIAL Y ACLARACIONES
    await user.click(screen.getByRole('button', { name: /Copiar/i }));
    expect(writeText).toHaveBeenCalledWith(
      expect.stringContaining('LEYENDA OFICIAL Y ACLARACIONES:')
    );

    // Resend Brevo
    await user.click(screen.getByRole('button', { name: 'Reenviar' }));
    await user.click(screen.getByRole('button', { name: 'Enviar' }));
    await waitFor(() => expect(screen.getByText('Enviado OK')).toBeInTheDocument());
  });

  it('renders PENDIENTE state with UPC warning, official Leyenda, NO Descargar PDF button, and Ir al Historial', async () => {
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
            leyenda: 'Leyenda personalizada de conciliación bancaria.',
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
    expect(screen.getByText('Leyenda personalizada de conciliación bancaria.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Descargar PDF/i })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Ir al Historial/i }));
    expect(onClose).toHaveBeenCalled();
    expect(onGoToHistory).toHaveBeenCalled();
  });

  it('renders RECHAZADA -1 Recarga Telcel ticket with Origen no valido and rejected official Leyenda', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
      writable: true,
    });

    render(
      <MockAuthProvider>
        <ReceiptModal
          isOpen={true}
          onClose={onClose}
          receipt={{
            estadoOperacion: 'RECHAZADA',
            codigoProveedor: '-1',
            numeroAutorizacion: '-1',
            motivoProveedor: 'Origen no valido',
            firmaDigital: 'AbCdEf123456...XyZ9==',
            upc: 'GP1234567890',
            folioTicket: 'FT-20261127-ABCD',
            mensajeOperacion:
              'Recarga Telcel $20 — Operación rechazada: Origen no valido (Respuesta GestoPago: -1 | Código: -1).',
            montoPagado: 20,
            saldoRestanteCuenta: 500,
          }}
          product={mockTelcelProduct}
          referencia="1111111111"
        />
      </MockAuthProvider>
    );

    expect(screen.getAllByText('Operación rechazada')).toHaveLength(1);
    expect(screen.getAllByText(/Recarga Telcel/i)).toHaveLength(1);
    expect(screen.getAllByText('Origen no valido')).toHaveLength(1);
    expect(screen.getAllByText('-1')).toHaveLength(1);
    expect(screen.getByText('LEYENDA OFICIAL Y ACLARACIONES')).toBeInTheDocument();
    expect(
      screen.getByText(/Operación no aplicada: no se realizó ningún cargo a su cuenta/i)
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Descargar PDF/i })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Copiar/i }));
    expect(writeText).toHaveBeenCalledWith(
      expect.stringContaining('Folio de Ticket: FT-20261127-ABCD.')
    );

    fireEvent.keyDown(window, { key: 'Escape' });
    expect(onClose).toHaveBeenCalled();
  });
});
