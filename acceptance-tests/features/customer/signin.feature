@customer @signin
Feature: Customer Signin Form
  As a returning customer
  I want a secure and user-friendly signin form
  So that I can access my account and continue shopping

  Background:
    Given I am on the signin page

  # AC-0003-01-01: Email Input Validation
  @smoke
  Scenario: Display error for invalid email format
    When I enter signin email "invalid-email"
    And I move focus away from the signin email field
    Then I should see the signin email error "Please enter a valid email address"

  Scenario: Display error for empty email
    When I leave the signin email field empty
    And I move focus away from the signin email field
    Then I should see the signin email error "Email is required"

  Scenario: Accept valid email address
    When I enter signin email "user@example.com"
    And I move focus away from the signin email field
    Then the signin email error should not be visible
    And I should see a success indicator on the signin email field

  # AC-0003-01-02: Password Input Validation
  Scenario: Display error for empty password
    When I leave the signin password field empty
    And I move focus away from the signin password field
    Then I should see the signin password error "Password is required"

  Scenario: Accept any password value (no strength requirements)
    When I enter signin password "simple"
    And I move focus away from the signin password field
    Then the signin password error should not be visible

  # AC-0003-01-03: Password Show/Hide Toggle
  Scenario: Toggle password visibility
    When I enter signin password "Test@123!"
    Then the signin password field should be masked
    When I click the signin password visibility toggle
    Then the signin password field should show the password text
    When I click the signin password visibility toggle
    Then the signin password field should be masked

  # AC-0003-01-05: Remember Me Checkbox
  Scenario: Remember me checkbox is unchecked by default
    Then the remember me checkbox should be unchecked

  Scenario: Check remember me checkbox
    When I check the remember me checkbox
    Then the remember me checkbox should be checked

  # AC-0003-01-06: Sign In Button State
  @smoke
  Scenario: Sign in button is disabled with incomplete form
    Then the signin button should be disabled

  Scenario: Sign in button is disabled with empty email
    When I enter signin password "Test@123!"
    Then the signin button should be disabled

  Scenario: Sign in button is disabled with empty password
    When I enter signin email "user@example.com"
    Then the signin button should be disabled

  Scenario: Sign in button is enabled when form is valid
    When I enter signin email "user@example.com"
    And I enter signin password "password123"
    Then the signin button should be enabled

  # AC-0003-01-08: Forgot Password Link
  Scenario: Forgot password link is visible
    Then I should see the forgot password link

  Scenario: Forgot password link navigation
    When I click the forgot password link
    Then I should be on the "forgot-password" page

  # AC-0003-01-09: Registration Link
  Scenario: Registration link is visible
    Then I should see the registration link on signin page

  Scenario: Registration link navigation
    When I click the registration link on signin page
    Then I should be on the "register" page

  # AC-0003-01-10: Error Display for API Errors
  Scenario: Display error message when signin fails
    Given the signin API will return an error
    When I enter signin email "invalid@example.com"
    And I enter signin password "wrongpassword"
    And I submit the signin form
    Then I should see the signin error message

  # Full Signin Flow
  @smoke
  Scenario: Successfully complete signin form
    Given an active user exists with email "customer@acme.com" and password "ValidP@ssw0rd!"
    When I enter signin email "customer@acme.com"
    And I enter signin password "ValidP@ssw0rd!"
    And I submit the signin form
    Then I should be redirected to the dashboard page
