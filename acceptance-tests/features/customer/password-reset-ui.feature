@customer @signin @password-reset
Feature: Password Reset UI (US-0003-13)
  As a customer who has forgotten their password
  I want to request a reset and set a new password
  So that I can regain access to my account

  Background:
    Given the customer storefront is available

  @smoke
  Scenario: Customer requests a password reset from the signin page
    Given I am on the signin page
    When I click the "Forgot password?" link
    Then I should be on the forgot-password page
    When I enter "user@acme.com" in the email field
    And I submit the forgot-password form
    Then I should see a "Check your inbox" confirmation
    And the confirmation should mention that the link expires in 1 hour

  Scenario: The reset-password page rejects an expired token
    Given I am on the reset-password page with an expired token
    Then I should see an "expired link" message
    And I should see a link to request a new reset link

  @smoke
  Scenario: Customer completes the password reset and is redirected to signin
    Given a valid password reset token exists for an active user
    And I am on the reset-password page with that token
    When I enter "NewSecureP@ss123" in the new password field
    And I enter "NewSecureP@ss123" in the confirm password field
    And I submit the reset-password form
    Then I should see a "Password updated" confirmation
    And a "Sign in now" button should link to /signin
    And I should be redirected to /signin within 5 seconds

  Scenario: Mismatched confirmation prevents submission
    Given a valid password reset token exists for an active user
    And I am on the reset-password page with that token
    When I enter "NewSecureP@ss123" in the new password field
    And I enter "Different123!" in the confirm password field
    And I submit the reset-password form
    Then I should see an error indicating the passwords do not match
    And the reset-password form should still be visible

  Scenario: Weak password shows unmet requirements
    Given a valid password reset token exists for an active user
    And I am on the reset-password page with that token
    When I enter "weakweak" in the new password field
    And I enter "weakweak" in the confirm password field
    And I submit the reset-password form
    Then I should see the password requirements list
    And at least one requirement should be marked as unmet
