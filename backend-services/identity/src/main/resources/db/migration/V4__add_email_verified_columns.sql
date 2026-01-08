-- V4: Add email verification status columns to users table
-- This migration adds columns to track email verification status
-- as part of US-0002-05: Email Verification Processing

-- Add email_verified boolean column (default false for existing users)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Add verified_at timestamp column (nullable, set when email is verified)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS verified_at TIMESTAMP WITH TIME ZONE;

-- Update existing active users to be marked as verified
-- (assuming they were activated through a previous verification mechanism)
UPDATE users
SET email_verified = TRUE,
    verified_at = updated_at
WHERE status = 'ACTIVE' AND email_verified = FALSE;

-- Index for filtering verified/unverified users
CREATE INDEX IF NOT EXISTS idx_users_email_verified ON users(email_verified);

-- Add comment for documentation
COMMENT ON COLUMN users.email_verified IS 'Whether the user has verified their email address';
COMMENT ON COLUMN users.verified_at IS 'Timestamp when the user verified their email address';
