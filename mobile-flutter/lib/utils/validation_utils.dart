import 'mexico_catalogs.dart';

/// Utilidades centralizadas de validación comercial y clasificación del catálogo GestoPago.
/// Reglas sincronizadas exactamente con el backend (TelefonoUtil.java) y la app Web.
class ValidationUtils {
  ValidationUtils._();

  static const Set<String> categoriasTelefonia = {'1', '2', '3', '4', '12', '13', '19'};
  static const Set<String> categoriasFront1NoTelefonia = {'8', '10', '11', '16', '18'};
  static const Set<String> secuenciasInvalidas = {
    '0123456789',
    '1234567890',
    '9876543210',
    '0987654321',
    '2345678901',
    '1234512345',
    '5555555555',
  };

  static final RegExp _soloDiezDigitos = RegExp(r'^\d{10}$');
  static final RegExp _telefonoMexicoPattern = RegExp(r'^[2-9]\d{9}$');
  static final RegExp _todosDigitosIguales = RegExp(r'^(\d)\1{9}$');
  static final RegExp _emailPattern = RegExp(
    r'^[A-Za-z0-9+_-]+(?:\.[A-Za-z0-9+_-]+)*@(?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,}$',
  );
  static final RegExp _passwordSegura = RegExp(
    r'^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,100}$',
  );
  static final RegExp _nombreLetrasPattern = RegExp(r'^[A-Za-zÁÉÍÓÚáéíóúÜüÑñ\s]+$');
  static final RegExp _usuarioPattern = RegExp(r'^[A-Za-z0-9_.\-]{4,50}$');
  static final RegExp _fechaIsoPattern = RegExp(r'^(\d{4})-(\d{2})-(\d{2})$');

  static const String telefonoPruebaGestoPago = '1111111111';

  static bool esTelefonoMexicanoValido(String? raw) {
    if (raw == null) return false;
    final tel = raw.trim();
    if (tel == telefonoPruebaGestoPago) return true;
    if (!_soloDiezDigitos.hasMatch(tel)) return false;
    if (!_telefonoMexicoPattern.hasMatch(tel)) return false;
    if (_todosDigitosIguales.hasMatch(tel)) return false;
    return !secuenciasInvalidas.contains(tel);
  }

  static String? diagnosticarTelefono(String? raw) {
    if (raw == null || raw.trim().isEmpty) {
      return 'El número de teléfono es obligatorio y debe contener 10 dígitos.';
    }
    final tel = raw.trim();
    if (tel == telefonoPruebaGestoPago) return null;
    if (!_soloDiezDigitos.hasMatch(tel)) {
      return 'El número de teléfono debe contener exactamente 10 dígitos numéricos.';
    }
    if (!_telefonoMexicoPattern.hasMatch(tel)) {
      return 'En México un número telefónico debe iniciar con un dígito entre 2 y 9 (no con 0 ni 1).';
    }
    if (_todosDigitosIguales.hasMatch(tel) || secuenciasInvalidas.contains(tel)) {
      return 'Ingresa un número telefónico real de 10 dígitos (sin dígitos repetidos ni secuencias).';
    }
    return null;
  }

  /// Normaliza texto de búsqueda removiendo acentos, espacios y símbolos como & o -
  /// (ej. "AT&T", "AT-T" y "ATT" coinciden como "att").
  static String normalizarBusquedaCatalogo(String? raw) {
    if (raw == null || raw.isEmpty) return '';
    const map = {
      'á': 'a', 'é': 'e', 'í': 'i', 'ó': 'o', 'ú': 'u', 'ü': 'u', 'ñ': 'n',
      'Á': 'a', 'É': 'e', 'Í': 'i', 'Ó': 'o', 'Ú': 'u', 'Ü': 'u', 'Ñ': 'n',
    };
    final sb = StringBuffer();
    for (final rune in raw.toLowerCase().runes) {
      final ch = String.fromCharCode(rune);
      final mapped = map[ch] ?? ch;
      if (RegExp(r'[a-z0-9]').hasMatch(mapped)) {
        sb.write(mapped);
      }
    }
    return sb.toString();
  }

