-- ============================================================
-- AICIT Platform – V7: Certificate Batches + Payment fields
-- Adds manual-UPI payment gating for single + bulk requests.
-- Non-destructive: only ADDs a table and nullable/defaulted columns.
-- Pre-existing issued certificates are left untouched.
-- ============================================================

-- 1. New table: certificate_batches ---------------------------
CREATE TABLE IF NOT EXISTS certificate_batches (
    id                        BIGSERIAL     NOT NULL,
    batch_code                VARCHAR(40)   NOT NULL,   -- public id, e.g. BATCH-2026-000001
    institute_id              BIGINT        NOT NULL REFERENCES institutes(id),
    certificate_count         INTEGER       NOT NULL DEFAULT 0,
    total_amount              NUMERIC(10,2) NOT NULL DEFAULT 0,
    unit_amount               NUMERIC(10,2) NOT NULL DEFAULT 250,  -- Rs.250 per cert
    type                      VARCHAR(10)   NOT NULL DEFAULT 'BULK', -- SINGLE | BULK
    payment_status            VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    batch_status              VARCHAR(30)   NOT NULL DEFAULT 'DRAFT',
    utr_number                VARCHAR(50),
    payment_rejection_reason  VARCHAR(500),
    submitted_by              BIGINT        REFERENCES institute_users(id),
    verified_by               BIGINT        REFERENCES admin_users(id),
    paid_at                   TIMESTAMP,
    created_at                TIMESTAMP     NOT NULL,
    updated_at                TIMESTAMP     NOT NULL,
    CONSTRAINT pk_cert_batches        PRIMARY KEY (id),
    CONSTRAINT uq_batch_code          UNIQUE (batch_code),
    CONSTRAINT chk_batch_type         CHECK (type IN ('SINGLE','BULK')),
    CONSTRAINT chk_batch_pay_status   CHECK (payment_status IN (
        'PENDING','UTR_SUBMITTED','VERIFICATION_PENDING','PAID','REJECTED'
    )),
    CONSTRAINT chk_batch_status       CHECK (batch_status IN (
        'DRAFT','VALIDATED','SUBMITTED','PROCESSING','GENERATED','COMPLETED','REJECTED'
    ))
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_batch_code       ON certificate_batches (batch_code);
CREATE INDEX        IF NOT EXISTS idx_batch_institute  ON certificate_batches (institute_id);
CREATE INDEX        IF NOT EXISTS idx_batch_pay_status ON certificate_batches (payment_status);
CREATE INDEX        IF NOT EXISTS idx_batch_status     ON certificate_batches (batch_status);

-- 2. New columns on certificates ------------------------------
ALTER TABLE certificates
    ADD COLUMN IF NOT EXISTS batch_id       BIGINT        REFERENCES certificate_batches(id),
    ADD COLUMN IF NOT EXISTS amount         NUMERIC(10,2) NOT NULL DEFAULT 250,
    ADD COLUMN IF NOT EXISTS payment_status VARCHAR(30)   NOT NULL DEFAULT 'PENDING';

-- Add payment-status check constraint (guarded so re-runs don't fail)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_cert_pay_status'
    ) THEN
        ALTER TABLE certificates
            ADD CONSTRAINT chk_cert_pay_status CHECK (payment_status IN (
                'PENDING','UTR_SUBMITTED','VERIFICATION_PENDING','PAID','REJECTED'
            ));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_cert_batch      ON certificates (batch_id);
CREATE INDEX IF NOT EXISTS idx_cert_pay_status ON certificates (payment_status);
