import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TransactionDetailModal } from '../components/TransactionDetailModal';
import { paymentApi } from '../api/paymentApi';
import { MockAuthProvider, mockHistoryItem } from './testUtils';

describe('TransactionDetailModal Component', () => {
  it('renders detail modal, copies folio, downloads PDF with fallback, prints, and resends email', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true, writable: true });

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

    await user.click(screen.getByRole('button', { name: 'Copiar folio' }));
    expect(writeText).toHaveBeenCalledWith('FT-20261120-1010');

    await user.click(screen.getByRole('button', { name: 'PDF' }));
    await waitFor(() =>
      expect(paymentApi.descargarComprobantePdf).toHaveBeenCalledWith('FT-20261120-1010')
    );

    await user.click(screen.getByRole('button', { name: 'Imprimir' }));
    expect(window.print).toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'Enviar' }));
    await waitFor(() => expect(screen.getByText('Comprobante enviado')).toBeInTheDocument());

    fireEvent.keyDown(window, { key: 'Escape' });
    expect(onClose).toHaveBeenCalled();
  });

  it('renders PENDIENTE and RECHAZADA statuses with service reference', () => {
    const { rerender } = render(
      <MockAuthProvider>
        <TransactionDetailModal
          item={{
            ...mockHistoryItem,
            estado: 'PENDIENTE',
            telefono: undefined,
            referencia: '9988776655',
          }}
          isOpen={true}
          onClose={vi.fn()}
        />
      </MockAuthProvider>
    );
    expect(screen.getByText('PENDIENTE')).toBeInTheDocument();
    expect(screen.getByText('9988776655')).toBeInTheDocument();

    rerender(
      <MockAuthProvider>
        <TransactionDetailModal
          item={{ ...mockHistoryItem, estado: 'RECHAZADA' }}
          isOpen={true}
          onClose={vi.fn()}
        />
      </MockAuthProvider>
    );
    expect(screen.getByText('RECHAZADA')).toBeInTheDocument();
  });
});
