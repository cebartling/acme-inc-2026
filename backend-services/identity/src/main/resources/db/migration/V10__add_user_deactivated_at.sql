-- V10: Add deactivated_at column to users table
-- Supports US-0003-11 (PIN-91): Inactive Account Handling — surface the
-- deactivation timestamp to the customer so the signin UI can display it.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS deactivated_at TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN users.deactivated_at IS
    'Timestamp when the account transitioned to DEACTIVATED status. NULL otherwise.';
