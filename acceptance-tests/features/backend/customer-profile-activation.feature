@api @customer @activation
Feature: Customer Profile Activation (US-0002-06)
  As a Customer Management Service
  I want to activate the customer profile when the user's email is verified
  So that the customer can access all platform features

  Background:
    Given the Customer Service is available
    And the Identity Service is available
    And Kafka is available

  # AC-0002-06-01: Status Transition Timeliness
  @smoke
  Scenario: Activate customer profile within 5 seconds of email verification
    Given a user has registered with email "activation1@example.com"
    And the customer profile has been created with status "PENDING_VERIFICATION"
    When the user verifies their email
    Then the customer status should transition to "ACTIVE" within 5 seconds

  # AC-0002-06-02: Email Verification Flag
  Scenario: Set email verified flag to true on activation
    Given a user has registered with email "emailverified@example.com"
    And the customer profile has been created with status "PENDING_VERIFICATION"
    When the user verifies their email
    Then the customer profile should be activated
    And the email verified flag should be "true"

  # AC-0002-06-03: Event Publishing
  Scenario: Publish CustomerActivated event on activation
    Given a user has registered with email "eventpublish@example.com"
    And the customer profile has been created
    When the user verifies their email
    Then a CustomerActivated event should be published
    And the event should include the causationId linking to the UserActivated event

  # AC-0002-06-04: Platform Access
  Scenario: Customer can access platform after activation
    Given a user has registered with email "platformaccess@example.com"
    And the user has verified their email
    When the customer profile is activated
    Then the customer status should be "ACTIVE"
    And no activation-pending restrictions should apply

  # AC-0002-06-05: Activity Timestamp Update
  Scenario: Update lastActivityAt timestamp on activation
    Given a user has registered with email "timestamp@example.com"
    And the customer profile has been created
    When the user verifies their email
    Then the customer profile should be activated
    And the lastActivityAt timestamp should be updated to the activation time

  # AC-0002-06-06: Idempotent Processing
  @smoke
  Scenario: Handle duplicate UserActivated events idempotently
    Given a user has registered with email "idempotent@example.com"
    And the customer has been activated
    When the same UserActivated event is received again
    Then the customer should remain in "ACTIVE" status
    And no duplicate CustomerActivated events should be published

  # Additional test scenarios

  # Customer profile read model update
  Scenario: Update MongoDB read model on activation
    Given a user has registered with email "mongodb@example.com"
    And the customer profile has been created
    When the user verifies their email
    Then the customer profile should be activated
    And the MongoDB read model should show status "ACTIVE"
    And the MongoDB read model should show email verified as "true"

  # Event correlation
  Scenario: Maintain correlation ID through activation flow
    Given a user has registered with email "correlation@example.com"
    And the customer profile has been created
    When the user verifies their email
    Then the CustomerActivated event should have the same correlationId as the registration
