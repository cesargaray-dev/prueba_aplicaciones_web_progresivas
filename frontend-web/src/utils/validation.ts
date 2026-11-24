/**
 * Utilidades centralizadas de validación comercial y clasificación del catálogo GestoPago.
 * Reglas sincronizadas con el backend (TelefonoUtil.java).
 */

export const CATEGORIAS_TELEFONIA = new Set(['1', '2', '3', '4', '12', '13', '19']);

export const CATEGORIAS_FRONT1_NO_TELEFONIA = new Set(['8', '10', '11', '16', '18']);

export const SECUENCIAS_TELEFONO_INVALIDAS = new Set([
  '0123456789',
  '1234567890',
  '9876543210',
  '0987654321',
  '2345678901',
  '1234512345',
  '5555555555',
]);

export const TELEFONO_PRUEBA_GESTOPAGO = '1111111111';

export function esTelefonoMexicanoValido(raw: string): boolean {
  if (!raw) return false;
  const tel = raw.trim();
  if (tel === TELEFONO_PRUEBA_GESTOPAGO) return true;
  if (!/^\d{10}$/.test(tel)) return false;
  if (!/^[2-9]\d{9}$/.test(tel)) return false;
  if (/^(\d)\1{9}$/.test(tel)) return false;
  return !SECUENCIAS_TELEFONO_INVALIDAS.has(tel);
}

export function diagnosticarTelefono(raw: string): string | null {
  if (!raw || !raw.trim()) {
    return 'El número de teléfono es obligatorio y debe contener 10 dígitos.';
  }
  const tel = raw.trim();
  if (tel === TELEFONO_PRUEBA_GESTOPAGO) return null;
  if (!/^\d{10}$/.test(tel)) {
    return 'El número telefónico debe contener exactamente 10 dígitos numéricos (ej. 5512345678).';
  }
  if (!/^[2-9]\d{9}$/.test(tel)) {
    return 'En México un número telefónico debe iniciar con un dígito entre 2 y 9 (no con 0 ni 1).';
  }
  if (/^(\d)\1{9}$/.test(tel) || SECUENCIAS_TELEFONO_INVALIDAS.has(tel)) {
    return 'Ingresa un número telefónico real de 10 dígitos (no se permiten dígitos repetidos ni secuencias como 1234567890).';
  }
  return null;
}

/**
 * Normaliza texto para búsquedas del catálogo ignorando acentos, guiones,
 * símbolos (&) y espacios (ej. "AT&T", "AT-T" y "ATT" coinciden como "att").
 */
export function normalizarBusquedaCatalogo(raw: string): string {
  if (!raw) return '';
  return raw
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '');
}

export function esRecargaTelefonica(product: {
  frontType?: string;
  categoryId?: string;
  service?: string;
}): boolean {
  if (!product || product.frontType !== '1') return false;
  const cat = product.categoryId?.trim();
  if (cat) {
    if (CATEGORIAS_TELEFONIA.has(cat)) return true;
    if (CATEGORIAS_FRONT1_NO_TELEFONIA.has(cat)) return false;
  }
  const s = (product.service || '').toUpperCase();
  if (
    s.includes('AFORE') ||
    s.includes('AMAZON') ||
    s.includes('XBOX') ||
    s.includes('PLAYSTATION') ||
    s.includes('NETFLIX') ||
    s.includes('SPOTIFY') ||
    s.includes('YOUTUBE') ||
    s.includes('NINTENDO') ||
    s.includes('ACUARIO') ||
    s.includes('AZTLAN') ||
    s.includes('KATAPLUM') ||
    s.includes('RECORCHOLIS') ||
    s.includes('PASE') ||
    s.includes('TELEVIA') ||
    s.includes('EASYTRIP') ||
    s.includes('KINGSBET') ||
    s.includes('AMESHOP') ||
    s.includes('GOOGLE PLAY')
  ) {
    return false;
  }
  return true;
}

