import { describe, it, expect } from 'vitest';
import {
  diagnosticarCurp,
  diagnosticarRfc,
  diagnosticarTelefono,
  esCodigoPostalValido,
  esConsultaSaldo,
  esContrasenaSegura,
  esCorreoPlaceholder,
  esCorreoValido,
  esCurpValida,
  esItemHistorialRecarga,
  esMayorDeEdad,
  esNombreValido,
  esRecargaTelefonica,
  esRfcValido,
  esTelefonoMexicanoValido,
  esUsuarioValido,
  generarCurpYRfcSugeridos,
  getMaxBirthDateIso,
  getProductCategoryName,
  normalizarBusquedaCatalogo,
} from '../utils/validation';

describe('Validation & Catalog Classification Suite', () => {
  it('validates Mexican 10-digit phone numbers and returns clear diagnostics', () => {
    expect(esTelefonoMexicanoValido('')).toBe(false);
    expect(esTelefonoMexicanoValido('5512345678')).toBe(true);
    expect(esTelefonoMexicanoValido('8119876543')).toBe(true);
    expect(esTelefonoMexicanoValido('1111111111')).toBe(true);
    expect(esTelefonoMexicanoValido('2222222222')).toBe(false);
    expect(esTelefonoMexicanoValido('1234567890')).toBe(false);
    expect(esTelefonoMexicanoValido('0512345678')).toBe(false);

    expect(diagnosticarTelefono('')).toContain('obligatorio');
    expect(diagnosticarTelefono('12345')).toContain('10 dígitos numéricos');
    expect(diagnosticarTelefono('1512345678')).toContain('entre 2 y 9');
    expect(diagnosticarTelefono('2222222222')).toContain('dígitos repetidos');
    expect(diagnosticarTelefono('2345678901')).toContain('secuencias');
    expect(diagnosticarTelefono('5512345678')).toBeNull();
    expect(diagnosticarTelefono('1111111111')).toBeNull();

    expect(normalizarBusquedaCatalogo('AT&T')).toBe('att');
    expect(normalizarBusquedaCatalogo('AT-T')).toBe('att');
    expect(normalizarBusquedaCatalogo('Telefonía')).toBe('telefonia');
  });

  it('classifies recargas, frontType=1 vouchers, inquiry items, and category names', () => {
    expect(esRecargaTelefonica({ frontType: '1', categoryId: '2', service: 'Telcel' })).toBe(true);
    expect(esRecargaTelefonica({ frontType: '1', categoryId: '10', service: 'Amazon Cash' })).toBe(false);
    expect(esRecargaTelefonica({ frontType: '1', service: 'NETFLIX PIN' })).toBe(false);
    expect(esRecargaTelefonica({ frontType: '1', service: 'Telcel Libre' })).toBe(true);
    expect(esRecargaTelefonica({ frontType: '2', categoryId: '5', service: 'CFE' })).toBe(false);

    expect(esConsultaSaldo({ frontType: '4' })).toBe(true);
    expect(esConsultaSaldo({ frontType: '5' })).toBe(true);
    expect(esConsultaSaldo({ frontType: '2' })).toBe(false);

    expect(getProductCategoryName({ frontType: '1', categoryId: '2', service: 'Telcel' })).toBe('Telefonía');
    expect(getProductCategoryName({ frontType: '2', service: 'CFE' })).toBe('Luz y Energía (CFE)');
    expect(getProductCategoryName({ frontType: '2', service: 'SIAPA AGUA' })).toBe('Agua Potable');
    expect(getProductCategoryName({ frontType: '2', service: 'IZZI' })).toBe('Internet y TV');
    expect(getProductCategoryName({ frontType: '1', categoryId: '18', service: 'PASE' })).toBe('Peajes y Transporte');
    expect(getProductCategoryName({ frontType: '1', categoryId: '8', service: 'AFORE' })).toBe('Financieros');
    expect(getProductCategoryName({ frontType: '1', categoryId: '10', service: 'Amazon' })).toBe('Entretenimiento');
    expect(getProductCategoryName({ frontType: '2', service: 'Predial Municipal' })).toBe('Otros Servicios');
  });

  it('validates emails, placeholders, passwords, names, usernames, and 18+ birth dates', () => {
    expect(esCorreoValido('')).toBe(false);
    expect(esCorreoValido('cliente@gmail.com')).toBe(true);
    expect(esCorreoValido('bad-email')).toBe(false);

    expect(esCorreoPlaceholder('')).toBe(true);
    expect(esCorreoPlaceholder('demo@ejemplo.com')).toBe(true);
    expect(esCorreoPlaceholder('a@sub.example.com')).toBe(true);
    expect(esCorreoPlaceholder('a@test.com')).toBe(true);
    expect(esCorreoPlaceholder('a@host.local')).toBe(true);
    expect(esCorreoPlaceholder('cliente@gmail.com')).toBe(false);

    expect(esContrasenaSegura('Segura2026')).toBe(true);
    expect(esContrasenaSegura('debil')).toBe(false);

    expect(esNombreValido('María José', true)).toBe(true);
    expect(esNombreValido('A', true)).toBe(false);
    expect(esNombreValido('', true)).toBe(false);
    expect(esNombreValido('', false)).toBe(true);
    expect(esNombreValido('Juan123', true)).toBe(false);
    expect(esNombreValido('X'.repeat(51), false)).toBe(false);

    expect(esUsuarioValido('cesar.123-ok_1')).toBe(true);
    expect(esUsuarioValido('abc')).toBe(false);
    expect(esUsuarioValido('bad user!')).toBe(false);

    const refDate = new Date(2026, 10, 20); // 2026-11-20
    expect(getMaxBirthDateIso(refDate)).toBe('2008-11-20');
    expect(esMayorDeEdad('2008-11-20', refDate)).toBe(true);
    expect(esMayorDeEdad('2008-11-21', refDate)).toBe(false);
    expect(esMayorDeEdad('1900-01-01', refDate)).toBe(false);
    expect(esMayorDeEdad('2000-02-30', refDate)).toBe(false);
    expect(esMayorDeEdad('invalid', refDate)).toBe(false);
  });

  it('validates CURP, RFC, Postal Codes, generates official RENAPO/SAT keys, and classifies 1111111111 history recharges', () => {
    expect(esCurpValida('GAGC990515HDFRRL01')).toBe(true);
    expect(esCurpValida('INVALID')).toBe(false);
    expect(diagnosticarCurp('')).toContain('obligatoria');
    expect(diagnosticarCurp('ABC')).toContain('18 caracteres');
    expect(diagnosticarCurp('123456789012345678')).toContain('Formato de CURP');
    expect(diagnosticarCurp('GAGC990515HDFRRL01')).toBeNull();

    expect(esRfcValido('GAGC990515AB1')).toBe(true);
    expect(esRfcValido('BAD')).toBe(false);
    expect(diagnosticarRfc('')).toContain('obligatorio');
    expect(diagnosticarRfc('ABC')).toContain('13 caracteres');
    expect(diagnosticarRfc('1234567890123')).toContain('Formato de RFC');
    expect(diagnosticarRfc('GAGC990515AB1')).toBeNull();

    expect(esCodigoPostalValido('06600')).toBe(true);
    expect(esCodigoPostalValido('00000')).toBe(false);
    expect(esCodigoPostalValido('123')).toBe(false);

    const suggested = generarCurpYRfcSugeridos({
      nombre: 'José',
      segundoNombre: 'César',
      apellidoPaterno: 'Garay',
      apellidoMaterno: 'García',
      fechaNacimiento: '2001-05-15',
      sexo: 'HOMBRE',
      estadoCodigo: 'DF',
    });
    expect(suggested).not.toBeNull();
    expect(esCurpValida(suggested!.curp)).toBe(true);
    expect(esRfcValido(suggested!.rfc)).toBe(true);
    expect(
      generarCurpYRfcSugeridos({ nombre: '', apellidoPaterno: 'Garay', fechaNacimiento: '1999-01-01' })
    ).toBeNull();

    // History classification with 1111111111 vs service payment
    expect(
      esItemHistorialRecarga({
        telefono: '1111111111',
        referencia: '1111111111',
        servicio: 'AT-T',
        producto: 'Recarga $100',
      })
    ).toBe(true);
    expect(
      esItemHistorialRecarga({
        telefono: '1111111111',
        referencia: '012345678912',
        servicio: 'CFE',
        producto: 'Recibo CFE',
      })
    ).toBe(false);
  });
});
