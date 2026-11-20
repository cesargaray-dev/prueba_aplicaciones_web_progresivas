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
  final String numeroCuenta;
  final double saldo;
  final String? ultimoAcceso;

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
    required this.numeroCuenta,
    required this.saldo,
    this.ultimoAcceso,
  });

  factory UserSession.fromJson(Map<String, dynamic> json, {String? existingToken}) {
    return UserSession(
      token: (json['token'] ?? existingToken ?? '').toString(),
      usuario: (json['usuario'] ?? '').toString(),
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
      numeroCuenta: (json['numeroCuenta'] ?? '').toString(),
      saldo: (json['saldo'] is num)
          ? (json['saldo'] as num).toDouble()
          : double.tryParse(json['saldo']?.toString() ?? '0') ?? 0.0,
      ultimoAcceso: json['ultimoAcceso']?.toString(),
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
      'numeroCuenta': numeroCuenta,
      'saldo': saldo,
      'ultimoAcceso': ultimoAcceso,
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
    String? numeroCuenta,
    double? saldo,
    String? ultimoAcceso,
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
      numeroCuenta: numeroCuenta ?? this.numeroCuenta,
      saldo: saldo ?? this.saldo,
      ultimoAcceso: ultimoAcceso ?? this.ultimoAcceso,
    );
  }

  String get maskedAccount {
    if (numeroCuenta.length <= 4) return numeroCuenta;
    return '**** **** **** ${numeroCuenta.substring(numeroCuenta.length - 4)}';
  }
}
