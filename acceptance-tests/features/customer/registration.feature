@customer @registration
Feature: Customer Registration Form
  As a new customer visiting the ACME platform
  I want a clear and user-friendly registration form
  So that I can quickly create an account and start shopping

  Background:
    Given I am on the registration page

  # AC-0002-01-01: Email Validation
  @smoke
  Scenario: Display error for invalid email format
    When I enter an invalid email "invalid-email"
    And I move focus away from the email field
    Then I should see the email error "Please enter a valid email address"
    And the email field should be highlighted with an error state

  Scenario: Clear error when valid email is entered
    When I enter an invalid email "invalid-email"
    And I move focus away from the email field
    Then I should see the email error "Please enter a valid email address"
    When I enter a valid email "user@example.com"
    And I move focus away from the email field
    Then the email error should not be visible
    And I should see a success indicator on the email field

  # AC-0002-01-02: Password Requirements
  @smoke
  Scenario: Display password strength indicator
    When I enter password "Test@123!"
    Then I should see the password strength indicator showing "Strong"
    And all password requirements should be marked as complete

  Scenario: Display weak password strength for short password
    When I enter password "Te@1"
    Then I should see the password strength indicator showing "Weak"

  Scenario: Display password requirements list
    When I enter password "test"
    Then I should see the following password requirements:
      | Minimum 8 characters               |
      | At least one uppercase letter      |
      | At least one lowercase letter      |
      | At least one digit                 |
      | At least one special character     |

  # AC-0002-01-03: Confirm Password Match
  Scenario: Display error when passwords do not match
    When I enter password "Test@123!"
    And I enter confirm password "Different@123!"
    And I move focus away from the confirm password field
    Then I should see the confirm password error "Passwords do not match"

  Scenario: Clear error when passwords match
    When I enter password "Test@123!"
    And I enter confirm password "Test@123!"
    And I move focus away from the confirm password field
    Then the confirm password error should not be visible
    And I should see a success indicator on the confirm password field

  # AC-0002-01-04: Name Field Validation
  Scenario: Display error for empty first name
    When I leave the first name field empty
    And I move focus away from the first name field
    Then I should see the first name error "First name is required"

  Scenario: Display error for empty last name
    When I leave the last name field empty
    And I move focus away from the last name field
    Then I should see the last name error "Last name is required"

  Scenario: Truncate first name at 50 characters
    When I enter a first name with more than 50 characters
    Then the first name should be truncated to 50 characters
    And the character counter should show "50/50"

  # AC-0002-01-05: Terms of Service Requirement
  Scenario: Display error when Terms of Service not accepted
    When I fill in the registration form with valid data but without accepting Terms of Service
    And I attempt to submit the form
    Then the form should not submit
    And I should see the Terms of Service error

  # AC-0002-01-06: Submit Button State
  @smoke
  Scenario: Submit button is disabled with incomplete form
    Then the submit button should be disabled

  Scenario: Submit button is enabled when form is valid
    When I fill in the complete registration form with valid data
    Then the submit button should be enabled

  # AC-0002-01-08: Password Show/Hide Toggle
  Scenario: Toggle password visibility
    When I enter password "Test@123!"
    Then the password field should be masked
    When I click the password visibility toggle
    Then the password field should show the password text
    When I click the password visibility toggle
    Then the password field should be masked

  # Full Registration Flow
  @smoke
  Scenario: Successfully complete registration form
    When I fill in the registration form with:
      | Email            | newuser@example.com |
      | Password         | Test@123!           |
      | Confirm Password | Test@123!           |
      | First Name       | John                |
      | Last Name        | Doe                 |
    And I accept the Terms of Service
    And I accept the Privacy Policy
    And I submit the registration form
    Then I should be redirected to the home page

  Scenario: Navigate to signin from registration page
    When I click the "Sign in" link
    Then I should be on the "signin" page
