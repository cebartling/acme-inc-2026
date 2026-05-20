-- V12: Create password reset tokens table
-- Supports US-0003-13 (PIN-93): a customer who has forgotten their password
-- requests a reset via POST /api/v1/auth/password-reset. The endpoint issues
-- a single-use token with a 1-hour TTL.

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_token ON password_reset_tokens(token);
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user_id ON password_reset_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_expires_at ON password_reset_tokens(expires_at);

COMMENT ON TABLE password_reset_tokens IS 'Single-use tokens issued when a customer requests a password reset.';

-- Per-email request log for enforcing the 3-per-hour rate limit. Logged for
-- every attempt (real or not) so enumeration is impossible and limits cannot
-- be bypassed by guessing emails.
CREATE TABLE IF NOT EXISTS password_reset_request_logs (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45),
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_password_reset_request_logs_email_requested_at
    ON password_reset_request_logs(email, requested_at);

COMMENT ON TABLE password_reset_request_logs IS 'Per-email log of password-reset requests, used for rate limiting (3 per hour).';
