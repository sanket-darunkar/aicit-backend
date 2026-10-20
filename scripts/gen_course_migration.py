#!/usr/bin/env python3
"""
Generate the Flyway migration that imports the 317-course catalog from the PDF.

Reads scripts/course_list.tsv (srNo<TAB>name<TAB>duration) and emits
src/main/resources/db/migration/V12__import_course_catalog.sql

Design decisions (safe, idempotent, non-destructive):
  * Stable code per offering: AICIT-C<srNo> (keeps source numbering for
    reference without using it as the PK, and makes duplicate names with
    different durations distinct offerings).
  * duration_label : raw text exactly as the PDF ("45 DAYS", "1.5 MONTHS"...)
  * duration_days  : normalized for sort/filter (months*30, years*365, weeks? )
  * duration_months: whole months when the duration is an exact month count,
    else NULL — preserves the certificate PDF's getDurationMonths() behaviour.
  * category       : inferred from keywords for filtering.
  * Upsert ON CONFLICT (code): re-running the migration/import never duplicates.
"""
import re, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
TSV  = ROOT / "scripts" / "course_list.tsv"
OUT  = ROOT / "src/main/resources/db/migration/V12__import_course_catalog.sql"

def parse_duration(raw):
    """Return (duration_months|None, duration_days, label)."""
    s = raw.strip().upper()
    m = re.match(r'^([\d.]+)\s*(MONTH|MONTHS|YEAR|YEARS|DAY|DAYS|HOUR|HOURS|WEEK|WEEKS)$', s)
    if not m:
        return None, None, raw.strip()
    val = float(m.group(1))
    unit = m.group(2)
    months = None
    if unit.startswith("MONTH"):
        days = int(round(val * 30))
        if val == int(val):
            months = int(val)
    elif unit.startswith("YEAR"):
        days = int(round(val * 365))
        months = int(round(val * 12))
    elif unit.startswith("WEEK"):
        days = int(round(val * 7))
    elif unit.startswith("DAY"):
        days = int(round(val))
    elif unit.startswith("HOUR"):
        # treat teaching hours as a span; approximate days for sorting only
        days = int(round(val / 6)) or 1   # ~6 hrs/day
    else:
        days = None
    return months, days, raw.strip()

CATEGORY_RULES = [
    ("Typing",               [r'\bTYPING\b', r'W\.P\.M', r'SHORTHAND', r'STENOGRAPHY']),
    ("Accounting & Finance", [r'\bTALLY\b', r'ACCOUNT', r'\bGST\b', r'FINANC', r'TAXATION',
                              r'\bSAP\b', r'BANKING', r'INSURANCE', r'BUSY\b', r'POWER BI', r'\bEXCEL\b']),
    ("Design & Media",       [r'DESIGN', r'PHOTOSHOP', r'COREL', r'ILLUSTRATOR', r'GRAPHIC',
                              r'MULTIMEDIA', r'ANIMATION', r'VIDEO', r'PHOTOGRAPH', r'DTP',
                              r'DESKTOP PUBLISHING', r'INTERIOR', r'FASHION', r'UI/UX',
                              r'SKETCHUP', r'LUMION', r'V-RAY', r'3D', r'2D', r'MEHANDI',
                              r'AARI', r'ZARDOSI']),
    ("Engineering & CAD",    [r'AUTOCAD', r'CATIA', r'CREO', r'PRO-E', r'SOLIDWORKS', r'STAAD',
                              r'REVIT', r'ETABS', r'PRIMAVERA', r'\bCAD\b', r'\bCNC\b',
                              r'UNIGRAPHICS', r'\bBIM\b', r'ANSYS', r'ARCHITECT', r'CIVIL ENGINEERING']),
    ("Programming & Software",[r'\bJAVA\b', r'PYTHON', r'\bC\+\+', r'\bC#', r'\.NET', r'\bPHP\b',
                              r'\bSQL\b', r'PROGRAMMING', r'SOFTWARE', r'WEB DEV', r'FULL STACK',
                              r'REACT', r'ANGULAR', r'JAVA SCRIPT', r'\bHTML', r'\bCSS\b',
                              r'BOOTSTRAP', r'JQUERY', r'DATA STRUCTURE', r'DATA ANALY',
                              r'ARTIFICIAL INTELLIGENCE', r'MACHINE', r'IOT', r'SPRING BOOT',
                              r'VISUAL BASIC', r'ETHICAL HACKING', r'SOFTWARE TESTING',
                              r'GOOGLE DATA', r'CHATGPT', r'ROBOTICS', r'SCRATCH']),
    ("Hardware & Networking",[r'HARDWARE', r'NETWORK', r'CLOUD', r'LAPTOP REPAIR', r'PC TECH',
                              r'MOBILE PHONE REPAIR', r'WIRELESS', r'OPERATING SYSTEM']),
    ("Vocational & Trades",  [r'ELECTRICIAN', r'WIREMAN', r'PLUMB', r'CARPENTER', r'WELDING',
                              r'DIESEL', r'AUTOMOBILE', r'AC AND REFRIG', r'VEHICLE REPAIR',
                              r'BEAUTY PARLOUR', r'TAILORING', r'FIRE AND SAFETY', r'SOLAR',
                              r'FOOD', r'NUTRITION', r'HOTEL', r'YOGA', r'NURSERY',
                              r'LIBRARIAN', r'QUANTUM']),
    ("Office & Business",    [r'MS OFFICE', r'OFFICE AUTOMATION', r'OFFICE MANAGEMENT',
                              r'OFFICE PROFESSIONAL', r'DATA ENTRY', r'COMPUTER OPERATOR',
                              r'SECURITY MANAGEMENT', r'MANAGEMENT', r'MARKETING', r'RETAIL',
                              r'ENTREPRENEUR', r'COMMUNICATION', r'ENGLISH', r'HUMAN RESOURCE',
                              r'PROJECT', r'STATISTICS', r'DIGITAL MARKETING',
                              r'MANAGEMENT INFORMATION']),
]

