-- Add proper IP address and detail columns to audit_logs
ALTER TABLE audit_logs ADD COLUMN ip_address VARCHAR(45);
ALTER TABLE audit_logs ADD COLUMN detail TEXT;

