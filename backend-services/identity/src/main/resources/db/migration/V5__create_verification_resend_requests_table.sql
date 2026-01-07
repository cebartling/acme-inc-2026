-- V5: Create verification resend requests table for rate limiting
-- This migration adds a table to track resend verification email requests
-- for rate limiting purposes as part of US-0002-05: Email Verification Processing

CREATE TABLE IF NOT EXISTS verification_resend_requests (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45),
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index for rate limiting queries by email within time window
CREATE INDEX IF NOT EXISTS idx_resend_requests_email_time
    ON verification_resend_requests(email, requested_at);

-- Index for rate limiting queries by IP within time window
CREATE INDEX IF NOT EXISTS idx_resend_requests_ip_time
    ON verification_resend_requests(ip_address, requested_at);

-- Add comment for documentation
COMMENT ON TABLE verification_resend_requests IS 'Tracks resend verification email requests for rate limiting';
COMMENT ON COLUMN verification_resend_requests.email IS 'Email address that requested a resend';
COMMENT ON COLUMN verification_resend_requests.ip_address IS 'IP address from which the request originated';
COMMENT ON COLUMN verification_resend_requests.requested_at IS 'Timestamp when the resend was requested';