def infer_category(name):
    up = name.upper()
    for cat, pats in CATEGORY_RULES:
        for p in pats:
            if re.search(p, up):
                return cat
    return "Computer & IT"

def sql_escape(s):
    return s.replace("'", "''")

def main():
    rows = []
    for line in TSV.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        parts = line.split("\t")
        sr, name, dur = parts[0].strip(), parts[1].strip(), parts[2].strip()
        months, days, label = parse_duration(dur)
        rows.append((int(sr), name, label, months, days, infer_category(name)))

    # MS-CIT (originally sr 317) was removed from the catalog by a later
    # business decision (migration V14), so the source list may have fewer rows.
    assert 300 <= len(rows) <= 317, f"unexpected row count: {len(rows)}"

    values = []
    for sr, name, label, months, days, cat in rows:
        code = f"AICIT-C{sr:03d}"
        m = "NULL" if months is None else str(months)
        d = "NULL" if days   is None else str(days)
        values.append(
            f"  ('{sql_escape(name)}', '{code}', {m}, {d}, '{sql_escape(label)}', "
            f"'{sql_escape(cat)}', {sr}, TRUE, NOW(), NOW())"
        )

    body = ",\n".join(values)
    sql = f"""-- ============================================================
-- AICIT Platform – V12: Import full 317-course catalog
--
-- Source of truth: COURSE LIST.pdf (317 entries).
-- NON-DESTRUCTIVE:
--   * Adds structured duration columns (label + days) and source_sr_no.
--   * Upserts all 317 courses with stable codes AICIT-C001..AICIT-C317.
--     Duplicate names with different durations are preserved as separate
--     offerings (distinct sr-no-based codes).
--   * Deactivates the OLD seed courses (V4) so only the new catalog is
--     selectable — but never deletes them, preserving historical
--     enrollment/certificate references.
--   * ON CONFLICT (code) DO UPDATE makes re-runs safe and idempotent.
-- Generated by scripts/gen_course_migration.py — do not edit by hand.
-- ============================================================

-- 1. New structured duration + provenance columns (idempotent) ----
ALTER TABLE courses ADD COLUMN IF NOT EXISTS duration_label VARCHAR(40);
ALTER TABLE courses ADD COLUMN IF NOT EXISTS duration_days  INTEGER;
ALTER TABLE courses ADD COLUMN IF NOT EXISTS source_sr_no   INTEGER;

CREATE INDEX IF NOT EXISTS idx_course_name        ON courses (LOWER(name));
CREATE INDEX IF NOT EXISTS idx_course_duration_d  ON courses (duration_days);

-- 2. Deactivate the old V4 seed catalog (kept for history, not selectable).
--    Only rows NOT part of the new import are touched; referenced rows remain
--    in the table so enrollments/certificates keep resolving.
UPDATE courses
   SET is_active = FALSE, updated_at = NOW()
 WHERE source_sr_no IS NULL
   AND code NOT LIKE 'AICIT-C%';

-- 3. Import the 317-course catalog (idempotent upsert) ------------
INSERT INTO courses
  (name, code, duration_months, duration_days, duration_label, category, source_sr_no,
   is_active, created_at, updated_at)
VALUES
{body}
ON CONFLICT (code) DO UPDATE SET
  name            = EXCLUDED.name,
  duration_months = EXCLUDED.duration_months,
  duration_days   = EXCLUDED.duration_days,
  duration_label  = EXCLUDED.duration_label,
  category        = EXCLUDED.category,
  source_sr_no    = EXCLUDED.source_sr_no,
  is_active       = TRUE,
  updated_at      = NOW();
"""
    OUT.write_text(sql, encoding="utf-8")
    print(f"Wrote {OUT} with {len(rows)} courses")
    # quick category distribution for sanity
    from collections import Counter
    print(Counter(r[5] for r in rows))

if __name__ == "__main__":
    main()
