-- ============================================================
-- AICIT Platform – V8: Atomic sequences + batch optimistic lock
-- Fixes race conditions in certificate-number / batch-code
-- generation and double-processing of batches.
-- ============================================================

-- 1. Batch code sequence (mirrors the existing cert_number_seq) ----
CREATE SEQUENCE IF NOT EXISTS batch_code_seq START 1 INCREMENT 1;

-- 2. Align cert_number_seq to the current max suffix so sequence-based
--    numbering never collides with numbers already issued in-application.
--    (Safe no-op if there are no certificates yet.)
DO $$
DECLARE
    max_suffix BIGINT;
BEGIN
    SELECT COALESCE(MAX(
        CAST(NULLIF(regexp_replace(certificate_number, '^AICIT-\d{4}-', ''), '') AS BIGINT)
    ), 0)
    INTO max_suffix
    FROM certificates
    WHERE certificate_number ~ '^AICIT-\d{4}-\d+$';

    IF max_suffix > 0 THEN
        PERFORM setval('cert_number_seq', max_suffix, true);
    END IF;
END $$;

-- 3. Optimistic-lock version column on certificate_batches --------
ALTER TABLE certificate_batches
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