  static bool esRecargaTelefonica({
    required String frontType,
    String? categoryId,
    String? service,
  }) {
    if (frontType.trim() != '1') return false;
    final cat = (categoryId ?? '').trim();
    if (cat.isNotEmpty) {
      if (categoriasTelefonia.contains(cat)) return true;
      if (categoriasFront1NoTelefonia.contains(cat)) return false;
    }
    final s = (service ?? '').toUpperCase();
    const noPhoneKeywords = [
      'AFORE',
      'AMAZON',
      'XBOX',
      'PLAYSTATION',
      'NETFLIX',
      'SPOTIFY',
      'YOUTUBE',
      'NINTENDO',
      'ACUARIO',
      'AZTLAN',
      'KATAPLUM',
      'RECORCHOLIS',
      'PASE',
      'TELEVIA',
      'EASYTRIP',
      'KINGSBET',
      'AMESHOP',
      'GOOGLE PLAY',
    ];
    for (final kw in noPhoneKeywords) {
      if (s.contains(kw)) return false;
    }
    return true;
  }

  static bool esCorreoValido(String? correo) {
    if (correo == null || correo.trim().isEmpty) return false;
    return _emailPattern.hasMatch(correo.trim());
  }

  static bool esCorreoPlaceholder(String? correo) {
    if (correo == null || correo.trim().isEmpty) return true;
    final lower = correo.trim().toLowerCase();
    return lower.endsWith('@ejemplo.com') ||
        lower.endsWith('.ejemplo.com') ||
        lower.endsWith('@example.com') ||
        lower.endsWith('.example.com') ||
        lower.endsWith('@test.com') ||
        lower.endsWith('.local');
  }

  static bool esContrasenaSegura(String? password) {
    if (password == null) return false;
    return _passwordSegura.hasMatch(password);
  }

  static bool esNombreValido(String? raw, {bool optional = false, int maxLength = 60}) {
    return diagnosticarNombre(raw, 'El nombre', optional: optional, maxLength: maxLength) == null;
  }

  static String? diagnosticarNombre(
    String? raw,
    String campo, {
    bool optional = false,
    int maxLength = 60,
  }) {
    final val = (raw ?? '').trim();
    if (val.isEmpty) {
      return optional ? null : '$campo es obligatorio (2 a $maxLength letras).';
    }
    if (!_nombreLetrasPattern.hasMatch(val)) {
      return '$campo solo debe contener letras y espacios (sin números ni símbolos).';
    }
    if (RegExp(r'\s{2,}').hasMatch(val)) {
      return '$campo no debe contener espacios dobles.';
    }
    if (val.length < 2) {
      return '$campo debe tener al menos 2 letras (máx. $maxLength).';
    }
    if (val.length > maxLength) {
      return '$campo no puede exceder $maxLength caracteres.';
    }
    return null;
  }

  static bool esApellidoValido(String? raw, {bool optional = false, int maxLength = 40}) {
    return diagnosticarApellido(raw, 'El apellido', optional: optional, maxLength: maxLength) == null;
  }

  static String? diagnosticarApellido(
    String? raw,
    String campo, {
    bool optional = false,
    int maxLength = 40,
  }) {
    final val = (raw ?? '').trim();
    if (val.isEmpty) {
      return optional ? null : '$campo es obligatorio (mín. 3 letras, máx. $maxLength).';
    }
    if (!_nombreLetrasPattern.hasMatch(val)) {
      return '$campo solo debe contener letras y espacios (sin números ni símbolos).';
    }
    if (RegExp(r'\s{2,}').hasMatch(val)) {
      return '$campo no debe contener espacios dobles.';
    }
    if (val.length < 3) {
      return '$campo debe tener al menos 3 letras (máx. $maxLength).';
    }
    if (val.length > maxLength) {
      return '$campo no puede exceder $maxLength caracteres.';
    }
    return null;
  }

