@customer @preferences
Feature: Preferences when my session ends
  As a customer whose session has ended
  I want to be sent to sign in and brought back
  So that I'm not left on a page that can't work

  # PIN-428, PIN-459.

  Background:
    Given an active customer with email "preferences-session-ui@example.com" exists
    And I navigate to "/signin"
    And I sign in with valid credentials

  Scenario: A customer whose access cookie is gone goes from preferences to sign-in and back
    Given my access cookie is gone
    When I navigate to "/preferences"
    Then the header should show me signed out
    When I sign in from the page I was sent to
    Then I should be on "/preferences"

  Scenario: A customer whose access cookie goes while they're editing is sent to sign-in and back
    Given I navigate to "/preferences"
    And I change a preference
    And my access cookie is gone
    When I save my preferences
    Then the header should show me signed out
    When I sign in from the page I was sent to
    Then I should be on "/preferences"
