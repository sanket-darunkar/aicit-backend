-- ============================================================
-- AICIT Platform – V14: Remove MS-CIT from the course catalog
--
-- Business decision: MS-CIT must not appear anywhere on the platform.
-- The V12 catalog import re-added MS-CIT as code 'AICIT-C317' (and the
-- original V4 seed used code 'MSCIT' + two combo courses). This migration
-- removes them again.
--
-- NON-DESTRUCTIVE where it matters:
--   1. Deactivate first — if a course is referenced by any certificate or
--      enrollment it is kept (is_active=FALSE) so historical records never
--      break.
--   2. Hard-delete only the rows that are NOT referenced anywhere.
-- Idempotent: safe to re-run; matches by code and by MS-CIT name variants.
-- ============================================================

-- Target set: the new catalog code, the legacy seed code, the combos, and
-- any row whose name is an MS-CIT variant (defensive).
-- 1. Deactivate (covers referenced rows) -------------------------
UPDATE courses
SET is_active = FALSE, updated_at = NOW()
WHERE code IN ('AICIT-C317', 'MSCIT', 'COMBO-1', 'COMBO-2')
   OR UPPER(REPLACE(REPLACE(name, '-', ''), ' ', '')) = 'MSCIT'
   OR UPPER(name) LIKE 'MS-CIT %'
   OR UPPER(name) LIKE '% MS-CIT %'
   OR UPPER(name) LIKE '%MS-CIT +%';

-- 2. Hard-delete the unreferenced ones ---------------------------
DELETE FROM courses c
WHERE (
        c.code IN ('AICIT-C317', 'MSCIT', 'COMBO-1', 'COMBO-2')
        OR UPPER(REPLACE(REPLACE(c.name, '-', ''), ' ', '')) = 'MSCIT'
        OR UPPER(c.name) LIKE 'MS-CIT %'
        OR UPPER(c.name) LIKE '% MS-CIT %'
        OR UPPER(c.name) LIKE '%MS-CIT +%'
      )
  AND NOT EXISTS (SELECT 1 FROM certificates ct WHERE ct.course_id = c.id)
  AND NOT EXISTS (SELECT 1 FROM enrollments  en WHERE en.course_id = c.id);
