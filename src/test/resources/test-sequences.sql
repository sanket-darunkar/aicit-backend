-- Sequences required by SequenceRepository (Flyway is disabled in the test profile,
-- so these are created here after Hibernate builds the schema).
CREATE SEQUENCE IF NOT EXISTS cert_number_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS batch_code_seq  START WITH 1 INCREMENT BY 1;
