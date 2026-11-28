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
  telefonoAlternativo?: string;
  curp?: string;
  rfc?: string;
  fechaNacimiento?: string;
  sexo?: string;
  nacionalidad?: string;
  estadoCivil?: string;
  ocupacion?: string;
  profesion?: string;
  empresa?: string;
  ingresoMensual?: number;
  calle?: string;
  numeroExterior?: string;
  numeroInterior?: string;
  colonia?: string;
  municipio?: string;
  estado?: string;
  codigoPostal?: string;
  pais?: string;
  numeroCuenta: string;
  saldo: number;
  ultimoAcceso?: string;
  esAdmin?: boolean;
  rol?: string;
  totpHabilitado?: boolean;
  sesionPreviaCerrada?: boolean;
  avisoSesion?: string;
}

export interface RegisterRequest {
  nombre: string;
  segundoNombre?: string;
  apellidoPaterno: string;
  apellidoMaterno?: string;
  fechaNacimiento?: string;
  sexo?: 'HOMBRE' | 'MUJER';
  nacionalidad?: string;
  estadoCivil?: string;
  curp?: string;
  rfc?: string;
  correo: string;
  telefonoMovil: string;
  telefonoAlternativo?: string;
  ocupacion?: string;
  profesion?: string;
  empresa?: string;
  ingresoMensual?: number;
  calle?: string;
  numeroExterior?: string;
  numeroInterior?: string;
  colonia?: string;
  municipio?: string;
  estado?: string;
  codigoPostal?: string;
  pais?: string;
  usuario: string;
  contrasena: string;
  codigoVerificacion?: string;
  totpSecret?: string;
  codigoTotp?: string;
}

export interface DisponibilidadRegistroParams {
  correo?: string;
  usuario?: string;
  curp?: string;
  rfc?: string;
}

export interface DisponibilidadRegistroResponse {
  correoDisponible?: boolean;
  usuarioDisponible?: boolean;
  curpDisponible?: boolean;
  rfcDisponible?: boolean;
  mensajes?: {
    correo?: string;
    usuario?: string;
    curp?: string;
    rfc?: string;
  };
}

export interface RegisterTotpRequest {
  correo: string;
  usuario: string;
}

export interface TotpSetupResponse {
  secret: string;
  secretFormateado: string;
  otpauthUri: string;
  qrPngBase64: string;
  emisor: string;
  cuenta: string;
  digitos: number;
  periodoSegundos: number;
}

export interface TotpEstadoResponse {
  totpHabilitado: boolean;
  mensaje: string;
}

export interface EnviarCodigoRegistroRequest {
  correo: string;
  nombre?: string;
  usuario?: string;
  curp?: string;
  rfc?: string;
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
  ocupacion?: string;
  empresa?: string;
  calle?: string;
  numeroExterior?: string;
  numeroInterior?: string;
  colonia?: string;
  municipio?: string;
  estado?: string;
  codigoPostal?: string;
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
  telefonoAlternativo?: string;
  curp?: string;
  rfc?: string;
  fechaNacimiento?: string;
  sexo?: string;
  nacionalidad?: string;
  estadoCivil?: string;
  ocupacion?: string;
  profesion?: string;
  empresa?: string;
  ingresoMensual?: number;
  calle?: string;
  numeroExterior?: string;
  numeroInterior?: string;
  colonia?: string;
  municipio?: string;
  estado?: string;
  codigoPostal?: string;
  pais?: string;
  numeroCuenta: string;
  saldo: number;
  ultimoAcceso?: string;
  estatusCuenta?: string;
  activo?: boolean;
  esAdmin?: boolean;
  rol?: string;
  totpHabilitado?: boolean;
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
  nacionalidad?: string;
  estadoCivil?: string;
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
  curp?: string;
  rfc?: string;
  sexo?: string;
  nacionalidad?: string;
  estadoCivil?: string;
  correo?: string;
  telefonoMovil?: string;
  usuario?: string;
  nuevaContrasena?: string;
  saldo?: number;
  activo?: boolean;
  bloqueado?: boolean;
}