export function esConsultaSaldo(product: { frontType?: string }): boolean {
  return product?.frontType === '4' || product?.frontType === '5';
}

export function esCorreoValido(correo: string): boolean {
  if (!correo) return false;
  return /^[A-Za-z0-9+_-]+(?:\.[A-Za-z0-9+_-]+)*@(?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,}$/.test(
    correo.trim()
  );
}

export function esCorreoPlaceholder(correo: string): boolean {
  if (!correo || !correo.trim()) return true;
  const lower = correo.trim().toLowerCase();
  return (
    lower.endsWith('@ejemplo.com') ||
    lower.endsWith('.ejemplo.com') ||
    lower.endsWith('@example.com') ||
    lower.endsWith('.example.com') ||
    lower.endsWith('@test.com') ||
    lower.endsWith('.local')
  );
}

export function esContrasenaSegura(password: string): boolean {
  return /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,100}$/.test(password || '');
}

export function esNombreValido(value: string, required = true): boolean {
  const trimmed = (value || '').trim();
  if (!trimmed) {
    return !required;
  }
  if (required && trimmed.length < 2) return false;
  if (trimmed.length > 50) return false;
  return /^[A-Za-zÁÉÍÓÚáéíóúÑñ\s]+$/.test(trimmed);
}

export function esUsuarioValido(value: string): boolean {
  const trimmed = (value || '').trim();
  return /^[A-Za-z0-9_.-]{4,50}$/.test(trimmed);
}

