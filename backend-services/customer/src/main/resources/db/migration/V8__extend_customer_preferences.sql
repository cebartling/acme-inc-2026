-- =============================================================================
-- V8: Extend customer_preferences table and create preference_change_log
-- US-0002-10: Preference Configuration
-- =============================================================================

-- Add new columns to customer_preferences
ALTER TABLE customer_preferences
    ADD COLUMN IF NOT EXISTS notification_frequency VARCHAR(20) NOT NULL DEFAULT 'IMMEDIATE',
    ADD COLUMN IF NOT EXISTS allow_personalization BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS language VARCHAR(10) NOT NULL DEFAULT 'en-US',
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    ADD COLUMN IF NOT EXISTS timezone VARCHAR(50) NOT NULL DEFAULT 'UTC';

-- Add check constraint for notification_frequency
ALTER TABLE customer_preferences
    ADD CONSTRAINT chk_notification_frequency
    CHECK (notification_frequency IN ('IMMEDIATE', 'DAILY_DIGEST', 'WEEKLY_DIGEST'));

-- Create preference_change_log table for GDPR compliance
CREATE TABLE IF NOT EXISTS preference_change_log (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    preference_name VARCHAR(50) NOT NULL,
    old_value TEXT,
    new_value TEXT NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ip_address VARCHAR(45),
    user_agent TEXT
);

-- Create indexes for preference_change_log
CREATE INDEX IF NOT EXISTS idx_pref_log_customer ON preference_change_log(customer_id);
CREATE INDEX IF NOT EXISTS idx_pref_log_time ON preference_change_log(changed_at);
CREATE INDEX IF NOT EXISTS idx_pref_log_customer_time ON preference_change_log(customer_id, changed_at DESC);

-- Add comments
COMMENT ON TABLE preference_change_log IS 'GDPR compliance audit log for preference changes';
COMMENT ON COLUMN preference_change_log.preference_name IS 'Dot-notation path to the preference (e.g., communication.sms)';
COMMENT ON COLUMN preference_change_log.ip_address IS 'Client IP address when change was made';
COMMENT ON COLUMN preference_change_log.user_agent IS 'Client user agent string when change was made';

COMMENT ON COLUMN customer_preferences.notification_frequency IS 'How often to batch notifications: IMMEDIATE, DAILY_DIGEST, WEEKLY_DIGEST';
COMMENT ON COLUMN customer_preferences.allow_personalization IS 'Allow personalized recommendations';
COMMENT ON COLUMN customer_preferences.language IS 'Preferred display language (e.g., en-US)';
COMMENT ON COLUMN customer_preferences.currency IS 'Preferred currency for prices (e.g., USD)';
COMMENT ON COLUMN customer_preferences.timezone IS 'Preferred timezone (e.g., America/New_York)';
