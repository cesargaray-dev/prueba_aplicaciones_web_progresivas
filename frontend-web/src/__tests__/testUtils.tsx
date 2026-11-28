import React from 'react';
import { vi } from 'vitest';
import { AuthContext } from '../context/authContextInstance';
import type { AuthContextType } from '../context/authContextInstance';
import type { LoginResponse, TotpSetupResponse, UserProfile } from '../types/auth';
import type { ProductDTO } from '../types/catalog';
import type { ItemHistorialPago } from '../types/history';
import type { PagoServicioResponse, ValidarReferenciaResponse } from '../types/payment';

export const mockTotpSetup: TotpSetupResponse = {
  secret: 'JBSWY3DPEHPK3PXP',
  secretFormateado: 'JBSW Y3DP EHPK 3PXP',
  otpauthUri: 'otpauth://totp/Banco%20Digital:cesar@gmail.com?secret=JBSWY3DPEHPK3PXP&issuer=Banco%20Digital',
  qrPngBase64: 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=',
  emisor: 'Banco Digital',
  cuenta: 'cesar@gmail.com',
  digitos: 6,
  periodoSegundos: 30,
};

export const mockLoginResponse: LoginResponse = {
  token: 'test-token-123',
  usuario: 'cesar123',
  clienteId: 1,
  nombre: 'Cesar',
  segundoNombre: 'Enrique',
  apellidoPaterno: 'Garay',
  apellidoMaterno: 'Garcia',
  nombreCompleto: 'Cesar Enrique Garay Garcia',
  email: 'cesar@gmail.com',
  telefonoMovil: '5512345678',
  curp: 'GAGC990515HDFRRL01',
  rfc: 'GAGC990515AB1',
  nacionalidad: 'MEXICANA',
  ocupacion: 'Empleado(a) del sector privado',
  profesion: 'Ingeniero en Sistemas / Desarrollo de Software / TI',
  totpHabilitado: false,
  calle: 'Av. Reforma',
  numeroExterior: '250',
  colonia: 'Juárez',
  codigoPostal: '06600',
  municipio: 'Cuauhtémoc',
  estado: 'Ciudad de México',
  numeroCuenta: '5225253189113705',
  saldo: 500.0,
  ultimoAcceso: '2026-11-20 00:00:00',
};

export const mockUserProfile: UserProfile = {
  usuario: 'cesar123',
  clienteId: 1,
  nombre: 'Cesar',
  segundoNombre: 'Enrique',
  apellidoPaterno: 'Garay',
  apellidoMaterno: 'Garcia',
  nombreCompleto: 'Cesar Enrique Garay Garcia',
  email: 'cesar@gmail.com',
  telefonoMovil: '5512345678',
  curp: 'GAGC990515HDFRRL01',
  rfc: 'GAGC990515AB1',
  nacionalidad: 'MEXICANA',
  ocupacion: 'Empleado(a) del sector privado',
  profesion: 'Ingeniero en Sistemas / Desarrollo de Software / TI',
  totpHabilitado: false,
  calle: 'Av. Reforma',
  numeroExterior: '250',
  colonia: 'Juárez',
  codigoPostal: '06600',
  municipio: 'Cuauhtémoc',
  estado: 'Ciudad de México',
  numeroCuenta: '5225253189113705',
  saldo: 500.0,
  ultimoAcceso: '2026-11-20 00:00:00',
};

export const mockTelcelProduct: ProductDTO = {
  id: '205',
  name: 'Paquete Sin Limite 20',
  service: 'Telcel',
  serviceId: '76',
  price: '20.00',
  referenceType: 'a',
  frontType: '1',
  categoryId: '2',
  legend: 'Tiempo aire y megas nacionales',
  showAyuda: false,
  logoUrl: 'https://gestopago.portalventas.net/sistema/images/gestopago/servicios/76.png',
};

