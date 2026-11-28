import { describe, it, expect } from 'vitest';
import {
  diagnosticarApellido,
  diagnosticarCalle,
  diagnosticarCodigoPostal,
  diagnosticarColonia,
  diagnosticarCurp,
  diagnosticarEmpresa,
  diagnosticarNombre,
  diagnosticarNumeroDomicilio,
  diagnosticarRfc,
  diagnosticarTelefono,
  esApellidoValido,
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
  obtenerLeyendaComprobante,
} from '../utils/validation';
import {
  CATALOGO_OCUPACIONES,
  TABULADOR_PROFESIONES,
  TABULADOR_SUELDOS,
  obtenerEstadoPorNombreOCodigo,
  obtenerMunicipiosPorEstado,
  resolverCodigoPostalMexicano,
} from '../data/mexicoCatalogs';

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
    expect(esNombreValido('Bo', true)).toBe(true);
    expect(esNombreValido('A', true)).toBe(false);
    expect(esNombreValido('', true)).toBe(false);
    expect(esNombreValido('', false)).toBe(true);
    expect(esNombreValido('Juan123', true)).toBe(false);
    expect(esNombreValido('X'.repeat(41), false)).toBe(false);
    expect(diagnosticarNombre('A')).toContain('al menos 2 letras');
    expect(diagnosticarNombre('Juan  José')).toContain('espacios dobles');

    expect(esApellidoValido('Garay', true)).toBe(true);
    expect(esApellidoValido('Ga', true)).toBe(false);
    expect(diagnosticarApellido('Ga')).toContain('al menos 3 letras');
    expect(diagnosticarApellido('Garay9')).toContain('solo debe contener letras');
    expect(diagnosticarApellido('X'.repeat(41))).toContain('no debe exceder 40');

    expect(diagnosticarCalle('')).toContain('obligatorio');
    expect(diagnosticarCalle('Ab')).toContain('al menos 3 caracteres');
    expect(diagnosticarCalle('1234')).toContain('nombre de calle descriptivo');
    expect(diagnosticarCalle('Paseo de la Reforma')).toBeNull();
    expect(diagnosticarNumeroDomicilio('')).toContain('obligatorio');
    expect(diagnosticarNumeroDomicilio('S/N')).toBeNull();
    expect(diagnosticarColonia('Juárez')).toBeNull();
    expect(diagnosticarEmpresa('UNAM')).toBeNull();

    expect(TABULADOR_PROFESIONES.length).toBeGreaterThanOrEqual(15);
    expect(TABULADOR_SUELDOS.length).toBeGreaterThanOrEqual(6);
    expect(obtenerEstadoPorNombreOCodigo('')).toBeUndefined();
    expect(obtenerEstadoPorNombreOCodigo('Morelos')?.codigoRenapo).toBe('MS');
    expect(obtenerMunicipiosPorEstado('')).toEqual([]);
    expect(obtenerMunicipiosPorEstado('DF')).toContain('Cuauhtémoc');
    expect(obtenerMunicipiosPorEstado('MS')).toContain('Cuernavaca');

    // SEPOMEX subrange branches across CDMX, Morelos, Jalisco, Nuevo León, Estado de México & other states
    for (const [cp, expectedMun] of [
      ['01000', 'Álvaro Obregón'],
      ['06600', 'Cuauhtémoc'],
      ['62000', 'Cuernavaca'],
      ['62520', 'Tepoztlán'],
      ['62555', 'Jiutepec'],
      ['62585', 'Temixco'],
      ['62735', 'Yautepec'],
      ['62745', 'Cuautla'],
      ['62765', 'Emiliano Zapata'],
      ['62795', 'Xochitepec'],
      ['62905', 'Jojutla'],
      ['62990', 'Cuernavaca'],
      ['44100', 'Guadalajara'],
      ['45050', 'Zapopan'],
      ['45410', 'Tonalá'],
      ['45550', 'San Pedro Tlaquepaque'],
      ['45650', 'Tlajomulco de Zúñiga'],
      ['48310', 'Puerto Vallarta'],
      ['49000', 'Guadalajara'],
      ['64000', 'Monterrey'],
      ['66220', 'San Pedro Garza García'],
      ['66450', 'San Nicolás de los Garza'],
      ['66610', 'Apodaca'],
      ['67110', 'Guadalupe'],
      ['67900', 'Monterrey'],
      ['50010', 'Toluca'],
      ['52150', 'Metepec'],
      ['52770', 'Huixquilucan'],
      ['52910', 'Atizapán de Zaragoza'],
      ['53100', 'Naucalpan de Juárez'],
      ['54010', 'Tlalnepantla de Baz'],
      ['54710', 'Cuautitlán Izcalli'],
      ['55010', 'Ecatepec de Morelos'],
      ['56000', 'Toluca'],
      ['57010', 'Nezahualcóyotl'],
      ['59000', 'Morelia'],
      ['97000', 'Mérida'],
    ] as const) {
      expect(resolverCodigoPostalMexicano(cp).municipioSugerido).toBe(expectedMun);
    }

    expect(resolverCodigoPostalMexicano('').valido).toBe(false);
    expect(resolverCodigoPostalMexicano('12A45').valido).toBe(false);
    expect(resolverCodigoPostalMexicano('123').valido).toBe(false);
    expect(resolverCodigoPostalMexicano('18500').valido).toBe(false);
    expect(diagnosticarCodigoPostal('18500')).toContain('SEPOMEX');
    expect(diagnosticarCalle('Calle #bad')).toContain('no permitidos');
    expect(diagnosticarCalle('X'.repeat(81))).toContain('80');
    expect(diagnosticarNumeroDomicilio('@@')).toContain('válidos');
    expect(diagnosticarColonia('')).toContain('colonia');
    expect(diagnosticarColonia('AB')).toContain('3 y 60');
    expect(diagnosticarColonia('Col#')).toContain('inválidos');
    expect(diagnosticarEmpresa('')).toContain('trabajo');
    expect(diagnosticarEmpresa('A')).toContain('2 y 80');

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

    // Official Occupation Catalog & Ticket Leyenda helper
    expect(CATALOGO_OCUPACIONES).toHaveLength(15);
    expect(CATALOGO_OCUPACIONES).toContain('Empleado(a) del sector privado');
    expect(CATALOGO_OCUPACIONES).toContain('Otra ocupación');

    expect(obtenerLeyendaComprobante({ leyenda: 'Leyenda backend' })).toBe('Leyenda backend');
    expect(
      obtenerLeyendaComprobante({ esRechazada: true, numAuth: '-1', folio: 'FT-1' })
    ).toContain('Operación no aplicada: no se realizó ningún cargo a su cuenta.');
    expect(
      obtenerLeyendaComprobante({ esRechazada: false, numAuth: 'AUTH-1', folio: 'FT-2' })
    ).toContain('Conserve este comprobante digital como constancia oficial de pago.');
  });
});
