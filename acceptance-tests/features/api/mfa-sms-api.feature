@api @identity @mfa @sms @authentication
Feature: MFA SMS Verification API (US-0003-06)
  As a security-conscious customer
  I want to verify my identity using SMS verification codes
  So that my account has an additional layer of protection without needing an authenticator app

  Background:
    Given the Identity Service is available

  # AC-0003-06-01: SMS Code Generation and Delivery
  @smoke
  Scenario: Successfully verify valid SMS code
    Given an active user exists with email "sms-user@acme.com" and password "ValidP@ss123!"
    And the user has SMS MFA enabled with phone number "+15551234567"
    When I submit a signin request with:
      | email    | sms-user@acme.com |
      | password | ValidP@ss123!     |
    Then the API should respond with status 200
    And the response should contain "status" with value "MFA_REQUIRED"
    And the response should contain "mfaToken"
    And the response should contain "mfaMethods" with value containing "SMS"
    And I store the MFA token from the response
    When I submit an MFA verification request with the correct SMS code
    Then the API should respond with status 200
    And the response should contain "status" with value "SUCCESS"
    And the response should contain a valid UUID for "userId"

  # AC-0003-06-02: 6-Digit Numeric Code
  Scenario: SMS codes are 6 digits
    Given an active user exists with email "sms-code@acme.com" and password "ValidP@ss123!"
    And the user has SMS MFA enabled with phone number "+15551234568"
    And I have completed SMS credential validation and received an MFA token
    Then the sent SMS code should be 6 numeric digits

  # AC-0003-06-03: Rate Limiting (Max 3 SMS per hour)
  Scenario: Enforce rate limit of 3 SMS per hour
    Given an active user exists with email "rate-limit@acme.com" and password "ValidP@ss123!"
    And the user has SMS MFA enabled with phone number "+15551234569"
    And the user has received 3 SMS codes in the last hour
    When I submit a signin request with:
      | email    | rate-limit@acme.com |
      | password | ValidP@ss123!       |
    Then the API should respond with status 429
    And the response should contain error "SMS_RATE_LIMITED"

  # AC-0003-06-04: Resend Cooldown (60 seconds)
  Scenario: Enforce 60 second cooldown between resends
    Given an active user exists with email "cooldown@acme.com" and password "ValidP@ss123!"
    And the user has SMS MFA enabled with phone number "+15551234570"
    And I have completed SMS credential validation and received an MFA token
    When I request to resend the SMS code immediately
    Then the API should respond with status 429
    And the response should contain error "COOLDOWN_ACTIVE"
    And the response should contain "resendAvailableIn"

  # AC-0003-06-05: Single Use Codes
  Scenario: Reject already used SMS code
    Given an active user exists with email "single-use-sms@acme.com" and password "ValidP@ss123!"
    And the user has SMS MFA enabled with phone number "+15551234571"
    And I have completed SMS credential validation and received an MFA token
    And I have successfully verified with the SMS code
    When I create a new MFA challenge for the same user
    And I submit an MFA verification request with the same SMS code
    Then the API should respond with status 401
    And the response should contain error "INVALID_MFA_CODE"

  # AC-0003-06-06: Maximum 3 Attempts
  Scenario: Expire SMS MFA challenge after 3 failed attempts
    Given an active user exists with email "sms-attempts@acme.com" and password "ValidP@ss123!"
    And the user has SMS MFA enabled with phone number "+15551234572"
    And I have completed SMS credential validation and received an MFA token
    When I submit 3 SMS MFA verification requests with wrong codes
    Then the API should respond with status 401
    And the response should contain error "MFA_EXPIRED"
    And the response should contain message "MFA challenge has expired. Please sign in again."

  Scenario: Show remaining attempts after failed SMS verification
    Given an active user exists with email "sms-remaining@acme.com" and password "ValidP@ss123!"
    And the user has SMS MFA enabled with phone number "+15551234573"
    And I have completed SMS credential validation and received an MFA token
    When I submit an SMS MFA verification request with an invalid code
    Then the API should respond with status 401
    And the response should contain error "INVALID_MFA_CODE"
    And the response should contain "remainingAttempts" with value 2

  # AC-0003-06-07: Challenge Expiry
  Scenario: Reject SMS MFA verification after challenge expires
    Given an active user exists with email "sms-expired@acme.com" and password "ValidP@ss123!"
    And the user has SMS MFA enabled with phone number "+15551234574"
    And I have completed SMS credential validation and received an MFA token
    And I wait for the MFA challenge to expire
    When I submit an SMS MFA verification request with the correct code
    Then the API should respond with status 401
    And the response should contain error "MFA_EXPIRED"
    And the response should contain message "MFA challenge has expired. Please sign in again."

  # Resend Functionality
  @smoke
  Scenario: Successfully resend SMS code
    Given an active user exists with email "resend@acme.com" and password "ValidP@ss123!"
    And the user has SMS MFA enabled with phone number "+15551234575"
    And I have completed SMS credential validation and received an MFA token
    And I wait for the resend cooldown to elapse
    When I request to resend the SMS code
    Then the API should respond with status 200
    And the response should contain "status" with value "CODE_SENT"
    And the response should contain "maskedPhone"
    And the response should contain "expiresIn"
    And the response should contain "resendAvailableIn"

  Scenario: Resend endpoint only supports SMS method
    Given an active user exists with email "resend-method@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled with a valid secret
    And I have completed credential validation and received an MFA token
    When I request to resend the code with method "TOTP"
    Then the API should respond with status 400
    And the response should contain error "INVALID_METHOD"

  # Validation Errors
  Scenario: Reject SMS verification with invalid token
    When I submit an SMS MFA verification request with:
      | mfaToken | invalid-token |
      | code     | 123456        |
      | method   | SMS           |
    Then the API should respond with status 401
    And the response should contain error "INVALID_MFA_TOKEN"

  Scenario: Reject SMS verification with wrong code length
    Given an active user exists with email "sms-length@acme.com" and password "ValidP@ss123!"
    And the user has SMS MFA enabled with phone number "+15551234576"
    And I have completed SMS credential validation and received an MFA token
    When I submit an SMS MFA verification request with:
      | code   | 12345 |
      | method | SMS   |
    Then the API should respond with status 400

  # Phone Number Masking
  Scenario: Mask phone number in resend response
    Given an active user exists with email "mask@acme.com" and password "ValidP@ss123!"
    And the user has SMS MFA enabled with phone number "+15559876543"
    And I have completed SMS credential validation and received an MFA token
    And I wait for the resend cooldown to elapse
    When I request to resend the SMS code
    Then the API should respond with status 200
    And the response should contain "maskedPhone" matching pattern "\*\*\*-\*\*\*-\d{4}"
