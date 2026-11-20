import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../App';
import { LoginPage } from '../pages/LoginPage';
import { CatalogPage } from '../pages/CatalogPage';
import { HistoryPage } from '../pages/HistoryPage';
import { AdminUsersPage } from '../pages/AdminUsersPage';
import { authApi } from '../api/authApi';
import { adminApi } from '../api/adminApi';
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
        ...mockCfeProduct,
        id: '301',
        name: 'Agua SIAPA',
        service: 'SIAPA',
        serviceId: '80',
        price: '0',
        frontType: '2',
        categoryId: '15',
      },
      {
        ...mockCfeProduct,
        id: '302',
        name: 'Internet IZZI',
        service: 'IZZI',
        serviceId: '81',
        price: '0',
        frontType: '2',
        categoryId: '4',
      },
      {
        ...mockTelcelProduct,
        id: '303',
        name: 'Recarga PASE',
        service: 'PASE',
        serviceId: '82',
        price: '200',
        frontType: '1',
        categoryId: '18',
      },
      {
        ...mockCfeProduct,
        id: '304',
        name: 'Pago Infonavit',
        service: 'INFONAVIT',
        serviceId: '83',
        price: '0',
        frontType: '2',
        categoryId: '8',
      },
      {
        ...mockTelcelProduct,
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

      // Verify Premium Banking Card renders and its refresh button works
      expect(screen.getByText(/PLATINUM • 2FA VERIFIED/i)).toBeInTheDocument();
      await user.click(screen.getByLabelText('Sincronizar saldo de tarjeta'));

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

      // Verify Premium Banking Card renders and its refresh button works
      expect(screen.getByText(/PLATINUM • 2FA VERIFIED/i)).toBeInTheDocument();
      await user.click(screen.getByLabelText('Sincronizar saldo de tarjeta'));

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

      // Verify Premium Banking Card renders and its refresh button works
      expect(screen.getByText(/PLATINUM • 2FA VERIFIED/i)).toBeInTheDocument();
      await user.click(screen.getByLabelText('Sincronizar saldo de tarjeta'));

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

  describe('AdminUsersPage Component', () => {
    const sampleAdminUsers = [
      {
        clienteId: 1,
        usuario: 'admin',
        rol: 'ADMIN',
        esAdmin: true,
        nombre: 'ADMINISTRADOR',
        apellidoPaterno: 'BANCO',
        apellidoMaterno: 'DIGITAL',
        nombreCompleto: 'ADMINISTRADOR BANCO DIGITAL',
        correo: 'admin@bancodigital.com.mx',
        telefonoMovil: '5523456789',
        numeroCuenta: '4000000000000001',
        estatusCuenta: 'ACTIVA',
        saldo: 10000,
        activo: true,
        bloqueado: false,
        intentosFallidos: 0,
      },
      {
        clienteId: 2,
        usuario: 'carlos98',
        rol: 'CLIENTE',
        esAdmin: false,
        nombre: 'CARLOS',
        apellidoPaterno: 'GARAY',
        apellidoMaterno: 'LOPEZ',
        nombreCompleto: 'CARLOS GARAY LOPEZ',
        correo: 'carlos@gmail.com',
        telefonoMovil: '5512345678',
        numeroCuenta: '5225253189113705',
        estatusCuenta: 'ACTIVA',
        saldo: 350,
        activo: true,
        bloqueado: false,
        intentosFallidos: 0,
        biometriaActiva: true,
      },
      {
        clienteId: 3,
        usuario: 'bloqueado1',
        rol: 'CLIENTE',
        esAdmin: false,
        nombre: 'PEDRO',
        apellidoPaterno: 'RUIZ',
        apellidoMaterno: 'DIAZ',
        nombreCompleto: 'PEDRO RUIZ DIAZ',
        correo: 'pedro@gmail.com',
        telefonoMovil: '5587654321',
        numeroCuenta: '5225253189119999',
        estatusCuenta: 'INACTIVA',
        saldo: 0,
        activo: false,
        bloqueado: true,
        intentosFallidos: 5,
      },
    ];

    it('lists, filters, edits, toggles status, creates, and deletes users', async () => {
      const user = userEvent.setup();
      vi.spyOn(adminApi, 'listarUsuarios').mockResolvedValue(sampleAdminUsers);
      vi.spyOn(adminApi, 'actualizarUsuario').mockResolvedValue({
        ...sampleAdminUsers[1],
        nombreCompleto: 'CARLOS EDITADO',
        saldo: 900,
      });
      vi.spyOn(adminApi, 'crearUsuario').mockResolvedValue({
        ...sampleAdminUsers[1],
        clienteId: 4,
        usuario: 'nuevo_usr',
        nombreCompleto: 'NUEVO USUARIO',
      });
      vi.spyOn(adminApi, 'eliminarUsuario').mockResolvedValue({
        codigo: 0,
        mensaje: 'Usuario eliminado permanentemente.',
      });

      render(<AdminUsersPage />);

      await waitFor(() =>
        expect(screen.getByText('CARLOS GARAY LOPEZ')).toBeInTheDocument()
      );

      // Search & Filter tabs
      const searchInput = screen.getByPlaceholderText(/Buscar por nombre/i);
      await user.type(searchInput, 'carlos');
      await user.clear(searchInput);
      await user.click(screen.getByRole('button', { name: 'Activos' }));
      await user.click(screen.getByRole('button', { name: 'Inactivos / Bloqueados' }));
      await user.click(screen.getByRole('button', { name: 'Todos' }));

      // Edit user & change fields
      const editButtons = screen.getAllByRole('button', { name: /Editar/i });
      await user.click(editButtons[1]);
      const editDialog = screen.getByRole('dialog', { name: /Editar usuario/i });
      const textInputs = editDialog.querySelectorAll('input');
      for (const inp of Array.from(textInputs)) {
        if (inp.type === 'text' || inp.type === 'email') {
          fireEvent.change(inp, { target: { value: inp.value || 'NuevoValor123' } });
        } else if (inp.type === 'number') {
          fireEvent.change(inp, { target: { value: '950' } });
        } else if (inp.type === 'checkbox') {
          fireEvent.click(inp);
        }
      }
      await user.click(screen.getByRole('button', { name: /Guardar Cambios/i }));
      await waitFor(() =>
        expect(screen.getByText(/actualizado correctamente/i)).toBeInTheDocument()
      );

      // Quick toggle status (Desactivar & Desbloquear)
      await user.click(screen.getByRole('button', { name: /Desactivar/i }));
      await user.click(screen.getByRole('button', { name: /Desbloquear/i }));
      await waitFor(() => expect(adminApi.actualizarUsuario).toHaveBeenCalledTimes(3));

      // Create user modal & fill inputs
      await user.click(screen.getByRole('button', { name: /Nuevo Usuario/i }));
      const createDialog = screen.getByRole('dialog', { name: /Crear nuevo usuario/i });
      const createInputs = createDialog.querySelectorAll('input');
      for (const inp of Array.from(createInputs)) {
        fireEvent.change(inp, {
          target: { value: inp.type === 'date' ? '1994-03-10' : 'TestValue1' },
        });
      }
      fireEvent.submit(createDialog.querySelector('form')!);
      await waitFor(() =>
        expect(screen.getByText(/creado exitosamente/i)).toBeInTheDocument()
      );

      // Delete user modal
      const deleteButtons = screen.getAllByRole('button', { name: /Borrar/i });
      await user.click(deleteButtons[0]);
      expect(screen.getByRole('dialog', { name: /Confirmar eliminación/i })).toBeInTheDocument();
      await user.click(screen.getByRole('button', { name: /Sí, Eliminar Usuario/i }));
      await waitFor(() =>
        expect(screen.getByText(/Usuario eliminado permanentemente/i)).toBeInTheDocument()
      );

      // Refresh & error branches
      vi.spyOn(adminApi, 'listarUsuarios').mockRejectedValueOnce(new Error('Fallo red admin'));
      await user.click(screen.getByRole('button', { name: /Actualizar/i }));
      await waitFor(() => expect(screen.getByText('Fallo red admin')).toBeInTheDocument());
    });
  });
});
