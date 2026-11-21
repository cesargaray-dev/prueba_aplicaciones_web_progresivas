export interface LoginRequest {
  usuario: string;
  contrasena: string;
}

export interface LoginResponse {
  token: string;
  usuario: string;
  clienteId?: number;
  nombre?: string;
  segundoNombre?: string;
  apellidoPaterno?: string;
  apellidoMaterno?: string;
  nombreCompleto: string;
  email: string;
  telefonoMovil?: string;
  numeroCuenta: string;
  saldo: number;
  ultimoAcceso?: string;
  esAdmin?: boolean;
  rol?: string;
  sesionPreviaCerrada?: boolean;
  avisoSesion?: string;
}

export interface RegisterRequest {
  nombre: string;
  segundoNombre?: string;
  apellidoPaterno: string;
  apellidoMaterno?: string;
  fechaNacimiento?: string;
  correo: string;
  telefonoMovil: string;
  usuario: string;
  contrasena: string;
  sexo?: 'HOMBRE' | 'MUJER';
  codigoVerificacion?: string;
}

export interface EnviarCodigoRegistroRequest {
  correo: string;
  nombre?: string;
  usuario?: string;
}

export interface EnviarCodigoRegistroResponse {
  codigo: number;
  mensaje: string;
  correoEnviado: boolean;
  correoDestino: string;
  expiraEnSegundos: number;
}

export interface ActualizarContactoRequest {
  nombre?: string;
  segundoNombre?: string;
  apellidoPaterno?: string;
  apellidoMaterno?: string;
  correo: string;
  telefonoMovil: string;
}

export interface CambiarContrasenaRequest {
  contrasenaActual: string;
  contrasenaNueva: string;
}

export interface AbonoSaldoRequest {
  monto: number;
  concepto?: string;
}

export interface LogoutRequest {
  usuario?: string;
}

export interface UserProfile {
  usuario: string;
  clienteId: number;
  nombre?: string;
  segundoNombre?: string;
  apellidoPaterno?: string;
  apellidoMaterno?: string;
  nombreCompleto: string;
  email: string;
  telefonoMovil?: string;
  numeroCuenta: string;
  saldo: number;
  ultimoAcceso?: string;
  estatusCuenta?: string;
  activo?: boolean;
  esAdmin?: boolean;
  rol?: string;
  sesionPreviaCerrada?: boolean;
  avisoSesion?: string;
}

export interface AdminUserItem {
  clienteId: number;
  usuario: string;
  rol: string;
  esAdmin: boolean;
  nombre: string;
  segundoNombre?: string;
  apellidoPaterno: string;
  apellidoMaterno: string;
  nombreCompleto: string;
  fechaNacimiento?: string;
  sexo?: string;
  curp?: string;
  rfc?: string;
  correo: string;
  telefonoMovil: string;
  numeroCuenta: string;
  estatusCuenta: string;
  saldo: number;
  activo: boolean;
  bloqueado: boolean;
  intentosFallidos: number;
  biometriaActiva?: boolean;
  ultimoAcceso?: string;
  fechaRegistro?: string;
}

export interface AdminUpdateUserRequest {
  nombre?: string;
  segundoNombre?: string;
  apellidoPaterno?: string;
  apellidoMaterno?: string;
  correo?: string;
  telefonoMovil?: string;
  usuario?: string;
  nuevaContrasena?: string;
  saldo?: number;
  activo?: boolean;
  bloqueado?: boolean;
}
