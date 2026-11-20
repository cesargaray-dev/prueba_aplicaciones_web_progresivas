import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../App';
import { LoginPage } from '../pages/LoginPage';
import { CatalogPage } from '../pages/CatalogPage';
import { HistoryPage } from '../pages/HistoryPage';
import { authApi } from '../api/authApi';
import { setAuthToken } from '../api/client';
import { catalogApi } from '../api/catalogApi';
import { historyApi } from '../api/historyApi';
import {
  MockAuthProvider,
  createMockAuthContext,
  mockUserProfile,
  mockTelcelProduct,
  mockCfeProduct,
  mockHistoryItem,
} from './testUtils';
import type { ProductDTO } from '../types/catalog';

describe('Pages Suite (LoginPage, CatalogPage, HistoryPage)', () => {
  describe('LoginPage Component', () => {
    it('validates login form, toggles password visibility, and handles login error and success', async () => {
      const user = userEvent.setup();
      const loginMock = vi
        .fn()
        .mockRejectedValueOnce(new Error('Credenciales incorrectas'))
        .mockResolvedValueOnce(undefined);
      const clearExpiredMock = vi.fn();

      const ctx = createMockAuthContext({
        user: null,
        isAuthenticated: false,
        login: loginMock,
        sessionExpiredMessage: 'Sesión cerrada por inactividad',
        clearSessionExpired: clearExpiredMock,
      });

      render(
        <MockAuthProvider value={ctx}>
          <LoginPage />
        </MockAuthProvider>
      );

      expect(screen.getByText('Sesión cerrada por inactividad')).toBeInTheDocument();
      await user.click(screen.getByLabelText('Cerrar aviso'));
      expect(clearExpiredMock).toHaveBeenCalled();

      // Submit empty login
      await user.click(screen.getByRole('button', { name: /Ingresar de forma segura/i }));
      expect(
        screen.getByText('Por favor, ingresa tu usuario y contraseña.')
      ).toBeInTheDocument();

      await user.type(screen.getByLabelText(/Usuario/i), 'cesar123');
      await user.type(screen.getByLabelText(/^Contraseña$/i), 'Venta2026#');
      await user.click(screen.getByLabelText('Mostrar contraseña'));
      expect(screen.getByLabelText('Ocultar contraseña')).toBeInTheDocument();

      // First submit rejects
      await user.click(screen.getByRole('button', { name: /Ingresar de forma segura/i }));
      await waitFor(() =>
        expect(screen.getByText('Credenciales incorrectas')).toBeInTheDocument()
      );

      // Second submit succeeds
      await user.click(screen.getByRole('button', { name: /Ingresar de forma segura/i }));
      expect(loginMock).toHaveBeenCalledTimes(2);
    });

    it('validates all registration fields and submits registration', async () => {
      const user = userEvent.setup();
      const registerMock = vi
        .fn()
        .mockRejectedValueOnce(new Error('Usuario duplicado'))
        .mockResolvedValueOnce(undefined);
      const ctx = createMockAuthContext({
        user: null,
        isAuthenticated: false,
        register: registerMock,
      });

      render(
        <MockAuthProvider value={ctx}>
          <LoginPage />
        </MockAuthProvider>
      );

      await user.click(screen.getByRole('button', { name: /Crear Cuenta/i }));
      await user.click(screen.getByRole('button', { name: /Iniciar Sesión/i }));
      await user.click(screen.getByRole('button', { name: /Crear Cuenta/i }));
      const submitBtn = () =>
        screen.getByRole('button', { name: /Crear Cuenta y Acceder/i });

      // 1. Invalid nombre
      await user.click(submitBtn());
      expect(screen.getByText(/El nombre debe tener entre 2 y 50/i)).toBeInTheDocument();
      await user.type(screen.getByLabelText(/^Nombre \*/i), 'Cesar');

      // 2. Invalid segundoNombre
      const segInput = screen.getByLabelText(/Segundo Nombre/i);
      await user.type(segInput, '123');
      await user.click(submitBtn());
      expect(screen.getByText(/El segundo nombre solo puede contener letras/i)).toBeInTheDocument();
      await user.clear(segInput);
      await user.type(segInput, 'Enrique');

      // 3. Invalid apellidoPaterno
      await user.click(submitBtn());
      expect(screen.getByText(/El apellido paterno debe tener/i)).toBeInTheDocument();
      await user.type(screen.getByLabelText(/Ap\. Paterno/i), 'Garay');

      // 4. Invalid apellidoMaterno
      await user.click(submitBtn());
      expect(screen.getByText(/El apellido materno debe tener/i)).toBeInTheDocument();
      await user.type(screen.getByLabelText(/Ap\. Materno/i), 'Garcia');

      // 5. Empty & underage birth date
      const birthInput = screen.getByLabelText(/Fecha de nacimiento/i);
      fireEvent.change(birthInput, { target: { value: '' } });
      await user.click(submitBtn());
      expect(screen.getByText(/Selecciona tu fecha de nacimiento/i)).toBeInTheDocument();
      fireEvent.change(birthInput, { target: { value: '2020-01-01' } });
      await user.click(submitBtn());
      expect(screen.getByText(/Debes ser mayor de 18 años/i)).toBeInTheDocument();
      fireEvent.change(birthInput, { target: { value: '1998-01-15' } });

      // 6. Invalid & placeholder email + sexo change
      fireEvent.change(screen.getByLabelText(/Sexo \*/i), { target: { value: 'MUJER' } });
      const emailInput = screen.getByLabelText(/Correo electrónico real/i);
      await user.type(emailInput, 'correo-invalido');
      await user.click(submitBtn());
      expect(screen.getByText(/formato válido/i)).toBeInTheDocument();
      await user.clear(emailInput);
      await user.type(emailInput, 'user@ejemplo.com');
      await user.click(submitBtn());
      expect(screen.getByText(/Ingresa un correo electrónico real/i)).toBeInTheDocument();
      await user.clear(emailInput);
      await user.type(emailInput, 'cesar@gmail.com');

      // 7. Invalid phone
      const telInput = screen.getByLabelText(/Teléfono móvil México/i);
      await user.type(telInput, '1111111111');
      await user.click(submitBtn());
      await user.clear(telInput);
      await user.type(telInput, '5512345678');

      // 8. Invalid usuario
      const usrInput = screen.getByLabelText(/^Usuario \*/i);
      await user.type(usrInput, 'ab');
      await user.click(submitBtn());
      expect(screen.getByText(/El usuario debe tener entre 4 y 50/i)).toBeInTheDocument();
      await user.clear(usrInput);
      await user.type(usrInput, 'cesar_nuevo');

      // 9. Weak password & mismatch + toggle register password visibility
      await user.click(screen.getByLabelText('Mostrar contraseña'));
      const passInput = screen.getByLabelText(/^Contraseña \*/i);
      const confInput = screen.getByLabelText(/Confirmar contraseña/i);
      await user.type(passInput, 'debil');
      await user.click(submitBtn());
      expect(screen.getByText(/La contraseña debe tener al menos 8/i)).toBeInTheDocument();

      await user.clear(passInput);
      await user.type(passInput, 'Venta2026#');
      await user.type(confInput, 'Otra2026#');
      await user.click(submitBtn());
      expect(screen.getByText(/Las contraseñas no coinciden/i)).toBeInTheDocument();

      await user.clear(confInput);
      await user.type(confInput, 'Venta2026#');

      // First submit rejects, second succeeds
      await user.click(submitBtn());
      await waitFor(() => expect(screen.getByText('Usuario duplicado')).toBeInTheDocument());

      await user.click(submitBtn());
      expect(registerMock).toHaveBeenCalledTimes(2);
    }, 20000);
  });

  describe('CatalogPage Component', () => {
    const sampleProducts: ProductDTO[] = [
      mockTelcelProduct,
      mockCfeProduct,
      {
        id: '301',
        name: 'Agua SIAPA',
        service: 'SIAPA',
        serviceId: '80',
        price: '0',
        frontType: '2',
        categoryId: '15',
      },
      {
        id: '302',
        name: 'Internet IZZI',
        service: 'IZZI',
        serviceId: '81',
        price: '0',
        frontType: '2',
        categoryId: '4',
      },
      {
        id: '303',
        name: 'Recarga PASE',
        service: 'PASE',
        serviceId: '82',
        price: '200',
        frontType: '1',
        categoryId: '18',
      },
      {
        id: '304',
        name: 'Pago Infonavit',
        service: 'INFONAVIT',
        serviceId: '83',
        price: '0',
        frontType: '2',
        categoryId: '8',
      },
      {
        id: '305',
        name: 'Tarjeta Netflix',
        service: 'NETFLIX',
        serviceId: '84',
        price: '300',
        frontType: '1',
        categoryId: '10',
      },
    ];

    it('loads products, filters by frontType, category, popular brand, search, and opens ReferenceModal', async () => {
      const user = userEvent.setup();
      vi.spyOn(catalogApi, 'getProductos').mockResolvedValue(sampleProducts);

      render(
        <MockAuthProvider>
          <CatalogPage />
        </MockAuthProvider>
      );

      await waitFor(() =>
        expect(screen.getByText('Paquete Sin Limite 20')).toBeInTheDocument()
      );

      // Filter by frontType Recargas and Pago de Servicios
      await user.click(screen.getByRole('button', { name: /^Recargas$/i }));
      await user.click(screen.getByRole('button', { name: /Pago de Servicios/i }));
      await user.click(screen.getByRole('button', { name: /^Todos$/i }));

      // Filter by categories
      for (const catLabel of [
        /Luz y Energía/i,
        /Agua Potable/i,
        /Internet y Televisión/i,
        /TAG y Casetas/i,
        /Financiero y Créditos/i,
        /Streaming y Pines/i,
        /Todos los Servicios/i,
      ]) {
        await user.click(screen.getByRole('button', { name: catLabel }));
      }

      // Popular brand chip
      await user.click(screen.getByRole('button', { name: 'CFE' }));
      expect(screen.getByText('Pago Recibo CFE')).toBeInTheDocument();

      // Search with no results & reset
      const searchInput = screen.getByPlaceholderText(/Buscar servicio/i);
      await user.clear(searchInput);
      await user.type(searchInput, 'NO_EXISTE_XYZ');
      expect(screen.getByText(/No se encontraron servicios/i)).toBeInTheDocument();
      await user.click(screen.getByRole('button', { name: /Ver todos los servicios/i }));

      // Open ReferenceModal by selecting a product
      const payButtons = screen.getAllByRole('button', { name: /Recargar|Pagar/i });
      await user.click(payButtons[0]);
      expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    it('renders error state, retries loading catalog, and paginates >24 items', async () => {
      const user = userEvent.setup();
      const manyProducts: ProductDTO[] = [
        ...sampleProducts,
        ...Array.from({ length: 20 }, (_, idx) => ({
          ...mockTelcelProduct,
          id: String(5000 + idx),
          name: `Paquete Extra ${idx}`,
        })),
      ];
      vi.spyOn(catalogApi, 'getProductos')
        .mockRejectedValueOnce(new Error('Fallo de red GestoPago'))
        .mockResolvedValueOnce(manyProducts);

      render(
        <MockAuthProvider>
          <CatalogPage />
        </MockAuthProvider>
      );

      await waitFor(() =>
        expect(screen.getByText('Fallo de red GestoPago')).toBeInTheDocument()
      );
      await user.click(screen.getByRole('button', { name: /Reintentar Carga/i }));
      await waitFor(() =>
        expect(screen.getByText('Paquete Sin Limite 20')).toBeInTheDocument()
      );

      const loadMoreBtn = screen.getByRole('button', { name: /Mostrar .* más/i });
      await user.click(loadMoreBtn);
    });
  });

  describe('HistoryPage Component', () => {
    it('loads history, renders KPIs, filters by type and search, and opens detail modal', async () => {
      const user = userEvent.setup();
      const items = [
        mockHistoryItem,
        {
          ...mockHistoryItem,
          idPago: 2,
          folioTicket: 'FT-CFE-2',
          servicio: 'CFE',
          producto: 'Recibo CFE',
          telefono: '5512345678',
          referencia: '998877665544',
          monto: 350,
          estado: 'PENDIENTE',
        },
        {
          ...mockHistoryItem,
          idPago: 3,
          folioTicket: 'FT-ERR-3',
          servicio: 'IZZI',
          producto: 'Mensualidad IZZI',
          telefono: '',
          referencia: '11223344',
          monto: 500,
          estado: 'RECHAZADA',
        },
      ];

      vi.spyOn(historyApi, 'getHistorial').mockResolvedValue({
        codigo: 0,
        mensaje: 'OK',
        numeroCuenta: '5915019417993769',
        totalMovimientos: 3,
        pagos: items,
      });

      render(
        <MockAuthProvider>
          <HistoryPage onGoToCatalog={vi.fn()} />
        </MockAuthProvider>
      );

      await waitFor(() => expect(screen.getAllByText(/TELCEL/i).length).toBeGreaterThan(0));

      // Filter tabs
      await user.click(screen.getByRole('button', { name: 'Recargas' }));
      await user.click(screen.getByRole('button', { name: 'Servicios' }));
      await user.click(screen.getByRole('button', { name: 'Todos' }));

      // Search with no match & clear filters
      const searchInput = screen.getByPlaceholderText(/Buscar por servicio/i);
      await user.type(searchInput, 'SIN_COINCIDENCIA_999');
      expect(
        screen.getByText('Sin coincidencias para los filtros actuales')
      ).toBeInTheDocument();
      await user.click(screen.getByRole('button', { name: /Limpiar filtros/i }));

      // Open TransactionDetailModal
      const detailBtns = screen.getAllByRole('button', { name: 'Ver' });
      await user.click(detailBtns[0]);
      expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    it('handles error state and empty history state', async () => {
      const user = userEvent.setup();
      const onGoToCatalog = vi.fn();
      vi.spyOn(historyApi, 'getHistorial')
        .mockRejectedValueOnce(new Error('Error de servidor historial'))
        .mockResolvedValueOnce({
          codigo: 0,
          mensaje: 'OK',
          numeroCuenta: '5915019417993769',
          totalMovimientos: 0,
          pagos: [],
        });

      render(
        <MockAuthProvider>
          <HistoryPage onGoToCatalog={onGoToCatalog} />
        </MockAuthProvider>
      );

      await waitFor(() =>
        expect(screen.getByText('Error de servidor historial')).toBeInTheDocument()
      );
      await user.click(screen.getByRole('button', { name: 'Reintentar' }));
      await waitFor(() =>
        expect(screen.getByText('Aún no registras operaciones')).toBeInTheDocument()
      );
      await user.click(screen.getByRole('button', { name: /Explorar Catálogo/i }));
      expect(onGoToCatalog).toHaveBeenCalled();
    });
  });

  describe('App Root Component', () => {
    it('renders LoginPage when unauthenticated and MainLayout when authenticated', async () => {
      const user = userEvent.setup();

      // 1. Unauthenticated render -> shows LoginPage
      setAuthToken(null);
      const { unmount } = render(<App />);
      expect(screen.getByText(/Accede a tu banca/i)).toBeInTheDocument();
      unmount();

      // 2. Authenticated render -> hydrates from sessionStorage and switches tabs
      setAuthToken('boot-jwt-app');
      vi.spyOn(authApi, 'getMe').mockResolvedValue(mockUserProfile);
      vi.spyOn(catalogApi, 'getProductos').mockResolvedValue([mockTelcelProduct]);
      vi.spyOn(historyApi, 'getHistorial').mockResolvedValue({
        codigo: 0,
        mensaje: 'OK',
        numeroCuenta: '5225253189113705',
        totalMovimientos: 0,
        pagos: [],
      });

      render(<App />);
      expect(screen.getByText(/Verificando sesión segura/i)).toBeInTheDocument();

      await waitFor(() =>
        expect(screen.getByText('Paquete Sin Limite 20')).toBeInTheDocument()
      );

      await user.click(screen.getByRole('button', { name: /Historial de Pagos/i }));
      await waitFor(() =>
        expect(screen.getByText(/Aún no registras operaciones/i)).toBeInTheDocument()
      );

      // Click Explorar Catálogo callback in HistoryPage -> returns to CatalogPage
      await user.click(screen.getByRole('button', { name: /Explorar Catálogo/i }));
      await waitFor(() =>
        expect(screen.getByText('Paquete Sin Limite 20')).toBeInTheDocument()
      );
    });
  });
});
