@customer @identity @signin @inactive-account
Feature: Inactive Account UI (US-0003-11)
  As a customer attempting to sign in with a non-active account
  I want a clear card on the signin page describing my situation
  So that I know the next action to take to regain access

  Background:
    Given the Identity Service is available
    And the Customer frontend is available

  @smoke
  Scenario: PENDING_VERIFICATION shows a resend verification card
    Given a user exists with email "ui-pending@acme.com" and status "PENDING_VERIFICATION"
    And the user has password "ValidP@ss123!"
    And I am on the signin page
    When I fill in the signin form with:
      | email    | ui-pending@acme.com |
      | password | ValidP@ss123!       |
    And I submit the signin form
    Then I should see the inactive account card
    And the inactive account card reason should be "PENDING_VERIFICATION"
    And I should see the resend verification button enabled

  Scenario: Clicking resend verification disables the button and shows a success notice
    Given a user exists with email "ui-pending-resend@acme.com" and status "PENDING_VERIFICATION"
    And the user has password "ValidP@ss123!"
    And I am on the signin page
    When I fill in the signin form with:
      | email    | ui-pending-resend@acme.com |
      | password | ValidP@ss123!              |
    And I submit the signin form
    And I click the resend verification button
    Then I should see the resend verification success notice
    And the resend verification button should be disabled

  Scenario: SUSPENDED shows support URL and email
    Given a user exists with email "ui-suspended@acme.com" and status "SUSPENDED"
    And the user has password "ValidP@ss123!"
    And I am on the signin page
    When I fill in the signin form with:
      | email    | ui-suspended@acme.com |
      | password | ValidP@ss123!         |
    And I submit the signin form
    Then I should see the inactive account card
    And the inactive account card reason should be "SUSPENDED"
    And I should see the contact support link
    And I should see the support email link

  Scenario: DEACTIVATED shows the reactivation call to action
    Given a user exists with email "ui-deactivated@acme.com" and status "DEACTIVATED"
    And the user has password "ValidP@ss123!"
    And I am on the signin page
    When I fill in the signin form with:
      | email    | ui-deactivated@acme.com |
      | password | ValidP@ss123!           |
    And I submit the signin form
    Then I should see the inactive account card
    And the inactive account card reason should be "DEACTIVATED"
    And I should see the reactivate account link

  Scenario: Wrong password against a SUSPENDED account shows the generic error banner, not the inactive card
    Given a user exists with email "ui-suspended-wrong@acme.com" and status "SUSPENDED"
    And the user has password "ValidP@ss123!"
    And I am on the signin page
    When I fill in the signin form with:
      | email    | ui-suspended-wrong@acme.com |
      | password | WrongPassword               |
    And I submit the signin form
    Then I should see the signin error banner
    And I should not see the inactive account card
