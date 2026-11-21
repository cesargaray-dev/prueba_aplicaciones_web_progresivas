import { describe, it, expect, vi, beforeEach } from 'vitest';
import { authApi } from '../api/authApi';
import { adminApi } from '../api/adminApi';
import { catalogApi } from '../api/catalogApi';
import { paymentApi } from '../api/paymentApi';
import { historyApi } from '../api/historyApi';
import {
  apiClient,
  ApiError,
  getAuthToken,
  setAuthToken,
  setOnUnauthorizedHandler,
} from '../api/client';

describe('Frontend API Client Suite', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    setAuthToken(null);
    setOnUnauthorizedHandler(null);
    window.sessionStorage.clear();
    window.localStorage.clear();
  });

  describe('Token Storage & Client Hardening (F11)', () => {
    it('stores auth_token only in sessionStorage and clears legacy localStorage', () => {
      window.localStorage.setItem('auth_token', 'legacy-token');
      window.localStorage.setItem('auth_user', '{"usuario":"old"}');

      setAuthToken('session-jwt-999');
      expect(window.sessionStorage.getItem('auth_token')).toBe('session-jwt-999');
      expect(getAuthToken()).toBe('session-jwt-999');

      setAuthToken(null);
      expect(window.sessionStorage.getItem('auth_token')).toBeNull();
      expect(window.localStorage.getItem('auth_token')).toBeNull();
      expect(window.localStorage.getItem('auth_user')).toBeNull();
    });

    it('invokes global 401 handler on authenticated endpoints but NOT on /auth/login or 2FA OTP errors', async () => {
      const onUnauthorized = vi.fn();
      setOnUnauthorizedHandler(onUnauthorized);
      setAuthToken('valid-token');

      // 1. /auth/me 401 -> SHOULD trigger handler and clear token
      vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: false,
        status: 401,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({ mensaje: 'Sesión expirada' }),
      } as Response);

      await expect(authApi.getMe()).rejects.toThrow('Sesión expirada');
      expect(onUnauthorized).toHaveBeenCalledTimes(1);
      expect(getAuthToken()).toBeNull();

      // 2. /pagos/servicios 401 with OTP error -> MUST NOT trigger handler
      setAuthToken('valid-token-2');
      vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: false,
        status: 401,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({ mensaje: 'Código 2FA inválido o expirado' }),
      } as Response);

      await expect(
        paymentApi.pagarServicio({
          numeroCuenta: '1234',
          idServicio: 76,
          idProducto: 205,
          monto: 20,
          codigo2FA: '000000',
        })
      ).rejects.toThrow('Código 2FA inválido o expirado');
      expect(onUnauthorized).toHaveBeenCalledTimes(1);
      expect(getAuthToken()).toBe('valid-token-2');

      // 3. /auth/login 401 -> MUST NOT trigger handler
      vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: false,
        status: 401,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({ mensaje: 'Credenciales inválidas' }),
      } as Response);

      await expect(
        authApi.login({ usuario: 'bad', contrasena: 'bad' })
      ).rejects.toThrow('Credenciales inválidas');
      expect(onUnauthorized).toHaveBeenCalledTimes(1);
    });

    it('supports non-JSON text responses, endpoint without leading slash, and network error wrapping', async () => {
      const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        headers: new Headers({ 'content-type': 'text/plain' }),
        text: async () => 'plain-ok',
      } as Response);

      const res = await apiClient.get<string>('health');
      expect(res).toBe('plain-ok');
      expect(fetchMock).toHaveBeenCalledWith('/api/v1/health', expect.any(Object));

      vi.spyOn(globalThis, 'fetch').mockRejectedValueOnce(new Error('Network down'));
      await expect(apiClient.put('/test', { a: 1 })).rejects.toMatchObject({
        message: 'Network down',
        status: 0,
      });

      vi.spyOn(globalThis, 'fetch').mockRejectedValueOnce('unknown');
      await expect(apiClient.put('/test2')).rejects.toMatchObject({
        message: 'Error desconocido de conexión',
        status: 0,
      });
    });

    it('fetches binary PDF blob via getBlob and throws ApiError on failure', async () => {
      setAuthToken('jwt-pdf');
      const mockBlob = new Blob(['%PDF-1.4'], { type: 'application/pdf' });
      const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        headers: new Headers({ 'content-type': 'application/pdf' }),
        blob: async () => mockBlob,
      } as Response);

      const blob = await paymentApi.descargarComprobantePdf('FT-123/45');
      expect(blob).toBe(mockBlob);
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/v1/pagos/comprobante/FT-123%2F45/pdf',
        expect.objectContaining({
          headers: expect.objectContaining({
            Authorization: 'Bearer jwt-pdf',
            Accept: 'application/pdf, */*',
          }),
        })
      );

      vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: false,
        status: 404,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({ mensaje: 'Comprobante no encontrado' }),
      } as Response);

      await expect(paymentApi.descargarComprobantePdf('NO-EXISTE')).rejects.toBeInstanceOf(ApiError);
    });
  });

  describe('Pillar 1: Auth API (No ?usuario= bypass)', () => {
    it('logs in, registers, updates profile, changes password, deposits, and logs out without ?usuario= query param', async () => {
      const mockLogin = {
        token: 'auth-token-12345',
        usuario: 'cesar123',
        clienteId: 1,
        nombreCompleto: 'Cesar Enrique Garay Garcia',
        email: 'cesar@gmail.com',
        numeroCuenta: '5225253189113705',
        saldo: 60.0,
      };

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
        ok: true,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => mockLogin,
      } as Response);

      const loginRes = await authApi.login({ usuario: 'cesar123', contrasena: 'Password@123' });
      expect(loginRes.usuario).toBe('cesar123');
      expect(getAuthToken()).toBe('auth-token-12345');

      await authApi.enviarCodigoRegistro({
        correo: 'cesar@gmail.com',
        nombre: 'Cesar',
        usuario: 'cesar123',
      });
      await authApi.register({
        nombre: 'Cesar',
        apellidoPaterno: 'Garay',
        apellidoMaterno: 'Garcia',
        fechaNacimiento: '1998-01-15',
        correo: 'cesar@gmail.com',
        telefonoMovil: '5512345678',
        usuario: 'cesar123',
        contrasena: 'Password@123',
        codigoVerificacion: '123456',
      });
      await authApi.getMe();
      await authApi.keepAlive();
      await authApi.updateContacto({ correo: 'nuevo@gmail.com', telefonoMovil: '5512345678' });
      await authApi.cambiarContrasena({ contrasenaActual: 'Old12345', contrasenaNueva: 'New12345' });
      await authApi.abonarSaldo({ monto: 100 });

      for (const call of fetchSpy.mock.calls) {
        expect(String(call[0])).not.toContain('usuario=');
      }

      vi.spyOn(globalThis, 'fetch').mockRejectedValueOnce(new Error('Logout failed'));
      await authApi.logout();
      expect(getAuthToken()).toBeNull();
    });
  });

  describe('Pillar 2: Catalog API', () => {
    it('should fetch flat product list and handle missing data fallback', async () => {
      const mockCatalog = {
        status: 'OK',
        message: 'Catálogo recuperado',
        data: [
          {
            id: '205',
            name: 'Paquete Sin Limite 20',
            service: 'Telcel',
            serviceId: '76',
            price: '20.00',
            referenceType: 'a',
            frontType: '1',
            categoryId: '2',
          },
        ],
      };

      vi.spyOn(globalThis, 'fetch')
        .mockResolvedValueOnce({
          ok: true,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => mockCatalog,
        } as Response)
        .mockResolvedValueOnce({
          ok: true,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => ({ status: 'OK' }),
        } as Response);

      const products = await catalogApi.getProductos();
      expect(products).toHaveLength(1);
      expect(products[0].service).toBe('Telcel');

      const emptyFallback = await catalogApi.getProductos();
      expect(emptyFallback).toEqual([]);
    });
  });

  describe('Pillar 3 & 4: Reference Validation, 2FA, Payment & Reenviar API', () => {
    it('validates references, generates 2FA, processes payment, and resends receipt', async () => {
      vi.spyOn(globalThis, 'fetch')
        .mockResolvedValueOnce({
          ok: true,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => ({ esValida: true, mensaje: 'OK', tipoFront: 1 }),
        } as Response)
        .mockResolvedValueOnce({
          ok: true,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => ({ expiraEnSegundos: 300, correoEnviado: true }),
        } as Response)
        .mockResolvedValueOnce({
          ok: true,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => ({
            estadoOperacion: 'EXITOSA',
            folioTicket: 'FT-2026',
            numeroAutorizacion: 'AUTH-1',
            montoPagado: 20,
            saldoRestanteCuenta: 80,
          }),
        } as Response)
        .mockResolvedValueOnce({
          ok: true,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => ({ enviado: true, correoDestino: 'cesar@gmail.com' }),
        } as Response);

      const valRes = await paymentApi.validarReferencia({
        idServicio: 76,
        idProducto: 205,
        referencia: '5512345678',
      });
      expect(valRes.esValida).toBe(true);

      const twoFaRes = await paymentApi.generar2FA('5225253189113705');
      expect(twoFaRes.expiraEnSegundos).toBe(300);

      const payRes = await paymentApi.pagarServicio({
        numeroCuenta: '5225253189113705',
        idServicio: 76,
        idProducto: 205,
        monto: 20,
        codigo2FA: '123456',
      });
      expect(payRes.folioTicket).toBe('FT-2026');

      const resendRes = await paymentApi.reenviarComprobante({
        numeroCuenta: '5225253189113705',
        folioTicket: 'FT-2026',
      });
      expect(resendRes.enviado).toBe(true);
    });
  });

  describe('Pillar 5: Transaction History API', () => {
    it('queries history for authenticated account', async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
        ok: true,
        headers: new Headers({ 'content-type': 'application/json' }),
        json: async () => ({
          codigo: 0,
          numeroCuenta: '5225253189113705',
          totalMovimientos: 1,
          pagos: [{ idPago: 1, folioTicket: 'FT-1', estado: 'EXITOSA', monto: 20 }],
        }),
      } as Response);

      const res = await historyApi.getHistorial('5225253189113705');
      expect(res.pagos).toHaveLength(1);
      expect(res.pagos[0].estado).toBe('EXITOSA');
    });

    it('calls adminApi CRUD endpoints cleanly', async () => {
      vi.spyOn(globalThis, 'fetch')
        .mockResolvedValueOnce({
          ok: true,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => [{ clienteId: 1, usuario: 'admin' }],
        } as Response)
        .mockResolvedValueOnce({
          ok: true,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => ({ clienteId: 2, usuario: 'nuevo' }),
        } as Response)
        .mockResolvedValueOnce({
          ok: true,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => ({ clienteId: 2, usuario: 'editado' }),
        } as Response)
        .mockResolvedValueOnce({
          ok: true,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => ({ codigo: 0, mensaje: 'Eliminado' }),
        } as Response)
        .mockResolvedValueOnce({
          ok: true,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => ({ codigo: 0, mensaje: 'Todos eliminados' }),
        } as Response);

      const list = await adminApi.listarUsuarios();
      expect(list[0].usuario).toBe('admin');

      const created = await adminApi.crearUsuario({
        nombre: 'A',
        apellidoPaterno: 'B',
        apellidoMaterno: 'C',
        fechaNacimiento: '1990-01-01',
        correo: 'a@gmail.com',
        telefonoMovil: '5512345678',
        usuario: 'nuevo',
        contrasena: 'Password1',
      });
      expect(created.usuario).toBe('nuevo');

      const updated = await adminApi.actualizarUsuario(2, { nombre: 'AA' });
      expect(updated.usuario).toBe('editado');

      const deleted = await adminApi.eliminarUsuario(2);
      expect(deleted.codigo).toBe(0);

      const deletedAll = await adminApi.eliminarTodosUsuarios();
      expect(deletedAll.mensaje).toBe('Todos eliminados');
    });
  });
});
