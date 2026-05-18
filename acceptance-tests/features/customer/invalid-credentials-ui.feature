@customer @identity @signin @invalid-credentials
Feature: Invalid Credentials UI (US-0003-10)
  As a customer who enters incorrect credentials
  I want clear feedback about my signin attempt failure
  So that I understand what went wrong without exposing security information

  Background:
    Given the Identity Service is available
    And the Customer frontend is available
    And an active user exists with email "ui-invalid-creds@acme.com" and password "ValidP@ss123!"
    And I am on the signin page

  @smoke
  Scenario: Display generic error message with remaining attempts after first failure
    When I fill in the signin form with:
      | email    | ui-invalid-creds@acme.com |
      | password | WrongPassword!            |
    And I submit the signin form
    Then I should see the signin error banner
    And the signin error banner should contain "Invalid email or password"
    And the signin error banner should show "4 attempts remaining"
    And the signin password field should be empty
    And the signin email field should still contain "ui-invalid-creds@acme.com"
    And focus should be on the signin password field

  Scenario: Urgent warning style appears with two attempts remaining
    When I attempt to signin 3 times with email "ui-invalid-creds@acme.com" and wrong password
    Then I should see the signin error banner
    And the signin error banner should show "2 attempts remaining before account lockout"
    And the signin error banner should contain a password reset link

  Scenario: Error banner clears on next submission
    When I fill in the signin form with:
      | email    | ui-invalid-creds@acme.com |
      | password | WrongPassword!            |
    And I submit the signin form
    Then I should see the signin error banner
    When I fill in the signin form with:
      | email    | ui-invalid-creds@acme.com |
      | password | ValidP@ss123!             |
    And I submit the signin form
    Then I should not see the signin error banner
