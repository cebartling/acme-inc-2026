# Device Trust Acceptance Testing

## Overview

This document describes the acceptance test suite for the Device Trust feature (US-0003-08), which allows users to bypass MFA for 30 days on trusted devices.

## Test Coverage

### Feature File

**Location**: `features/api/device-trust-api.feature`

**Scenarios**: 15 scenarios covering all acceptance criteria

**Tags**:
- `@api` - API-only tests (no browser required)
- `@identity` - Identity service tests
- `@device-trust` - Device trust specific tests
- `@mfa` - MFA-related tests
- `@smoke` - Critical smoke tests (run first)
- `@future` - Future functionality (password change integration)

### Acceptance Criteria Coverage

| AC | Description | Scenarios |
|----|-------------|-----------|
| AC-0003-08-01 | Device trust token generation | 1 |
| AC-0003-08-02 | MFA bypass on trusted device | 1 |
| AC-0003-08-03 | Device trust expiry | 1 |
| AC-0003-08-04 | Maximum 10 trusted devices | 1 |
| AC-0003-08-05 | Device tied to fingerprint/user agent | 2 |
| AC-0003-08-06 | List trusted devices | 2 |
| AC-0003-08-07 | Revoke individual device | 2 |
| AC-0003-08-08 | Revoke all devices | 1 |
| AC-0003-08-09 | Password change revokes all | 1 (future) |
| AC-0003-08-10 | Device trust audit trail | 1 |
| AC-0003-08-11 | Update lastUsedAt timestamp | 1 |
| AC-0003-08-12 | IP address not enforced | 1 |
| AC-0003-08-13 | Device name parsing | 1 |
| AC-0003-08-14 | No trust without rememberDevice | 1 |
| AC-0003-08-15 | Current device indicator | 1 |

**Total**: 18 scenarios

### Step Definitions

**Location**: `steps/api/device-trust-api.steps.ts`

**Step Types**:
- **GIVEN** (12 steps) - Test setup and preconditions
- **WHEN** (7 steps) - Actions and API calls
- **THEN** (22 steps) - Assertions and verifications

**Total**: 41 step definitions

## Test Prerequisites

### Required Services

1. **Identity Service** - Must be running on configured port
2. **Redis** - Must be available for device trust storage
3. **Kafka** (optional) - For event publishing verification
4. **PostgreSQL** - For event store persistence

### Test API Endpoints Required

The following test-only endpoints must be implemented in the Identity service:

#### Device Trust Management

```http
POST /api/v1/test/users/:userId/device-trusts
{
  "deviceFingerprint": "string",
  "userAgent": "string",
  "ipAddress": "string",
  "ttlSeconds"?: number,
  "createdDaysAgo"?: number,
  "deviceTrustId"?: string
}
```

```http
GET /api/v1/test/users/:userId/device-trusts
Response: { "devices": DeviceTrust[] }
```

```http
GET /api/v1/test/device-trusts/:deviceTrustId
Response: DeviceTrust
```

```http
GET /api/v1/test/device-trusts/:deviceTrustId/ttl
Response: { "ttl": number }
```

#### Event Verification

```http
GET /api/v1/test/events/DeviceRemembered?userId=:userId
Response: { "events": Event[] }
```

```http
GET /api/v1/test/events/DeviceRevoked?userId=:userId
Response: { "events": Event[] }
```

## Running the Tests

### Run All Device Trust Tests

```bash
npm test -- --tags "@device-trust"
```

### Run Smoke Tests Only

```bash
npm test -- --tags "@device-trust and @smoke"
```

### Run Specific Scenario

```bash
npm test -- --name "Create device trust after MFA verification"
```

### Run API Tests (No Browser)

```bash
npm test -- --tags "@api and @device-trust"
```

## Test Scenarios

### 1. Device Trust Creation (AC-01)

**Scenario**: Create device trust after MFA verification with rememberDevice flag

**Steps**:
1. User signs in with email/password
2. User completes MFA with `rememberDevice=true`
3. System creates device trust in Redis
4. System sets `device_trust` cookie (30-day TTL)
5. System publishes `DeviceRemembered` event

**Verifications**:
- Cookie has correct attributes (HttpOnly, Secure, SameSite=Strict, Max-Age=2592000)
- Device trust stored in Redis with correct TTL
- DeviceRemembered event published with all required fields

### 2. MFA Bypass (AC-02)

**Scenario**: Bypass MFA when device trust cookie is present and valid

**Steps**:
1. User has existing valid device trust
2. User signs in with email/password
3. User provides valid device trust cookie
4. System verifies device trust (fingerprint, user agent, expiry)
5. System skips MFA and creates session directly

**Verifications**:
- Response status is SUCCESS (not MFA_REQUIRED)
- Access and refresh tokens are issued
- No MFA challenge created

### 3. Device Trust Expiry (AC-03)

**Scenario**: Require MFA when device trust has expired

**Steps**:
1. User has expired device trust (TTL=0)
2. User signs in with email/password
3. System detects expired trust
4. System requires MFA

**Verifications**:
- Response status is MFA_REQUIRED
- MFA challenge is created

### 4. Device Limit (AC-04)

**Scenario**: Evict oldest device trust when limit of 10 is exceeded

**Steps**:
1. User has 10 active device trusts
2. User signs in from new device
3. System evicts oldest device trust
4. System creates new device trust

**Verifications**:
- User has exactly 10 device trusts (not 11)
- Oldest device trust no longer exists in Redis
- DeviceRevoked event published with reason LIMIT_EXCEEDED

### 5. Fingerprint Validation (AC-05)

**Scenario**: Invalidate device trust when fingerprint changes

