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
