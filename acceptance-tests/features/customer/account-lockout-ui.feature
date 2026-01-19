@customer @identity @signin @lockout
Feature: Account Lockout UI (US-0003-04)
  As a Customer
  I want to see a clear message when my account is locked
  So that I understand why I cannot sign in and how to resolve it

  Background:
    Given the Identity Service is available
    And the Customer frontend is available

  @smoke
  Scenario: Display lockout message after 5 failed attempts
    Given an active user exists with email "ui-lockout@acme.com" and password "ValidP@ss123!"
    And I am on the signin page
    When I attempt to signin 5 times with email "ui-lockout@acme.com" and wrong password
    Then I should see the account lockout message
    And I should see the lockout countdown timer
    And I should see a link to reset my password
    And the signin form should be disabled

  Scenario: Lockout message displays remaining time
    Given an active user exists with email "ui-countdown@acme.com" and password "ValidP@ss123!"
    And the user account is locked
    And I am on the signin page
    When I fill in the signin form with:
      | email    | ui-countdown@acme.com |
      | password | ValidP@ss123!         |
    And I submit the signin form
    Then I should see the account lockout message
    And the lockout countdown should show minutes remaining
