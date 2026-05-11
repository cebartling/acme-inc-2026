@api @identity @logout @authentication
Feature: Customer Logout API (US-0003-14)
  As a signed-in customer
  I want to securely log out of my account
  So that my session is ended and my account is protected on shared devices

  Background:
    Given the Identity Service is available

  # AC-0003-14-01, AC-0003-14-02, AC-0003-14-03
  @smoke
  Scenario: Standard logout invalidates the current session and clears all auth cookies
    Given an active user exists with email "logout-single@acme.com" and password "ValidP@ss123!"
    And the user is signed in
    When I post to the logout endpoint with the access token cookie
    Then the logout API should respond with status 200
    And the logout response status should be "SUCCESS"
    And the response should clear the "access_token" cookie
    And the response should clear the "refresh_token" cookie
    And the response should clear the "device_trust" cookie
    And the user should have 0 active sessions

  # AC-0003-14-06
  Scenario: Standard logout publishes a SessionInvalidated event with reason USER_LOGOUT
    Given an active user exists with email "logout-event@acme.com" and password "ValidP@ss123!"
    And the user is signed in
    When I post to the logout endpoint with the access token cookie
    Then the logout API should respond with status 200
    And a SessionInvalidated event should be published
    And the event should contain "reason" with value "USER_LOGOUT"

  # AC-0003-14-07
  Scenario: Logout without a valid token still returns 200 and clears cookies
    When I post to the logout endpoint without any cookie
    Then the logout API should respond with status 200
    And the logout response status should be "SUCCESS"
    And the response should clear the "access_token" cookie
    And the response should clear the "refresh_token" cookie
    And the response should clear the "device_trust" cookie

  Scenario: Logout with a malformed access token still returns 200
    When I post to the logout endpoint with a malformed access token
    Then the logout API should respond with status 200
    And the logout response status should be "SUCCESS"
    And the response should clear the "access_token" cookie

  # AC-0003-14-05
  Scenario: Logout all devices invalidates every active session and reports the count
    Given an active user exists with email "logout-all@acme.com" and password "ValidP@ss123!"
    And the user is signed in 3 times
    When I post to the logout-all endpoint with the access token cookie
    Then the logout API should respond with status 200
    And the logout-all response should report 3 sessions invalidated
    And the user should have 0 active sessions
    And the response should clear the "access_token" cookie
    And the response should clear the "refresh_token" cookie
    And the response should clear the "device_trust" cookie

  Scenario: Logout all devices without a valid token returns 401
    When I post to the logout-all endpoint without any cookie
    Then the logout API should respond with status 401
