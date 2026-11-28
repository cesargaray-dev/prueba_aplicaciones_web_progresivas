-- ============================================================
-- V8: Control de sesión única activa entre Web y App Móvil
-- ============================================================
ALTER TABLE login_clientes
    ADD COLUMN IF NOT EXISTS token_sesion_activa TEXT,
    ADD COLUMN IF NOT EXISTS token_sesion_desplazada TEXT,
    ADD COLUMN IF NOT EXISTS sesion_expira_en TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_login_token_activo ON login_clientes (token_sesion_activa);
CREATE INDEX IF NOT EXISTS idx_login_token_desplazado ON login_clientes (token_sesion_desplazada);
