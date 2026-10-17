-- ============================================================
-- AICIT Platform – V9: Remove all MS-CIT courses
-- Business decision: MS-CIT must not appear anywhere on the platform.
-- Deletes the standalone MS-CIT course and the two combo courses that
-- bundle MS-CIT. Guarded so it is safe if a course is already referenced.
-- ============================================================

-- Only delete combos/standalone that are NOT referenced by any existing
-- certificate (protects data integrity; a referenced course is deactivated
-- instead of deleted).

-- 1. Deactivate first (covers rows referenced by certificates) ----
UPDATE courses
SET is_active = FALSE, updated_at = NOW()
WHERE code IN ('MSCIT', 'COMBO-1', 'COMBO-2');

-- 2. Hard-delete the ones with no certificate references ----------
DELETE FROM courses c
WHERE c.code IN ('MSCIT', 'COMBO-1', 'COMBO-2')
  AND NOT EXISTS (
      SELECT 1 FROM certificates ct WHERE ct.course_id = c.id
  );
