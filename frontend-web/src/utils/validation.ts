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

export function esTelefonoMexicanoValido(raw: string): boolean {
  if (!raw) return false;
  const tel = raw.trim();
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
