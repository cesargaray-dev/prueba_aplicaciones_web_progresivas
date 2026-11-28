export interface ProfesionItem {
  clave: string;
  label: string;
  sector: string;
  empresaSugerida?: string;
}

export interface TabuladorSueldoItem {
  clave: string;
  label: string;
  rangoCorto: string;
  montoRepresentativo: number;
}

export interface EstadoSepomexItem {
  codigoRenapo: string;
  nombre: string;
  cpMin: number;
  cpMax: number;
  municipios: string[];
  coloniasBase: string[];
}

export interface ResolucionCodigoPostal {
  valido: boolean;
  error?: string;
  estadoNombre?: string;
  codigoRenapo?: string;
  municipioSugerido?: string;
  municipiosEstado: string[];
  coloniasSugeridas: string[];
}

export const TABULADOR_PROFESIONES: ProfesionItem[] = [
  { clave: 'ING_SOFTWARE_TI', label: 'Ingeniero en Sistemas / Desarrollo de Software / TI', sector: 'Tecnología e Ingeniería' },
  { clave: 'ING_INDUSTRIAL_CIVIL', label: 'Ingeniero Industrial / Civil / Mecánico / Eléctrico', sector: 'Tecnología e Ingeniería' },
  { clave: 'ARQUITECTO_DISENO', label: 'Arquitecto / Urbanista / Diseñador Especializado', sector: 'Tecnología e Ingeniería' },
  { clave: 'MEDICO_SALUD', label: 'Médico / Cirujano / Odontólogo / Especialista en Salud', sector: 'Salud y Ciencias' },
  { clave: 'ENFERMERIA_CLINICA', label: 'Enfermería / Psicología / Nutrición / Químico Clínico', sector: 'Salud y Ciencias' },
  { clave: 'DOCENTE_INVESTIGADOR', label: 'Docente / Catedrático / Investigador Académico', sector: 'Educación y Ciencia' },
  { clave: 'CONTADOR_FINANZAS', label: 'Contador Público / Auditor / Analista Financiero', sector: 'Económico y Financiero' },
  { clave: 'ABOGADO_JURIDICO', label: 'Abogado / Notario / Consultor Jurídico', sector: 'Económico y Financiero' },
  { clave: 'ADMIN_EJECUTIVO', label: 'Administrador de Empresas / Ejecutivo / Mercadotecnia', sector: 'Económico y Financiero' },
  { clave: 'EMPRESARIO_PYME', label: 'Empresario / Socio / Director de PyME', sector: 'Sector Empresarial y Comercio' },
  { clave: 'COMERCIANTE_VENTAS', label: 'Comerciante Establecido / Ejecutivo Comercial', sector: 'Sector Empresarial y Comercio' },
  { clave: 'PROFESIONISTA_INDEP', label: 'Profesionista Independiente / Consultor (Honorarios)', sector: 'Sector Empresarial y Comercio', empresaSugerida: 'INDEPENDIENTE' },
  { clave: 'EMPLEADO_PRIVADO', label: 'Empleado Administrativo / Operativo Sector Privado', sector: 'Sector Empresarial y Comercio' },
  { clave: 'TECNICO_ESPECIALIZADO', label: 'Técnico Especializado / Mantenimiento / Transporte', sector: 'Sector Empresarial y Comercio' },
  { clave: 'SERVIDOR_PUBLICO', label: 'Servidor Público / Funcionario de Gobierno', sector: 'Sector Público' },
  { clave: 'ESTUDIANTE_UNIV', label: 'Estudiante Universitario / Posgrado / Becario', sector: 'Otros Perfiles CNBV', empresaSugerida: 'INSTITUCIÓN EDUCATIVA' },
  { clave: 'JUBILADO_PENSIONADO', label: 'Jubilado / Pensionado (IMSS / ISSSTE / Privado)', sector: 'Otros Perfiles CNBV', empresaSugerida: 'PENSIONADO' },
  { clave: 'LABORES_HOGAR', label: 'Labores del Hogar / Administración Familiar', sector: 'Otros Perfiles CNBV', empresaSugerida: 'HOGAR' },
];

