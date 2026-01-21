-- V9: Remove phone_number from SMS rate limits table
-- Security improvement: phone_number is not needed for rate limiting
-- (user_id is sufficient) and storing it creates a data exposure risk.

-- Drop the phone_number column from sms_rate_limits
ALTER TABLE sms_rate_limits DROP COLUMN IF EXISTS phone_number;

-- Update comment to reflect the change
COMMENT ON TABLE sms_rate_limits IS 'Tracks SMS sending history for rate limiting by user_id. Entries older than 1 hour can be cleaned up.';
