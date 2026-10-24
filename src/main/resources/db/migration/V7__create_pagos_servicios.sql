-- ============================================================
-- V7: Tabla de auditoría y registro de Pagos de Servicios (GestoPago / PuntoRed)
-- ============================================================

-- Ajustar cuentas existentes a 16 dígitos numéricos en caso de que tuvieran longitud menor
UPDATE cuentas
SET numero_cuenta = RPAD(numero_cuenta, 16, '0')
WHERE LENGTH(numero_cuenta) < 16;

CREATE TABLE IF NOT EXISTS pagos_servicios (
    id                  BIGSERIAL       PRIMARY KEY,
    cuenta_id           BIGINT          NOT NULL,
    numero_cuenta       TEXT            NOT NULL,
    id_servicio         INTEGER         NOT NULL,
    id_producto         INTEGER         NOT NULL,
    nombre_servicio     TEXT,
    nombre_producto     TEXT,
    referencia          TEXT            NOT NULL DEFAULT '',
    telefono            TEXT            NOT NULL,
    monto_pagado        NUMERIC(18,2)   NOT NULL,
    comision            NUMERIC(18,2)   NOT NULL DEFAULT 0.00,
    saldo_anterior      NUMERIC(18,2)   NOT NULL,
    saldo_restante      NUMERIC(18,2)   NOT NULL,
    id_tx_gestopago     BIGINT          NOT NULL,
    num_autorizacion    TEXT            NOT NULL,
    folio_ticket        TEXT            NOT NULL,
    upc                 TEXT            NOT NULL,
    codigo_respuesta    TEXT            NOT NULL,
    mensaje_respuesta   TEXT,
    concepto            TEXT            NOT NULL,
    fecha_operacion     TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pagos_servicios_cuenta FOREIGN KEY (cuenta_id) REFERENCES cuentas(id),
    CONSTRAINT uq_pagos_servicios_folio  UNIQUE (folio_ticket),
    CONSTRAINT ck_pagos_servicios_monto  CHECK (monto_pagado > 0)
);

CREATE INDEX IF NOT EXISTS idx_pagos_servicios_cuenta_id        ON pagos_servicios (cuenta_id);
CREATE INDEX IF NOT EXISTS idx_pagos_servicios_numero_cuenta    ON pagos_servicios (numero_cuenta);
CREATE INDEX IF NOT EXISTS idx_pagos_servicios_id_tx            ON pagos_servicios (id_tx_gestopago);
CREATE INDEX IF NOT EXISTS idx_pagos_servicios_folio            ON pagos_servicios (folio_ticket);
CREATE INDEX IF NOT EXISTS idx_pagos_servicios_fecha            ON pagos_servicios (fecha_operacion DESC);
