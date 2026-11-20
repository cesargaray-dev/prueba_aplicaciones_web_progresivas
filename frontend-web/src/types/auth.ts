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
}

export interface RegisterRequest {
  nombre: string;
  segundoNombre?: string;
  apellidoPaterno: string;
  apellidoMaterno: string;
  fechaNacimiento: string;
  correo: string;
  telefonoMovil: string;
  usuario: string;
  contrasena: string;
  sexo?: 'HOMBRE' | 'MUJER';
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
}
