-- =============================================================================
-- V2: Create customer_preferences table
-- Communication and privacy preferences for customers
-- =============================================================================

CREATE TABLE IF NOT EXISTS customer_preferences (
    customer_id UUID PRIMARY KEY REFERENCES customers(id) ON DELETE CASCADE,
    email_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    sms_notifications BOOLEAN NOT NULL DEFAULT FALSE,
    push_notifications BOOLEAN NOT NULL DEFAULT FALSE,
    marketing_communications BOOLEAN NOT NULL DEFAULT FALSE,
    share_data_with_partners BOOLEAN NOT NULL DEFAULT FALSE,
    allow_analytics BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Create trigger to auto-update updated_at
CREATE TRIGGER update_customer_preferences_updated_at
    BEFORE UPDATE ON customer_preferences
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE customer_preferences IS 'Customer communication and privacy preferences';
COMMENT ON COLUMN customer_preferences.marketing_communications IS 'Whether customer opted in to marketing during registration';
