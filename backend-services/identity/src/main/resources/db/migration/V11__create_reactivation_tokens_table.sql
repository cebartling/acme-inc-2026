-- V11: Create reactivation tokens table
-- Supports US-0003-11 (PIN-91): a DEACTIVATED customer requests reactivation
-- via POST /api/v1/auth/reactivate. The endpoint issues a single-use token
-- with its own TTL, kept separate from email-verification tokens so the
-- two flows can evolve and be audited independently.

CREATE TABLE IF NOT EXISTS reactivation_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reactivation_tokens_token ON reactivation_tokens(token);
CREATE INDEX IF NOT EXISTS idx_reactivation_tokens_user_id ON reactivation_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_reactivation_tokens_expires_at ON reactivation_tokens(expires_at);

COMMENT ON TABLE reactivation_tokens IS 'Single-use tokens issued when a deactivated customer requests reactivation.';