export const TABULADOR_SUELDOS: TabuladorSueldoItem[] = [
  {
    clave: 'NIVEL_1',
    label: 'Hasta $8,500 MXN / mes — Nivel Inicial / Becario / Estudiante',
    rangoCorto: 'Hasta $8,500 MXN',
    montoRepresentativo: 8500,
  },
  {
    clave: 'NIVEL_2',
    label: '$8,501 a $15,000 MXN / mes — Operativo / Técnico / Auxiliar',
    rangoCorto: '$8,501 – $15,000 MXN',
    montoRepresentativo: 12000,
  },
  {
    clave: 'NIVEL_3',
    label: '$15,001 a $25,000 MXN / mes — Profesionista Jr. / Ejecutivo',
    rangoCorto: '$15,001 – $25,000 MXN',
    montoRepresentativo: 20000,
  },
  {
    clave: 'NIVEL_4',
    label: '$25,001 a $45,000 MXN / mes — Profesionista Sr. / Especialista',
    rangoCorto: '$25,001 – $45,000 MXN',
    montoRepresentativo: 35000,
  },
  {
    clave: 'NIVEL_5',
    label: '$45,001 a $80,000 MXN / mes — Gerencia / Coordinación / PyME',
    rangoCorto: '$45,001 – $80,000 MXN',
    montoRepresentativo: 60000,
  },
  {
    clave: 'NIVEL_6',
    label: '$80,001 a $150,000 MXN / mes — Dirección / Corporativo',
    rangoCorto: '$80,001 – $150,000 MXN',
    montoRepresentativo: 110000,
  },
  {
    clave: 'NIVEL_7',
    label: 'Más de $150,000 MXN / mes — Alta Dirección / Empresarial',
    rangoCorto: 'Más de $150,000 MXN',
    montoRepresentativo: 180000,
  },
];

export const TIPOS_VIALIDAD: { clave: string; label: string; prefijo: string }[] = [
  { clave: 'CALLE', label: 'Calle', prefijo: 'Calle' },
  { clave: 'AVENIDA', label: 'Avenida (Av.)', prefijo: 'Av.' },
  { clave: 'BOULEVARD', label: 'Boulevard (Blvd.)', prefijo: 'Blvd.' },
  { clave: 'CALZADA', label: 'Calzada (Calz.)', prefijo: 'Calz.' },
  { clave: 'PRIVADA', label: 'Privada (Priv.)', prefijo: 'Priv.' },
  { clave: 'CERRADA', label: 'Cerrada (Cerr.)', prefijo: 'Cerr.' },
  { clave: 'PROLONGACION', label: 'Prolongación (Prol.)', prefijo: 'Prol.' },
  { clave: 'CIRCUITO', label: 'Circuito (Cto.)', prefijo: 'Cto.' },
  { clave: 'ANDADOR', label: 'Andador', prefijo: 'Andador' },
  { clave: 'CARRETERA', label: 'Carretera (Carr.)', prefijo: 'Carr.' },
  { clave: 'VIADUCTO', label: 'Viaducto', prefijo: 'Viaducto' },
  { clave: 'RETORNO', label: 'Retorno', prefijo: 'Retorno' },
];

