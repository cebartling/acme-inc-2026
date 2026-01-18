@api @identity @signin @authentication
Feature: Credential Validation API (US-0003-02)
  As the Identity Management Service
  I want to securely validate customer credentials
  So that only authenticated customers can access their accounts

  Background:
    Given the Identity Service is available

  # AC-0003-02-01: Password Verification with Argon2id
  @smoke
  Scenario: Successfully authenticate with valid credentials
    Given an active user exists with email "customer@acme.com" and password "ValidP@ss123!"
    When I submit a signin request with:
      | email    | customer@acme.com |
      | password | ValidP@ss123!     |
    Then the API should respond with status 200
    And the response should contain "status" with value "SUCCESS"
    And the response should contain a valid UUID for "userId"
    And the response should contain "expiresIn"

  # AC-0003-02-02: Timing Attack Prevention
  Scenario: Response time is consistent for non-existent users
    When I submit a signin request with:
      | email    | nonexistent@example.com |
      | password | anypassword             |
    Then the API should respond with status 401
    And the response should contain error "INVALID_CREDENTIALS"
    And the response time should be within 50ms of a valid user response

  # AC-0003-02-03: Failed Attempts Counter
  Scenario: Increment failed attempts on invalid password
    Given an active user exists with email "attempts@acme.com" and password "CorrectP@ss123!"
    When I submit a signin request with:
      | email    | attempts@acme.com |
      | password | WrongPassword     |
    Then the API should respond with status 401
    And the response should contain error "INVALID_CREDENTIALS"
    And the response should contain "remainingAttempts" with value 4

  Scenario: Show decreasing remaining attempts on repeated failures
    Given an active user exists with email "countdown@acme.com" and password "CorrectP@ss123!"
    And the user has 3 failed signin attempts
    When I submit a signin request with:
      | email    | countdown@acme.com |
      | password | WrongPassword      |
    Then the API should respond with status 401
    And the response should contain "remainingAttempts" with value 1

  # AC-0003-02-04: Reset Failed Attempts on Success
  @smoke
  Scenario: Reset failed attempts on successful signin
    Given an active user exists with email "reset@acme.com" and password "CorrectP@ss123!"
    And the user has 3 failed signin attempts
    When I submit a signin request with:
      | email    | reset@acme.com    |
      | password | CorrectP@ss123!   |
    Then the API should respond with status 200
    And the user's failed attempts should be reset to 0

  # AC-0003-02-05: Account Status Check
  Scenario: Reject signin for PENDING_VERIFICATION account
    Given a user exists with email "pending@acme.com" and status "PENDING_VERIFICATION"
    And the user has password "ValidP@ss123!"
    When I submit a signin request with:
      | email    | pending@acme.com |
      | password | ValidP@ss123!    |
    Then the API should respond with status 403
    And the response should contain error "ACCOUNT_INACTIVE"
    And the response should contain "reason" with value "PENDING_VERIFICATION"

  Scenario: Reject signin for SUSPENDED account
    Given a user exists with email "suspended@acme.com" and status "SUSPENDED"
    And the user has password "ValidP@ss123!"
    When I submit a signin request with:
      | email    | suspended@acme.com |
      | password | ValidP@ss123!      |
    Then the API should respond with status 403
    And the response should contain error "ACCOUNT_INACTIVE"
    And the response should contain "reason" with value "SUSPENDED"
    And the response should contain "supportUrl"

  Scenario: Reject signin for DEACTIVATED account
    Given a user exists with email "deactivated@acme.com" and status "DEACTIVATED"
    And the user has password "ValidP@ss123!"
    When I submit a signin request with:
      | email    | deactivated@acme.com |
      | password | ValidP@ss123!        |
    Then the API should respond with status 403
    And the response should contain error "ACCOUNT_INACTIVE"
    And the response should contain "reason" with value "DEACTIVATED"

  Scenario: Reject signin for LOCKED account
    Given a user exists with email "locked@acme.com" and status "LOCKED"
    And the user is locked until "2099-12-31T23:59:59Z"
    And the user has password "ValidP@ss123!"
    When I submit a signin request with:
      | email    | locked@acme.com |
      | password | ValidP@ss123!   |
    Then the API should respond with status 423
    And the response should contain error "ACCOUNT_LOCKED"
    And the response should contain "lockedUntil"

  # AC-0003-02-06: AuthenticationFailed Event
  Scenario: Publish AuthenticationFailed event for invalid credentials
    Given an active user exists with email "eventfail@acme.com" and password "CorrectP@ss123!"
    When I submit a signin request with:
      | email    | eventfail@acme.com |
      | password | WrongPassword      |
    Then the API should respond with status 401
    And an AuthenticationFailed event should be persisted in the event store
    And the event should contain "reason" with value "INVALID_PASSWORD"

  # AC-0003-02-07: MFA Check After Credential Validation
  Scenario: Return MFA_REQUIRED for user with MFA enabled
    Given an active user exists with email "mfa@acme.com" and password "ValidP@ss123!"
    And the user has MFA enabled
    When I submit a signin request with:
      | email    | mfa@acme.com  |
      | password | ValidP@ss123! |
    Then the API should respond with status 200
    And the response should contain "status" with value "MFA_REQUIRED"
    And the response should contain "mfaToken"
    And the response should contain "mfaMethods"
    And the response should contain "expiresIn" with value 300

  # AC-0003-02-08: Device Fingerprint Capture
  Scenario: Capture device fingerprint on signin
    Given an active user exists with email "fingerprint@acme.com" and password "ValidP@ss123!"
    When I submit a signin request with:
      | email             | fingerprint@acme.com |
      | password          | ValidP@ss123!        |
      | deviceFingerprint | fp_abc123xyz789      |
    Then the API should respond with status 200
    And the user's device fingerprint should be "fp_abc123xyz789"

  # AC-0003-02-09: Error Message Security
  @smoke
  Scenario: Do not reveal whether email exists in error message
    When I submit a signin request with:
      | email    | nonexistent@example.com |
      | password | anypassword             |
    Then the API should respond with status 401
    And the response should contain message "Invalid email or password"

  Scenario: Same error message for wrong password as non-existent user
    Given an active user exists with email "security@acme.com" and password "CorrectP@ss123!"
    When I submit a signin request with:
      | email    | security@acme.com |
      | password | WrongPassword     |
    Then the API should respond with status 401
    And the response should contain message "Invalid email or password"

  # Rate Limiting
  @slow @rate-limiting
  Scenario: Rate limit signin requests
    Given I have made 10 signin requests from the same IP for the same email
    When I submit another signin request
    Then the API should respond with status 429
    And the response should contain error "RATE_LIMITED"

  # Validation Errors
  Scenario: Reject signin with empty email
    When I submit a signin request with:
      | email    |               |
      | password | SomePassword  |
    Then the API should respond with status 400
    And the response should contain a validation error for field "email"

  Scenario: Reject signin with invalid email format
    When I submit a signin request with:
      | email    | not-an-email |
      | password | SomePassword |
    Then the API should respond with status 400
    And the response should contain a validation error for field "email"

  Scenario: Reject signin with empty password
    When I submit a signin request with:
      | email    | user@example.com |
      | password |                  |
    Then the API should respond with status 400
    And the response should contain a validation error for field "password"

  # Correlation ID Tracking
  Scenario: Accept and propagate correlation ID
    Given an active user exists with email "correlation@acme.com" and password "ValidP@ss123!"
    When I submit a signin request with correlation ID "550e8400-e29b-41d4-a716-446655440001"
    And the request includes:
      | email    | correlation@acme.com |
      | password | ValidP@ss123!        |
    Then the API should respond with status 200
    And the AuthenticationSucceeded event should contain correlation ID "550e8400-e29b-41d4-a716-446655440001"

  # Email Normalization
  Scenario: Normalize email to lowercase for authentication
    Given an active user exists with email "uppercase@acme.com" and password "ValidP@ss123!"
    When I submit a signin request with:
      | email    | UPPERCASE@ACME.COM |
      | password | ValidP@ss123!      |
    Then the API should respond with status 200
    And the response should contain "status" with value "SUCCESS"
