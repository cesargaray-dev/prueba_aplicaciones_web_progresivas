class ProfesionItem {
  final String clave;
  final String label;
  final String sector;
  final String? empresaSugerida;

  const ProfesionItem({
    required this.clave,
    required this.label,
    required this.sector,
    this.empresaSugerida,
  });
}

class TabuladorSueldoItem {
  final String clave;
  final String label;
  final String rangoCorto;
  final double montoRepresentativo;

  const TabuladorSueldoItem({
    required this.clave,
    required this.label,
    required this.rangoCorto,
    required this.montoRepresentativo,
  });
}

class EstadoSepomexItem {
  final String codigoRenapo;
  final String nombre;
  final int cpMin;
  final int cpMax;
  final List<String> municipios;
  final List<String> coloniasBase;

  const EstadoSepomexItem({
    required this.codigoRenapo,
    required this.nombre,
    required this.cpMin,
    required this.cpMax,
    required this.municipios,
    required this.coloniasBase,
  });
}

class ResolucionCodigoPostal {
  final bool valido;
  final String? error;
  final String? estadoNombre;
  final String? codigoRenapo;
  final String? municipioSugerido;
  final List<String> municipiosEstado;
  final List<String> coloniasSugeridas;

  const ResolucionCodigoPostal({
    required this.valido,
    this.error,
    this.estadoNombre,
    this.codigoRenapo,
    this.municipioSugerido,
    this.municipiosEstado = const [],
    this.coloniasSugeridas = const [],
  });
}

class MexicoCatalogs {
  MexicoCatalogs._();

  static const List<ProfesionItem> tabuladorProfesiones = [
    ProfesionItem(clave: 'ING_SOFTWARE_TI', label: 'Ingeniero en Sistemas / Software / TI', sector: 'Tecnología e Ingeniería'),
    ProfesionItem(clave: 'ING_INDUSTRIAL_CIVIL', label: 'Ingeniero Industrial / Civil / Mecánico / Eléctrico', sector: 'Tecnología e Ingeniería'),
    ProfesionItem(clave: 'ARQUITECTO_DISENO', label: 'Arquitecto / Urbanista / Diseñador', sector: 'Tecnología e Ingeniería'),
    ProfesionItem(clave: 'MEDICO_SALUD', label: 'Médico / Cirujano / Odontólogo / Salud', sector: 'Salud y Ciencias'),
    ProfesionItem(clave: 'ENFERMERIA_CLINICA', label: 'Enfermería / Psicología / Nutrición', sector: 'Salud y Ciencias'),
    ProfesionItem(clave: 'DOCENTE_INVESTIGADOR', label: 'Docente / Catedrático / Investigador', sector: 'Educación y Ciencia'),
    ProfesionItem(clave: 'CONTADOR_FINANZAS', label: 'Contador Público / Auditor / Finanzas', sector: 'Económico y Financiero'),
    ProfesionItem(clave: 'ABOGADO_JURIDICO', label: 'Abogado / Notario / Consultor Jurídico', sector: 'Económico y Financiero'),
    ProfesionItem(clave: 'ADMIN_EJECUTIVO', label: 'Administrador de Empresas / Ejecutivo', sector: 'Económico y Financiero'),
    ProfesionItem(clave: 'EMPRESARIO_PYME', label: 'Empresario / Socio / Director PyME', sector: 'Sector Empresarial'),
    ProfesionItem(clave: 'COMERCIANTE_VENTAS', label: 'Comerciante Establecido / Ejecutivo Comercial', sector: 'Sector Empresarial'),
    ProfesionItem(clave: 'PROFESIONISTA_INDEP', label: 'Profesionista Independiente / Freelance', sector: 'Sector Empresarial', empresaSugerida: 'INDEPENDIENTE'),
    ProfesionItem(clave: 'EMPLEADO_PRIVADO', label: 'Empleado Administrativo / Sector Privado', sector: 'Sector Empresarial'),
    ProfesionItem(clave: 'TECNICO_ESPECIALIZADO', label: 'Técnico Especializado / Operativo', sector: 'Sector Empresarial'),
    ProfesionItem(clave: 'SERVIDOR_PUBLICO', label: 'Servidor Público / Gobierno', sector: 'Sector Público'),
    ProfesionItem(clave: 'ESTUDIANTE_UNIV', label: 'Estudiante Universitario / Becario', sector: 'Otros Perfiles CNBV', empresaSugerida: 'INSTITUCIÓN EDUCATIVA'),
    ProfesionItem(clave: 'JUBILADO_PENSIONADO', label: 'Jubilado / Pensionado (IMSS / ISSSTE)', sector: 'Otros Perfiles CNBV', empresaSugerida: 'PENSIONADO'),
    ProfesionItem(clave: 'LABORES_HOGAR', label: 'Labores del Hogar', sector: 'Otros Perfiles CNBV', empresaSugerida: 'HOGAR'),
  ];

