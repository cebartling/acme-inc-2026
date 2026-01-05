-- =============================================================================
-- V1: Create customers table
-- Customer profile storage for the Customer Management Service
-- =============================================================================

-- Ensure the update_updated_at_column function exists
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create the customers table
CREATE TABLE IF NOT EXISTS customers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    customer_number VARCHAR(20) NOT NULL UNIQUE,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    phone_country_code VARCHAR(5),
    phone_number VARCHAR(20),
    phone_verified BOOLEAN DEFAULT FALSE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    type VARCHAR(50) NOT NULL DEFAULT 'INDIVIDUAL',
    date_of_birth DATE,
    gender VARCHAR(20),
    preferred_locale VARCHAR(10) NOT NULL DEFAULT 'en-US',
    timezone VARCHAR(50) NOT NULL DEFAULT 'UTC',
    preferred_currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    profile_completeness INTEGER NOT NULL DEFAULT 25,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_activity_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Create indexes for common queries
CREATE INDEX IF NOT EXISTS idx_customers_user_id ON customers(user_id);
CREATE INDEX IF NOT EXISTS idx_customers_email ON customers(email);
CREATE INDEX IF NOT EXISTS idx_customers_customer_number ON customers(customer_number);
CREATE INDEX IF NOT EXISTS idx_customers_status ON customers(status);

-- Create trigger to auto-update updated_at
CREATE TRIGGER update_customers_updated_at
    BEFORE UPDATE ON customers
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE customers IS 'Customer profile information for the ACME e-commerce platform';
COMMENT ON COLUMN customers.id IS 'Unique customer identifier (UUID v7, time-ordered)';
COMMENT ON COLUMN customers.user_id IS 'Corresponding user ID from Identity Service';
COMMENT ON COLUMN customers.customer_number IS 'Human-readable customer number (ACME-YYYYMM-NNNNNN)';
COMMENT ON COLUMN customers.profile_completeness IS 'Percentage of profile completion (0-100)';