export function getMaxBirthDateIso(referenceDate: Date = new Date()): string {
  const year = referenceDate.getFullYear() - 18;
  const month = String(referenceDate.getMonth() + 1).padStart(2, '0');
  const day = String(referenceDate.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function esMayorDeEdad(fechaIso: string, referenceDate: Date = new Date()): boolean {
  if (!fechaIso || !/^\d{4}-\d{2}-\d{2}$/.test(fechaIso.trim())) {
    return false;
  }
  const [yStr, mStr, dStr] = fechaIso.trim().split('-');
  const y = Number(yStr);
  const m = Number(mStr);
  const d = Number(dStr);
  const birth = new Date(y, m - 1, d);
  if (
    Number.isNaN(birth.getTime()) ||
    birth.getFullYear() !== y ||
    birth.getMonth() !== m - 1 ||
    birth.getDate() !== d
  ) {
    return false;
  }
  let age = referenceDate.getFullYear() - y;
  const monthDiff = referenceDate.getMonth() + 1 - m;
  if (monthDiff < 0 || (monthDiff === 0 && referenceDate.getDate() < d)) {
    age -= 1;
  }
  return age >= 18 && age <= 100;
}

export interface EstadoMexicoOption {
  code: string;
  name: string;
}

export const ESTADOS_MEXICO: readonly EstadoMexicoOption[] = [
  { code: 'AS', name: 'Aguascalientes' },
  { code: 'BC', name: 'Baja California' },
  { code: 'BS', name: 'Baja California Sur' },
  { code: 'CC', name: 'Campeche' },
  { code: 'CL', name: 'Coahuila' },
  { code: 'CM', name: 'Colima' },
  { code: 'CS', name: 'Chiapas' },
  { code: 'CH', name: 'Chihuahua' },
  { code: 'DF', name: 'Ciudad de México' },
  { code: 'DG', name: 'Durango' },
  { code: 'GT', name: 'Guanajuato' },
  { code: 'GR', name: 'Guerrero' },
  { code: 'HG', name: 'Hidalgo' },
  { code: 'JC', name: 'Jalisco' },
  { code: 'MC', name: 'Estado de México' },
  { code: 'MN', name: 'Michoacán' },
  { code: 'MS', name: 'Morelos' },
  { code: 'NT', name: 'Nayarit' },
  { code: 'NL', name: 'Nuevo León' },
  { code: 'OC', name: 'Oaxaca' },
  { code: 'PL', name: 'Puebla' },
  { code: 'QT', name: 'Querétaro' },
  { code: 'QR', name: 'Quintana Roo' },
  { code: 'SP', name: 'San Luis Potosí' },
  { code: 'SL', name: 'Sinaloa' },
  { code: 'SR', name: 'Sonora' },
  { code: 'TC', name: 'Tabasco' },
  { code: 'TS', name: 'Tamaulipas' },
  { code: 'TL', name: 'Tlaxcala' },
  { code: 'VZ', name: 'Veracruz' },
  { code: 'YN', name: 'Yucatán' },
  { code: 'ZS', name: 'Zacatecas' },
  { code: 'NE', name: 'Nacido en el Extranjero' },
];

const PALABRAS_INCONVENIENTES_CURP = new Set([
  'BACA', 'BAKA', 'BUEI', 'BUEY', 'CACA', 'CACO', 'CAGA', 'CAGO', 'CAKA', 'CAKO',
  'COGE', 'COGI', 'COJA', 'COJE', 'COJI', 'COJO', 'COLA', 'CULO', 'FALO', 'FETO',
  'GETA', 'GUEI', 'GUEY', 'JETA', 'JOTO', 'KACA', 'KACO', 'KAGA', 'KAGO', 'KAKA',
  'KAKO', 'KOGE', 'KOGI', 'KOJA', 'KOJE', 'KOJI', 'KOJO', 'KOLA', 'KULO', 'LILO',
  'LOCA', 'LOCO', 'LOKA', 'LOKO', 'MAME', 'MAMO', 'MEAR', 'MEAS', 'MEON', 'MIAR',
  'MION', 'MOCO', 'MOKO', 'MULA', 'MULO', 'NACA', 'NACO', 'PEDA', 'PEDO', 'PENE',
  'PIPI', 'PITO', 'POPO', 'PUTA', 'PUTO', 'QULO', 'RATA', 'ROBA', 'ROBE', 'ROBO',
  'RUIN', 'SENO', 'TETA', 'VACA', 'VAGA', 'VAGO', 'VAKA', 'VUEI', 'VUEY', 'WUEI', 'WUEY',
]);

export function esCurpValida(raw: string): boolean {
  if (!raw) return false;
  return /^[A-Z]{4}\d{6}[HM][A-Z]{5}[A-Z\d]\d$/.test(raw.trim().toUpperCase());
}

export function diagnosticarCurp(raw: string): string | null {
  const c = (raw || '').trim().toUpperCase();
  if (!c) return 'La CURP es obligatoria (18 caracteres oficiales).';
  if (c.length !== 18) return `La CURP debe tener exactamente 18 caracteres (actual: ${c.length}/18).`;
  if (!esCurpValida(c)) {
    return 'Formato de CURP inválido (4 letras + 6 dígitos de fecha + H/M + 5 letras + 2 caracteres de homoclave).';
  }
  return null;
}

export function esRfcValido(raw: string): boolean {
  if (!raw) return false;
  return /^[A-ZÑ&]{3,4}\d{6}[A-Z\d]{3}$/.test(raw.trim().toUpperCase());
}

export function diagnosticarRfc(raw: string): string | null {
  const r = (raw || '').trim().toUpperCase();
  if (!r) return 'El RFC con homoclave es obligatorio (13 caracteres para persona física).';
  if (r.length < 12 || r.length > 13) {
    return `El RFC debe contener 13 caracteres con homoclave (actual: ${r.length}).`;
  }
  if (!esRfcValido(r)) {
    return 'Formato de RFC inválido (4 letras + 6 dígitos YYMMDD + 3 caracteres de homoclave).';
  }
  return null;
}

export function esCodigoPostalValido(raw: string): boolean {
  if (!raw) return false;
  return /^(0[1-9]|[1-9][0-9])\d{3}$/.test(raw.trim());
}

const limpiarTextoRenapo = (txt: string): string =>
  (txt || '')
    .toUpperCase()
    .replace(/Ñ/g, 'X')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^A-Z\s]/g, ' ')
    .trim();

