-- V8: Add MFA SMS support
-- Supports SMS verification (US-0003-06)

-- Add phone number column to users table for SMS MFA
-- Phone numbers are stored in E.164 format (+1234567890)
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_number VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS sms_mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Create index on phone_number for lookups
CREATE INDEX IF NOT EXISTS idx_users_phone_number ON users(phone_number) WHERE phone_number IS NOT NULL;

-- Add SMS-specific columns to mfa_challenges table
-- code_hash stores the SHA-256 hash of the SMS verification code
ALTER TABLE mfa_challenges ADD COLUMN IF NOT EXISTS code_hash CHAR(64);
ALTER TABLE mfa_challenges ADD COLUMN IF NOT EXISTS last_sent_at TIMESTAMP WITH TIME ZONE;

-- Create sms_rate_limits table for tracking SMS sending rate limits per user
-- Uses sliding window rate limiting: 3 SMS per hour
CREATE TABLE IF NOT EXISTS sms_rate_limits (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    phone_number VARCHAR(20) NOT NULL
);

-- Create index on user_id and sent_at for rate limit queries
CREATE INDEX IF NOT EXISTS idx_sms_rate_limits_user_time ON sms_rate_limits(user_id, sent_at DESC);

-- Create index on sent_at for cleanup of old entries
CREATE INDEX IF NOT EXISTS idx_sms_rate_limits_sent_at ON sms_rate_limits(sent_at);

-- Add comments for documentation
COMMENT ON COLUMN users.phone_number IS 'Phone number in E.164 format for SMS MFA. Must be verified before enabling SMS MFA.';
COMMENT ON COLUMN users.phone_verified IS 'Whether the phone number has been verified via SMS confirmation.';
COMMENT ON COLUMN users.sms_mfa_enabled IS 'Whether SMS-based MFA is enabled for this user.';
COMMENT ON COLUMN mfa_challenges.code_hash IS 'SHA-256 hash of the SMS verification code. NULL for TOTP challenges.';
COMMENT ON COLUMN mfa_challenges.last_sent_at IS 'Timestamp when the SMS code was last sent. Used for resend cooldown.';
COMMENT ON TABLE sms_rate_limits IS 'Tracks SMS sending history for rate limiting. Entries older than 1 hour can be cleaned up.';