  static const List<TabuladorSueldoItem> tabuladorSueldos = [
    TabuladorSueldoItem(clave: 'NIVEL_1', label: 'Hasta \$8,500 MXN / mes (Inicial / Becario)', rangoCorto: 'Hasta \$8,500', montoRepresentativo: 8500),
    TabuladorSueldoItem(clave: 'NIVEL_2', label: '\$8,501 a \$15,000 MXN / mes (Operativo / Técnico)', rangoCorto: '\$8,501 – \$15,000', montoRepresentativo: 12000),
    TabuladorSueldoItem(clave: 'NIVEL_3', label: '\$15,001 a \$25,000 MXN / mes (Profesionista Jr.)', rangoCorto: '\$15,001 – \$25,000', montoRepresentativo: 20000),
    TabuladorSueldoItem(clave: 'NIVEL_4', label: '\$25,001 a \$45,000 MXN / mes (Profesionista Sr.)', rangoCorto: '\$25,001 – \$45,000', montoRepresentativo: 35000),
    TabuladorSueldoItem(clave: 'NIVEL_5', label: '\$45,001 a \$80,000 MXN / mes (Gerencia / PyME)', rangoCorto: '\$45,001 – \$80,000', montoRepresentativo: 60000),
    TabuladorSueldoItem(clave: 'NIVEL_6', label: '\$80,001 a \$150,000 MXN / mes (Dirección)', rangoCorto: '\$80,001 – \$150,000', montoRepresentativo: 110000),
    TabuladorSueldoItem(clave: 'NIVEL_7', label: 'Más de \$150,000 MXN / mes (Alta Dirección)', rangoCorto: 'Más de \$150,000', montoRepresentativo: 180000),
  ];

  static const List<MapEntry<String, String>> tiposVialidad = [
    MapEntry('Calle', 'Calle'),
    MapEntry('Av.', 'Avenida (Av.)'),
    MapEntry('Blvd.', 'Boulevard (Blvd.)'),
    MapEntry('Calz.', 'Calzada (Calz.)'),
    MapEntry('Priv.', 'Privada (Priv.)'),
    MapEntry('Cerr.', 'Cerrada (Cerr.)'),
    MapEntry('Prol.', 'Prolongación (Prol.)'),
    MapEntry('Cto.', 'Circuito (Cto.)'),
    MapEntry('Andador', 'Andador'),
    MapEntry('Carr.', 'Carretera (Carr.)'),
  ];