  static String? diagnosticarCalle(String? raw) {
    final val = (raw ?? '').trim();
    if (val.isEmpty) {
      return 'El nombre de la calle es obligatorio.';
    }
    if (RegExp(r'[^A-Za-zÁÉÍÓÚÜáéíóúüÑñ0-9\s.,\-°/]').hasMatch(val)) {
      return 'La calle contiene caracteres inválidos.';
    }
    if (val.length < 3) {
      return 'La calle debe tener al menos 3 caracteres.';
    }
    if (val.length > 80) {
      return 'La calle no debe exceder 80 caracteres.';
    }
    final letters = RegExp(r'[A-Za-zÁÉÍÓÚÜáéíóúüÑñ]').allMatches(val).length;
    if (letters < 2) {
      return 'Ingresa un nombre de calle descriptivo (ej. Reforma, 5 de Mayo).';
    }
    return null;
  }

  static String? diagnosticarNumeroDomicilio(
    String? raw, {
    bool optional = false,
    String campo = 'El número exterior',
  }) {
    final val = (raw ?? '').trim();
    if (val.isEmpty) {
      return optional ? null : '$campo es obligatorio (usa S/N si no aplica).';
    }
    if (!RegExp(r'^[A-Za-z0-9\s/\-.]{1,10}$').hasMatch(val)) {
      return '$campo debe tener entre 1 y 10 caracteres válidos.';
    }
    return null;
  }

  static String? diagnosticarColonia(String? raw) {
    final val = (raw ?? '').trim();
    if (val.isEmpty) return 'Selecciona o escribe tu colonia.';
    if (val.length < 3 || val.length > 60) {
      return 'La colonia debe tener entre 3 y 60 caracteres.';
    }
    if (RegExp(r'[^A-Za-zÁÉÍÓÚÜáéíóúüÑñ0-9\s.,\-/]').hasMatch(val)) {
      return 'La colonia contiene caracteres inválidos.';
    }
    return null;
  }

  static String? diagnosticarEmpresa(String? raw) {
    final val = (raw ?? '').trim();
    if (val.isEmpty) return 'Ingresa tu empresa, institución o actividad.';
    if (val.length < 2 || val.length > 80) {
      return 'Debe tener entre 2 y 80 caracteres.';
    }
    return null;
  }

  static bool esUsuarioValido(String? raw) {
    return diagnosticarUsuario(raw) == null;
  }

  static String? diagnosticarUsuario(String? raw) {
    final val = (raw ?? '').trim();
    if (val.isEmpty) {
      return 'El usuario es obligatorio.';
    }
    if (val.length < 4 || val.length > 50) {
      return 'El usuario debe tener entre 4 y 50 caracteres.';
    }
    if (!_usuarioPattern.hasMatch(val)) {
      return 'Solo se permiten letras, números, punto, guión y guión bajo.';
    }
    return null;
  }

  static String? diagnosticarFechaNacimiento(String? raw, {DateTime? now}) {
    final val = (raw ?? '').trim();
    if (val.isEmpty) {
      return 'La fecha de nacimiento es obligatoria.';
    }
    final match = _fechaIsoPattern.firstMatch(val);
    if (match == null) {
      return 'Formato inválido. Usa YYYY-MM-DD.';
    }
    final year = int.tryParse(match.group(1)!);
    final month = int.tryParse(match.group(2)!);
    final day = int.tryParse(match.group(3)!);
    if (year == null || month == null || day == null) {
      return 'Fecha de nacimiento inválida.';
    }
    final parsed = DateTime(year, month, day);
    if (parsed.year != year || parsed.month != month || parsed.day != day) {
      return 'Fecha de nacimiento inválida.';
    }
    final ref = now ?? DateTime.now();
    final today = DateTime(ref.year, ref.month, ref.day);
    if (parsed.isAfter(today)) {
      return 'La fecha de nacimiento no puede ser futura.';
    }
    int age = today.year - parsed.year;
    if (today.month < parsed.month || (today.month == parsed.month && today.day < parsed.day)) {
      age--;
    }
    if (age < 18) {
      return 'Debes ser mayor de 18 años para abrir una cuenta.';
    }
    if (age > 110) {
      return 'Ingresa una fecha de nacimiento válida.';
    }
    return null;
  }

