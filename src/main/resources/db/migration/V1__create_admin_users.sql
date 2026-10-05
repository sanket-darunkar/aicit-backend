-- ============================================================
-- AICIT Platform – V1: Admin Users
-- ============================================================
CREATE TABLE IF NOT EXISTS admin_users (
    id            BIGSERIAL    NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password      VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    role          VARCHAR(30)  NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT pk_admin_users       PRIMARY KEY (id),
    CONSTRAINT uq_admin_email       UNIQUE (email),
    CONSTRAINT chk_admin_role       CHECK (role IN ('SUPER_ADMIN','MCA_ADMIN'))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_admin_email ON admin_users (email);
CREATE INDEX        IF NOT EXISTS idx_admin_role  ON admin_users (role);
