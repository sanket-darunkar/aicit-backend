-- ============================================================
-- AICIT Platform – V9: Allow NULL certificate_number on creation
--
-- Since V7 introduced batch-based certificate requests, certificates
-- are created in REQUESTED / PENDING status WITHOUT a certificate
-- number. The number is only generated when the admin processes the
-- batch (after payment verification). The original NOT NULL constraint
-- from V5 now causes inserts to fail.
--
-- Fix: drop NOT NULL on certificate_number. The UNIQUE constraint
-- and index are kept — Postgres allows multiple NULLs in a unique
-- column by default, so unpaid/unprocessed certs coexist fine.
-- ============================================================

ALTER TABLE certificates
    ALTER COLUMN certificate_number DROP NOT NULL;