  static const List<EstadoSepomexItem> catalogoEstados = [
    EstadoSepomexItem(
      codigoRenapo: 'DF',
      nombre: 'Ciudad de México',
      cpMin: 1000,
      cpMax: 16999,
      municipios: [
        'Álvaro Obregón', 'Azcapotzalco', 'Benito Juárez', 'Coyoacán', 'Cuajimalpa de Morelos',
        'Cuauhtémoc', 'Gustavo A. Madero', 'Iztacalco', 'Iztapalapa', 'La Magdalena Contreras',
        'Miguel Hidalgo', 'Milpa Alta', 'Tláhuac', 'Tlalpan', 'Venustiano Carranza', 'Xochimilco',
      ],
      coloniasBase: ['Centro', 'Juárez', 'Roma Norte', 'Roma Sur', 'Condesa', 'Del Valle Centro', 'Narvarte Poniente', 'Polanco', 'Coyoacán Centro', 'Lindavista'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'AS',
      nombre: 'Aguascalientes',
      cpMin: 20000,
      cpMax: 20999,
      municipios: ['Aguascalientes', 'Jesús María', 'Calvillo', 'Rincón de Romos', 'Pabellón de Arteaga', 'San Francisco de los Romo', 'Asientos'],
      coloniasBase: ['Zona Centro', 'Bosques del Prado', 'Jardines de la Asunción', 'Las Américas', 'Trojes de Alonso'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'BC',
      nombre: 'Baja California',
      cpMin: 21000,
      cpMax: 22999,
      municipios: ['Mexicali', 'Tijuana', 'Ensenada', 'Tecate', 'Playas de Rosarito', 'San Quintín', 'San Felipe'],
      coloniasBase: ['Zona Urbana Río Tijuana', 'Centro', 'Chapultepec', 'Otay Constituyentes', 'Nueva Mexicali'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'BS',
      nombre: 'Baja California Sur',
      cpMin: 23000,
      cpMax: 23999,
      municipios: ['La Paz', 'Los Cabos', 'Comondú', 'Loreto', 'Mulegé'],
      coloniasBase: ['Zona Central', 'El Médano', 'Cabo San Lucas Centro', 'San José del Cabo Centro', 'Fidepaz'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'CC',
      nombre: 'Campeche',
      cpMin: 24000,
      cpMax: 24999,
      municipios: ['Campeche', 'Carmen', 'Champotón', 'Escárcega', 'Calkiní', 'Hecelchakán', 'Hopelchén', 'Candelaria'],
      coloniasBase: ['Zona Centro', 'San Román', 'Barrio de Guadalupe', 'Ciudad del Carmen Centro', 'Playa Norte'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'CL',
      nombre: 'Coahuila',
      cpMin: 25000,
      cpMax: 27999,
      municipios: ['Saltillo', 'Torreón', 'Monclova', 'Piedras Negras', 'Acuña', 'Ramos Arizpe', 'Sabinas', 'Matamoros', 'Parras'],
      coloniasBase: ['Zona Centro', 'República Oriente', 'Jardines del Lago', 'Nuevo Torreón', 'Campestre La Rosita'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'CM',
      nombre: 'Colima',
      cpMin: 28000,
      cpMax: 28999,
      municipios: ['Colima', 'Manzanillo', 'Villa de Álvarez', 'Tecomán', 'Cuauhtémoc', 'Armería', 'Comala'],
      coloniasBase: ['Centro', 'Lomas de Circunvalación', 'Jardines Vista Hermosa', 'Salahua', 'Real Centenario'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'CS',
      nombre: 'Chiapas',
      cpMin: 29000,
      cpMax: 30999,
      municipios: ['Tuxtla Gutiérrez', 'Tapachula', 'San Cristóbal de las Casas', 'Comitán de Domínguez', 'Chiapa de Corzo', 'Palenque', 'Ocosingo'],
      coloniasBase: ['Centro', 'Terán', 'Moctezuma', 'Las Palmas', 'Barrio de Mexicanos'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'CH',
      nombre: 'Chihuahua',
      cpMin: 31000,
      cpMax: 33999,
      municipios: ['Chihuahua', 'Juárez', 'Cuauhtémoc', 'Delicias', 'Hidalgo del Parral', 'Nuevo Casas Grandes', 'Camargo'],
      coloniasBase: ['Zona Centro', 'San Felipe', 'Panamericana', 'Campestre', 'Partido Romero'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'DG',
      nombre: 'Durango',
      cpMin: 34000,
      cpMax: 35999,
      municipios: ['Durango', 'Gómez Palacio', 'Lerdo', 'Santiago Papasquiaro', 'Guadalupe Victoria', 'Pueblo Nuevo'],
      coloniasBase: ['Zona Centro', 'Lomas del Parque', 'Jardines de Durango', 'Las Rosas', 'Bellavista'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'GT',
      nombre: 'Guanajuato',
      cpMin: 36000,
      cpMax: 38999,
      municipios: ['León', 'Irapuato', 'Celaya', 'Salamanca', 'Guanajuato', 'San Miguel de Allende', 'Silao de la Victoria', 'Dolores Hidalgo'],
      coloniasBase: ['Centro', 'Jardines del Moral', 'León Moderno', 'Las Reynas', 'Alameda'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'GR',
      nombre: 'Guerrero',
      cpMin: 39000,
      cpMax: 41999,
      municipios: ['Acapulco de Juárez', 'Chilpancingo de los Bravo', 'Iguala de la Independencia', 'Zihuatanejo de Azueta', 'Taxco de Alarcón', 'Tlapa de Comonfort'],
      coloniasBase: ['Centro', 'Costa Azul', 'Magallanes', 'Las Playas', 'Renacimiento'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'HG',
      nombre: 'Hidalgo',
      cpMin: 42000,
      cpMax: 43999,
      municipios: ['Pachuca de Soto', 'Mineral de la Reforma', 'Tulancingo de Bravo', 'Tizayuca', 'Tula de Allende', 'Huejutla de Reyes', 'Ixmiquilpan'],
      coloniasBase: ['Centro', 'Zona Plateada', 'Periodistas', 'Valle de San Javier', 'Juan C. Doria'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'JC',
      nombre: 'Jalisco',
      cpMin: 44000,
      cpMax: 49999,
      municipios: ['Guadalajara', 'Zapopan', 'San Pedro Tlaquepaque', 'Tlajomulco de Zúñiga', 'Tonalá', 'Puerto Vallarta', 'Lagos de Moreno', 'Tepatitlán de Morelos'],
      coloniasBase: ['Centro', 'Americana', 'Providencia', 'Chapalita', 'Puerta de Hierro', 'Zapopan Centro'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'MC',
      nombre: 'Estado de México',
      cpMin: 50000,
      cpMax: 57999,
      municipios: ['Toluca', 'Ecatepec de Morelos', 'Nezahualcóyotl', 'Naucalpan de Juárez', 'Tlalnepantla de Baz', 'Metepec', 'Atizapán de Zaragoza', 'Cuautitlán Izcalli', 'Huixquilucan', 'Texcoco'],
      coloniasBase: ['Centro', 'Ciudad Satélite', 'Lomas Verdes', 'Las Américas', 'Benito Juárez', 'Interlomas'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'MN',
      nombre: 'Michoacán',
      cpMin: 58000,
      cpMax: 61999,
      municipios: ['Morelia', 'Uruapan', 'Zamora', 'Lázaro Cárdenas', 'Zitácuaro', 'Apatzingán', 'Pátzcuaro', 'La Piedad'],
      coloniasBase: ['Centro Histórico', 'Chapultepec Norte', 'Las Américas', 'Camelinas', 'Vista Bella'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'MS',
      nombre: 'Morelos',
      cpMin: 62000,
      cpMax: 62999,
      municipios: [
        'Cuernavaca', 'Jiutepec', 'Cuautla', 'Temixco', 'Emiliano Zapata', 'Yautepec', 'Xochitepec',
        'Jojutla', 'Zacatepec', 'Puente de Ixtla', 'Tepoztlán', 'Tlaltizapán de Zapata', 'Ayala', 'Tlayacapan',
      ],
      coloniasBase: ['Centro', 'Vista Hermosa', 'Reforma', 'Lomas de Cortés', 'Chapultepec', 'Las Palmas', 'Teopanzolco', 'Civac', 'Chamilpa'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'NT',
      nombre: 'Nayarit',
      cpMin: 63000,
      cpMax: 63999,
      municipios: ['Tepic', 'Bahía de Banderas', 'Santiago Ixcuintla', 'Compostela', 'Tuxpan', 'Xalisco'],
      coloniasBase: ['Centro', 'Ciudad del Valle', 'San Juan', 'Nuevo Vallarta', 'Bucerías'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'NL',
      nombre: 'Nuevo León',
      cpMin: 64000,
      cpMax: 67999,
      municipios: ['Monterrey', 'San Pedro Garza García', 'Guadalupe', 'Apodaca', 'San Nicolás de los Garza', 'General Escobedo', 'Santa Catarina', 'García', 'Santiago'],
      coloniasBase: ['Centro', 'Del Valle', 'Obispado', 'Cumbres', 'Tecnológico', 'Contry', 'Mitras Centro'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'OC',
      nombre: 'Oaxaca',
      cpMin: 68000,
      cpMax: 71999,
      municipios: ['Oaxaca de Juárez', 'San Juan Bautista Tuxtepec', 'Salina Cruz', 'Juchitán de Zaragoza', 'Santa Cruz Xoxocotlán', 'Huajuapan de León', 'Santa María Huatulco'],
      coloniasBase: ['Centro', 'Reforma', 'Xochimilco', 'San Felipe del Agua', 'La Crucecita'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'PL',
      nombre: 'Puebla',
      cpMin: 72000,
      cpMax: 75999,
      municipios: ['Puebla', 'Tehuacán', 'San Martín Texmelucan', 'San Andrés Cholula', 'San Pedro Cholula', 'Atlixco', 'Cuautlancingo', 'Teziutlán'],
      coloniasBase: ['Centro Histórico', 'La Paz', 'Angelópolis', 'Anzures', 'Zavaleta', 'Lomas de Angelópolis'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'QT',
      nombre: 'Querétaro',
      cpMin: 76000,
      cpMax: 76999,
      municipios: ['Querétaro', 'San Juan del Río', 'Corregidora', 'El Marqués', 'Tequisquiapan', 'Pedro Escobedo', 'Ezequiel Montes'],
      coloniasBase: ['Centro Histórico', 'Juriquilla', 'Milenio III', 'Carretas', 'Zibatá', 'El Pueblito'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'QR',
      nombre: 'Quintana Roo',
      cpMin: 77000,
      cpMax: 77999,
      municipios: ['Benito Juárez', 'Solidaridad', 'Othón P. Blanco', 'Cozumel', 'Tulum', 'Puerto Morelos', 'Isla Mujeres', 'Bacalar'],
      coloniasBase: ['Supermanzana 1 Centro', 'Zona Hotelera', 'Puerto Cancún', 'Playa del Carmen Centro', 'Chetumal Centro'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'SP',
      nombre: 'San Luis Potosí',
      cpMin: 78000,
      cpMax: 79999,
      municipios: ['San Luis Potosí', 'Soledad de Graciano Sánchez', 'Ciudad Valles', 'Matehuala', 'Rioverde', 'Tamazunchale'],
      coloniasBase: ['Centro Histórico', 'Lomas de San Luis', 'Tequisquiapan', 'Del Valle', 'Tangamanga'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'SL',
      nombre: 'Sinaloa',
      cpMin: 80000,
      cpMax: 82999,
      municipios: ['Culiacán', 'Mazatlán', 'Ahome', 'Guasave', 'Navolato', 'Salvador Alvarado'],
      coloniasBase: ['Centro', 'Tres Ríos', 'Las Quintas', 'Chapultepec', 'Zona Dorada'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'SR',
      nombre: 'Sonora',
      cpMin: 83000,
      cpMax: 85999,
      municipios: ['Hermosillo', 'Cajeme', 'Nogales', 'San Luis Río Colorado', 'Navojoa', 'Guaymas', 'Puerto Peñasco'],
      coloniasBase: ['Centro', 'Pitic', 'Centenario', 'Villa Satélite', 'Ciudad Obregón Centro'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'TC',
      nombre: 'Tabasco',
      cpMin: 86000,
      cpMax: 86999,
      municipios: ['Centro', 'Cárdenas', 'Comalcalco', 'Macuspana', 'Huimanguillo', 'Cunduacán', 'Paraíso'],
      coloniasBase: ['Villahermosa Centro', 'Tabasco 2000', 'Primero de Mayo', 'Atasta de Serra', 'Tamulté'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'TS',
      nombre: 'Tamaulipas',
      cpMin: 87000,
      cpMax: 89999,
      municipios: ['Reynosa', 'Matamoros', 'Nuevo Laredo', 'Victoria', 'Tampico', 'Altamira', 'Ciudad Madero'],
      coloniasBase: ['Zona Centro', 'Las Fuentes', 'Petrolera', 'Lomas de Rosales', 'Jardín'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'TL',
      nombre: 'Tlaxcala',
      cpMin: 90000,
      cpMax: 90999,
      municipios: ['Tlaxcala', 'Apizaco', 'Chiautempan', 'Huamantla', 'Zacatelco', 'Calpulalpan'],
      coloniasBase: ['Centro', 'Ocotlán', 'La Loma Xicohténcatl', 'Fátima', 'Santa Ana Centro'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'VZ',
      nombre: 'Veracruz',
      cpMin: 91000,
      cpMax: 96999,
      municipios: ['Veracruz', 'Xalapa', 'Coatzacoalcos', 'Córdoba', 'Poza Rica de Hidalgo', 'Boca del Río', 'Orizaba', 'Minatitlán'],
      coloniasBase: ['Centro', 'Reforma', 'Costa de Oro', 'Ánimas', 'Mocambo'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'YN',
      nombre: 'Yucatán',
      cpMin: 97000,
      cpMax: 97999,
      municipios: ['Mérida', 'Kanasín', 'Valladolid', 'Tizimín', 'Progreso', 'Umán', 'Ticul'],
      coloniasBase: ['Centro', 'Montejo', 'Altabrisa', 'Itzimná', 'Campestre', 'Francisco de Montejo'],
    ),
    EstadoSepomexItem(
      codigoRenapo: 'ZS',
      nombre: 'Zacatecas',
      cpMin: 98000,
      cpMax: 99998,
      municipios: ['Zacatecas', 'Guadalupe', 'Fresnillo', 'Jerez', 'Río Grande', 'Sombrerete'],
      coloniasBase: ['Centro Histórico', 'Sierra de Álica', 'Colinas del Padre', 'Bernárdez', 'Gavilanes'],
    ),
  ];

  static EstadoSepomexItem? obtenerEstadoPorNombreOCodigo(String valor) {
    final clean = valor.trim().toLowerCase();
    if (clean.isEmpty) return null;
    for (final e in catalogoEstados) {
      if (e.codigoRenapo.toLowerCase() == clean || e.nombre.toLowerCase() == clean) {
        return e;
      }
    }
    return null;
  }

  static List<String> obtenerMunicipiosPorEstado(String estadoNombreOCodigo) {
    return obtenerEstadoPorNombreOCodigo(estadoNombreOCodigo)?.municipios ?? const [];
  }

  static String _resolverMunicipioSubrango(int cpNum, EstadoSepomexItem est) {
    if (est.codigoRenapo == 'DF') {
      final idx = (cpNum ~/ 1000) - 1;
      if (idx >= 0 && idx < est.municipios.length) {
        return est.municipios[idx];
      }
    }
    if (est.codigoRenapo == 'MS') {
      if (cpNum >= 62000 && cpNum <= 62499) return 'Cuernavaca';
      if (cpNum >= 62550 && cpNum <= 62579) return 'Jiutepec';
      if (cpNum >= 62580 && cpNum <= 62599) return 'Temixco';
      if (cpNum >= 62520 && cpNum <= 62529) return 'Tepoztlán';
      if (cpNum >= 62730 && cpNum <= 62739) return 'Yautepec';
      if (cpNum >= 62740 && cpNum <= 62759) return 'Cuautla';
      if (cpNum >= 62760 && cpNum <= 62769) return 'Emiliano Zapata';
      if (cpNum >= 62790 && cpNum <= 62799) return 'Xochitepec';
      if (cpNum >= 62900 && cpNum <= 62919) return 'Jojutla';
    }
    if (est.codigoRenapo == 'JC') {
      if (cpNum >= 44100 && cpNum <= 44999) return 'Guadalajara';
      if (cpNum >= 45000 && cpNum <= 45299) return 'Zapopan';
      if (cpNum >= 45400 && cpNum <= 45429) return 'Tonalá';
      if (cpNum >= 45500 && cpNum <= 45639) return 'San Pedro Tlaquepaque';
      if (cpNum >= 45640 && cpNum <= 45679) return 'Tlajomulco de Zúñiga';
      if (cpNum >= 48300 && cpNum <= 48399) return 'Puerto Vallarta';
    }
    if (est.codigoRenapo == 'NL') {
      if (cpNum >= 64000 && cpNum <= 64999) return 'Monterrey';
      if (cpNum >= 66200 && cpNum <= 66299) return 'San Pedro Garza García';
      if (cpNum >= 66400 && cpNum <= 66499) return 'San Nicolás de los Garza';
      if (cpNum >= 66600 && cpNum <= 66699) return 'Apodaca';
      if (cpNum >= 67100 && cpNum <= 67199) return 'Guadalupe';
    }
    if (est.codigoRenapo == 'MC') {
      if (cpNum >= 50000 && cpNum <= 50299) return 'Toluca';
      if (cpNum >= 52140 && cpNum <= 52179) return 'Metepec';
      if (cpNum >= 52900 && cpNum <= 52999) return 'Atizapán de Zaragoza';
      if (cpNum >= 53000 && cpNum <= 53999) return 'Naucalpan de Juárez';
      if (cpNum >= 54000 && cpNum <= 54199) return 'Tlalnepantla de Baz';
      if (cpNum >= 55000 && cpNum <= 55599) return 'Ecatepec de Morelos';
      if (cpNum >= 57000 && cpNum <= 57999) return 'Nezahualcóyotl';
    }
    return est.municipios.first;
  }

  static ResolucionCodigoPostal resolverCodigoPostalMexicano(String cp) {
    final raw = cp.trim();
    if (raw.isEmpty) {
      return const ResolucionCodigoPostal(
        valido: false,
        error: 'El código postal es obligatorio (5 dígitos).',
      );
    }
    if (RegExp(r'[^\d]').hasMatch(raw)) {
      return const ResolucionCodigoPostal(
        valido: false,
        error: 'El código postal solo debe contener números.',
      );
    }
    if (raw.length != 5) {
      return ResolucionCodigoPostal(
        valido: false,
        error: 'El código postal debe tener exactamente 5 dígitos (llevas ${raw.length}).',
      );
    }

    final cpNum = int.tryParse(raw) ?? 0;
    for (final est in catalogoEstados) {
      if (cpNum >= est.cpMin && cpNum <= est.cpMax) {
        return ResolucionCodigoPostal(
          valido: true,
          estadoNombre: est.nombre,
          codigoRenapo: est.codigoRenapo,
          municipioSugerido: _resolverMunicipioSubrango(cpNum, est),
          municipiosEstado: est.municipios,
          coloniasSugeridas: est.coloniasBase,
        );
      }
    }

    return ResolucionCodigoPostal(
      valido: false,
      error: 'El código postal $raw no existe en el catálogo oficial SEPOMEX de México.',
    );
  }
}
