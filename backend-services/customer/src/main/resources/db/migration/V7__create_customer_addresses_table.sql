-- Customer addresses table for managing shipping and billing addresses
-- Supports address validation, geocoordinates, and default address per type

CREATE TABLE customer_addresses (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL CHECK (type IN ('SHIPPING', 'BILLING')),
    label VARCHAR(50),
    street_line1 VARCHAR(100) NOT NULL,
    street_line2 VARCHAR(100),
    city VARCHAR(50) NOT NULL,
    state VARCHAR(50) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    country VARCHAR(2) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_validated BOOLEAN NOT NULL DEFAULT FALSE,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    validation_details JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Comments for documentation
COMMENT ON TABLE customer_addresses IS 'Customer shipping and billing addresses with validation status';
COMMENT ON COLUMN customer_addresses.id IS 'UUID v7 (time-ordered) primary key';
COMMENT ON COLUMN customer_addresses.customer_id IS 'Reference to the customer who owns this address';
COMMENT ON COLUMN customer_addresses.type IS 'Address type: SHIPPING or BILLING';
COMMENT ON COLUMN customer_addresses.label IS 'Human-readable label (e.g., Home, Work)';
COMMENT ON COLUMN customer_addresses.street_line1 IS 'Primary street address';
COMMENT ON COLUMN customer_addresses.street_line2 IS 'Secondary address line (apartment, suite, etc.)';
COMMENT ON COLUMN customer_addresses.city IS 'City name';
COMMENT ON COLUMN customer_addresses.state IS 'State or province';
COMMENT ON COLUMN customer_addresses.postal_code IS 'Postal/ZIP code';
COMMENT ON COLUMN customer_addresses.country IS 'ISO 3166-1 alpha-2 country code';
COMMENT ON COLUMN customer_addresses.is_default IS 'Whether this is the default address for its type';
COMMENT ON COLUMN customer_addresses.is_validated IS 'Whether the address has been validated against postal standards';
COMMENT ON COLUMN customer_addresses.latitude IS 'Geographic latitude coordinate';
COMMENT ON COLUMN customer_addresses.longitude IS 'Geographic longitude coordinate';
COMMENT ON COLUMN customer_addresses.validation_details IS 'JSON details from address validation service';

-- Index for efficient lookups by customer
CREATE INDEX idx_addresses_customer ON customer_addresses(customer_id);

-- Index for queries by customer and type (e.g., get all shipping addresses)
CREATE INDEX idx_addresses_customer_type ON customer_addresses(customer_id, type);

-- Index for finding default addresses
CREATE INDEX idx_addresses_customer_default ON customer_addresses(customer_id, type, is_default) WHERE is_default = TRUE;

-- Unique constraint: only one address per customer can have a specific label
CREATE UNIQUE INDEX idx_addresses_customer_label ON customer_addresses(customer_id, label) WHERE label IS NOT NULL;

-- Auto-update updated_at timestamp trigger
CREATE TRIGGER update_customer_addresses_updated_at
    BEFORE UPDATE ON customer_addresses
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
