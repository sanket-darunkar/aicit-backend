-- ============================================================
-- AICIT Platform – V3: Institute Users
-- ============================================================
CREATE TABLE IF NOT EXISTS institute_users (
    id            BIGSERIAL    NOT NULL,
    institute_id  BIGINT       NOT NULL REFERENCES institutes(id),
    email         VARCHAR(255) NOT NULL,
    password      VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    mobile        VARCHAR(15),
    role          VARCHAR(30)  NOT NULL DEFAULT 'INSTITUTE_ADMIN',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT pk_institute_users     PRIMARY KEY (id),
    CONSTRAINT uq_institute_user_email UNIQUE (email),
    CONSTRAINT chk_inst_user_role      CHECK (role IN ('INSTITUTE_ADMIN','INSTITUTE_STAFF'))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_inst_user_email      ON institute_users (email);
CREATE INDEX        IF NOT EXISTS idx_inst_user_institute  ON institute_users (institute_id);
CREATE INDEX        IF NOT EXISTS idx_inst_user_role       ON institute_users (role);