  static double montoMinimoPago({required String frontType}) {
    return frontType.trim() == '1' ? 1.0 : 10.0;
  }

  static bool esMontoPagoValido(double monto, {required String frontType}) {
    final min = frontType.trim() == '1' ? 0.01 : 10.0;
    return monto >= min && monto <= 50000.0;
  }

  static const List<MapEntry<String, String>> estadosMexico = [
    MapEntry('AS', 'Aguascalientes'),
    MapEntry('BC', 'Baja California'),
    MapEntry('BS', 'Baja California Sur'),
    MapEntry('CC', 'Campeche'),
    MapEntry('CL', 'Coahuila'),
    MapEntry('CM', 'Colima'),
    MapEntry('CS', 'Chiapas'),
    MapEntry('CH', 'Chihuahua'),
    MapEntry('DF', 'Ciudad de México'),
    MapEntry('DG', 'Durango'),
    MapEntry('GT', 'Guanajuato'),
    MapEntry('GR', 'Guerrero'),
    MapEntry('HG', 'Hidalgo'),
    MapEntry('JC', 'Jalisco'),
    MapEntry('MC', 'Estado de México'),
    MapEntry('MN', 'Michoacán'),
    MapEntry('MS', 'Morelos'),
    MapEntry('NT', 'Nayarit'),
    MapEntry('NL', 'Nuevo León'),
    MapEntry('OC', 'Oaxaca'),
    MapEntry('PL', 'Puebla'),
    MapEntry('QT', 'Querétaro'),
    MapEntry('QR', 'Quintana Roo'),
    MapEntry('SP', 'San Luis Potosí'),
    MapEntry('SL', 'Sinaloa'),
    MapEntry('SR', 'Sonora'),
    MapEntry('TC', 'Tabasco'),
    MapEntry('TS', 'Tamaulipas'),
    MapEntry('TL', 'Tlaxcala'),
    MapEntry('VZ', 'Veracruz'),
    MapEntry('YN', 'Yucatán'),
    MapEntry('ZS', 'Zacatecas'),
    MapEntry('NE', 'Nacido en el Extranjero'),
  ];

  static const Set<String> _palabrasInconvenientesCurp = {
    'BACA', 'BAKA', 'BUEI', 'BUEY', 'CACA', 'CACO', 'CAGA', 'CAGO', 'CAKA', 'CAKO',
    'COGE', 'COGI', 'COJA', 'COJE', 'COJI', 'COJO', 'COLA', 'CULO', 'FALO', 'FETO',
    'GETA', 'GUEI', 'GUEY', 'JETA', 'JOTO', 'KACA', 'KACO', 'KAGA', 'KAGO', 'KAKA',
    'KAKO', 'KOGE', 'KOGI', 'KOJA', 'KOJE', 'KOJI', 'KOJO', 'KOLA', 'KULO', 'LILO',
    'LOCA', 'LOCO', 'LOKA', 'LOKO', 'MAME', 'MAMO', 'MEAR', 'MEAS', 'MEON', 'MIAR',
    'MION', 'MOCO', 'MOKO', 'MULA', 'MULO', 'NACA', 'NACO', 'PEDA', 'PEDO', 'PENE',
    'PIPI', 'PITO', 'POPO', 'PUTA', 'PUTO', 'QULO', 'RATA', 'ROBA', 'ROBE', 'ROBO',
    'RUIN', 'SENO', 'TETA', 'VACA', 'VAGA', 'VAGO', 'VAKA', 'VUEI', 'VUEY', 'WUEI', 'WUEY',
  };

