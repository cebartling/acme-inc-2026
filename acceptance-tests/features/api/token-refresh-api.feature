@api @identity @session @token @authentication @refresh
Feature: Token Refresh API (US-0003-12)
  As a signed-in customer's web client
  I want to refresh expired access tokens using the long-lived refresh token cookie
  So that the customer stays signed in for the full 7-day session lifetime
  without being interrupted every 15 minutes

  Background:
    Given the Identity Service is available

  # AC-0003-12-01, AC-0003-12-02: Automatic refresh + token rotation
  @smoke
  Scenario: Refresh rotates both cookies and the tokenFamily
    Given an active user exists with email "refresh-happy@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    And I complete signin and MFA verification for "refresh-happy@acme.com"
    And I remember the current refresh token's tokenFamily claim
    When I POST to "/api/v1/auth/refresh" with the current refresh_token cookie
    Then the API should respond with status 200
    And the response should set a secure cookie named "access_token"
    And the access token cookie should have HttpOnly flag
    And the access token cookie should have Path="/"
    And the access token cookie should have Max-Age=900
    And the response should set a secure cookie named "refresh_token"
    And the refresh token cookie should have HttpOnly flag
    And the refresh token cookie should have Path="/api/v1/auth/refresh"
    And the refresh token cookie should have Max-Age=604800
    And the new refresh token's tokenFamily claim should differ from the remembered tokenFamily
    And the refresh response status field should be "SUCCESS"
    And the refresh response expiresIn field should be 900

  # AC-0003-12-03, AC-0003-12-07: Reuse detection invalidates the family
  # The acceptance test simulates reuse by signing in, capturing the real
  # refresh-token cookie, then asking the server to rotate the session's
  # tokenFamily out from under us. Presenting the now-stale cookie must
  # fail with TOKEN_REUSE_DETECTED and clear all auth cookies. The
  # OWASP-style all-session invalidation behavior + TokenReuseDetected
  # event are covered by the integration / unit tests; this scenario
  # focuses on the publicly observable API contract.
  Scenario: Reuse detection rejects a stale refresh token and clears cookies
    Given an active user exists with email "refresh-reuse@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    And I complete signin and MFA verification for "refresh-reuse@acme.com"
    And the server has rotated the current session's tokenFamily out of band
    When I POST to "/api/v1/auth/refresh" with the current refresh_token cookie
    Then the API should respond with status 401
    And the response should contain error "TOKEN_REUSE_DETECTED"
    And the access_token cookie should be cleared
    And the refresh_token cookie should be cleared

  # AC-0003-12-05: Refresh failure cleanup — the endpoint must clear cookies
  # even on the "no token presented" path so a misbehaving client can't get
  # stuck with a stale refresh_token cookie wedged in its jar.
  Scenario: Refresh without the refresh_token cookie returns 401 and clears cookies
    When I POST to "/api/v1/auth/refresh" with no cookies
    Then the API should respond with status 401
    And the response should contain error "TOKEN_EXPIRED"
    And the access_token cookie should be cleared
    And the refresh_token cookie should be cleared

  # AC-0003-12-05, AC-0003-12-06: After logout, the session is gone from
  # Redis. Any client that still holds the old refresh-token cookie must
  # get a clean 401 + cleared cookies, not a successful rotation.
  Scenario: Refresh after logout returns 401 and clears cookies
    Given an active user exists with email "refresh-logout@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    And I complete signin and MFA verification for "refresh-logout@acme.com"
    And the user has logged out of the current session
    When I POST to "/api/v1/auth/refresh" with the current refresh_token cookie
    Then the API should respond with status 401
    And the response should contain error "TOKEN_EXPIRED"
    And the access_token cookie should be cleared
    And the refresh_token cookie should be cleared