export const CATALOGO_ESTADOS_SEPOMEX: EstadoSepomexItem[] = [
  {
    codigoRenapo: 'DF',
    nombre: 'Ciudad de México',
    cpMin: 1000,
    cpMax: 16999,
    municipios: [
      'Álvaro Obregón', 'Azcapotzalco', 'Benito Juárez', 'Coyoacán', 'Cuajimalpa de Morelos',
      'Cuauhtémoc', 'Gustavo A. Madero', 'Iztacalco', 'Iztapalapa', 'La Magdalena Contreras',
      'Miguel Hidalgo', 'Milpa Alta', 'Tláhuac', 'Tlalpan', 'Venustiano Carranza', 'Xochimilco',
    ],
    coloniasBase: ['Centro', 'Juárez', 'Roma Norte', 'Roma Sur', 'Condesa', 'Del Valle Centro', 'Narvarte Poniente', 'Polanco', 'Coyoacán Centro', 'Lindavista', 'Santa Fe', 'Doctores'],
  },
  {
    codigoRenapo: 'AS',
    nombre: 'Aguascalientes',
    cpMin: 20000,
    cpMax: 20999,
    municipios: ['Aguascalientes', 'Jesús María', 'Calvillo', 'Rincón de Romos', 'Pabellón de Arteaga', 'San Francisco de los Romo', 'Asientos', 'Cosío', 'El Llano', 'San José de Gracia', 'Tepezalá'],
    coloniasBase: ['Zona Centro', 'Bosques del Prado', 'Jardines de la Asunción', 'Las Américas', 'Trojes de Alonso', ' Morelos'],
  },
  {
    codigoRenapo: 'BC',
    nombre: 'Baja California',
    cpMin: 21000,
    cpMax: 22999,
    municipios: ['Mexicali', 'Tijuana', 'Ensenada', 'Tecate', 'Playas de Rosarito', 'San Quintín', 'San Felipe'],
    coloniasBase: ['Zona Urbana Río Tijuana', 'Centro', 'Chapultepec', 'Otay Constituyentes', 'Nueva Mexicali', 'Valle Dorado'],
  },
  {
    codigoRenapo: 'BS',
    nombre: 'Baja California Sur',
    cpMin: 23000,
    cpMax: 23999,
    municipios: ['La Paz', 'Los Cabos', 'Comondú', 'Loreto', 'Mulegé'],
    coloniasBase: ['Zona Central', 'El Médano', 'Cabo San Lucas Centro', 'San José del Cabo Centro', 'Fidepaz', 'Las Garzas'],
  },
  {
    codigoRenapo: 'CC',
    nombre: 'Campeche',
    cpMin: 24000,
    cpMax: 24999,
    municipios: ['Campeche', 'Carmen', 'Champotón', 'Escárcega', 'Calkiní', 'Hecelchakán', 'Hopelchén', 'Candelaria', 'Palizada', 'Calakmul', 'Tenabo', 'Seybaplaya', 'Dzitbalché'],
    coloniasBase: ['Zona Centro', 'San Román', 'Barrio de Guadalupe', 'Ah-Kim-Pech', 'Ciudad del Carmen Centro', 'Playa Norte'],
  },
  {
    codigoRenapo: 'CL',
    nombre: 'Coahuila',
    cpMin: 25000,
    cpMax: 27999,
    municipios: ['Saltillo', 'Torreón', 'Monclova', 'Piedras Negras', 'Acuña', 'Ramos Arizpe', 'Sabinas', 'Matamoros', 'San Pedro', 'Frontera', 'Parras', 'Arteaga'],
    coloniasBase: ['Zona Centro', 'República Oriente', 'Jardines del Lago', 'Nuevo Torreón', 'Campestre La Rosita', 'Tecnológico'],
  },
  {
    codigoRenapo: 'CM',
    nombre: 'Colima',
    cpMin: 28000,
    cpMax: 28999,
    municipios: ['Colima', 'Manzanillo', 'Villa de Álvarez', 'Tecomán', 'Cuauhtémoc', 'Armería', 'Comala', 'Coquimatlán', 'Ixtlahuacán', 'Minatitlán'],
    coloniasBase: ['Centro', 'Lomas de Circunvalación', 'Jardines Vista Hermosa', 'Salahua', ' Valle de las Garzas', 'Real Centenario'],
  },
  {
    codigoRenapo: 'CS',
    nombre: 'Chiapas',
    cpMin: 29000,
    cpMax: 30999,
    municipios: ['Tuxtla Gutiérrez', 'Tapachula', 'San Cristóbal de las Casas', 'Comitán de Domínguez', 'Chiapa de Corzo', 'Palenque', 'Ocosingo', 'Tonalá', 'Villaflores', 'Berriozábal'],
    coloniasBase: ['Centro', 'Terán', 'Moctezuma', 'Las Palmas', 'Barrio de Mexicanos', 'Laureles'],
  },
  {
    codigoRenapo: 'CH',
    nombre: 'Chihuahua',
    cpMin: 31000,
    cpMax: 33999,
    municipios: ['Chihuahua', 'Juárez', 'Cuauhtémoc', 'Delicias', 'Hidalgo del Parral', 'Nuevo Casas Grandes', 'Camargo', 'Jiménez', 'Meoqui', 'Ojinaga'],
    coloniasBase: ['Zona Centro', 'San Felipe', 'Panamericana', 'Campestre', 'Partido Romero', 'Las Quintas'],
  },
  {
    codigoRenapo: 'DG',
    nombre: 'Durango',
    cpMin: 34000,
    cpMax: 35999,
    municipios: ['Durango', 'Gómez Palacio', 'Lerdo', 'Santiago Papasquiaro', 'Guadalupe Victoria', 'Pueblo Nuevo', 'Canatlán', 'Cuencamé', 'Vicente Guerrero'],
    coloniasBase: ['Zona Centro', 'Lomas del Parque', 'Jardines de Durango', 'Las Rosas', 'Bellavista', 'Guadalupe'],
  },
  {
    codigoRenapo: 'GT',
    nombre: 'Guanajuato',
    cpMin: 36000,
    cpMax: 38999,
    municipios: ['León', 'Irapuato', 'Celaya', 'Salamanca', 'Guanajuato', 'San Miguel de Allende', 'Silao de la Victoria', 'Dolores Hidalgo', 'Pénjamo', 'San Francisco del Rincón', 'Valle de Santiago', 'Acámbaro'],
    coloniasBase: ['Centro', 'Jardines del Moral', ' León Moderno', 'Las Reynas', 'Alameda', 'Marfil'],
  },
  {
    codigoRenapo: 'GR',
    nombre: 'Guerrero',
    cpMin: 39000,
    cpMax: 41999,
    municipios: ['Acapulco de Juárez', 'Chilpancingo de los Bravo', 'Iguala de la Independencia', 'Zihuatanejo de Azueta', 'Taxco de Alarcón', 'Chilapa de Álvarez', 'Tlapa de Comonfort', 'Coyuca de Benítez', 'Ometepec'],
    coloniasBase: ['Centro', 'Costa Azul', 'Magallanes', 'Fraccionamiento Las Playas', 'El Progreso', 'Renacimiento'],
  },
  {
    codigoRenapo: 'HG',
    nombre: 'Hidalgo',
    cpMin: 42000,
    cpMax: 43999,
    municipios: ['Pachuca de Soto', 'Mineral de la Reforma', 'Tulancingo de Bravo', 'Tizayuca', 'Tula de Allende', 'Huejutla de Reyes', 'Ixmiquilpan', 'Tepeji del Río de Ocampo', 'Actopan', 'Apan'],
    coloniasBase: ['Centro', 'Zona Plateada', 'Periodistas', 'Valle de San Javier', 'Juan C. Doria', 'Chacón'],
  },
  {
    codigoRenapo: 'JC',
    nombre: 'Jalisco',
    cpMin: 44000,
    cpMax: 49999,
    municipios: ['Guadalajara', 'Zapopan', 'San Pedro Tlaquepaque', 'Tlajomulco de Zúñiga', 'Tonalá', 'Puerto Vallarta', 'El Salto', 'Lagos de Moreno', 'Tepatitlán de Morelos', 'Zapotlán el Grande', 'Ocotlán', 'Tala'],
    coloniasBase: ['Centro', 'Americana', 'Providencia', 'Chapalita', 'Puerta de Hierro', 'Jardines del Bosque', 'Ciudad del Sol', 'Zapopan Centro'],
  },
  {
    codigoRenapo: 'MC',
    nombre: 'Estado de México',
    cpMin: 50000,
    cpMax: 57999,
    municipios: ['Toluca', 'Ecatepec de Morelos', 'Nezahualcóyotl', 'Naucalpan de Juárez', 'Tlalnepantla de Baz', 'Metepec', 'Atizapán de Zaragoza', 'Cuautitlán Izcalli', 'Chimalhuacán', 'Tecámac', 'Huixquilucan', 'Texcoco', 'Chalco', 'Valle de Bravo'],
    coloniasBase: ['Centro', 'Ciudad Satélite', 'Lomas Verdes', 'San Jerónimo Chicahualco', 'Las Américas', 'Benito Juárez', 'Interlomas', 'Izcalli Centro'],
  },
  {
    codigoRenapo: 'MN',
    nombre: 'Michoacán',
    cpMin: 58000,
    cpMax: 61999,
    municipios: ['Morelia', 'Uruapan', 'Zamora', 'Lázaro Cárdenas', 'Zitácuaro', 'Apatzingán', 'Pátzcuaro', 'La Piedad', 'Hidalgo', 'Sahuayo', 'Tarímbaro'],
    coloniasBase: ['Centro Histórico', 'Chapultepec Norte', 'Las Américas', 'Camelinas', 'Félix Ireta', 'Vista Bella'],
  },
  {
    codigoRenapo: 'MS',
    nombre: 'Morelos',
    cpMin: 62000,
    cpMax: 62999,
    municipios: [
      'Cuernavaca', 'Jiutepec', 'Cuautla', 'Temixco', 'Emiliano Zapata', 'Yautepec', 'Xochitepec',
      'Jojutla', 'Zacatepec', 'Puente de Ixtla', 'Tepoztlán', 'Tlaltizapán de Zapata', 'Ayala',
      'Tlayacapan', 'Huitzilac', 'Miacatlán', 'Tlaquiltenango', 'Yecapixtla', 'Atlatlahucan',
    ],
    coloniasBase: ['Centro', 'Vista Hermosa', 'Reforma', 'Lomas de Cortés', 'Chapultepec', 'Las Palmas', 'Teopanzolco', 'Civac', 'Burgos', 'Chamilpa'],
  },
  {
    codigoRenapo: 'NT',
    nombre: 'Nayarit',
    cpMin: 63000,
    cpMax: 63999,
    municipios: ['Tepic', 'Bahía de Banderas', 'Santiago Ixcuintla', 'Compostela', 'Tuxpan', 'Xalisco', 'Ixtlán del Río', 'Acaponeta', 'Tecuala', 'San Blas'],
    coloniasBase: ['Centro', 'Ciudad del Valle', 'San Juan', 'Nuevo Vallarta', 'Bucerías', 'Fray Junípero Serra'],
  },
  {
    codigoRenapo: 'NL',
    nombre: 'Nuevo León',
    cpMin: 64000,
    cpMax: 67999,
    municipios: ['Monterrey', 'San Pedro Garza García', 'Guadalupe', 'Apodaca', 'San Nicolás de los Garza', 'General Escobedo', 'Santa Catarina', 'García', 'Juárez', 'Santiago', 'Cadereyta Jiménez', 'Linares'],
    coloniasBase: ['Centro', 'Del Valle', 'Obispado', 'Cumbres', 'Tecnológico', 'Contry', 'Mitras Centro', 'Anáhuac', 'Valle Oriente'],
  },
  {
    codigoRenapo: 'OC',
    nombre: 'Oaxaca',
    cpMin: 68000,
    cpMax: 71999,
    municipios: ['Oaxaca de Juárez', 'San Juan Bautista Tuxtepec', 'Salina Cruz', 'Juchitán de Zaragoza', 'Santa Cruz Xoxocotlán', 'Santa Lucía del Camino', 'Huajuapan de León', 'Santa María Huatulco', 'San Pedro Mixtepec'],
    coloniasBase: ['Centro', 'Reforma', 'Xochimilco', 'San Felipe del Agua', 'La Crucecita', '5 Señores'],
  },
  {
    codigoRenapo: 'PL',
    nombre: 'Puebla',
    cpMin: 72000,
    cpMax: 75999,
    municipios: ['Puebla', 'Tehuacán', 'San Martín Texmelucan', 'San Andrés Cholula', 'San Pedro Cholula', 'Atlixco', 'Cuautlancingo', 'Amozoc', 'Teziutlán', 'Huauchinango', 'Zacatlán'],
    coloniasBase: ['Centro Histórico', 'La Paz', 'Angelópolis', 'Anzures', 'Zavaleta', 'Lomas de Angelópolis', 'Chulavista'],
  },
  {
    codigoRenapo: 'QT',
    nombre: 'Querétaro',
    cpMin: 76000,
    cpMax: 76999,
    municipios: ['Querétaro', 'San Juan del Río', 'Corregidora', 'El Marqués', 'Tequisquiapan', 'Pedro Escobedo', 'Ezequiel Montes', 'Cadereyta de Montes', 'Colón', 'Jalpan de Serra'],
    coloniasBase: ['Centro Histórico', 'Juriquilla', 'Milenio III', 'Áamos 2a Sección', 'Carretas', 'Zibatá', 'El Pueblito'],
  },
  {
    codigoRenapo: 'QR',
    nombre: 'Quintana Roo',
    cpMin: 77000,
    cpMax: 77999,
    municipios: ['Benito Juárez', 'Solidaridad', 'Othón P. Blanco', 'Cozumel', 'Tulum', 'Puerto Morelos', 'Isla Mujeres', 'Bacalar', 'Felipe Carrillo Puerto'],
    coloniasBase: ['Supermanzana 1 Centro', 'Zona Hotelera', 'Puerto Cancún', 'Playa del Carmen Centro', 'Playacar', 'Chetumal Centro'],
  },
  {
    codigoRenapo: 'SP',
    nombre: 'San Luis Potosí',
    cpMin: 78000,
    cpMax: 79999,
    municipios: ['San Luis Potosí', 'Soledad de Graciano Sánchez', 'Ciudad Valles', 'Matehuala', 'Rioverde', 'Tamazunchale', 'Mexquitic de Carmona', 'Villa de Reyes'],
    coloniasBase: ['Centro Histórico', 'Lomas de San Luis', 'Tequisquiapan', 'Del Valle', 'Himno Nacional', 'Tangamanga'],
  },
  {
    codigoRenapo: 'SL',
    nombre: 'Sinaloa',
    cpMin: 80000,
    cpMax: 82999,
    municipios: ['Culiacán', 'Mazatlán', 'Ahome', 'Guasave', 'Navolato', 'Salvador Alvarado', 'Escuinapa', 'El Fuerte'],
    coloniasBase: ['Centro', 'Tres Ríos', 'Las Quintas', 'Chapultepec', 'Zona Dorada', 'Los Mochis Centro'],
  },
  {
    codigoRenapo: 'SR',
    nombre: 'Sonora',
    cpMin: 83000,
    cpMax: 85999,
    municipios: ['Hermosillo', 'Cajeme', 'Nogales', 'San Luis Río Colorado', 'Navojoa', 'Guaymas', 'Agua Prieta', 'Caborca', 'Puerto Peñasco'],
    coloniasBase: ['Centro', 'Pitic', 'Centenario', 'Villa Satélite', 'Ciudad Obregón Centro', 'Nainari'],
  },
  {
    codigoRenapo: 'TC',
    nombre: 'Tabasco',
    cpMin: 86000,
    cpMax: 86999,
    municipios: ['Centro', 'Cárdenas', 'Comalcalco', 'Macuspana', 'Huimanguillo', 'Cunduacán', 'Paraíso', ' Nacajuca', 'Tenosique'],
    coloniasBase: ['Villahermosa Centro', 'Tabasco 2000', 'Primero de Mayo', 'Atasta de Serra', 'Tamulté', 'Guayabal'],
  },
  {
    codigoRenapo: 'TS',
    nombre: 'Tamaulipas',
    cpMin: 87000,
    cpMax: 89999,
    municipios: ['Reynosa', 'Matamoros', 'Nuevo Laredo', 'Victoria', 'Tampico', 'Altamira', 'Ciudad Madero', 'El Mante', 'Río Bravo'],
    coloniasBase: ['Zona Centro', 'Las Fuentes', 'Petrolera', 'Lomas de Rosales', 'Jardín', 'San José'],
  },
  {
    codigoRenapo: 'TL',
    nombre: 'Tlaxcala',
    cpMin: 90000,
    cpMax: 90999,
    municipios: ['Tlaxcala', 'Apizaco', 'Chiautempan', 'Huamantla', 'Zacatelco', 'Calpulalpan', 'San Pablo del Monte', 'Tlaxco', 'Yauhquemehcan'],
    coloniasBase: ['Centro', 'Ocotlán', 'La Loma Xicohténcatl', 'Fátima', 'Santa Ana Centro', 'San Martín'],
  },
  {
    codigoRenapo: 'VZ',
    nombre: 'Veracruz',
    cpMin: 91000,
    cpMax: 96999,
    municipios: ['Veracruz', 'Xalapa', 'Coatzacoalcos', 'Córdoba', 'Poza Rica de Hidalgo', 'Boca del Río', 'Orizaba', 'Minatitlán', 'Tuxpan', 'Papantla', 'San Andrés Tuxtla', 'Martínez de la Torre'],
    coloniasBase: ['Centro', 'Reforma', 'Costa de Oro', 'Ánimas', 'Zaragoza', 'Mocambo', 'Obras Sociales'],
  },
  {
    codigoRenapo: 'YN',
    nombre: 'Yucatán',
    cpMin: 97000,
    cpMax: 97999,
    municipios: ['Mérida', 'Kanasín', 'Valladolid', 'Tizimín', 'Progreso', 'Umán', 'Ticul', 'Tekax', 'Conkal', 'Motul'],
    coloniasBase: ['Centro', 'Montejo', 'Altabrisa', 'Itzimná', 'Campestre', 'Francisco de Montejo', 'Temozón Norte'],
  },
  {
    codigoRenapo: 'ZS',
    nombre: 'Zacatecas',
    cpMin: 98000,
    cpMax: 99998,
    municipios: ['Zacatecas', 'Guadalupe', 'Fresnillo', 'Jerez', 'Río Grande', 'Sombrerete', 'Loreto', 'Ojocaliente', 'Calera'],
    coloniasBase: ['Centro Histórico', 'Sierra de Álica', 'Colinas del Padre', 'Gavilanes', 'Bernárdez', 'Lomas de la Soledad'],
  },
];

