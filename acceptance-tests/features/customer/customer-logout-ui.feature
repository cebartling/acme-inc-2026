@customer @logout @wip
Feature: Customer Logout UI (US-0003-14)
  As a signed-in customer
  I want to log out from the user menu in the header
  So that my session ends and I land back on the signin page

  Background:
    Given an active customer with email "logout-ui@example.com" exists
    And I am on the signin page
    When I sign in with valid credentials
    Then I should be redirected to the dashboard

  # AC-0003-14-01, AC-0003-14-04, AC-0003-14-09
  Scenario: Standard logout from the user menu redirects to signin with banner
    When I open the user menu
    And I click the "Sign Out" menu item
    Then I should be redirected to the signin page with logout=true
    And I should see a signed-out banner
    And the auth storage should be cleared
    And the customer storage should be cleared

  # AC-0003-14-08
  Scenario: Sign Out All Devices shows a confirmation dialog
    When I open the user menu
    And I click the "Sign Out All Devices" menu item
    Then a confirmation dialog should appear
    And the dialog title should mention all devices

  Scenario: Confirming Sign Out All Devices signs the user out
    When I open the user menu
    And I click the "Sign Out All Devices" menu item
    And I confirm the all-devices logout
    Then I should be redirected to the signin page with logout=true
    And the auth storage should be cleared

  Scenario: Canceling the Sign Out All Devices dialog keeps the user signed in
    When I open the user menu
    And I click the "Sign Out All Devices" menu item
    And I cancel the all-devices logout
    Then I should remain on the dashboard
