-- V6: Add authentication fields for signin flow
-- Supports credential validation (US-0003-02), rate limiting (US-0003-03), and account lockout (US-0003-04)

-- Add failed_attempts column for tracking failed signin attempts
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_attempts INTEGER NOT NULL DEFAULT 0;

-- Add locked_until column for account lockout functionality
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP WITH TIME ZONE;

-- Add mfa_enabled column for multi-factor authentication support
ALTER TABLE users ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Add last_login_at column for tracking last successful signin
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP WITH TIME ZONE;

-- Add device_fingerprint column for fraud detection (optional capture during signin)
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_device_fingerprint VARCHAR(255);

-- Create index for lockout queries
CREATE INDEX IF NOT EXISTS idx_users_locked_until ON users(locked_until)
    WHERE locked_until IS NOT NULL;

-- Create index for failed attempts queries (for monitoring/security)
CREATE INDEX IF NOT EXISTS idx_users_failed_attempts ON users(failed_attempts)
    WHERE failed_attempts > 0;

-- Add comment for documentation
COMMENT ON COLUMN users.failed_attempts IS 'Number of consecutive failed signin attempts. Reset to 0 on successful signin.';
COMMENT ON COLUMN users.locked_until IS 'Account locked until this timestamp. NULL means not locked.';
COMMENT ON COLUMN users.mfa_enabled IS 'Whether multi-factor authentication is enabled for this user.';
COMMENT ON COLUMN users.last_login_at IS 'Timestamp of the last successful signin.';
COMMENT ON COLUMN users.last_device_fingerprint IS 'Device fingerprint from the last signin attempt for fraud detection.';