**Steps**:
1. User has device trust with fingerprint "fp_original"
2. User signs in with fingerprint "fp_different"
3. System detects fingerprint mismatch
4. System requires MFA

**Verifications**:
- Response status is MFA_REQUIRED
- Device trust not used for bypass

### 6. User Agent Validation (AC-05)

**Scenario**: Invalidate device trust when user agent changes

**Steps**:
1. User has device trust with user agent "Chrome"
2. User signs in with user agent "Firefox"
3. System detects user agent mismatch
4. System requires MFA

**Verifications**:
- Response status is MFA_REQUIRED
- Device trust not used for bypass

### 7. List Devices (AC-06)

**Scenario**: Retrieve list of all trusted devices

**Steps**:
1. User has 3 active device trusts
2. User requests GET /api/v1/auth/devices
3. System returns all device trusts

**Verifications**:
- Response contains exactly 3 devices
- Each device has all required fields
- Current device is marked with `isCurrent=true`

### 8. Revoke Device (AC-07)

**Scenario**: Revoke a single trusted device

**Steps**:
1. User has device trust "trust_device123"
2. User requests DELETE /api/v1/auth/devices/trust_device123
3. System deletes device trust
4. System publishes DeviceRevoked event

**Verifications**:
- Response status is 204 No Content
- Device trust removed from Redis
- DeviceRevoked event published with reason USER_REVOKED

### 9. Revoke All (AC-08)

**Scenario**: Revoke all trusted devices for a user

**Steps**:
1. User has 5 active device trusts
2. User requests DELETE /api/v1/auth/devices
3. System deletes all device trusts
4. System publishes 5 DeviceRevoked events

**Verifications**:
- Response status is 204 No Content
- User has 0 device trusts in Redis
- 5 DeviceRevoked events published with reason USER_REVOKED_ALL

### 10. Audit Trail (AC-10)

**Scenario**: DeviceRemembered event published when device trust is created

**Steps**:
1. User completes MFA with `rememberDevice=true`
2. System publishes DeviceRemembered event

**Verifications**:
- Event includes all required fields (userId, deviceTrustId, fingerprint, userAgent, ipAddress, trustedUntil)
- Event persisted to event store
- Event published to Kafka

## Edge Cases

### IP Address Changes

**Expected Behavior**: Device trust remains valid when IP address changes

**Rationale**: Mobile networks and VPNs frequently change IPs

**Test**: AC-0003-08-12

### Last Used Timestamp

**Expected Behavior**: Update `lastUsedAt` when device trust is used for bypass

**Purpose**: Track device activity for security monitoring

**Test**: AC-0003-08-11

### Device Name Parsing

**Expected Behavior**: Parse human-readable name from user agent

**Examples**:
- "Chrome on macOS"
- "Firefox on Windows"
- "Safari on iOS"

**Test**: AC-0003-08-13

### No Trust Without Flag

**Expected Behavior**: Device trust not created when `rememberDevice=false`

**Test**: AC-0003-08-14

### Current Device Indicator

**Expected Behavior**: Mark current device with `isCurrent=true`

**Purpose**: Help users identify which device they're using

**Test**: AC-0003-08-15

## Security Tests

### Cross-User Access Prevention

**Scenario**: User cannot revoke another user's device trust

**Steps**:
1. User A has device trust "trust_victim123"
2. User B attempts to revoke "trust_victim123"
3. System denies access

**Verifications**:
- Response status is 404 Not Found
- Device trust still exists in Redis

### Authentication Required

**Scenario**: All device management endpoints require authentication

**Test**: GET /api/v1/auth/devices without authentication returns 401

## Future Tests (Tagged @future)

### Password Change Integration

**Status**: Not yet implemented in Identity service

**Scenario**: All device trusts are revoked when user changes password

**Steps**:
1. User has 3 active device trusts
2. User changes password
3. System revokes all device trusts
4. System publishes 3 DeviceRevoked events with reason PASSWORD_CHANGED

**Note**: This test is tagged with `@future` and will be skipped until password change functionality is implemented.

## Troubleshooting

### Common Issues

1. **Redis Connection Errors**
   - Ensure Redis is running
   - Check Redis connection configuration
   - Verify network connectivity

2. **Event Publishing Failures**
   - Ensure Kafka is running
   - Check Kafka topic configuration
   - Verify event serialization

3. **Test Data Cleanup**
   - Tests use test sessions for automatic cleanup
   - If cleanup fails, manually flush Redis test keys
   - Check test API endpoints are accessible

### Debugging Steps

1. **Enable Verbose Logging**
   ```bash
   DEBUG=* npm test -- --tags "@device-trust"
   ```

2. **Check Test API Endpoints**
   ```bash
   curl http://localhost:10300/api/v1/test/health
   ```

3. **Inspect Redis Data**
   ```bash
   redis-cli
   > KEYS device_trusts:*
   > GET device_trusts:trust_xxxxx
   ```

4. **View Event Store**
   ```sql
   SELECT * FROM event_store
   WHERE event_type IN ('DeviceRemembered', 'DeviceRevoked')
   ORDER BY timestamp DESC;
   ```

## Maintenance

### Adding New Tests

1. Add scenario to `features/api/device-trust-api.feature`
2. Implement missing steps in `steps/api/device-trust-api.steps.ts`
3. Update this documentation
4. Run tests to verify
5. Update coverage table

### Updating Test API

When backend implementation changes:
1. Update test API endpoints
2. Update step definitions
3. Update documentation
4. Verify all tests pass

## Success Criteria

All 18 scenarios should pass (excluding @future tagged scenarios):
- ✓ 15 core scenarios
- ✓ 3 future scenarios (skipped)

**Coverage Target**: 100% of acceptance criteria