  static final RegExp _curpPattern = RegExp(r'^[A-Z]{4}\d{6}[HM][A-Z]{5}[A-Z\d]\d$');
  static final RegExp _rfcPattern = RegExp(r'^[A-ZÑ&]{3,4}\d{6}[A-Z\d]{3}$');

  static bool esCurpValida(String? raw) {
    if (raw == null) return false;
    return _curpPattern.hasMatch(raw.trim().toUpperCase());
  }

  static String? diagnosticarCurp(String? raw) {
    final c = (raw ?? '').trim().toUpperCase();
    if (c.isEmpty) return 'La CURP es obligatoria (18 caracteres oficiales).';
    if (c.length != 18) return 'La CURP debe tener exactamente 18 caracteres (${c.length}/18).';
    if (!esCurpValida(c)) {
      return 'Formato de CURP inválido (4 letras + 6 dígitos + H/M + 5 letras + 2 homoclave).';
    }
    return null;
  }

  static bool esRfcValido(String? raw) {
    if (raw == null) return false;
    return _rfcPattern.hasMatch(raw.trim().toUpperCase());
  }

  static String? diagnosticarRfc(String? raw) {
    final r = (raw ?? '').trim().toUpperCase();
    if (r.isEmpty) return 'El RFC con homoclave es obligatorio (13 caracteres).';
    if (r.length < 12 || r.length > 13) {
      return 'El RFC debe contener 13 caracteres con homoclave (${r.length}).';
    }
    if (!esRfcValido(r)) {
      return 'Formato de RFC inválido (4 letras + 6 dígitos YYMMDD + 3 homoclave).';
    }
    return null;
  }

  static bool esCodigoPostalValido(String? raw) {
    if (raw == null) return false;
    return MexicoCatalogs.resolverCodigoPostalMexicano(raw).valido;
  }

  static String? diagnosticarCodigoPostal(String? raw) {
    final res = MexicoCatalogs.resolverCodigoPostalMexicano(raw ?? '');
    return res.valido ? null : (res.error ?? 'Código postal inválido.');
  }

  static String _limpiarTextoRenapo(String raw) {
    const map = {
      'Á': 'A', 'É': 'E', 'Í': 'I', 'Ó': 'O', 'Ú': 'U', 'Ü': 'U', 'Ñ': 'X',
    };
    final upper = raw.toUpperCase();
    final sb = StringBuffer();
    for (final rune in upper.runes) {
      final ch = String.fromCharCode(rune);
      final m = map[ch] ?? ch;
      if (RegExp(r'[A-Z\s]').hasMatch(m)) {
        sb.write(m);
      } else {
        sb.write(' ');
      }
    }
    return sb.toString().trim();
  }

  static String _primeraVocalInterna(String palabra) {
    for (var i = 1; i < palabra.length; i++) {
      if ('AEIOU'.contains(palabra[i])) return palabra[i];
    }
    return 'X';
  }

  static String _primeraConsonanteInterna(String palabra) {
    for (var i = 1; i < palabra.length; i++) {
      final c = palabra[i];
      if (!'AEIOU'.contains(c) && RegExp(r'[A-Z]').hasMatch(c)) return c;
    }
    return 'X';
  }

  static String _calcularDigitoCurp(String curp17) {
    const dic = '0123456789ABCDEFGHIJKLMNÑOPQRSTUVWXYZ';
    var suma = 0;
    for (var i = 0; i < 17; i++) {
      final idx = dic.indexOf(curp17[i]);
      suma += (idx >= 0 ? idx : 0) * (18 - i);
    }
    final res = 10 - (suma % 10);
    return (res == 10 ? 0 : res).toString();
  }

