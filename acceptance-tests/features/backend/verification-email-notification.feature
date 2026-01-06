@api @notification @email
Feature: Verification Email Notification (US-0002-04)
  As a Notification Service
  I want to send a verification email when a user registers
  So that the user can verify their email address and activate their account

  Background:
    Given the Notification Service is available
    And the Identity Service is available
    And Kafka is available
    And SendGrid sandbox mode is enabled

  # AC-0002-04-01: Email Delivery Timeliness
  @smoke
  Scenario: Send verification email within 30 seconds of registration
    When a user registers with email "timeliness@example.com"
    Then a verification email should be sent within 30 seconds
    And the email should be addressed to "timeliness@example.com"

  # AC-0002-04-02: Personalized Recipient Name
  Scenario: Include personalized greeting with user's first name
    When a user registers with:
      | email     | personal@example.com |
      | firstName | Alice                |
      | lastName  | Smith                |
    Then a verification email should be sent
    And the email greeting should include "Alice"
    And the email should be addressed to "personal@example.com"

  # AC-0002-04-03: Unique Verification Link
  Scenario: Include unique verification link in email
    When a user registers with email "uniquelink@example.com"
    Then a verification email should be sent
    And the email should contain a verification link
    And the verification token should be at least 32 characters

  # AC-0002-04-04: Expiration Notice
  Scenario: Clearly state link expiration time
    When a user registers with email "expiration@example.com"
    Then a verification email should be sent
    And the email should state "This link expires in 24 hours"

  # AC-0002-04-06: Delivery Tracking
  @smoke
  Scenario: Track email delivery status as SENT
    When a user registers with email "tracking@example.com"
    Then a verification email should be sent
    And the delivery status should be recorded as "SENT"
    And the provider message ID should be recorded

  # AC-0002-04-07: Retry on Failure
  Scenario: Retry email sending on provider failure
    Given the email provider returns an error
    When a user registers with email "retry@example.com"
    Then the service should retry up to 3 times
    And retry intervals should be 5 minutes apart
    And each retry should be logged with attempt number

  # AC-0002-04-08: Localization Support
  Scenario: Support default locale (en-US)
    When a user registers with email "locale@example.com"
    Then a verification email should be sent
    And the email should use locale "en-US"

  # AC-0002-04-09: No Unsubscribe Link
  Scenario: Verification email has no unsubscribe link
    When a user registers with email "transactional@example.com"
    Then a verification email should be sent
    And the email should not contain an unsubscribe link
    And the email should be classified as "transactional"

  # NotificationSent Event Publishing
  Scenario: Publish NotificationSent event after sending email
    When a user registers with email "event@example.com"
    Then a verification email should be sent
    And a NotificationSent event should be published
    And the event should contain:
      | notificationType | EMAIL_VERIFICATION  |
      | recipientEmail   | event@example.com   |
      | status           | SENT                |

  # Idempotent Email Sending
  @smoke
  Scenario: Send only one verification email per user registration
    Given a user has already received a verification email for "idempotent@example.com"
    When the same UserRegistered event is received again
    Then no additional verification email should be sent
    And only one delivery record should exist

  # Event Correlation
  Scenario: Maintain correlation ID from UserRegistered event
    When a user registers with email "correlation@example.com"
    Then a verification email should be sent
    And the email header should contain the correlation ID
    And the NotificationSent event should have the same correlation ID
