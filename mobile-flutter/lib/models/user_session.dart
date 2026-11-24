class UserSession {
  final String token;
  final String usuario;
  final int clienteId;
  final String? nombre;
  final String? segundoNombre;
  final String? apellidoPaterno;
  final String? apellidoMaterno;
  final String nombreCompleto;
  final String email;
  final String? telefonoMovil;
  final String? telefonoAlternativo;
  final String? curp;
  final String? rfc;
  final String? fechaNacimiento;
  final String? sexo;
  final String? nacionalidad;
  final String? estadoCivil;
  final String? ocupacion;
  final String? empresa;
  final double? ingresoMensual;
  final String? calle;
  final String? numeroExterior;
  final String? numeroInterior;
  final String? colonia;
  final String? municipio;
  final String? estado;
  final String? codigoPostal;
  final String? pais;
  final String numeroCuenta;
  final double saldo;
  final String? ultimoAcceso;
  final bool esAdmin;
  final String? rol;
  final bool sesionPreviaCerrada;
  final String? avisoSesion;

  UserSession({
    required this.token,
    required this.usuario,
    required this.clienteId,
    this.nombre,
    this.segundoNombre,
    this.apellidoPaterno,
    this.apellidoMaterno,
    required this.nombreCompleto,
    required this.email,
    this.telefonoMovil,
    this.telefonoAlternativo,
    this.curp,
    this.rfc,
    this.fechaNacimiento,
    this.sexo,
    this.nacionalidad,
    this.estadoCivil,
    this.ocupacion,
    this.empresa,
    this.ingresoMensual,
    this.calle,
    this.numeroExterior,
    this.numeroInterior,
    this.colonia,
    this.municipio,
    this.estado,
    this.codigoPostal,
    this.pais,
    required this.numeroCuenta,
    required this.saldo,
    this.ultimoAcceso,
    this.esAdmin = false,
    this.rol,
    this.sesionPreviaCerrada = false,
    this.avisoSesion,
  });

  bool get isAdmin => esAdmin || usuario.trim().toLowerCase() == 'admin' || rol == 'ADMIN';

  factory UserSession.fromJson(Map<String, dynamic> json, {String? existingToken}) {
    final usr = (json['usuario'] ?? '').toString();
    final adminFlag = json['esAdmin'] == true || usr.trim().toLowerCase() == 'admin';
    return UserSession(
      token: (json['token'] ?? existingToken ?? '').toString(),
      usuario: usr,
      clienteId: json['clienteId'] is int
          ? json['clienteId']
          : int.tryParse(json['clienteId']?.toString() ?? '0') ?? 0,
      nombre: json['nombre']?.toString(),
      segundoNombre: json['segundoNombre']?.toString(),
      apellidoPaterno: json['apellidoPaterno']?.toString(),
      apellidoMaterno: json['apellidoMaterno']?.toString(),
      nombreCompleto: (json['nombreCompleto'] ?? '').toString(),
      email: (json['email'] ?? '').toString(),
      telefonoMovil: json['telefonoMovil']?.toString(),
      telefonoAlternativo: json['telefonoAlternativo']?.toString(),
      curp: json['curp']?.toString(),
      rfc: json['rfc']?.toString(),
      fechaNacimiento: json['fechaNacimiento']?.toString(),
      sexo: json['sexo']?.toString(),
      nacionalidad: json['nacionalidad']?.toString(),
      estadoCivil: json['estadoCivil']?.toString(),
      ocupacion: json['ocupacion']?.toString(),
      empresa: json['empresa']?.toString(),
      ingresoMensual: json['ingresoMensual'] is num
          ? (json['ingresoMensual'] as num).toDouble()
          : double.tryParse(json['ingresoMensual']?.toString() ?? ''),
      calle: json['calle']?.toString(),
      numeroExterior: json['numeroExterior']?.toString(),
      numeroInterior: json['numeroInterior']?.toString(),
      colonia: json['colonia']?.toString(),
      municipio: json['municipio']?.toString(),
      estado: json['estado']?.toString(),
      codigoPostal: json['codigoPostal']?.toString(),
      pais: json['pais']?.toString(),
      numeroCuenta: (json['numeroCuenta'] ?? '').toString(),
      saldo: (json['saldo'] is num)
          ? (json['saldo'] as num).toDouble()
          : double.tryParse(json['saldo']?.toString() ?? '0') ?? 0.0,
      ultimoAcceso: json['ultimoAcceso']?.toString(),
      esAdmin: adminFlag,
      rol: json['rol']?.toString() ?? (adminFlag ? 'ADMIN' : 'CLIENTE'),
      sesionPreviaCerrada: json['sesionPreviaCerrada'] == true,
      avisoSesion: json['avisoSesion']?.toString(),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'token': token,
      'usuario': usuario,
      'clienteId': clienteId,
      'nombre': nombre,
      'segundoNombre': segundoNombre,
      'apellidoPaterno': apellidoPaterno,
      'apellidoMaterno': apellidoMaterno,
      'nombreCompleto': nombreCompleto,
      'email': email,
      'telefonoMovil': telefonoMovil,
      'telefonoAlternativo': telefonoAlternativo,
      'curp': curp,
      'rfc': rfc,
      'fechaNacimiento': fechaNacimiento,
      'sexo': sexo,
      'nacionalidad': nacionalidad,
      'estadoCivil': estadoCivil,
      'ocupacion': ocupacion,
      'empresa': empresa,
      'ingresoMensual': ingresoMensual,
      'calle': calle,
      'numeroExterior': numeroExterior,
      'numeroInterior': numeroInterior,
      'colonia': colonia,
      'municipio': municipio,
      'estado': estado,
      'codigoPostal': codigoPostal,
      'pais': pais,
      'numeroCuenta': numeroCuenta,
      'saldo': saldo,
      'ultimoAcceso': ultimoAcceso,
      'esAdmin': esAdmin,
      'rol': rol,
      'sesionPreviaCerrada': sesionPreviaCerrada,
      'avisoSesion': avisoSesion,
    };
  }

  UserSession copyWith({
    String? token,
    String? usuario,
    int? clienteId,
    String? nombre,
    String? segundoNombre,
    String? apellidoPaterno,
    String? apellidoMaterno,
    String? nombreCompleto,
    String? email,
    String? telefonoMovil,
    String? telefonoAlternativo,
    String? curp,
    String? rfc,
    String? fechaNacimiento,
    String? sexo,
    String? nacionalidad,
    String? estadoCivil,
    String? ocupacion,
    String? empresa,
    double? ingresoMensual,
    String? calle,
    String? numeroExterior,
    String? numeroInterior,
    String? colonia,
    String? municipio,
    String? estado,
    String? codigoPostal,
    String? pais,
    String? numeroCuenta,
    double? saldo,
    String? ultimoAcceso,
    bool? esAdmin,
    String? rol,
    bool? sesionPreviaCerrada,
    String? avisoSesion,
  }) {
    return UserSession(
      token: token ?? this.token,
      usuario: usuario ?? this.usuario,
      clienteId: clienteId ?? this.clienteId,
      nombre: nombre ?? this.nombre,
      segundoNombre: segundoNombre ?? this.segundoNombre,
      apellidoPaterno: apellidoPaterno ?? this.apellidoPaterno,
      apellidoMaterno: apellidoMaterno ?? this.apellidoMaterno,
      nombreCompleto: nombreCompleto ?? this.nombreCompleto,
      email: email ?? this.email,
      telefonoMovil: telefonoMovil ?? this.telefonoMovil,
      telefonoAlternativo: telefonoAlternativo ?? this.telefonoAlternativo,
      curp: curp ?? this.curp,
      rfc: rfc ?? this.rfc,
      fechaNacimiento: fechaNacimiento ?? this.fechaNacimiento,
      sexo: sexo ?? this.sexo,
      nacionalidad: nacionalidad ?? this.nacionalidad,
      estadoCivil: estadoCivil ?? this.estadoCivil,
      ocupacion: ocupacion ?? this.ocupacion,
      empresa: empresa ?? this.empresa,
      ingresoMensual: ingresoMensual ?? this.ingresoMensual,
      calle: calle ?? this.calle,
      numeroExterior: numeroExterior ?? this.numeroExterior,
      numeroInterior: numeroInterior ?? this.numeroInterior,
      colonia: colonia ?? this.colonia,
      municipio: municipio ?? this.municipio,
      estado: estado ?? this.estado,
      codigoPostal: codigoPostal ?? this.codigoPostal,
      pais: pais ?? this.pais,
      numeroCuenta: numeroCuenta ?? this.numeroCuenta,
      saldo: saldo ?? this.saldo,
      ultimoAcceso: ultimoAcceso ?? this.ultimoAcceso,
      esAdmin: esAdmin ?? this.esAdmin,
      rol: rol ?? this.rol,
      sesionPreviaCerrada: sesionPreviaCerrada ?? this.sesionPreviaCerrada,
      avisoSesion: avisoSesion ?? this.avisoSesion,
    );
  }

  String get maskedAccount {
    if (numeroCuenta.length <= 4) return numeroCuenta;
    return '**** **** **** ${numeroCuenta.substring(numeroCuenta.length - 4)}';
  }
}
