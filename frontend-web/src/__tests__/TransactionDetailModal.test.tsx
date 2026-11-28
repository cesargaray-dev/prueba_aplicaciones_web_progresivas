import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TransactionDetailModal } from '../components/TransactionDetailModal';
import { paymentApi } from '../api/paymentApi';
import { MockAuthProvider, mockHistoryItem } from './testUtils';

describe('TransactionDetailModal Component & Official Leyenda', () => {
  it('renders detail modal with official Leyenda, copies folio & ticket, downloads PDF, prints, and resends email', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
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
      mensaje: 'Comprobante enviado',
    });

    render(
      <MockAuthProvider>
        <TransactionDetailModal item={mockHistoryItem} isOpen={true} onClose={onClose} />
      </MockAuthProvider>
    );

    expect(screen.getByText('TELCEL')).toBeInTheDocument();
    expect(screen.getByText('EXITOSA')).toBeInTheDocument();
    expect(screen.getByText('LEYENDA OFICIAL Y ACLARACIONES')).toBeInTheDocument();
    expect(
      screen.getByText(/Conserve este comprobante digital como constancia oficial de pago/i)
    ).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Copiar folio' }));
    expect(writeText).toHaveBeenCalledWith('FT-20261120-1010');

    await user.click(screen.getByRole('button', { name: 'Copiar' }));
    expect(writeText).toHaveBeenCalledWith(
      expect.stringContaining('LEYENDA OFICIAL Y ACLARACIONES:')
    );

    await user.click(screen.getByRole('button', { name: /Descargar PDF/i }));
    await waitFor(() =>
      expect(paymentApi.descargarComprobantePdf).toHaveBeenCalledWith('FT-20261120-1010')
    );

    // PDF fallback to window.print() on error
    vi.spyOn(paymentApi, 'descargarComprobantePdf').mockRejectedValueOnce(new Error('404'));
    await user.click(screen.getByRole('button', { name: /Descargar PDF/i }));
    await waitFor(() =>
      expect(screen.getByText(/vista de impresión para guardar en PDF/i)).toBeInTheDocument()
    );

    await user.click(screen.getByRole('button', { name: 'Imprimir' }));
    expect(window.print).toHaveBeenCalled();

    // Invalid placeholder email check before valid send
    const resendInput = screen.getByPlaceholderText('tucorreo@gmail.com');
    fireEvent.change(resendInput, { target: { value: 'demo@ejemplo.com' } });
    await user.click(screen.getByRole('button', { name: 'Enviar' }));
    expect(screen.getByText(/correo electrónico real/i)).toBeInTheDocument();

    fireEvent.change(resendInput, { target: { value: 'cesar@gmail.com' } });
    await user.click(screen.getByRole('button', { name: 'Enviar' }));
    await waitFor(() => expect(screen.getByText('Comprobante enviado')).toBeInTheDocument());

    fireEvent.keyDown(window, { key: 'Escape' });
    expect(onClose).toHaveBeenCalled();
  });

  it('hides Descargar PDF on PENDIENTE and renders RECHAZADA with rejected official Leyenda', () => {
    const { rerender } = render(
      <MockAuthProvider>
        <TransactionDetailModal
          item={{
            ...mockHistoryItem,
            estado: 'PENDIENTE',
            telefono: undefined,
            referencia: '9988776655',
            leyenda: 'Leyenda en conciliación',
          }}
          isOpen={true}
          onClose={vi.fn()}
        />
      </MockAuthProvider>
    );
    expect(screen.getByText('PENDIENTE')).toBeInTheDocument();
    expect(screen.getByText('9988776655')).toBeInTheDocument();
    expect(screen.getByText('Leyenda en conciliación')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Descargar PDF/i })).not.toBeInTheDocument();

    rerender(
      <MockAuthProvider>
        <TransactionDetailModal
          item={{
            ...mockHistoryItem,
            estado: 'RECHAZADA',
            numeroAutorizacion: '-1',
          }}
          isOpen={true}
          onClose={vi.fn()}
        />
      </MockAuthProvider>
    );
    expect(screen.getByText('RECHAZADA')).toBeInTheDocument();
    expect(
      screen.getByText(/Operación no aplicada: no se realizó ningún cargo a su cuenta/i)
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Descargar PDF/i })).toBeInTheDocument();
  });
});
