-- V7: Add MFA TOTP support
-- Supports TOTP verification (US-0003-05)

-- Add totp_secret column to users table for storing encrypted TOTP secrets
-- IMPORTANT: This column stores application-level encrypted TOTP secrets.
-- The encryption/decryption is handled by the application layer (TotpSecretEncryptor).
-- Raw base32 TOTP secrets must NEVER be stored directly in this column.
-- If migrating from a system with unencrypted secrets, a data migration must be
-- performed to encrypt existing values before enabling this feature.
ALTER TABLE users ADD COLUMN IF NOT EXISTS totp_secret VARCHAR(255);

-- Add totp_enabled column to track if TOTP is specifically enabled (separate from mfa_enabled)
ALTER TABLE users ADD COLUMN IF NOT EXISTS totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Create mfa_challenges table for tracking active MFA challenges
CREATE TABLE IF NOT EXISTS mfa_challenges (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(100) NOT NULL UNIQUE,
    method VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Create index on token for fast lookups during verification
CREATE INDEX IF NOT EXISTS idx_mfa_challenges_token ON mfa_challenges(token);

-- Create index on user_id for finding user's active challenges
CREATE INDEX IF NOT EXISTS idx_mfa_challenges_user_id ON mfa_challenges(user_id);

-- Create index on expires_at for cleanup of expired challenges
CREATE INDEX IF NOT EXISTS idx_mfa_challenges_expires_at ON mfa_challenges(expires_at);

-- Create used_totp_codes table for preventing code replay within time window
CREATE TABLE IF NOT EXISTS used_totp_codes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash CHAR(64) NOT NULL,  -- SHA-256 hex is always exactly 64 characters
    time_step BIGINT NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (user_id, code_hash, time_step)
);

-- Create index on user_id and time_step for fast lookups
CREATE INDEX IF NOT EXISTS idx_used_totp_codes_lookup ON used_totp_codes(user_id, time_step);

-- Create index on expires_at for cleanup of expired entries
CREATE INDEX IF NOT EXISTS idx_used_totp_codes_expires_at ON used_totp_codes(expires_at);

-- Add comments for documentation
COMMENT ON COLUMN users.totp_secret IS 'Application-layer encrypted TOTP secret for authenticator app. Must be encrypted via TotpSecretEncryptor before storage. NULL if TOTP not configured.';
COMMENT ON COLUMN users.totp_enabled IS 'Whether TOTP-based MFA is enabled for this user.';
COMMENT ON TABLE mfa_challenges IS 'Stores active MFA challenges during authentication flow. Challenges expire after 5 minutes.';
COMMENT ON TABLE used_totp_codes IS 'Tracks used TOTP codes to prevent replay attacks within the validity window.';
