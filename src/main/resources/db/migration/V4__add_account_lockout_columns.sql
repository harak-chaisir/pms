-- Account lockout support for brute-force protection (HIPAA §164.312(d))
ALTER TABLE users ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN lock_expires_at TIMESTAMP;