export const mockCfeProduct: ProductDTO = {
  id: '1001',
  name: 'Pago Recibo CFE',
  service: 'CFE',
  serviceId: '166',
  price: '0.00',
  referenceType: 'bc',
  frontType: '2',
  hasVerificationDigit: 'true',
  categoryId: '5',
  legend: 'Pago de recibo de luz CFE nacional',
  showAyuda: 'true',
  logoUrl: 'https://gestopago.portalventas.net/sistema/images/gestopago/servicios/166.png',
};

export const mockValidationSuccess: ValidarReferenciaResponse = {
  esValida: true,
  mensaje: 'Referencia válida ante proveedor',
  tipoFront: 1,
  tipoReferencia: 'a',
  requiereMonto: false,
  montoSugerido: 20,
  servicio: 'Telcel',
  producto: 'Paquete Sin Limite 20',
};

export const mockPagoSuccess: PagoServicioResponse = {
  codigo: 0,
  estadoOperacion: 'EXITOSA',
  mensaje: 'Pago de servicio aplicado exitosamente',
  idTransaccion: 101,
  numeroAutorizacion: 'AUTH-998877',
  folioTicket: 'FT-20261120-1010',
  montoPagado: 20.0,
  comision: 0,
  saldoRestanteCuenta: 480.0,
  fechaHoraOperacion: '2026-11-20 01:20:00',
  concepto: 'RECARGA TELCEL $20.00',
  servicio: 'Telcel',
  producto: 'Paquete Sin Limite 20',
  correoEnviado: true,
  correoDestino: 'cesar@gmail.com',
  mensajeCorreo: 'Enviado con Brevo',
};

export const mockHistoryItem: ItemHistorialPago = {
  idPago: 1,
  folioTicket: 'FT-20261120-1010',
  numeroAutorizacion: 'AUTH-998877',
  idServicio: 76,
  servicio: 'TELCEL',
  idProducto: 205,
  producto: 'PAQUETE SIN LIMITE 20',
  telefono: '5512345678',
  monto: 20.0,
  comision: 0,
  saldoAnterior: 500.0,
  saldoRestante: 480.0,
  concepto: 'Recarga Telcel $20',
  fechaOperacion: '2026-11-20 01:20:00',
  estado: 'EXITOSA',
  logoUrl: 'https://gestopago.portalventas.net/sistema/images/gestopago/servicios/76.png',
};

export const createMockAuthContext = (
  overrides: Partial<AuthContextType> = {}
): AuthContextType => ({
  user: mockLoginResponse,
  isAuthenticated: true,
  isLoading: false,
  isInitializing: false,
  secondsRemaining: 290,
  sessionExpiredMessage: null,
  clearSessionExpired: vi.fn(),
  sessionNotice: null,
  clearSessionNotice: vi.fn(),
  login: vi.fn().mockResolvedValue(undefined),
  enviarCodigoRegistro: vi.fn().mockResolvedValue({
    codigo: 0,
    mensaje: 'Código de verificación 2FA enviado a tu correo electrónico.',
    correoEnviado: true,
    correoDestino: 'cesar@gmail.com',
    expiraEnSegundos: 300,
  }),
  register: vi.fn().mockResolvedValue(undefined),
  logout: vi.fn().mockResolvedValue(undefined),
  updateBalance: vi.fn(),
  refreshBalance: vi.fn().mockResolvedValue(mockUserProfile),
  updateContacto: vi.fn().mockResolvedValue(mockUserProfile),
  cambiarContrasena: vi.fn().mockResolvedValue(undefined),
  abonarSaldo: vi.fn().mockResolvedValue(mockUserProfile),
  resetInactivityTimer: vi.fn(),
  ...overrides,
});

export const MockAuthProvider: React.FC<{
  value?: AuthContextType;
  children: React.ReactNode;
}> = ({ value, children }) => {
  const ctx = value ?? createMockAuthContext();
  return <AuthContext.Provider value={ctx}>{children}</AuthContext.Provider>;
};