const primeraVocalInternaCurp = (palabra: string): string => {
  for (let i = 1; i < palabra.length; i++) {
    if ('AEIOU'.includes(palabra[i])) return palabra[i];
  }
  return 'X';
};

const primeraConsonanteInternaCurp = (palabra: string): string => {
  for (let i = 1; i < palabra.length; i++) {
    const c = palabra[i];
    if (!'AEIOU'.includes(c) && c >= 'A' && c <= 'Z') return c;
  }
  return 'X';
};

const calcularDigitoVerificadorCurp = (curp17: string): string => {
  const diccionario = '0123456789ABCDEFGHIJKLMNÑOPQRSTUVWXYZ';
  let suma = 0;
  for (let i = 0; i < 17; i++) {
    const idx = diccionario.indexOf(curp17[i]);
    suma += (idx >= 0 ? idx : 0) * (18 - i);
  }
  const residuo = 10 - (suma % 10);
  return String(residuo === 10 ? 0 : residuo);
};

export function generarCurpYRfcSugeridos(params: {
  nombre: string;
  segundoNombre?: string;
  apellidoPaterno: string;
  apellidoMaterno?: string;
  fechaNacimiento: string;
  sexo?: string;
  estadoCodigo?: string;
}): { curp: string; rfc: string } | null {
  const {
    nombre,
    segundoNombre,
    apellidoPaterno,
    apellidoMaterno,
    fechaNacimiento,
    sexo = 'HOMBRE',
    estadoCodigo = 'DF',
  } = params;

  if (!nombre?.trim() || !apellidoPaterno?.trim() || !/^\d{4}-\d{2}-\d{2}$/.test(fechaNacimiento || '')) {
    return null;
  }

  const nombres = limpiarTextoRenapo(`${nombre} ${segundoNombre || ''}`).split(/\s+/).filter(Boolean);
  let nombreBase = nombres[0] || 'X';
  if (nombres.length > 1 && (nombreBase === 'JOSE' || nombreBase === 'MARIA' || nombreBase === 'MA' || nombreBase === 'J')) {
    nombreBase = nombres[1];
  }

  const patTokens = limpiarTextoRenapo(apellidoPaterno).split(/\s+/).filter(Boolean);
  const matTokens = limpiarTextoRenapo(apellidoMaterno || '').split(/\s+/).filter(Boolean);
  const pat = patTokens[0] || 'X';
  const mat = matTokens[0] || 'X';

  let prefijo = `${pat[0] || 'X'}${primeraVocalInternaCurp(pat)}${matTokens.length > 0 ? mat[0] : 'X'}${nombreBase[0] || 'X'}`;
  if (PALABRAS_INCONVENIENTES_CURP.has(prefijo)) {
    prefijo = `${prefijo[0]}X${prefijo.slice(2)}`;
  }

  const [yyyy, mm, dd] = fechaNacimiento.trim().split('-');
  const fecha6 = `${yyyy.slice(-2)}${mm}${dd}`;
  const genero = sexo.toUpperCase().startsWith('M') ? 'M' : 'H';
  const entidad = /^[A-Z]{2}$/.test((estadoCodigo || '').toUpperCase())
    ? estadoCodigo.toUpperCase()
    : 'DF';

  const consonantes = `${primeraConsonanteInternaCurp(pat)}${matTokens.length > 0 ? primeraConsonanteInternaCurp(mat) : 'X'}${primeraConsonanteInternaCurp(nombreBase)}`;
  const diferenciadorSiglo = Number(yyyy) >= 2000 ? 'A' : '0';
  const curp17 = `${prefijo}${fecha6}${genero}${entidad}${consonantes}${diferenciadorSiglo}`;
  const curp = `${curp17}${calcularDigitoVerificadorCurp(curp17)}`;

  // Homoclave determinística de 3 caracteres alfanuméricos para RFC
  const seedStr = `${pat}|${mat}|${nombreBase}|${fechaNacimiento}`;
  let hash = 0;
  for (let i = 0; i < seedStr.length; i++) {
    hash = (hash * 31 + seedStr.charCodeAt(i)) >>> 0;
  }
  const alfa = 'ABCDEFGHJKLMNPQRSTUVWXYZ123456789';
  const h1 = alfa[hash % alfa.length];
  const h2 = alfa[Math.floor(hash / 37) % alfa.length];
  const h3 = String((hash % 9) + 1);
  const rfc = `${prefijo}${fecha6}${h1}${h2}${h3}`;

  return { curp, rfc };
}

