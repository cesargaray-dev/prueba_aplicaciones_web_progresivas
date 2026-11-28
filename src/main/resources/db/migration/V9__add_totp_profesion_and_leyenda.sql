-- ============================================================
-- V9: Google Authenticator (TOTP RFC 6238), Profesión, Catálogo de Ocupación y Leyenda en Ticket
-- ============================================================
ALTER TABLE login_clientes
    ADD COLUMN IF NOT EXISTS totp_secret TEXT,
    ADD COLUMN IF NOT EXISTS totp_habilitado BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE clientes
    ADD COLUMN IF NOT EXISTS profesion TEXT;

ALTER TABLE pagos_servicios
    ADD COLUMN IF NOT EXISTS leyenda TEXT;

INSERT INTO catalogos (tipo, clave, descripcion, orden) VALUES
    ('OCUPACION', 'EMP_PRIV', 'Empleado(a) del sector privado', 10),
    ('OCUPACION', 'SERV_PUB', 'Servidor(a) público(a)', 11),
    ('OCUPACION', 'PROF_IND', 'Profesionista independiente', 12),
    ('OCUPACION', 'OFICIO', 'Trabajador(a) por cuenta propia / Oficio', 13),
    ('OCUPACION', 'EMPRESARIO', 'Empresario(a) / Dueño(a) de negocio', 14),
    ('OCUPACION', 'COMERCIANTE', 'Comerciante', 15),
    ('OCUPACION', 'DOCENTE', 'Docente / Académico(a)', 16),
    ('OCUPACION', 'SALUD', 'Personal del sector salud', 17),
    ('OCUPACION', 'SEG_PUB', 'Fuerzas armadas / Seguridad pública', 18),
    ('OCUPACION', 'AGRO', 'Agricultor(a) / Ganadero(a) / Pescador(a)', 19),
    ('OCUPACION', 'ESTUDIANTE', 'Estudiante', 20),
    ('OCUPACION', 'HOGAR', 'Labores del hogar', 21),
    ('OCUPACION', 'JUBILADO', 'Jubilado(a) / Pensionado(a)', 22),
    ('OCUPACION', 'BUSQ_EMP', 'Desempleado(a) / En búsqueda de empleo', 23),
    ('OCUPACION', 'OTRA_OCUP', 'Otra ocupación', 24)
ON CONFLICT DO NOTHING;
