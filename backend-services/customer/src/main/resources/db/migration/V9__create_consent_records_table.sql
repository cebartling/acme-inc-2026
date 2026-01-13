-- =============================================================================
-- V9: Create consent_records table for GDPR-compliant consent management
-- US-0002-11: Consent Management
-- =============================================================================

-- Immutable consent records table (append-only)
-- This table stores the complete history of all consent changes.
-- Records are NEVER updated or deleted - all changes are new inserts.
CREATE TABLE IF NOT EXISTS consent_records (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    consent_type VARCHAR(50) NOT NULL,
    granted BOOLEAN NOT NULL,
    source VARCHAR(50) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    user_agent TEXT,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version INTEGER NOT NULL,

    -- Ensure valid consent types
    CONSTRAINT chk_consent_type CHECK (consent_type IN (
        'DATA_PROCESSING',
        'MARKETING',
        'ANALYTICS',
        'THIRD_PARTY',
        'PERSONALIZATION'
    )),

    -- Ensure valid sources
    CONSTRAINT chk_consent_source CHECK (source IN (
        'REGISTRATION',
        'PROFILE_WIZARD',
        'PRIVACY_SETTINGS',
        'API',
        'SYSTEM_EXPIRATION',
        'ACCOUNT_CLOSURE'
    )),

    -- Ensure version is positive
    CONSTRAINT chk_consent_version_positive CHECK (version > 0)
);

-- Create indexes for common query patterns
-- Index for finding all consents for a customer
CREATE INDEX IF NOT EXISTS idx_consent_customer ON consent_records(customer_id);

-- Index for finding consents by type for a customer
CREATE INDEX IF NOT EXISTS idx_consent_customer_type ON consent_records(customer_id, consent_type);

-- Index for finding latest consent by type (for current status queries)
CREATE INDEX IF NOT EXISTS idx_consent_customer_type_time ON consent_records(customer_id, consent_type, created_at DESC);

-- Index for time-based queries (e.g., recent consent changes)
CREATE INDEX IF NOT EXISTS idx_consent_created_at ON consent_records(created_at);

-- Index for expiration queries
CREATE INDEX IF NOT EXISTS idx_consent_expires_at ON consent_records(expires_at)
    WHERE expires_at IS NOT NULL;

-- Add comments for documentation
COMMENT ON TABLE consent_records IS 'Immutable append-only table for GDPR-compliant consent tracking. Each row represents a consent grant or revocation.';
COMMENT ON COLUMN consent_records.id IS 'UUID v7 primary key for this consent record';
COMMENT ON COLUMN consent_records.customer_id IS 'Foreign key to the customer this consent belongs to';
COMMENT ON COLUMN consent_records.consent_type IS 'Type of consent: DATA_PROCESSING, MARKETING, ANALYTICS, THIRD_PARTY, PERSONALIZATION';
COMMENT ON COLUMN consent_records.granted IS 'Whether consent was granted (true) or revoked (false)';
COMMENT ON COLUMN consent_records.source IS 'Where the consent change originated: REGISTRATION, PROFILE_WIZARD, PRIVACY_SETTINGS, API, SYSTEM_EXPIRATION, ACCOUNT_CLOSURE';
COMMENT ON COLUMN consent_records.ip_address IS 'Client IP address when consent was changed (IPv4 or IPv6)';
COMMENT ON COLUMN consent_records.user_agent IS 'Client user agent string when consent was changed';
COMMENT ON COLUMN consent_records.expires_at IS 'When this consent expires (NULL = never expires). Only DATA_PROCESSING has no expiration.';
COMMENT ON COLUMN consent_records.created_at IS 'Timestamp when this record was created (immutable)';
COMMENT ON COLUMN consent_records.version IS 'Incrementing version number for this consent type per customer';

-- Create a function to get the current consent status for a customer
-- This is more efficient than a materialized view for our use case
CREATE OR REPLACE FUNCTION get_current_consents(p_customer_id UUID)
RETURNS TABLE (
    consent_type VARCHAR(50),
    granted BOOLEAN,
    source VARCHAR(50),
    expires_at TIMESTAMP WITH TIME ZONE,
    last_updated TIMESTAMP WITH TIME ZONE,
    version INTEGER,
    is_required BOOLEAN
) AS $$
BEGIN
    RETURN QUERY
    SELECT DISTINCT ON (cr.consent_type)
        cr.consent_type,
        cr.granted,
        cr.source,
        cr.expires_at,
        cr.created_at AS last_updated,
        cr.version,
        CASE WHEN cr.consent_type = 'DATA_PROCESSING' THEN TRUE ELSE FALSE END AS is_required
    FROM consent_records cr
    WHERE cr.customer_id = p_customer_id
    ORDER BY cr.consent_type, cr.created_at DESC;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_current_consents(UUID) IS 'Returns the current consent status for each consent type for a customer';

-- Create a function to get the consent history for GDPR export
CREATE OR REPLACE FUNCTION get_consent_history(p_customer_id UUID)
RETURNS TABLE (
    consent_id UUID,
    consent_type VARCHAR(50),
    granted BOOLEAN,
    timestamp TIMESTAMP WITH TIME ZONE,
    source VARCHAR(50),
    ip_address VARCHAR(45),
    user_agent TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        cr.id AS consent_id,
        cr.consent_type,
        cr.granted,
        cr.created_at AS timestamp,
        cr.source,
        cr.ip_address,
        cr.user_agent
    FROM consent_records cr
    WHERE cr.customer_id = p_customer_id
    ORDER BY cr.created_at ASC;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_consent_history(UUID) IS 'Returns the complete consent history for GDPR data subject requests';

-- Create a function to get the current version for a consent type
CREATE OR REPLACE FUNCTION get_consent_version(p_customer_id UUID, p_consent_type VARCHAR(50))
RETURNS INTEGER AS $$
DECLARE
    current_version INTEGER;
BEGIN
    SELECT cr.version INTO current_version
    FROM consent_records cr
    WHERE cr.customer_id = p_customer_id
      AND cr.consent_type = p_consent_type
    ORDER BY cr.created_at DESC
    LIMIT 1;

    RETURN COALESCE(current_version, 0);
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_consent_version(UUID, VARCHAR) IS 'Returns the current version number for a consent type';
