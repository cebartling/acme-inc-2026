@api @identity @registration
Feature: User Registration API (US-0002-02)
  As a web application
  I want to submit registration data to the Identity Service
  So that a new user account is securely created and verified

  Background:
    Given the Identity Service is available

  # AC-0002-02-01: Duplicate Email Detection
  @smoke
  Scenario: Reject registration with duplicate email
    Given a user with email "existing@example.com" already exists
    When I submit a registration request with:
      | email          | existing@example.com |
      | password       | SecureP@ss123        |
      | firstName      | Jane                 |
      | lastName       | Doe                  |
      | tosAccepted    | true                 |
      | marketingOptIn | false                |
    Then the API should respond with status 409
    And the response should contain error "DUPLICATE_EMAIL"
    And the response should contain message "An account with this email already exists"

  # AC-0002-02-02: Password Hashing
  @smoke
  Scenario: Successfully register a new user with hashed password
    When I submit a registration request with:
      | email          | newuser@example.com |
      | password       | SecureP@ss123       |
      | firstName      | Jane                |
      | lastName       | Doe                 |
      | tosAccepted    | true                |
      | marketingOptIn | false               |
    Then the API should respond with status 201
    And the response should contain a valid UUID for "userId"
    And the response should contain "email" with value "newuser@example.com"
    And the response should contain "status" with value "PENDING_VERIFICATION"
    And the user's password should be stored as an Argon2id hash

  # AC-0002-02-03: User ID Generation
  Scenario: Generate UUID v7 for new user
    When I submit a registration request with:
      | email          | uuidtest@example.com |
      | password       | SecureP@ss123        |
      | firstName      | Test                 |
      | lastName       | User                 |
      | tosAccepted    | true                 |
      | marketingOptIn | false                |
    Then the API should respond with status 201
    And the "userId" should be a valid UUID v7

  # AC-0002-02-04: Event Store Persistence
  Scenario: Persist UserRegistered event to event store
    When I submit a registration request with:
      | email          | eventstore@example.com |
      | password       | SecureP@ss123          |
      | firstName      | Event                  |
      | lastName       | Store                  |
      | tosAccepted    | true                   |
      | marketingOptIn | true                   |
    Then the API should respond with status 201
    And a UserRegistered event should be persisted in the event store

  # AC-0002-02-05: Event Bus Publishing
  Scenario: Publish UserRegistered event to Kafka
    When I submit a registration request with:
      | email          | kafka@example.com |
      | password       | SecureP@ss123     |
      | firstName      | Kafka             |
      | lastName       | Test              |
      | tosAccepted    | true              |
      | marketingOptIn | false             |
    Then the API should respond with status 201
    And a UserRegistered event should be published to topic "identity.user.events"

  # AC-0002-02-06: Verification Token Generation
  Scenario: Generate verification token for new user
    When I submit a registration request with:
      | email          | verify@example.com |
      | password       | SecureP@ss123      |
      | firstName      | Verify             |
      | lastName       | Token              |
      | tosAccepted    | true               |
      | marketingOptIn | false              |
    Then the API should respond with status 201
    And a verification token should be created for the user
    And the verification token should expire in 24 hours

  # AC-0002-02-08: Terms of Service Recording
  Scenario: Record Terms of Service acceptance timestamp
    When I submit a registration request with TOS accepted at "2026-01-02T10:30:00Z"
    Then the API should respond with status 201
    And the user's TOS acceptance should be recorded with timestamp "2026-01-02T10:30:00Z"

  # AC-0002-02-09: Registration Source Tracking
  Scenario Outline: Track registration source
    When I submit a registration request with source "<source>"
    Then the API should respond with status 201
    And the registration source should be "<source>"

    Examples:
      | source |
      | WEB    |
      | MOBILE |
      | API    |

  # Validation Error Scenarios
  @smoke
  Scenario: Reject registration with invalid email format
    When I submit a registration request with:
      | email          | invalid-email |
      | password       | SecureP@ss123 |
      | firstName      | Jane          |
      | lastName       | Doe           |
      | tosAccepted    | true          |
      | marketingOptIn | false         |
    Then the API should respond with status 400
    And the response should contain error "VALIDATION_ERROR"
    And the response should contain a validation error for field "email"

  Scenario: Reject registration with weak password
    When I submit a registration request with:
      | email          | weak@example.com |
      | password       | weak             |
      | firstName      | Jane             |
      | lastName       | Doe              |
      | tosAccepted    | true             |
      | marketingOptIn | false            |
    Then the API should respond with status 400
    And the response should contain error "VALIDATION_ERROR"
    And the response should contain a validation error for field "password"

  Scenario: Reject registration without TOS acceptance
    When I submit a registration request with:
      | email          | notos@example.com |
      | password       | SecureP@ss123     |
      | firstName      | Jane              |
      | lastName       | Doe               |
      | tosAccepted    | false             |
      | marketingOptIn | false             |
    Then the API should respond with status 400
    And the response should contain error "VALIDATION_ERROR"
    And the response should contain a validation error for field "tosAccepted"

  # Rate Limiting
  # Note: This test requires RATE_LIMITING_ENABLED=true on the Identity Service
  @slow @rate-limiting
  Scenario: Rate limit registration requests
    Given I have made 5 registration requests from the same IP
    When I submit another registration request
    Then the API should respond with status 429
    And the response should contain error "RATE_LIMIT_EXCEEDED"

  # Correlation ID Tracking
  Scenario: Accept and propagate correlation ID
    When I submit a registration request with correlation ID "550e8400-e29b-41d4-a716-446655440000"
    Then the API should respond with status 201
    And the UserRegistered event should contain correlation ID "550e8400-e29b-41d4-a716-446655440000"
