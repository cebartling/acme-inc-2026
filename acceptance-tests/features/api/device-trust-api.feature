@api @identity @device-trust @mfa
Feature: Device Trust for MFA Bypass API (US-0003-08)
  As the Identity Management Service
  I want to allow users to trust devices and bypass MFA for 30 days
  So that users have a better experience while maintaining security

  Background:
    Given the Identity Service is available
    And the Redis cache is available

  # AC-0003-08-01: Device Trust Token Generation
  @smoke
  Scenario: Create device trust after MFA verification with rememberDevice flag
    Given an active user exists with email "devicetrust@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    When I signin with email "devicetrust@acme.com" and password "ValidP@ss123!"
    And I verify MFA with the correct TOTP code and rememberDevice set to true
    Then the API should respond with status 200
    And the response should set a secure cookie named "device_trust"
    And the device_trust cookie should have HttpOnly flag
    And the device_trust cookie should have Secure flag
    And the device_trust cookie should have SameSite=Strict
    And the device_trust cookie should have Path="/"
    And the device_trust cookie should have Max-Age=2592000
    And the device trust token should be stored in Redis
    And the device trust should have a TTL of approximately 2592000 seconds

  # AC-0003-08-02: MFA Bypass on Trusted Device
  @smoke
  Scenario: Bypass MFA when device trust cookie is present and valid
    Given an active user exists with email "trusted@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    And the user has a valid device trust from a previous signin
    When I signin with email "trusted@acme.com" and password "ValidP@ss123!" with the device trust cookie
    Then the API should respond with status 200
    And the response should contain "SUCCESS" status
    And the response should set a secure cookie named "access_token"
    And the response should set a secure cookie named "refresh_token"
    And MFA verification should not be required

  # AC-0003-08-03: Device Trust Expiry
  Scenario: Require MFA when device trust has expired
    Given an active user exists with email "expired@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    And the user has an expired device trust
    When I signin with email "expired@acme.com" and password "ValidP@ss123!" with the expired device trust cookie
    Then the API should respond with status 200
    And the response should contain "MFA_REQUIRED" status
    And MFA verification should be required

  # AC-0003-08-04: Maximum 10 Trusted Devices
  Scenario: Evict oldest device trust when limit of 10 is exceeded
    Given an active user exists with email "limit@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    And the user has 10 active device trusts
    When I signin with email "limit@acme.com" and password "ValidP@ss123!" from a new device
    And I verify MFA with the correct TOTP code and rememberDevice set to true
    Then the API should respond with status 200
    And the user should have exactly 10 device trusts in Redis
    And the oldest device trust should have been evicted
    And a DeviceRevoked event should be published with reason LIMIT_EXCEEDED

  # AC-0003-08-05: Device Tied to Fingerprint and User Agent
  Scenario: Invalidate device trust when fingerprint changes
    Given an active user exists with email "fingerprint@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    And the user has a valid device trust with fingerprint "fp_original"
    When I signin with email "fingerprint@acme.com" and password "ValidP@ss123!" with fingerprint "fp_different"
    Then the API should respond with status 200
    And the response should contain "MFA_REQUIRED" status
    And MFA verification should be required

  Scenario: Invalidate device trust when user agent changes
    Given an active user exists with email "useragent@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    And the user has a valid device trust with user agent "Mozilla/5.0 (Chrome)"
    When I signin with email "useragent@acme.com" and password "ValidP@ss123!" with user agent "Mozilla/5.0 (Firefox)"
    Then the API should respond with status 200
    And the response should contain "MFA_REQUIRED" status
    And MFA verification should be required

  # AC-0003-08-06: List Trusted Devices
  @smoke
  Scenario: Retrieve list of all trusted devices for authenticated user
    Given an active user exists with email "list@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    And the user has 3 active device trusts
    And I have an active session for "list@acme.com"
    When I request GET "/api/v1/auth/devices"
    Then the API should respond with status 200
    And the response should contain exactly 3 devices
    And each device should include id
    And each device should include deviceName
    And each device should include createdAt
    And each device should include lastUsedAt
    And each device should include expiresAt
    And each device should include ipAddress
    And each device should include isCurrent

  Scenario: List devices requires authentication
    When I request GET "/api/v1/auth/devices" without authentication
    Then the API should respond with status 401
    And the response should contain error "UNAUTHORIZED"

  # AC-0003-08-07: Revoke Individual Device
  @smoke
  Scenario: Revoke a single trusted device
    Given an active user exists with email "revoke@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    And the user has a device trust with ID "trust_device123"
    And I have an active session for "revoke@acme.com"
    When I request DELETE "/api/v1/auth/devices/trust_device123"
    Then the API should respond with status 204
    And the device trust "trust_device123" should not exist in Redis
    And a DeviceRevoked event should be published with reason USER_REVOKED

  Scenario: Cannot revoke device belonging to another user
    Given an active user exists with email "owner@acme.com" and password "ValidP@ss123!"
    And an active user exists with email "attacker@acme.com" and password "ValidP@ss123!"
    And the user "owner@acme.com" has TOTP MFA enabled
    And the user "attacker@acme.com" has TOTP MFA enabled
    And the user "owner@acme.com" has a device trust with ID "trust_victim123"
    And I have an active session for "attacker@acme.com"
    When I request DELETE "/api/v1/auth/devices/trust_victim123"
    Then the API should respond with status 404
    And the device trust "trust_victim123" should still exist in Redis

  # AC-0003-08-08: Revoke All Devices
  @smoke
  Scenario: Revoke all trusted devices for a user
    Given an active user exists with email "revokeall@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    And the user has 5 active device trusts
    And I have an active session for "revokeall@acme.com"
    When I request DELETE "/api/v1/auth/devices"
    Then the API should respond with status 204
    And the user should have 0 device trusts in Redis
    And 5 DeviceRevoked events should be published with reason USER_REVOKED_ALL

  # AC-0003-08-09: Password Change Revokes All Devices
  @future
  Scenario: All device trusts are revoked when user changes password
    Given an active user exists with email "pwchange@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    And the user has 3 active device trusts
    And I have an active session for "pwchange@acme.com"
    When I change my password from "ValidP@ss123!" to "NewP@ss123!"
    Then the password change should succeed
    And the user should have 0 device trusts in Redis
    And 3 DeviceRevoked events should be published with reason PASSWORD_CHANGED

  # AC-0003-08-10: Device Trust Audit Trail
  @smoke
  Scenario: DeviceRemembered event published when device trust is created
    Given an active user exists with email "audit@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    When I signin with email "audit@acme.com" and password "ValidP@ss123!"
    And I verify MFA with the correct TOTP code and rememberDevice set to true
    Then a DeviceRemembered event should be published
    And the DeviceRemembered event should include userId
    And the DeviceRemembered event should include deviceTrustId
    And the DeviceRemembered event should include deviceFingerprint
    And the DeviceRemembered event should include userAgent
    And the DeviceRemembered event should include ipAddress
    And the DeviceRemembered event should include trustedUntil

  # AC-0003-08-11: Device Trust Updates Last Used Timestamp
  Scenario: Update lastUsedAt when device trust is used for MFA bypass
    Given an active user exists with email "lastused@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    And the user has a valid device trust created 5 days ago
    When I signin with email "lastused@acme.com" and password "ValidP@ss123!" with the device trust cookie
    Then the API should respond with status 200
    And the device trust lastUsedAt should be updated to now

  # AC-0003-08-12: IP Address Not Enforced
  Scenario: Device trust remains valid when IP address changes
    Given an active user exists with email "ipchange@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    And the user has a valid device trust created from IP "192.168.1.100"
    When I signin with email "ipchange@acme.com" and password "ValidP@ss123!" from IP "10.0.0.50"
    Then the API should respond with status 200
    And the response should contain "SUCCESS" status
    And MFA verification should not be required

  # AC-0003-08-13: Device Name Parsing
  Scenario: Parse human-readable device name from user agent
    Given an active user exists with email "devicename@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    When I signin with email "devicename@acme.com" and password "ValidP@ss123!" with user agent "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
    And I verify MFA with the correct TOTP code and rememberDevice set to true
    And I have an active session for "devicename@acme.com"
    And I request GET "/api/v1/auth/devices"
    Then the API should respond with status 200
    And the first device should have deviceName "Chrome on macOS"

  # AC-0003-08-14: No Device Trust Without rememberDevice Flag
  Scenario: Device trust is not created when rememberDevice is false
    Given an active user exists with email "noremember@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    When I signin with email "noremember@acme.com" and password "ValidP@ss123!"
    And I verify MFA with the correct TOTP code and rememberDevice set to false
    Then the API should respond with status 200
    And the response should not set a cookie named "device_trust"
    And the user should have 0 device trusts in Redis

  # AC-0003-08-15: Current Device Indicator
  Scenario: Mark current device in device list
    Given an active user exists with email "current@acme.com" and password "ValidP@ss123!"
    And the user has TOTP MFA enabled
    And the user has 2 active device trusts
    And the user has a device trust with ID "trust_current123"
    And I have an active session for "current@acme.com" with device trust "trust_current123"
    When I request GET "/api/v1/auth/devices"
    Then the API should respond with status 200
    And the device "trust_current123" should have isCurrent set to true
    And all other devices should have isCurrent set to false
