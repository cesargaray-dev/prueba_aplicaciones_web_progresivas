import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrandLogo } from '../components/BrandLogo';
import { CategoryChips } from '../components/CategoryChips';
import { ServiceCard } from '../components/ServiceCard';
import { mockCfeProduct, mockTelcelProduct } from './testUtils';

describe('BrandLogo, CategoryChips, and ServiceCard', () => {
  it('renders BrandLogo with encoded serviceId, external logoUrl precedence, and fallbacks', () => {
    const { rerender } = render(
      <BrandLogo serviceId="76/test" serviceName="Telcel Móvil" size="sm" />
    );
    const img = screen.getByRole('img', { name: 'Telcel Móvil' });
    expect(img).toHaveAttribute(
      'src',
      'https://gestopago.portalventas.net/sistema/images/gestopago/servicios/76%2Ftest.png'
    );
    // Uses dedicated pearl medallion class instead of bg-white so dark mode never turns logo background dark
    expect(screen.getByTestId('brand-logo-tile')).toHaveClass('brand-logo-tile');
    expect(screen.getByTestId('brand-logo-tile')).not.toHaveClass('bg-white');

    // External https logoUrl takes precedence
    rerender(
      <BrandLogo
        serviceId="76"
        serviceName="CFE Electricidad"
        logoUrl="https://cdn.example.com/cfe.png"
        size="lg"
      />
    );
    expect(screen.getByRole('img', { name: 'CFE Electricidad' })).toHaveAttribute(
      'src',
      'https://cdn.example.com/cfe.png'
    );

    // Image error falls back to icon + initials
    fireEvent.error(screen.getByRole('img', { name: 'CFE Electricidad' }));
    expect(screen.getByText('CE')).toBeInTheDocument();

    // Missing serviceId + different fallback keywords
    rerender(<BrandLogo serviceName="SIAPA Agua" />);
    expect(screen.getByText('SA')).toBeInTheDocument();
    rerender(<BrandLogo serviceName="IZZI" />);
    expect(screen.getByText('IZ')).toBeInTheDocument();
    rerender(<BrandLogo serviceName="Banco Afore" />);
    expect(screen.getByText('BA')).toBeInTheDocument();
    rerender(<BrandLogo serviceName="Otro" />);
    expect(screen.getByText('OT')).toBeInTheDocument();
  });

  it('renders CategoryChips with counts and triggers onSelectCategory', async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();

    render(
      <CategoryChips
        selectedCategory="RECARGAS"
        onSelectCategory={onSelect}
        counts={{ ALL: 10, RECARGAS: 4, CFE: 0 }}
      />
    );

    expect(screen.getByText('4')).toBeInTheDocument();
    await user.click(screen.getByText('Luz y Energía (CFE)'));
    expect(onSelect).toHaveBeenCalledWith('CFE');
  });

  it('renders ServiceCard with category label, legend, fixed/open price, and triggers onSelect', async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();

    const { rerender } = render(<ServiceCard product={mockTelcelProduct} onSelect={onSelect} />);
    expect(screen.getByText('Recarga Móvil')).toBeInTheDocument();
    expect(screen.getByText('Telefonía')).toBeInTheDocument();
    expect(screen.getByText('Tiempo aire y megas nacionales')).toBeInTheDocument();
    expect(screen.getByText('$20.00')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Recargar/i }));
    expect(onSelect).toHaveBeenCalledWith(mockTelcelProduct);

    rerender(<ServiceCard product={mockCfeProduct} onSelect={onSelect} />);
    expect(screen.getByText('Pago de Servicio')).toBeInTheDocument();
    expect(screen.getByText('Luz y Energía (CFE)')).toBeInTheDocument();
    expect(screen.getByText('Monto Abierto')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Pagar/i }));
    expect(onSelect).toHaveBeenCalledWith(mockCfeProduct);
  });
});
