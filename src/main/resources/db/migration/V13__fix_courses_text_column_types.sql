-- ============================================================
-- AICIT Platform – V13: Repair courses text column types
--
-- Problem: on the production database the courses.name / courses.code
-- (and possibly other text columns) are typed as BYTEA instead of
-- VARCHAR/TEXT. This happened because Flyway was baselined over a
-- pre-existing schema (baseline-on-migrate=true), so the original V4
-- VARCHAR definition never actually ran there — the table had been
-- created by an earlier Hibernate ddl-auto run that mapped String
-- columns to bytea. The result: the course search query fails with
--   ERROR: function lower(bytea) does not exist
--
-- Fix: for each affected column, IF it is currently bytea, convert it
-- in place to the correct text type, decoding the bytes as UTF-8. This
-- is idempotent (guarded per column) and non-destructive — existing
-- values are preserved via convert_from(col,'UTF8').
-- ============================================================

DO $$
DECLARE
    rec RECORD;
    -- column_name -> target type
    targets CONSTANT text[][] := ARRAY[
        ['name',        'VARCHAR(255)'],
        ['code',        'VARCHAR(50)'],
        ['description', 'TEXT'],
        ['category',    'VARCHAR(100)'],
        ['duration_label', 'VARCHAR(40)']
    ];
    t text[];
BEGIN
    FOREACH t SLICE 1 IN ARRAY targets LOOP
        SELECT data_type INTO rec
          FROM information_schema.columns
         WHERE table_name = 'courses' AND column_name = t[1];

        IF rec.data_type = 'bytea' THEN
            RAISE NOTICE 'Converting courses.% from bytea to %', t[1], t[2];
            EXECUTE format(
                'ALTER TABLE courses ALTER COLUMN %I TYPE %s USING convert_from(%I, ''UTF8'')',
                t[1], t[2], t[1]
            );
        END IF;
    END LOOP;
END $$;

-- Re-assert the NOT NULL + UNIQUE guarantees that V4 intended, in case
-- the drifted table lost them. Guarded so re-runs never fail.
DO $$
BEGIN
    -- name NOT NULL
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name='courses' AND column_name='name' AND is_nullable='YES') THEN
        BEGIN
            ALTER TABLE courses ALTER COLUMN name SET NOT NULL;
        EXCEPTION WHEN others THEN
            RAISE NOTICE 'Could not set courses.name NOT NULL: %', SQLERRM;
        END;
    END IF;

    -- code NOT NULL
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name='courses' AND column_name='code' AND is_nullable='YES') THEN
        BEGIN
            ALTER TABLE courses ALTER COLUMN code SET NOT NULL;
        EXCEPTION WHEN others THEN
            RAISE NOTICE 'Could not set courses.code NOT NULL: %', SQLERRM;
        END;
    END IF;

    -- unique code (constraint may already exist under a different name)
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_course_code'
    ) AND NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE tablename='courses' AND indexname='idx_course_code'
    ) THEN
        BEGIN
            CREATE UNIQUE INDEX idx_course_code ON courses (code);
        EXCEPTION WHEN others THEN
            RAISE NOTICE 'Could not create unique index on courses.code: %', SQLERRM;
        END;
    END IF;
END $$;
