-- ============================================================
-- AICIT Platform – V2: Institutes
-- ============================================================
CREATE TABLE IF NOT EXISTS institutes (
    id                   BIGSERIAL     NOT NULL,
    institute_code       VARCHAR(50)   NOT NULL,
    name                 VARCHAR(255)  NOT NULL,
    type                 VARCHAR(100),
    address_line1        VARCHAR(255),
    address_line2        VARCHAR(255),
    city                 VARCHAR(100),
    district             VARCHAR(100),
    state                VARCHAR(100),
    pin_code             VARCHAR(10),
    website_url          VARCHAR(500),
    contact_person_name  VARCHAR(255)  NOT NULL,
    contact_email        VARCHAR(255)  NOT NULL,
    contact_mobile       VARCHAR(15)   NOT NULL,
    status               VARCHAR(30)   NOT NULL DEFAULT 'PENDING_REVIEW',
    rejection_reason     VARCHAR(1000),
    suspension_reason    VARCHAR(500),
    approved_at          TIMESTAMP,
    approved_by          BIGINT        REFERENCES admin_users(id),
    suspended_at         TIMESTAMP,
    created_at           TIMESTAMP     NOT NULL,
    updated_at           TIMESTAMP     NOT NULL,
    CONSTRAINT pk_institutes        PRIMARY KEY (id),
    CONSTRAINT uq_institute_code    UNIQUE (institute_code),
    CONSTRAINT uq_institute_email   UNIQUE (contact_email),
    CONSTRAINT chk_institute_status CHECK (status IN (
        'PENDING_REVIEW','APPROVED','SUSPENDED','DEACTIVATED','REJECTED'
    ))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_institute_code   ON institutes (institute_code);
CREATE INDEX        IF NOT EXISTS idx_institute_status ON institutes (status);
CREATE INDEX        IF NOT EXISTS idx_institute_email  ON institutes (contact_email);