export const obtenerEstadoPorNombreOCodigo = (valor: string): EstadoSepomexItem | undefined => {
  const clean = (valor || '').trim().toLowerCase();
  if (!clean) return undefined;
  return CATALOGO_ESTADOS_SEPOMEX.find(
    (e) =>
      e.codigoRenapo.toLowerCase() === clean ||
      e.nombre.toLowerCase() === clean
  );
};

export const obtenerMunicipiosPorEstado = (estadoNombreOCodigo: string): string[] => {
  const est = obtenerEstadoPorNombreOCodigo(estadoNombreOCodigo);
  return est ? est.municipios : [];
};

const resolverMunicipioSubrango = (cpNum: number, est: EstadoSepomexItem): string => {
  if (est.codigoRenapo === 'DF') {
    const idx = Math.floor(cpNum / 1000) - 1;
    if (idx >= 0 && idx < est.municipios.length) {
      return est.municipios[idx];
    }
  }
  if (est.codigoRenapo === 'MS') {
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
  if (est.codigoRenapo === 'JC') {
    if (cpNum >= 44100 && cpNum <= 44999) return 'Guadalajara';
    if (cpNum >= 45000 && cpNum <= 45299) return 'Zapopan';
    if (cpNum >= 45400 && cpNum <= 45429) return 'Tonalá';
    if (cpNum >= 45500 && cpNum <= 45639) return 'San Pedro Tlaquepaque';
    if (cpNum >= 45640 && cpNum <= 45679) return 'Tlajomulco de Zúñiga';
    if (cpNum >= 48300 && cpNum <= 48399) return 'Puerto Vallarta';
  }
  if (est.codigoRenapo === 'NL') {
    if (cpNum >= 64000 && cpNum <= 64999) return 'Monterrey';
    if (cpNum >= 66200 && cpNum <= 66299) return 'San Pedro Garza García';
    if (cpNum >= 66400 && cpNum <= 66499) return 'San Nicolás de los Garza';
    if (cpNum >= 66600 && cpNum <= 66699) return 'Apodaca';
    if (cpNum >= 67100 && cpNum <= 67199) return 'Guadalupe';
  }
  if (est.codigoRenapo === 'MC') {
    if (cpNum >= 50000 && cpNum <= 50299) return 'Toluca';
    if (cpNum >= 52140 && cpNum <= 52179) return 'Metepec';
    if (cpNum >= 52760 && cpNum <= 52799) return 'Huixquilucan';
    if (cpNum >= 52900 && cpNum <= 52999) return 'Atizapán de Zaragoza';
    if (cpNum >= 53000 && cpNum <= 53999) return 'Naucalpan de Juárez';
    if (cpNum >= 54000 && cpNum <= 54199) return 'Tlalnepantla de Baz';
    if (cpNum >= 54700 && cpNum <= 54799) return 'Cuautitlán Izcalli';
    if (cpNum >= 55000 && cpNum <= 55599) return 'Ecatepec de Morelos';
    if (cpNum >= 57000 && cpNum <= 57999) return 'Nezahualcóyotl';
  }
  return est.municipios[0];
};

export const resolverCodigoPostalMexicano = (cp: string): ResolucionCodigoPostal => {
  const raw = (cp || '').trim();
  if (!raw) {
    return {
      valido: false,
      error: 'El código postal es obligatorio (5 dígitos).',
      municipiosEstado: [],
      coloniasSugeridas: [],
    };
  }
  if (/[^\d]/.test(raw)) {
    return {
      valido: false,
      error: 'El código postal solo debe contener números.',
      municipiosEstado: [],
      coloniasSugeridas: [],
    };
  }
  if (raw.length !== 5) {
    return {
      valido: false,
      error: `El código postal debe tener exactamente 5 dígitos (llevas ${raw.length}).`,
      municipiosEstado: [],
      coloniasSugeridas: [],
    };
  }

  const cpNum = parseInt(raw, 10);
  const est = CATALOGO_ESTADOS_SEPOMEX.find((e) => cpNum >= e.cpMin && cpNum <= e.cpMax);
  if (!est) {
    return {
      valido: false,
      error: `El código postal ${raw} no existe en el catálogo oficial SEPOMEX de México.`,
      municipiosEstado: [],
      coloniasSugeridas: [],
    };
  }

  return {
    valido: true,
    estadoNombre: est.nombre,
    codigoRenapo: est.codigoRenapo,
    municipioSugerido: resolverMunicipioSubrango(cpNum, est),
    municipiosEstado: est.municipios,
    coloniasSugeridas: est.coloniasBase.map((c) => c.trim()),
  };
};
