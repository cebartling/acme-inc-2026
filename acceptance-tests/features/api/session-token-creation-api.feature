@api @identity @session @token @authentication
Feature: Session and Token Creation API (US-0003-07)
  As the Identity Management Service
  I want to create secure sessions and generate JWT tokens after successful authentication
  So that authenticated customers can access protected resources securely

  Background:
    Given the Identity Service is available

  # AC-0003-07-01: Access Token Generation
  @smoke
  Scenario: Generate access token after successful MFA verification
    Given an active user exists with email "token@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    When I complete signin and MFA verification for "token@acme.com"
    Then the API should respond with status 200
    And the response should set a secure cookie named "access_token"
    And the access token cookie should have HttpOnly flag
    And the access token cookie should have Secure flag
    And the access token cookie should have SameSite=Strict
    And the access token cookie should have Path="/"
    And the access token cookie should have Max-Age=900

  # AC-0003-07-02: Refresh Token Generation
  @smoke
  Scenario: Generate refresh token after successful MFA verification
    Given an active user exists with email "refresh@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    When I complete signin and MFA verification for "refresh@acme.com"
    Then the API should respond with status 200
    And the response should set a secure cookie named "refresh_token"
    And the refresh token cookie should have HttpOnly flag
    And the refresh token cookie should have Secure flag
    And the refresh token cookie should have SameSite=Strict
    And the refresh token cookie should have Path="/api/v1/auth/refresh"
    And the refresh token cookie should have Max-Age=604800

  # AC-0003-07-03: Key Rotation Support
  Scenario: Access token includes key ID for rotation support
    Given an active user exists with email "keyid@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    When I complete signin and MFA verification for "keyid@acme.com"
    Then the access token JWT should include a "kid" header
    And the "kid" header should match pattern "key-[0-9]{4}-[0-9]{2}"
    And the refresh token JWT should include a "kid" header

  # AC-0003-07-04: Secure Cookie Configuration
  @smoke
  Scenario: Tokens are delivered via secure cookies only
    Given an active user exists with email "cookie@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    When I complete signin and MFA verification for "cookie@acme.com"
    Then the API should respond with status 200
    And the response body should not contain "accessToken"
    And the response body should not contain "refreshToken"
    And the response should set exactly 2 cookies

  # AC-0003-07-05: Session Storage in Redis
  @smoke
  Scenario: Session is created in Redis with TTL
    Given an active user exists with email "session@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    When I complete signin and MFA verification for "session@acme.com"
    Then a session should be created in Redis for the user
    And the session should include sessionId
    And the session should include userId
    And the session should include deviceId
    And the session should include ipAddress
    And the session should include userAgent
    And the session should include createdAt
    And the session should include expiresAt
    And the session TTL in Redis should be approximately 604800 seconds

  # AC-0003-07-06: Concurrent Session Limit
  Scenario: Evict oldest session when 5 session limit is exceeded
    Given an active user exists with email "sessions@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    And the user has 5 active sessions from different devices
    When I complete signin and MFA verification for "sessions@acme.com" from a new device
    Then the API should respond with status 200
    And the user should have exactly 5 active sessions
    And a SessionInvalidated event should be published
    And the invalidated session reason should be "CONCURRENT_SESSION_LIMIT"

  Scenario: Allow up to 5 concurrent sessions without eviction
    Given an active user exists with email "multi@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    And the user has 4 active sessions
    When I complete signin and MFA verification for "multi@acme.com"
    Then the API should respond with status 200
    And the user should have exactly 5 active sessions
    And no SessionInvalidated event should be published

  # AC-0003-07-08: SessionCreated Event Publishing
  Scenario: Publish SessionCreated event with device information
    Given an active user exists with email "session-event@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    When I complete signin and MFA verification for "session-event@acme.com"
    Then a SessionCreated event should be published to Kafka
    And the event should contain sessionId
    And the event should contain userId
    And the event should contain deviceId
    And the event should contain ipAddress
    And the event should contain userAgent
    And the event should contain expiresAt

  # AC-0003-07-09: UserLoggedIn Event Publishing
  @smoke
  Scenario: Publish UserLoggedIn event with MFA context
    Given an active user exists with email "logged-in@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    When I complete signin and MFA verification for "logged-in@acme.com"
    Then a UserLoggedIn event should be published to Kafka
    And the event should contain userId
    And the event should contain sessionId
    And the event should contain ipAddress
    And the event should contain userAgent
    And the event should contain "mfaUsed" with value true
    And the event should contain "mfaMethod" with value "TOTP"
    And the event should contain "loginSource" with value "WEB"

  Scenario: UserLoggedIn event includes device fingerprint when provided
    Given an active user exists with email "fingerprint@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    When I complete signin and MFA verification for "fingerprint@acme.com" with device fingerprint "fp_abc123xyz789"
    Then a UserLoggedIn event should be published
    And the event should contain "deviceFingerprint" with value "fp_abc123xyz789"

  # AC-0003-07-10: Token Family for Rotation
  Scenario: Refresh token includes tokenFamily claim
    Given an active user exists with email "family@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    When I complete signin and MFA verification for "family@acme.com"
    Then the refresh token JWT should include "tokenFamily" claim
    And the tokenFamily should match pattern "fam_[a-f0-9\\-]{36}"

  # Token Claims Verification
  Scenario: Access token contains all required claims
    Given an active user exists with email "claims@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    When I complete signin and MFA verification for "claims@acme.com"
    Then the access token JWT should include claim "sub" with the user's UUID
    And the access token JWT should include claim "email" with value "claims@acme.com"
    And the access token JWT should include claim "roles" containing "CUSTOMER"
    And the access token JWT should include claim "sessionId"
    And the access token JWT should include claim "iat"
    And the access token JWT should include claim "exp"
    And the access token JWT should include claim "iss" with value "https://auth.acme.com"
    And the access token JWT should include claim "aud" with value "https://api.acme.com"

  Scenario: Refresh token contains all required claims
    Given an active user exists with email "refresh-claims@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    When I complete signin and MFA verification for "refresh-claims@acme.com"
    Then the refresh token JWT should include claim "sub" with the user's UUID
    And the refresh token JWT should include claim "sessionId"
    And the refresh token JWT should include claim "tokenFamily"
    And the refresh token JWT should include claim "iat"
    And the refresh token JWT should include claim "exp"
    And the refresh token JWT should include claim "iss" with value "https://auth.acme.com"
    And the refresh token JWT should not include claim "email"
    And the refresh token JWT should not include claim "roles"
    And the refresh token JWT should not include claim "aud"

  # Token Expiration Verification
  Scenario: Access token expires in 15 minutes
    Given an active user exists with email "exp-access@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    When I complete signin and MFA verification for "exp-access@acme.com"
    Then the access token should expire in 900 seconds

  Scenario: Refresh token expires in 7 days
    Given an active user exists with email "exp-refresh@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    When I complete signin and MFA verification for "exp-refresh@acme.com"
    Then the refresh token should expire in 604800 seconds

  # RS256 Signature Verification
  Scenario: Tokens are signed with RS256 algorithm
    Given an active user exists with email "rs256@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    When I complete signin and MFA verification for "rs256@acme.com"
    Then the access token JWT header should have "alg" value "RS256"
    And the access token JWT header should have "typ" value "JWT"
    And the refresh token JWT header should have "alg" value "RS256"
    And the refresh token JWT header should have "typ" value "JWT"

  # SMS MFA Integration
  Scenario: Create session and tokens after SMS MFA verification
    Given an active user exists with email "sms-session@acme.com" and password "ValidP@ss123!"
    And the user has SMS MFA enabled with phone number "+15551234567"
    When I complete signin and SMS MFA verification for "sms-session@acme.com"
    Then the API should respond with status 200
    And the response should set a secure cookie named "access_token"
    And the response should set a secure cookie named "refresh_token"
    And a UserLoggedIn event should be published
    And the event should contain "mfaMethod" with value "SMS"

  # Performance Requirements
  @slow
  Scenario: Token generation completes within 50ms (p95)
    Given an active user exists with email "perf@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    When I measure the response time for 100 MFA verification requests
    Then the 95th percentile response time should be less than 50ms

  # Security: No Token Exposure in Logs
  @security
  Scenario: Tokens are not exposed in response body or logs
    Given an active user exists with email "security@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    When I complete signin and MFA verification for "security@acme.com"
    Then the response body should not contain any JWT token strings
    And the application logs should not contain any JWT token strings
