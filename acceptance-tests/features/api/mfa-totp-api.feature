@api @identity @mfa @totp @authentication
Feature: MFA TOTP Verification API (US-0003-05)
  As a security-conscious customer
  I want to verify my identity using a time-based one-time password (TOTP)
  So that my account has an additional layer of protection against unauthorized access

  Background:
    Given the Identity Service is available

  # AC-0003-05-01: TOTP Code Validation
  @smoke
  Scenario: Successfully verify valid TOTP code
    Given an active user exists with email "mfa-user@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled with a valid secret
    When I submit a signin request with:
      | email    | mfa-user@acme.com |
      | password | ValidP@ss123!     |
    Then the API should respond with status 200
    And the response should contain "status" with value "MFA_REQUIRED"
    And the response should contain "mfaToken"
    And I store the MFA token from the response
    When I submit an MFA verification request with the correct TOTP code
    Then the API should respond with status 200
    And the response should contain "status" with value "SUCCESS"
    And the response should contain a valid UUID for "userId"

  # AC-0003-05-02: Time Window Tolerance
  @wip
  Scenario: Accept TOTP code within tolerance window
    Given an active user exists with email "tolerance@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled with a valid secret
    And I have completed credential validation and received an MFA token
    When I submit an MFA verification request with a code from the previous time step
    Then the API should respond with status 200
    And the response should contain "status" with value "SUCCESS"

  # AC-0003-05-03: Single Use Codes
  @wip
  Scenario: Reject already used TOTP code
    Given an active user exists with email "singleuse@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled with a valid secret
    And I have completed credential validation and received an MFA token
    And I have successfully verified with a TOTP code
    When I create a new MFA challenge for the same user
    And I submit an MFA verification request with the same TOTP code
    Then the API should respond with status 401
    And the response should contain error "INVALID_MFA_CODE"
    And the response should contain "remainingAttempts"

  # AC-0003-05-04: MFA Challenge Expiry
  Scenario: Reject MFA verification after challenge expires
    Given an active user exists with email "expired@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled with a valid secret
    And I have completed credential validation and received an MFA token
    And I wait for the MFA challenge to expire
    When I submit an MFA verification request with the correct TOTP code
    Then the API should respond with status 401
    And the response should contain error "MFA_EXPIRED"
    And the response should contain message "MFA challenge has expired. Please sign in again."

  # AC-0003-05-05: Maximum 3 Attempts
  Scenario: Expire MFA challenge after 3 failed attempts
    Given an active user exists with email "attempts@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled with a valid secret
    And I have completed credential validation and received an MFA token
    When I submit 3 MFA verification requests with wrong codes
    Then the API should respond with status 401
    And the response should contain error "MFA_EXPIRED"
    And the response should contain message "MFA challenge has expired. Please sign in again."

  Scenario: Show remaining attempts after failed verification
    Given an active user exists with email "remaining@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled with a valid secret
    And I have completed credential validation and received an MFA token
    When I submit an MFA verification request with an invalid code
    Then the API should respond with status 401
    And the response should contain error "INVALID_MFA_CODE"
    And the response should contain "remainingAttempts" with value 2

  # AC-0003-05-07: MFA Events Published
  @wip
  Scenario: Publish MFAChallengeInitiated event when MFA is required
    Given an active user exists with email "mfa-event@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled with a valid secret
    When I submit a signin request with:
      | email    | mfa-event@acme.com |
      | password | ValidP@ss123!      |
    Then the API should respond with status 200
    And the response should contain "status" with value "MFA_REQUIRED"
    And an MFAChallengeInitiated event should be persisted in the event store
    And the event should contain "method" with value "TOTP"

  @wip
  Scenario: Publish MFAVerificationSucceeded event on successful verification
    Given an active user exists with email "mfa-success-event@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled with a valid secret
    And I have completed credential validation and received an MFA token
    When I submit an MFA verification request with the correct TOTP code
    Then the API should respond with status 200
    And an MFAVerificationSucceeded event should be persisted in the event store

  @wip
  Scenario: Publish MFAVerificationFailed event on failed verification
    Given an active user exists with email "mfa-fail-event@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled with a valid secret
    And I have completed credential validation and received an MFA token
    When I submit an MFA verification request with an invalid code
    Then the API should respond with status 401
    And an MFAVerificationFailed event should be persisted in the event store
    And the event should contain "reason" with value "INVALID_CODE"

  # Validation Errors
  Scenario: Reject MFA verification with invalid token format
    When I submit an MFA verification request with:
      | mfaToken | invalid-token |
      | code     | 123456        |
      | method   | TOTP          |
    Then the API should respond with status 401
    And the response should contain error "INVALID_MFA_TOKEN"

  Scenario: Reject MFA verification with invalid code length
    Given an active user exists with email "invalid-code@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled with a valid secret
    And I have completed credential validation and received an MFA token
    When I submit an MFA verification request with:
      | code   | 12345 |
      | method | TOTP  |
    Then the API should respond with status 400

  Scenario: Reject MFA verification with non-numeric code
    Given an active user exists with email "non-numeric@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled with a valid secret
    And I have completed credential validation and received an MFA token
    When I submit an MFA verification request with:
      | code   | abcdef |
      | method | TOTP   |
    Then the API should respond with status 400

  # Remember Device
  @wip
  Scenario: Remember device on successful MFA verification
    Given an active user exists with email "remember@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled with a valid secret
    And I have completed credential validation and received an MFA token
    When I submit an MFA verification request with rememberDevice=true
    Then the API should respond with status 200
    And the response should contain "deviceTrusted" with value true
