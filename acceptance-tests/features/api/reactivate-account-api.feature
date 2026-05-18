@api @identity @signin @authentication
Feature: Account Reactivation API (US-0003-11)
  As the Identity Management Service
  I want to issue reactivation tokens to deactivated customers
  So that they can self-service their way back to an active account

  Background:
    Given the Identity Service is available

  # AC-0003-11-05: Reactivation flow returns generic success
  @smoke
  Scenario: Deactivated user with correct password gets a reactivation email
    Given a user exists with email "reactivate-ok@acme.com" and status "DEACTIVATED"
    And the user has password "ValidP@ss123!"
    When I submit a reactivation request with:
      | email    | reactivate-ok@acme.com |
      | password | ValidP@ss123!          |
    Then the API should respond with status 200
    And the response should contain "message"
    And a ReactivationRequested event should be persisted in the event store

  # AC-0003-11-06: Reactivation must not leak account state
  Scenario: Reactivation request for an unknown email returns the same generic response
    When I submit a reactivation request with:
      | email    | nobody@acme.com |
      | password | AnyPassword!    |
    Then the API should respond with status 200
    And the response should contain "message"
    And no ReactivationRequested event is persisted for that email

  Scenario: Reactivation request with the wrong password returns the same generic response
    Given a user exists with email "reactivate-wrong-pw@acme.com" and status "DEACTIVATED"
    And the user has password "ValidP@ss123!"
    When I submit a reactivation request with:
      | email    | reactivate-wrong-pw@acme.com |
      | password | WrongPassword                |
    Then the API should respond with status 200
    And the response should contain "message"
    And no ReactivationRequested event is persisted for that email

  Scenario: Reactivation request against a non-deactivated account returns the same generic response
    Given an active user exists with email "still-active@acme.com" and password "ValidP@ss123!"
    When I submit a reactivation request with:
      | email    | still-active@acme.com |
      | password | ValidP@ss123!         |
    Then the API should respond with status 200
    And the response should contain "message"
    And no ReactivationRequested event is persisted for that email

  Scenario: Reactivation request rejects malformed payloads
    When I submit a reactivation request with:
      | email    |              |
      | password | SomePassword |
    Then the API should respond with status 400
