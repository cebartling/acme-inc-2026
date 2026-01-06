-- =============================================================================
-- V3: Create customer_number_sequences table
-- Atomic sequence generation for customer numbers (ACME-YYYYMM-NNNNNN)
-- =============================================================================

CREATE TABLE IF NOT EXISTS customer_number_sequences (
    year_month VARCHAR(6) PRIMARY KEY,  -- YYYYMM format (e.g., "202601")
    current_value INTEGER NOT NULL DEFAULT 0
);

-- Function for atomic sequence increment
-- Returns the next sequence number for the given year-month
-- Thread-safe using INSERT ... ON CONFLICT for atomic increment
CREATE OR REPLACE FUNCTION next_customer_number(p_year_month VARCHAR(6))
RETURNS INTEGER AS $$
DECLARE
    v_next_value INTEGER;
BEGIN
    -- Atomically insert or update and return the new value
    INSERT INTO customer_number_sequences (year_month, current_value)
    VALUES (p_year_month, 1)
    ON CONFLICT (year_month)
    DO UPDATE SET current_value = customer_number_sequences.current_value + 1
    RETURNING current_value INTO v_next_value;

    RETURN v_next_value;
END;
$$ LANGUAGE plpgsql;

COMMENT ON TABLE customer_number_sequences IS 'Sequence tracking for customer number generation per month';
COMMENT ON COLUMN customer_number_sequences.year_month IS 'Year-month in YYYYMM format';
COMMENT ON COLUMN customer_number_sequences.current_value IS 'Current sequence value for the month';
COMMENT ON FUNCTION next_customer_number(VARCHAR) IS 'Atomically increments and returns next customer number sequence for given year-month';
