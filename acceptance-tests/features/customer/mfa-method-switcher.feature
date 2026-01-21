@customer @mfa @method-switcher
Feature: MFA Method Switcher UI (US-0003-06)
  As a customer with multiple MFA methods enabled
  I want to switch between TOTP and SMS verification on the MFA page
  So that I can use my preferred method to complete authentication

  Background:
    Given the Identity Service is available

  # Test Plan Item: Frontend method switcher works when both TOTP and SMS enabled
  @smoke
  Scenario: Method switcher is visible when user has both TOTP and SMS enabled
    Given an active user exists with email "dual-mfa@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled with a valid secret
    And the user has SMS MFA enabled with phone number "+15559990001"
    When I complete credential validation and navigate to MFA verification page
    Then I should see the MFA method switcher
    And the Authenticator button should be visible
    And the SMS button should be visible

  @smoke
  Scenario: Switch from TOTP to SMS method
    Given an active user exists with email "switch-to-sms@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled with a valid secret
    And the user has SMS MFA enabled with phone number "+15559990002"
    When I complete credential validation and navigate to MFA verification page
    Then the Authenticator method should be selected by default
    When I click the SMS method button
    Then the SMS method should be selected
    And I should see the resend code button

  Scenario: Switch from SMS to TOTP method
    Given an active user exists with email "switch-to-totp@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled with a valid secret
    And the user has SMS MFA enabled with phone number "+15559990003"
    When I complete credential validation and navigate to MFA verification page
    And I click the SMS method button
    Then the SMS method should be selected
    When I click the Authenticator method button
    Then the Authenticator method should be selected
    And the resend code button should not be visible

  Scenario: Method switcher is hidden when only TOTP is enabled
    Given an active user exists with email "totp-only@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled with a valid secret
    When I complete credential validation and navigate to MFA verification page
    Then I should not see the MFA method switcher

  Scenario: Method switcher is hidden when only SMS is enabled
    Given an active user exists with email "sms-only@acme.com" and password "ValidP@ss123!"
    And the user has SMS MFA enabled with phone number "+15559990004"
    When I complete credential validation and navigate to MFA verification page
    Then I should not see the MFA method switcher
    And I should see the resend code button

  Scenario: Successfully verify with TOTP after switching from SMS
    Given an active user exists with email "verify-totp@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled with a valid secret
    And the user has SMS MFA enabled with phone number "+15559990005"
    When I complete credential validation and navigate to MFA verification page
    And I click the SMS method button
    And I click the Authenticator method button
    And I enter a valid TOTP code
    And I submit the MFA verification form
    Then I should be redirected to the dashboard page

  # TODO: This test requires backend support for switching MFA methods mid-flow.
  # Currently, when TOTP is primary, no SMS challenge is created during signin.
  # Clicking "resend" expects an existing SMS challenge.
  # Need to implement a switch-method endpoint that creates a new SMS challenge.
  @wip
  Scenario: Successfully verify with SMS after switching from TOTP
    Given an active user exists with email "verify-sms@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled with a valid secret
    And the user has SMS MFA enabled with phone number "+15559990006"
    When I complete credential validation and navigate to MFA verification page
    And I click the SMS method button
    And I click the resend code button
    And I enter the correct SMS code
    And I submit the MFA verification form
    Then I should be redirected to the dashboard page
