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

  static bool esNombreValido(String? raw, {bool optional = false, int maxLength = 50}) {
    return diagnosticarNombre(raw, 'El campo', optional: optional, maxLength: maxLength) == null;
  }

  static String? diagnosticarNombre(
    String? raw,
    String campo, {
    bool optional = false,
    int maxLength = 50,
  }) {
    final val = (raw ?? '').trim();
    if (val.isEmpty) {
      return optional ? null : '$campo es obligatorio.';
    }
    if (val.length < 2) {
      return '$campo debe contener al menos 2 caracteres.';
    }
    if (val.length > maxLength) {
      return '$campo no puede exceder $maxLength caracteres.';
    }
    if (!_nombreLetrasPattern.hasMatch(val)) {
      return '$campo solo puede contener letras y espacios.';
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
}