export function esItemHistorialRecarga(item: {
  telefono?: string;
  referencia?: string;
  servicio?: string;
  producto?: string;
}): boolean {
  const tel = (item.telefono || '').trim();
  const ref = (item.referencia || '').trim();
  if (!tel) return false;
  if (ref && ref === tel) return true;
  if (!ref && tel !== TELEFONO_PRUEBA_GESTOPAGO) return true;
  if (!ref && tel === TELEFONO_PRUEBA_GESTOPAGO) {
    return /recarga|tiempo aire|paquete|telcel|at-t|at&t|att|movistar|unefon|bait|virgin|pillofon/i.test(
      `${item.servicio || ''} ${item.producto || ''}`
    );
  }
  return false;
}

export function getProductCategoryName(product: {
  frontType?: string;
  categoryId?: string;
  service?: string;
  name?: string;
}): string {
  if (esRecargaTelefonica(product)) {
    return 'Telefonía';
  }
  const s = (product.service || '').toUpperCase();
  const n = (product.name || '').toUpperCase();
  const catId = (product.categoryId || '').trim();

  if (catId === '5' || s.includes('CFE') || s.includes('LUZ') || n.includes('CFE')) {
    return 'Luz y Energía (CFE)';
  }
  if (
    catId === '15' ||
    s.includes('AGUA') ||
    s.includes('SAPA') ||
    s.includes('SIAPA') ||
    s.includes('CEA') ||
    s.includes('JAPAY') ||
    s.includes('COMAPA')
  ) {
    return 'Agua Potable';
  }
  if (
    catId === '4' ||
    s.includes('IZZI') ||
    s.includes('TOTALPLAY') ||
    s.includes('TELMEX') ||
    s.includes('SKY') ||
    s.includes('DISH') ||
    s.includes('MEGACABLE')
  ) {
    return 'Internet y TV';
  }
  if (
    catId === '14' ||
    catId === '18' ||
    s.includes('TAG') ||
    s.includes('PASE') ||
    s.includes('TELEVIA') ||
    s.includes('EASYTRIP')
  ) {
    return 'Peajes y Transporte';
  }
  if (
    catId === '8' ||
    s.includes('CREDITO') ||
    s.includes('BANCO') ||
    s.includes('FINAN') ||
    s.includes('AFORE') ||
    s.includes('INFONAVIT')
  ) {
    return 'Financieros';
  }
  if (
    catId === '10' ||
    catId === '11' ||
    catId === '16' ||
    s.includes('AMAZON') ||
    s.includes('SPOTIFY') ||
    s.includes('NETFLIX') ||
    s.includes('PLAYSTATION') ||
    s.includes('XBOX') ||
    s.includes('YOUTUBE') ||
    s.includes('NINTENDO') ||
    s.includes('GOOGLE PLAY') ||
    s.includes('KATAPLUM') ||
    s.includes('ACUARIO') ||
    s.includes('KINGSBET')
  ) {
    return 'Entretenimiento';
  }
  return 'Otros Servicios';
}
