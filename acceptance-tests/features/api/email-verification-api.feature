@api @identity @verification
Feature: Email Verification API (US-0002-05)
  As a new customer
  I want to verify my email by clicking the verification link
  So that my account is activated and I can start using the platform

  Background:
    Given the Identity Service is available

  # AC-0002-05-01: Valid Token Activation
  @smoke
  Scenario: Successfully verify email with valid token
    Given a user has registered with email "verify-valid@example.com"
    And the user has a valid verification token
    When I click the verification link with the token
    Then I should be redirected to the login page with "verified=true"
    And the user's email should be marked as verified
    And the user's account status should be "ACTIVE"

  # AC-0002-05-02: Expired Token Handling
  Scenario: Reject verification with expired token
    Given a user has registered with email "verify-expired@example.com"
    And the user has an expired verification token
    When I click the verification link with the token
    Then I should be redirected to the resend verification page with "error=expired"
    And the user's account status should still be "PENDING_VERIFICATION"

  # AC-0002-05-03: Already Used Token
  Scenario: Redirect already verified user
    Given a user has registered with email "verify-used@example.com"
    And the user has already verified their email
    When I click the verification link with an already used token
    Then I should be redirected to the login page with "already_verified=true"

  # AC-0002-05-04: Invalid Token (Security)
  Scenario: Reject verification with invalid token
    When I click the verification link with token "invalid-token-xyz123"
    Then I should be redirected to the resend verification page with "error=invalid"

  # AC-0002-05-06: Atomic Event Publishing
  Scenario: Publish EmailVerified and UserActivated events on successful verification
    Given a user has registered with email "verify-events@example.com"
    And the user has a valid verification token
    When I click the verification link with the token
    Then an EmailVerified event should be published
    And a UserActivated event should be published

  # Resend Verification Scenarios

  @smoke
  Scenario: Successfully request verification email resend
    Given a user has registered with email "resend-valid@example.com"
    And the user has not verified their email
    When I request to resend the verification email to "resend-valid@example.com"
    Then the API should respond with status 200
    And the response should contain message "If an account exists with this email, a verification link has been sent."
    And the response should contain "requestsRemaining"

  # Security - prevent email enumeration
  Scenario: Return success for nonexistent email to prevent enumeration
    When I request to resend the verification email to "nonexistent-user@example.com"
    Then the API should respond with status 200
    And the response should contain message "If an account exists with this email, a verification link has been sent."

  Scenario: Return success for already verified user to prevent enumeration
    Given a user has registered and verified with email "resend-verified@example.com"
    When I request to resend the verification email to "resend-verified@example.com"
    Then the API should respond with status 200
    And the response should contain message "If an account exists with this email, a verification link has been sent."

  # AC-0002-05-07: Resend Rate Limiting
  @slow @rate-limiting
  Scenario: Rate limit resend verification requests
    Given a user has registered with email "resend-ratelimit@example.com"
    And I have requested 3 verification resends for "resend-ratelimit@example.com"
    When I request to resend the verification email to "resend-ratelimit@example.com"
    Then the API should respond with status 429
    And the response should contain error "RATE_LIMIT_EXCEEDED"
    And the response should have a "Retry-After" header

  # Validation Scenarios
  Scenario: Reject resend request with invalid email format
    When I request to resend the verification email to "invalid-email"
    Then the API should respond with status 400
    And the response should contain error "VALIDATION_ERROR"

  Scenario: Reject resend request with empty email
    When I request to resend the verification email to ""
    Then the API should respond with status 400
    And the response should contain error "VALIDATION_ERROR"