  static ({String curp, String rfc})? generarCurpYRfcSugeridos({
    required String nombre,
    String? segundoNombre,
    required String apellidoPaterno,
    String? apellidoMaterno,
    required String fechaNacimiento,
    String sexo = 'HOMBRE',
    String estadoCodigo = 'DF',
  }) {
    if (nombre.trim().isEmpty ||
        apellidoPaterno.trim().isEmpty ||
        !_fechaIsoPattern.hasMatch(fechaNacimiento.trim())) {
      return null;
    }
    final nombres = _limpiarTextoRenapo('$nombre ${segundoNombre ?? ''}')
        .split(RegExp(r'\s+'))
        .where((e) => e.isNotEmpty)
        .toList();
    var nombreBase = nombres.isNotEmpty ? nombres.first : 'X';
    if (nombres.length > 1 &&
        (nombreBase == 'JOSE' ||
            nombreBase == 'MARIA' ||
            nombreBase == 'MA' ||
            nombreBase == 'J')) {
      nombreBase = nombres[1];
    }

    final patTokens = _limpiarTextoRenapo(apellidoPaterno)
        .split(RegExp(r'\s+'))
        .where((e) => e.isNotEmpty)
        .toList();
    final matTokens = _limpiarTextoRenapo(apellidoMaterno ?? '')
        .split(RegExp(r'\s+'))
        .where((e) => e.isNotEmpty)
        .toList();
    final pat = patTokens.isNotEmpty ? patTokens.first : 'X';
    final mat = matTokens.isNotEmpty ? matTokens.first : 'X';

    var prefijo =
        '${pat[0]}${_primeraVocalInterna(pat)}${matTokens.isNotEmpty ? mat[0] : 'X'}${nombreBase[0]}';
    if (_palabrasInconvenientesCurp.contains(prefijo)) {
      prefijo = '${prefijo[0]}X${prefijo.substring(2)}';
    }

    final parts = fechaNacimiento.trim().split('-');
    final yyyy = parts[0];
    final fecha6 = '${yyyy.substring(2)}${parts[1]}${parts[2]}';
    final gen = sexo.toUpperCase().startsWith('M') ? 'M' : 'H';
    final ent = RegExp(r'^[A-Z]{2}$').hasMatch(estadoCodigo.toUpperCase())
        ? estadoCodigo.toUpperCase()
        : 'DF';

    final cons =
        '${_primeraConsonanteInterna(pat)}${matTokens.isNotEmpty ? _primeraConsonanteInterna(mat) : 'X'}${_primeraConsonanteInterna(nombreBase)}';
    final siglo = (int.tryParse(yyyy) ?? 1995) >= 2000 ? 'A' : '0';
    final curp17 = '$prefijo$fecha6$gen$ent$cons$siglo';
    final curp = '$curp17${_calcularDigitoCurp(curp17)}';

    final seed = '$pat|$mat|$nombreBase|$fechaNacimiento';
    var hash = 0;
    for (final code in seed.codeUnits) {
      hash = ((hash * 31) + code) & 0x7fffffff;
    }
    const alfa = 'ABCDEFGHJKLMNPQRSTUVWXYZ123456789';
    final h1 = alfa[hash % alfa.length];
    final h2 = alfa[(hash ~/ 37) % alfa.length];
    final h3 = ((hash % 9) + 1).toString();
    final rfc = '$prefijo$fecha6$h1$h2$h3';

    return (curp: curp, rfc: rfc);
  }

  static bool esItemHistorialRecarga({
    required String telefono,
    required String referencia,
    String? servicio,
    String? producto,
  }) {
    final tel = telefono.trim();
    final ref = referencia.trim();
    if (tel.isEmpty) return false;
    if (ref.isNotEmpty && ref == tel) return true;
    if (ref.isEmpty && tel != telefonoPruebaGestoPago) return true;
    if (ref.isEmpty && tel == telefonoPruebaGestoPago) {
      final combined = '${servicio ?? ''} ${producto ?? ''}'.toLowerCase();
      return RegExp(
        r'recarga|tiempo aire|paquete|telcel|at-t|at&t|att|movistar|unefon|bait|virgin|pillofon',
      ).hasMatch(combined);
    }
    return false;
  }
}
