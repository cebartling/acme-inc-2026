-- V1__create_notification_tables.sql
-- Create tables for the Notification Service

-- Notification deliveries table for tracking email delivery status
CREATE TABLE notification_deliveries (
    id UUID PRIMARY KEY,
    notification_type VARCHAR(50) NOT NULL,
    recipient_id UUID NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    provider_message_id VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 1,
    sent_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    bounced_at TIMESTAMP WITH TIME ZONE,
    bounce_reason VARCHAR(500),
    correlation_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Indexes for common queries
CREATE INDEX idx_deliveries_recipient ON notification_deliveries(recipient_id);
CREATE INDEX idx_deliveries_status ON notification_deliveries(status);
CREATE INDEX idx_deliveries_correlation ON notification_deliveries(correlation_id);
CREATE INDEX idx_deliveries_type_recipient ON notification_deliveries(notification_type, recipient_id);

-- Processed events table for idempotency tracking
CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index for event type queries
CREATE INDEX idx_processed_events_type ON processed_events(event_type);

-- Trigger function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for notification_deliveries
CREATE TRIGGER update_notification_deliveries_updated_at
    BEFORE UPDATE ON notification_deliveries
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Comments for documentation
COMMENT ON TABLE notification_deliveries IS 'Tracks notification delivery attempts and status';
COMMENT ON COLUMN notification_deliveries.id IS 'Unique identifier for the delivery record';
COMMENT ON COLUMN notification_deliveries.notification_type IS 'Type of notification (EMAIL_VERIFICATION, PASSWORD_RESET, etc.)';
COMMENT ON COLUMN notification_deliveries.recipient_id IS 'User ID of the notification recipient';
COMMENT ON COLUMN notification_deliveries.recipient_email IS 'Email address of the recipient';
COMMENT ON COLUMN notification_deliveries.provider_message_id IS 'Message ID returned by the email provider (SendGrid)';
COMMENT ON COLUMN notification_deliveries.status IS 'Delivery status (PENDING, SENT, DELIVERED, BOUNCED, FAILED)';
COMMENT ON COLUMN notification_deliveries.attempt_count IS 'Number of send attempts made';
COMMENT ON COLUMN notification_deliveries.sent_at IS 'When the notification was accepted by the provider';
COMMENT ON COLUMN notification_deliveries.delivered_at IS 'When delivery was confirmed';
COMMENT ON COLUMN notification_deliveries.bounced_at IS 'When a bounce was detected';
COMMENT ON COLUMN notification_deliveries.bounce_reason IS 'Reason for the bounce';
COMMENT ON COLUMN notification_deliveries.correlation_id IS 'Distributed tracing correlation ID';

COMMENT ON TABLE processed_events IS 'Tracks processed Kafka events for idempotency';
COMMENT ON COLUMN processed_events.event_id IS 'Unique identifier of the processed event';
COMMENT ON COLUMN processed_events.event_type IS 'Type of event that was processed';
COMMENT ON COLUMN processed_events.processed_at IS 'When the event was processed';
