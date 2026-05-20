@api @identity @signin @authentication @password-reset
Feature: Password Reset API (US-0003-13)
  As the Identity Management Service
  I want to issue single-use password-reset tokens and complete resets
  So that a customer who has forgotten their password can regain access

  Background:
    Given the Identity Service is available

  # AC-0003-13-01: Request returns generic success regardless of account existence
  @smoke
  Scenario: Active user gets a password-reset email and PasswordResetRequested event
    Given an active user exists with email "reset-ok@acme.com" and password "ValidP@ss123!"
    When I submit a password reset request for "reset-ok@acme.com"
    Then the API should respond with status 200
    And the response should contain "message"
    And a PasswordResetRequested event should be persisted in the event store

  # AC-0003-13-01: No email enumeration
  Scenario: Password reset request for an unknown email returns the same generic response
    When I submit a password reset request for "nobody@acme.com"
    Then the API should respond with status 200
    And the response should contain "message"
    And no PasswordResetRequested event is persisted for that email

  # AC-0003-13-02: Rate limit 3/hour per email
  @rate-limiting
  Scenario: Fourth password-reset request within an hour is rate limited but indistinguishable
    Given an active user exists with email "reset-ratelimit@acme.com" and password "ValidP@ss123!"
    When I submit 3 password reset requests for "reset-ratelimit@acme.com"
    And I submit a password reset request for "reset-ratelimit@acme.com"
    Then the API should respond with status 200
    And exactly 3 PasswordResetRequested events should be persisted for that email

  # AC-0003-13-03 / AC-0003-13-09: Token has an expiry and is cryptographically random
  Scenario: Validating a fresh reset token returns valid with expiry seconds
    Given a password reset token has been issued for an active user
    When I validate the reset token
    Then the API should respond with status 200
    And the response should contain "valid"
    And the response should contain "expiresIn"

  # AC-0003-13-03: Expired token rejected
  Scenario: An expired reset token is rejected
    Given an expired password reset token exists
    When I validate the reset token
    Then the API should respond with status 400
    And the response should contain "error" with value "INVALID_RESET_TOKEN"

  # AC-0003-13-04: Single use
  Scenario: A reset token can only be used once
    Given a password reset token has been issued for an active user
    And the password reset has already been completed with that token
    When I validate the reset token
    Then the API should respond with status 400
    And the response should contain "error" with value "INVALID_RESET_TOKEN"

  # AC-0003-13-05: Password requirements validation
  Scenario: Confirming reset with a weak password returns the unmet requirements
    Given a password reset token has been issued for an active user
    When I submit a password reset confirmation with new password "weak"
    Then the API should respond with status 400
    And the response should contain "error" with value "PASSWORD_REQUIREMENTS_NOT_MET"
    And the response should contain "requirements"

  # AC-0003-13-06 / AC-0003-13-07 / AC-0003-13-08: Successful reset invalidates sessions/devices/lockout
  @smoke
  Scenario: Completing a password reset invalidates sessions and revokes device trusts
    Given a password reset token has been issued for an active user
    And the user has active sessions and device trusts
    When I submit a password reset confirmation with new password "NewSecureP@ss123"
    Then the API should respond with status 200
    And the response should contain "sessionsInvalidated"
    And the response should contain "deviceTrustsRevoked"
    And a PasswordChanged event should be persisted in the event store

  Scenario: Malformed payload is rejected
    When I submit a password reset request with an empty email
    Then the API should respond with status 400
