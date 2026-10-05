-- ============================================================
-- AICIT Platform – V6: Add missing created_at column to
--                      certificate_verification_logs
-- The entity has @CreatedDate on created_at but V5 omitted it.
-- ============================================================
ALTER TABLE certificate_verification_logs
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT NOW();
