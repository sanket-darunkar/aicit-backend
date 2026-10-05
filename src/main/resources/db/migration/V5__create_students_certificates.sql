-- ============================================================
-- AICIT Platform – V5: Students, Enrollments, Certificates,
--                      Verification Logs, Audit Logs
-- ============================================================

-- Students --------------------------------------------------
CREATE TABLE IF NOT EXISTS students (
    id               BIGSERIAL     NOT NULL,
    institute_id     BIGINT        NOT NULL REFERENCES institutes(id),
    student_id       VARCHAR(50)   NOT NULL,   -- e.g. INST001-2026-001
    first_name       VARCHAR(100)  NOT NULL,
    middle_name      VARCHAR(100),
    surname          VARCHAR(100)  NOT NULL,
    date_of_birth    DATE,
    gender           VARCHAR(20),
    aadhaar_number   VARCHAR(20),
    own_mobile       VARCHAR(15)   NOT NULL,
    address_line1    VARCHAR(255),
    city             VARCHAR(100),
    district         VARCHAR(100),
    state            VARCHAR(100),
    pin_code         VARCHAR(10),
    qualification    VARCHAR(100),
    status           VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    photo_data       BYTEA,
    photo_mime_type  VARCHAR(20),
    notes            VARCHAR(2000),
    created_by       BIGINT        REFERENCES institute_users(id),
    created_at       TIMESTAMP     NOT NULL,
    updated_at       TIMESTAMP     NOT NULL,
    CONSTRAINT pk_students        PRIMARY KEY (id),
    CONSTRAINT uq_student_id_inst UNIQUE (institute_id, student_id),
    CONSTRAINT chk_student_status CHECK (status IN ('ACTIVE','INACTIVE','COMPLETED','DROPPED'))
);

CREATE INDEX IF NOT EXISTS idx_student_institute ON students (institute_id);
CREATE INDEX IF NOT EXISTS idx_student_status    ON students (institute_id, status);
CREATE INDEX IF NOT EXISTS idx_student_name      ON students (first_name, surname);
CREATE INDEX IF NOT EXISTS idx_student_mobile    ON students (own_mobile);

-- Enrollments -----------------------------------------------
CREATE TABLE IF NOT EXISTS enrollments (
    id              BIGSERIAL      NOT NULL,
    student_id      BIGINT         NOT NULL REFERENCES students(id),
    course_id       BIGINT         NOT NULL REFERENCES courses(id),
    institute_id    BIGINT         NOT NULL REFERENCES institutes(id),
    admission_date  DATE           NOT NULL,
    batch_time      VARCHAR(50),
    total_fees      NUMERIC(10,2),
    fees_paid       NUMERIC(10,2),
    receipt_number  VARCHAR(50),
    receipt_date    DATE,
    status          VARCHAR(30)    NOT NULL DEFAULT 'ENROLLED',
    notes           VARCHAR(2000),
    created_at      TIMESTAMP      NOT NULL,
    updated_at      TIMESTAMP      NOT NULL,
    CONSTRAINT pk_enrollments         PRIMARY KEY (id),
    CONSTRAINT uq_enrollment          UNIQUE (student_id, course_id),
    CONSTRAINT chk_enrollment_status  CHECK (status IN ('ENROLLED','COMPLETED','DROPPED','EXAM_FORM_SUBMITTED'))
);

CREATE INDEX IF NOT EXISTS idx_enroll_student   ON enrollments (student_id);
CREATE INDEX IF NOT EXISTS idx_enroll_institute ON enrollments (institute_id);
CREATE INDEX IF NOT EXISTS idx_enroll_course    ON enrollments (course_id);
CREATE INDEX IF NOT EXISTS idx_enroll_status    ON enrollments (status);

-- Certificate number sequence (year-scoped via application logic)
CREATE SEQUENCE IF NOT EXISTS cert_number_seq START 1 INCREMENT 1;

-- Certificates -----------------------------------------------
CREATE TABLE IF NOT EXISTS certificates (
    id                  BIGSERIAL    NOT NULL,
    certificate_number  VARCHAR(30)  NOT NULL,  -- AICIT-2026-000001
    institute_id        BIGINT       NOT NULL REFERENCES institutes(id),
    student_id          BIGINT       NOT NULL REFERENCES students(id),
    enrollment_id       BIGINT       REFERENCES enrollments(id),
    course_id           BIGINT       NOT NULL REFERENCES courses(id),
    marks               VARCHAR(20),
    grade               VARCHAR(10),
    issue_date          DATE,
    status              VARCHAR(30)  NOT NULL DEFAULT 'REQUESTED',
    rejection_reason    VARCHAR(500),
    reviewed_by         BIGINT       REFERENCES admin_users(id),
    reviewed_at         TIMESTAMP,
    pdf_data            BYTEA,
    pdf_generated_at    TIMESTAMP,
    revoked_at          TIMESTAMP,
    revoke_reason       VARCHAR(500),
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,
    CONSTRAINT pk_certificates          PRIMARY KEY (id),
    CONSTRAINT uq_cert_number           UNIQUE (certificate_number),
    CONSTRAINT chk_certificate_status   CHECK (status IN (
        'REQUESTED','UNDER_REVIEW','APPROVED','ISSUED','REJECTED','REVOKED'
    ))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_cert_number    ON certificates (certificate_number);
CREATE INDEX        IF NOT EXISTS idx_cert_institute ON certificates (institute_id);
CREATE INDEX        IF NOT EXISTS idx_cert_student   ON certificates (student_id);
CREATE INDEX        IF NOT EXISTS idx_cert_status    ON certificates (status);

-- Certificate Verification Logs ------------------------------
CREATE TABLE IF NOT EXISTS certificate_verification_logs (
    id                  BIGSERIAL    NOT NULL,
    certificate_id      BIGINT       NOT NULL REFERENCES certificates(id),
    certificate_number  VARCHAR(30)  NOT NULL,
    verified_at         TIMESTAMP    NOT NULL,
    ip_address          VARCHAR(45),
    user_agent          VARCHAR(500),
    result              VARCHAR(20)  NOT NULL DEFAULT 'FOUND',
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_vlog  PRIMARY KEY (id),
    CONSTRAINT chk_vlog_result CHECK (result IN ('FOUND','NOT_FOUND','REVOKED'))
);

CREATE INDEX IF NOT EXISTS idx_vlog_cert_id  ON certificate_verification_logs (certificate_id);
CREATE INDEX IF NOT EXISTS idx_vlog_cert_num ON certificate_verification_logs (certificate_number);
CREATE INDEX IF NOT EXISTS idx_vlog_date     ON certificate_verification_logs (verified_at);

-- Audit Logs -------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id           BIGSERIAL     NOT NULL,
    actor_type   VARCHAR(30)   NOT NULL,
    actor_id     BIGINT        NOT NULL,
    actor_email  VARCHAR(255),
    action       VARCHAR(100)  NOT NULL,
    entity_type  VARCHAR(50),
    entity_id    BIGINT,
    description  VARCHAR(1000),
    ip_address   VARCHAR(45),
    created_at   TIMESTAMP     NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_audit_actor  ON audit_logs (actor_type, actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_logs (action);
CREATE INDEX IF NOT EXISTS idx_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_date   ON audit_logs (created_at);
