-- PHI encryption stores ciphertext, so date_of_birth must be VARCHAR
ALTER TABLE patients ALTER COLUMN date_of_birth TYPE VARCHAR(255) USING date_of_birth::VARCHAR;

