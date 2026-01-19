@api @identity @signin @authentication @lockout
Feature: Account Lockout (US-0003-04)
  As a Security Officer
  I want accounts to be temporarily locked after multiple failed signin attempts
  So that brute-force attacks are mitigated

  Background:
    Given the Identity Service is available

  # AC-0003-04-01: Lock after 5 failed attempts
  @smoke
  Scenario: Account is locked after 5 consecutive failed signin attempts
    Given an active user exists with email "lockout@acme.com" and password "ValidP@ss123!"
    When I submit 5 signin requests with wrong password for "lockout@acme.com"
    Then the API should respond with status 423
    And the response should contain error "ACCOUNT_LOCKED"
    And the response should contain "lockedUntil"
    And the response should contain "lockoutRemainingSeconds"
    And the response should contain "passwordResetUrl"

  # AC-0003-04-02: 15-minute lockout duration
  Scenario: Locked account returns remaining lockout time
    Given an active user exists with email "locktime@acme.com" and password "ValidP@ss123!"
    When I submit 5 signin requests with wrong password for "locktime@acme.com"
    Then the API should respond with status 423
    And the response should contain "lockoutRemainingSeconds" greater than 800
    And the response should contain "lockoutRemainingSeconds" less than 901

  # AC-0003-04-03: Cannot signin while locked
  Scenario: Cannot signin with correct password while account is locked
    Given an active user exists with email "locked-correct@acme.com" and password "ValidP@ss123!"
    And the user account is locked
    When I submit a signin request with:
      | email    | locked-correct@acme.com |
      | password | ValidP@ss123!           |
    Then the API should respond with status 423
    And the response should contain error "ACCOUNT_LOCKED"

  # AC-0003-04-04: Failed attempts reset on successful signin before lockout
  @smoke
  Scenario: Successful signin resets failed attempts before lockout threshold
    Given an active user exists with email "reset-before@acme.com" and password "ValidP@ss123!"
    When I submit 3 signin requests with wrong password for "reset-before@acme.com"
    And I submit a signin request with:
      | email    | reset-before@acme.com |
      | password | ValidP@ss123!         |
    Then the API should respond with status 200
    And the response should contain "status" with value "SUCCESS"

  # AC-0003-04-05: AccountLocked event is published
  Scenario: AccountLocked event is published when account is locked
    Given an active user exists with email "lock-event@acme.com" and password "ValidP@ss123!"
    When I submit 5 signin requests with wrong password for "lock-event@acme.com"
    Then the API should respond with status 423
    And an AccountLocked event should be persisted in the event store

  # AC-0003-04-06: Decreasing remaining attempts warning
  Scenario: Remaining attempts decreases with each failed attempt
    Given an active user exists with email "attempts@acme.com" and password "ValidP@ss123!"
    When I submit a signin request with:
      | email    | attempts@acme.com |
      | password | WrongPassword     |
    Then the API should respond with status 401
    And the response should contain "remainingAttempts" with value 4
    When I submit a signin request with:
      | email    | attempts@acme.com |
      | password | WrongPassword2    |
    Then the API should respond with status 401
    And the response should contain "remainingAttempts" with value 3
