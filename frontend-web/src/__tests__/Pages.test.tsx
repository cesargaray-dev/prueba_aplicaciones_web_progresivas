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

    it('validates all 4 KYC registration steps (Identity, CURP/RFC/Address, Access, 2FA OTP) and submits registration', async () => {
      const user = userEvent.setup();
      const enviarCodigoMock = vi
        .fn()
        .mockRejectedValueOnce(new Error('La CURP ingresada ya se encuentra registrada'))
        .mockResolvedValue({
          codigo: 0,
          mensaje: 'Código enviado',
          correoEnviado: true,
          correoDestino: 'cesar@gmail.com',
          expiraEnSegundos: 300,
        });
      const registerMock = vi
        .fn()
        .mockRejectedValueOnce(new Error('Código 2FA inválido'))
        .mockResolvedValueOnce(undefined);
      const ctx = createMockAuthContext({
        user: null,
        isAuthenticated: false,
        enviarCodigoRegistro: enviarCodigoMock,
        register: registerMock,
      });

      render(
        <MockAuthProvider value={ctx}>
          <LoginPage />
        </MockAuthProvider>
      );

      // Toggle dark mode on LoginPage
      await user.click(screen.getByLabelText('Cambiar tema'));

      await user.click(screen.getByRole('button', { name: /Crear Cuenta/i }));
      await user.click(screen.getByRole('button', { name: /Iniciar Sesión/i }));
      await user.click(screen.getByRole('button', { name: /Abrir cuenta digital/i }));

      // --- STEP 1: Identity (Immediate onBlur per-field validation + 2-40 name / 3-40 surname) ---
      const step1Next = () =>
        screen.getByRole('button', { name: /Continuar a Datos Fiscales y Domicilio/i });

      const nombreInput = screen.getByLabelText(/Nombre \(2–40 letras\)/i);
      await user.type(nombreInput, 'A');
      await user.tab(); // Passing to next field immediately triggers inline error without clicking submit!
      expect(screen.getByText(/El nombre debe tener al menos 2 letras/i)).toBeInTheDocument();

      await user.clear(nombreInput);
      await user.type(nombreInput, 'Cesar');
      await user.type(screen.getByLabelText(/Segundo nombre/i), 'Enrique');

      const patInput = screen.getByLabelText(/Apellido paterno \(3–40 letras\)/i);
      await user.type(patInput, 'Ga');
      await user.tab(); // 2 chars in surname immediately triggers 3-letter minimum error on blur!
      expect(
        screen.getByText(/El apellido paterno debe tener al menos 3 letras/i)
      ).toBeInTheDocument();
      await user.clear(patInput);
      await user.type(patInput, 'Garay');

      await user.click(step1Next());
      expect(screen.getAllByText(/El apellido materno es obligatorio/i).length).toBeGreaterThan(0);
      await user.type(screen.getByLabelText(/Apellido materno \(3–40 letras\)/i), 'Garcia');

      const birthInput = screen.getByLabelText(/Fecha de nacimiento/i);
      fireEvent.change(birthInput, { target: { value: '2020-01-01' } });
      await user.click(step1Next());
      expect(screen.getAllByText(/mayor de 18 años/i).length).toBeGreaterThan(0);
      fireEvent.change(birthInput, { target: { value: '1999-05-15' } });

      await user.click(step1Next());

      // --- STEP 2: Fiscal (CURP & RFC), Tabuladores & SEPOMEX Address ---
      const step2Next = () =>
        screen.getByRole('button', { name: /Continuar a Contacto y Acceso/i });

      await user.click(screen.getByRole('button', { name: /Calcular CURP \/ RFC/i }));
      const curpInput = screen.getByLabelText(/CURP \(18 caracteres\)/i) as HTMLInputElement;
      const rfcInput = screen.getByLabelText(/RFC con Homoclave/i) as HTMLInputElement;
      expect(curpInput.value).toHaveLength(18);
      expect(rfcInput.value).toHaveLength(13);

      // Select from Tabulador de Profesiones & Tabulador de Sueldo
      await user.selectOptions(
        screen.getByLabelText(/Tabulador de Profesión/i),
        'Estudiante Universitario / Posgrado / Becario'
      );
      await user.selectOptions(screen.getByLabelText(/Tabulador de Sueldo/i), '35000');

      // Test SEPOMEX postal code validation (invalid 18500 then valid 06600)
      const cpInput = screen.getByLabelText(/Código Postal/i);
      await user.type(cpInput, '18500');
      expect(screen.getByText(/no existe en el catálogo oficial SEPOMEX/i)).toBeInTheDocument();
      await user.clear(cpInput);
      await user.type(cpInput, '06600');
      expect(screen.getByText(/✓ C\.P\. 06600 · Ciudad de México/i)).toBeInTheDocument();

      // Test street & exterior number validations
      await user.click(step2Next());
      expect(screen.getAllByText(/nombre de la calle/i).length).toBeGreaterThan(0);
      await user.selectOptions(screen.getByLabelText(/Vialidad \*/i), 'Av.');
      await user.type(screen.getByLabelText(/Nombre de Calle/i), 'Paseo de la Reforma');

      await user.click(screen.getByRole('button', { name: /Usar S\/N/i }));
      const numExtInput = screen.getByLabelText(/Núm\. Exterior \*/i);
      await user.clear(numExtInput);
      await user.type(numExtInput, '250');
      await user.type(screen.getByLabelText(/Núm\. Interior/i), '4B');

      // Test manual colonia option and catalog colonia option
      await user.selectOptions(screen.getByLabelText(/Colonia \/ Asentamiento/i), '__OTRA__');
      await user.type(screen.getByPlaceholderText(/Escribe tu colonia/i), 'Juárez Centro');

      // Test stepper pill navigation back to Step 1 and forward again
      await user.click(screen.getByRole('button', { name: /Anterior/i }));
      await user.click(step1Next());

      await user.click(step2Next());

      // --- STEP 3: Contact & Access ---
      const sendCodeBtn = () =>
        screen.getByRole('button', { name: /Enviar Código 2FA al Correo/i });

      const emailInput = screen.getByLabelText(/Correo electrónico real/i);
      await user.type(emailInput, 'correo-invalido');
      await user.tab();
      expect(screen.getByText(/formato válido/i)).toBeInTheDocument();
      await user.clear(emailInput);
      await user.type(emailInput, 'user@ejemplo.com');
      await user.click(sendCodeBtn());
      expect(screen.getAllByText(/correo electrónico real/i).length).toBeGreaterThan(0);
      await user.clear(emailInput);
      await user.type(emailInput, 'cesar@gmail.com');

      const telInput = screen.getByLabelText(/Teléfono móvil México/i);
      await user.type(telInput, '0000000000');
      await user.click(sendCodeBtn());
      await user.clear(telInput);
      await user.type(telInput, '1111111111');

      const usrInput = screen.getByLabelText(/Usuario \(4–50 caracteres\)/i);
      await user.type(usrInput, 'ab');
      await user.tab();
      expect(screen.getByText(/El usuario debe tener entre 4 y 50/i)).toBeInTheDocument();
      await user.clear(usrInput);
      await user.type(usrInput, 'cesar_nuevo');

      await user.click(screen.getByLabelText('Mostrar contraseña'));
      const passInput = screen.getByLabelText(/^Contraseña \*/i);
      const confInput = screen.getByLabelText(/Confirmar contraseña/i);
      await user.type(passInput, 'debil');
      await user.tab();
      expect(screen.getByText(/Mínimo 8 caracteres/i)).toBeInTheDocument();

      await user.clear(passInput);
      await user.type(passInput, 'Venta2026#');
      await user.type(confInput, 'Otra2026#');
      await user.click(sendCodeBtn());
      expect(screen.getAllByText(/Las contraseñas no coinciden/i).length).toBeGreaterThan(0);

      await user.clear(confInput);
      await user.type(confInput, 'Venta2026#');

      // Step 3 -> Step 4: First OTP send rejects, second transitions to Step 4
      await user.click(sendCodeBtn());
      await waitFor(() =>
        expect(
          screen.getByText('La CURP ingresada ya se encuentra registrada')
        ).toBeInTheDocument()
      );

      await user.click(sendCodeBtn());
      await waitFor(() =>
        expect(screen.getByText(/Verifica tu Correo Electrónico \(2FA\)/i)).toBeInTheDocument()
      );

      // Step 4: Resend code, edit back & return, then verify OTP
      await user.click(screen.getByRole('button', { name: /Reenviar código/i }));
      await waitFor(() =>
        expect(screen.getByText(/Se envió un nuevo código 2FA/i)).toBeInTheDocument()
      );

      await user.click(screen.getByRole('button', { name: /Editar datos/i }));
      expect(sendCodeBtn()).toBeInTheDocument();
      await user.click(sendCodeBtn());
      await waitFor(() =>
        expect(screen.getByLabelText(/Código de verificación/i)).toBeInTheDocument()
      );

      const otpInput = screen.getByLabelText(/Código de verificación/i);
      await user.type(otpInput, '123456');

      const verifyBtn = screen.getByRole('button', { name: /Verificar y Crear Cuenta/i });
      await user.click(verifyBtn);
      await waitFor(() => expect(screen.getByText('Código 2FA inválido')).toBeInTheDocument());

      await user.click(verifyBtn);
      expect(registerMock).toHaveBeenCalledTimes(2);
      expect(registerMock).toHaveBeenLastCalledWith(
        expect.objectContaining({
          curp: expect.stringMatching(/^[A-Z]{4}\d{6}[HM][A-Z]{5}[A-Z\d]\d$/),
          rfc: expect.stringMatching(/^[A-ZÑ&]{3,4}\d{6}[A-Z\d]{3}$/),
          telefonoMovil: '1111111111',
          codigoPostal: '06600',
          codigoVerificacion: '123456',
        })
      );
    }, 25000);
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
      {
        ...mockTelcelProduct,
        id: '374',
        name: 'Recarga $100',
        service: 'AT-T',
        serviceId: '115',
        price: '100',
        frontType: '1',
        categoryId: '3',
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

      // Verify Premium Banking Card renders and its refresh & copy buttons work
      expect(screen.getByText(/PLATINUM • 2FA VERIFIED/i)).toBeInTheDocument();
      await user.click(screen.getByLabelText('Sincronizar saldo de tarjeta'));
      await user.click(screen.getByLabelText('Copiar número de cuenta'));

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

      // Popular brand chip (AT&T must match GestoPago service "AT-T")
      await user.click(screen.getByRole('button', { name: 'AT&T' }));
      expect(screen.getByText('Recarga $100')).toBeInTheDocument();

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

      // Verify Premium Banking Card renders and its refresh & copy buttons work
      expect(screen.getByText(/PLATINUM • 2FA VERIFIED/i)).toBeInTheDocument();
      await user.click(screen.getByLabelText('Sincronizar saldo de tarjeta'));
      await user.click(screen.getByLabelText('Copiar número de cuenta'));

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

      // Verify Premium Banking Card renders and its refresh & copy buttons work
      expect(screen.getByText(/PLATINUM • 2FA VERIFIED/i)).toBeInTheDocument();
      await user.click(screen.getByLabelText('Sincronizar saldo de tarjeta'));
      await user.click(screen.getByLabelText('Copiar número de cuenta'));

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
        curp: 'GALC980515HDFRRL01',
        rfc: 'GALC980515AB1',
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
      vi.spyOn(adminApi, 'eliminarTodosUsuarios').mockResolvedValue({
        codigo: 0,
        mensaje: 'Se eliminaron permanentemente 1 usuarios clientes (se conserva la cuenta @admin).',
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
        if (inp.maxLength === 18) {
          fireEvent.change(inp, { target: { value: 'GAGC980515HDFRRL01' } });
        } else if (inp.maxLength === 13) {
          fireEvent.change(inp, { target: { value: 'GAGC980515AB1' } });
        } else if (inp.type === 'text' || inp.type === 'email') {
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

      // Delete all users modal
      await user.click(screen.getByRole('button', { name: /Eliminar Todos/i }));
      expect(screen.getByRole('dialog', { name: /Confirmar eliminación masiva/i })).toBeInTheDocument();
      await user.click(screen.getByRole('button', { name: /Sí, Eliminar Todos/i }));
      await waitFor(() =>
        expect(screen.getByText(/Se eliminaron permanentemente 1 usuarios clientes/i)).toBeInTheDocument()
      );

      // Refresh & error branches
      vi.spyOn(adminApi, 'listarUsuarios').mockRejectedValueOnce(new Error('Fallo red admin'));
      await user.click(screen.getByRole('button', { name: /Actualizar/i }));
      await waitFor(() => expect(screen.getByText('Fallo red admin')).toBeInTheDocument());
    });
  });
});
