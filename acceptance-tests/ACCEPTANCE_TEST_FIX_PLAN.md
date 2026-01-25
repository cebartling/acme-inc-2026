# Acceptance Test Fix Plan

## Summary

20 acceptance test scenarios are failing across 3 feature areas:
- **Device Trust API**: 8 failures (authentication and business logic issues)
- **Session Token Creation API**: 11 failures (cookie extraction and JWT verification issues)
- **Email Verification API**: 1 failure (header access issue)

The root causes are:
1. **API Client Usage**: Incorrect parameter passing to ApiClient methods
2. **Header Access**: Treating plain object headers as Headers API objects
3. **Cookie Extraction**: Not accessing cookies from the correct response property
4. **Business Logic**: Password change not revoking device trusts, lastUsedAt not updating

---

## Issues by Category

### Category 1: API Client Method Signature Issues

**Affected Files:**
- `acceptance-tests/steps/api/device-trust-api.steps.ts`

**Problem:**
The ApiClient methods expect options as `{ headers: Record<string, string> }`, but the code is passing headers directly as the second parameter.

**Affected Step Definitions (lines in device-trust-api.steps.ts):**
- Line 643: `this.identityApiClient.get(path, headers)` - should be `get(path, { headers })`
- Line 671: `this.identityApiClient.delete(path, headers)` - should be `delete(path, { headers })`
- Line 692: `this.identityApiClient.post(..., headers)` - should be `post(..., { headers })`

**Impact:**
- Scenarios 1, 2, 3, 4, 8 fail with 401 Unauthorized because headers aren't sent correctly

---

### Category 2: Headers API vs Plain Object Confusion

**Affected Files:**
- `acceptance-tests/steps/api/email-verification.steps.ts`
- `acceptance-tests/steps/api/session-token-creation.steps.ts`

**Problem:**
The ApiResponse type defines headers as a plain object, but code tries to call `.get()` and `.getSetCookie()` methods which only exist on the Headers API object.

**Affected Locations:**

**email-verification.steps.ts:**
- Line 180: `response.headers.get('location')` - should be `response.headers['location']`
- Line 192: `response.headers.get('location')` - should be `response.headers['location']`

**session-token-creation.steps.ts:**
- Line 203: `verifyResponse.headers.getSetCookie` - should check `verifyResponse.headers['set-cookie']`
- Line 259: Similar pattern exists in other scenarios
- Line 286: Similar pattern exists in other scenarios

**Impact:**
- Scenario 9 (Email Verification): TypeError - headers.get is not a function
- Scenarios 10-20 (Session Token Creation): Cannot find Set-Cookie headers, all cookie/JWT assertions fail

---

### Category 3: Set-Cookie Header Extraction Logic

**Affected Files:**
- `acceptance-tests/steps/api/session-token-creation.steps.ts`
- `acceptance-tests/steps/api/device-trust-api.steps.ts`

**Problem:**
Code checks for `headers.getSetCookie()` method instead of accessing `headers['set-cookie']` array property.

**Fix Pattern:**
```typescript
// Current (incorrect):
if (verifyResponse.headers.getSetCookie) {
  const setCookieHeaders = verifyResponse.headers.getSetCookie();
  // ...
}

// Should be:
const setCookieHeaders = verifyResponse.headers['set-cookie'];
if (setCookieHeaders && setCookieHeaders.length > 0) {
  this.setTestData('setCookieHeaders', setCookieHeaders);
  // ...
}
```

**Affected Locations in session-token-creation.steps.ts:**
- Lines 203-219 (scenario: I complete signin and MFA verification)
- Lines 259-276 (scenario: I complete signin from a new device)
- Lines 307-324 (scenario: I complete signin and SMS MFA verification)
- Lines 355-372 (scenario: I complete signin with rememberDevice)

**Affected Locations in device-trust-api.steps.ts:**
- Lines 114-120 (scenario: I have an active session - handling MFA response)
- Lines 117-119 (scenario: I have an active session - handling direct signin)

**Impact:**
- Scenarios 10-20: No cookies extracted, all JWT/cookie assertions fail
- Scenarios 1-8: Session creation fails, authenticated requests get 401

---

### Category 4: Business Logic - Device Trust Not Revoked on Password Change

**Affected File:**
- Backend implementation (not in acceptance tests)

**Problem:**
When a user changes their password, the system should revoke all device trusts for security, but this is not happening.

**Test:**
- `features/api/device-trust-api.feature:142` - Scenario 5

**Expected Behavior:**
After password change, user should have 0 device trusts in Redis.

**Actual Behavior:**
User still has 3 device trusts after password change.

**Error:**
```
Expected length: 0
Received length: 3
```

**Fix Location:**
Backend password change endpoint should:
1. Update password
2. Query all device trusts for the user
3. Delete each device trust from Redis
4. Publish DeviceRevoked events with reason PASSWORD_CHANGED

---

### Category 5: Business Logic - Device Trust lastUsedAt Not Updated

**Affected File:**
- Backend implementation (not in acceptance tests)

**Problem:**
When a device trust is used to bypass MFA, the `lastUsedAt` timestamp should be updated to the current time, but it remains at the original creation time.

**Test:**
- `features/api/device-trust-api.feature:168` - Scenario 6

**Expected Behavior:**
lastUsedAt should be within 5 seconds of current time when device trust is used.

**Actual Behavior:**
lastUsedAt remains at creation time (5 days ago = 432,000 seconds difference).

**Error:**
```
Expected: < 5
Received: 432000.072
```

**Fix Location:**
Backend MFA bypass logic should:
1. When device trust cookie is valid and MFA is bypassed
2. Update the device trust record in Redis with `lastUsedAt = now()`
3. Extend the TTL if needed

---

## Fix Order and Dependencies

### Phase 1: API Client Usage Fixes (No Dependencies)
**Estimated Impact:** Fixes 8 device trust scenarios (1-8)

1. Fix `device-trust-api.steps.ts` - correct ApiClient method calls
   - Line 643: GET request
   - Line 671: DELETE request
   - Line 692: POST request

### Phase 2: Header Access Fixes (No Dependencies)
**Estimated Impact:** Fixes 1 email verification scenario (9)

2. Fix `email-verification.steps.ts` - use plain object property access
   - Line 180: `headers['location']`
   - Line 192: `headers['location']`

### Phase 3: Cookie Extraction Fixes (No Dependencies)
**Estimated Impact:** Fixes 11 session token scenarios (10-20)

3. Fix `session-token-creation.steps.ts` - extract cookies from headers['set-cookie']
   - Lines 203-219: Complete signin and MFA verification
   - Lines 259-276: Complete signin from new device
   - Lines 307-324: Complete signin and SMS MFA
   - Lines 355-372: Complete signin with rememberDevice

4. Fix `device-trust-api.steps.ts` - extract cookies from headers['set-cookie']
   - Lines 114-120: Active session with MFA
   - Lines 117-119: Active session direct signin

### Phase 4: Backend Business Logic (Requires Backend Changes)
**Estimated Impact:** Fixes remaining 2 device trust scenarios (5, 6)

5. Backend: Implement device trust revocation on password change
   - Endpoint: `/api/v1/auth/change-password`
   - Action: Delete all device trusts for user, publish events

6. Backend: Update lastUsedAt when device trust is used
   - Location: MFA bypass logic in signin flow
   - Action: Update Redis record with current timestamp

---

## Detailed Fix Instructions

### Fix 1: device-trust-api.steps.ts - API Client Method Calls

**File:** `acceptance-tests/steps/api/device-trust-api.steps.ts`

**Line 643 - GET request:**
```typescript
// Before:
const response = await this.identityApiClient.get(path, headers);

// After:
const response = await this.identityApiClient.get(path, { headers });
```

**Line 671 - DELETE request:**
```typescript
// Before:
const response = await this.identityApiClient.delete(path, headers);

// After:
const response = await this.identityApiClient.delete(path, { headers });
```

**Line 692 - POST request:**
```typescript
// Before:
const response = await this.identityApiClient.post(
  '/api/v1/auth/change-password',
  {
    currentPassword: oldPassword,
    newPassword,
  },
  headers
);

// After:
const response = await this.identityApiClient.post(
  '/api/v1/auth/change-password',
  {
    currentPassword: oldPassword,
    newPassword,
  },
  { headers }
);
```

---

### Fix 2: email-verification.steps.ts - Header Access

**File:** `acceptance-tests/steps/api/email-verification.steps.ts`

**Line 180:**
```typescript
// Before:
this.setTestData('redirectLocation', response.headers.get('location'));

// After:
this.setTestData('redirectLocation', response.headers['location']);
```

**Line 192:**
```typescript
// Before:
this.setTestData('redirectLocation', response.headers.get('location'));

// After:
this.setTestData('redirectLocation', response.headers['location']);
```

---

### Fix 3: session-token-creation.steps.ts - Cookie Extraction

**File:** `acceptance-tests/steps/api/session-token-creation.steps.ts`

**Pattern to apply at lines 203-219, 259-276, 307-324, 355-372:**

```typescript
// Before:
if (verifyResponse.headers.getSetCookie) {
  const setCookieHeaders = verifyResponse.headers.getSetCookie();
  if (setCookieHeaders.length > 0) {
    this.setTestData('setCookieHeaders', setCookieHeaders);
    // ...
  }
}

// After:
const setCookieHeaders = verifyResponse.headers['set-cookie'];
if (setCookieHeaders && setCookieHeaders.length > 0) {
  this.setTestData('setCookieHeaders', setCookieHeaders);

  // Automatically extract and store token values
  const accessToken = extractCookieValue(setCookieHeaders, 'access_token');
  if (accessToken) {
    this.setTestData('access_token_value', accessToken);
  }

  const refreshToken = extractCookieValue(setCookieHeaders, 'refresh_token');
  if (refreshToken) {
    this.setTestData('refresh_token_value', refreshToken);
  }
}
```

**Specific locations:**
1. **Line 203** - "I complete signin and MFA verification for"
2. **Line 259** - "I complete signin and MFA verification for from a new device"
3. **Line 307** - "I complete signin and SMS MFA verification for"
4. **Line 355** - "I signin with email and password and verify MFA with rememberDevice"

---

### Fix 4: device-trust-api.steps.ts - Cookie Extraction in Session Creation

**File:** `acceptance-tests/steps/api/device-trust-api.steps.ts`

**Lines 104-120 in "I have an active session for" step:**

```typescript
// Before:
const mfaResponse = await this.identityApiClient.post('/api/v1/auth/mfa/verify', {
  mfaToken,
  code: totpCode,
  method: 'TOTP',
  rememberDevice: false,
});

expect(mfaResponse.status).toBe(200);

// Store cookies for authenticated requests
const accessToken = extractCookieValue(mfaResponse.headers['set-cookie'], 'access_token');
this.setTestData('access_token', accessToken);

// After:
const mfaResponse = await this.identityApiClient.post('/api/v1/auth/mfa/verify', {
  mfaToken,
  code: totpCode,
  method: 'TOTP',
  rememberDevice: false,
});

expect(mfaResponse.status).toBe(200);

// Store cookies for authenticated requests
const setCookieHeaders = mfaResponse.headers['set-cookie'];
if (setCookieHeaders) {
  const accessToken = extractCookieValue(setCookieHeaders, 'access_token');
  this.setTestData('access_token', accessToken);
}
```

**Lines 117-119 (else block):**
```typescript
// Before:
const accessToken = extractCookieValue(signinResponse.headers['set-cookie'], 'access_token');
this.setTestData('access_token', accessToken);

// After:
const setCookieHeaders = signinResponse.headers['set-cookie'];
if (setCookieHeaders) {
  const accessToken = extractCookieValue(setCookieHeaders, 'access_token');
  this.setTestData('access_token', accessToken);
}
```

---

### Fix 5: Backend - Revoke Device Trusts on Password Change

**Location:** Backend password change endpoint handler

**Requirements:**
1. After successfully changing password
2. Query Redis for all device trusts belonging to the user
3. Delete each device trust from Redis
4. Publish `DeviceRevoked` event for each with reason `PASSWORD_CHANGED`

**Pseudocode:**
```typescript
async function changePassword(userId: string, currentPassword: string, newPassword: string) {
  // 1. Validate current password
  // 2. Update password in database

  // 3. Revoke all device trusts
  const deviceTrusts = await redis.getAllDeviceTrustsForUser(userId);

  for (const trust of deviceTrusts) {
    await redis.deleteDeviceTrust(trust.id);
    await eventBus.publish({
      type: 'DeviceRevoked',
      userId,
      deviceTrustId: trust.id,
      reason: 'PASSWORD_CHANGED',
      timestamp: new Date()
    });
  }

  return { success: true };
}
```

---

### Fix 6: Backend - Update lastUsedAt When Device Trust Used

**Location:** Backend MFA bypass logic in signin flow

**Requirements:**
1. When device trust cookie is present and valid
2. When MFA is successfully bypassed using the device trust
3. Update the device trust record in Redis with `lastUsedAt = current timestamp`

**Pseudocode:**
```typescript
async function bypassMfaWithDeviceTrust(userId: string, deviceTrustId: string) {
  // 1. Validate device trust exists and belongs to user
  const trust = await redis.getDeviceTrust(deviceTrustId);

  if (!trust || trust.userId !== userId) {
    return { allowed: false };
  }

  // 2. Check if expired
  if (new Date() > new Date(trust.expiresAt)) {
    return { allowed: false };
  }

  // 3. Update lastUsedAt
  trust.lastUsedAt = new Date().toISOString();
  await redis.updateDeviceTrust(deviceTrustId, trust);

  // 4. Allow signin without MFA
  return { allowed: true };
}
```

---

## Testing Strategy

### After Each Fix Phase

Run targeted tests to verify fixes:

**Phase 1 completion:**
```bash
npm run test -- --tags '@device-trust'
```
Expected: Scenarios 1-4, 7-8 pass (6 scenarios)

**Phase 2 completion:**
```bash
npm run test -- features/api/email-verification-api.feature:42
```
Expected: Scenario 9 passes

**Phase 3 completion:**
```bash
npm run test -- --tags '@session-token'
```
Expected: Scenarios 10-20 pass (11 scenarios)

**Phase 4 completion:**
```bash
npm run test -- features/api/device-trust-api.feature:142
npm run test -- features/api/device-trust-api.feature:168
```
Expected: Scenarios 5-6 pass (2 scenarios)

**Final verification:**
```bash
npm run test
```
Expected: All 249 scenarios pass, 0 failures

---

## Risk Assessment

### Low Risk (Phases 1-3)
- Pure test code fixes
- No backend changes required
- Can be done independently
- Easy to verify and rollback

### Medium Risk (Phase 4)
- Requires backend code changes
- Touches authentication and security logic
- Need to ensure device trust revocation is atomic
- Need to ensure lastUsedAt update doesn't fail silent

---

## Success Criteria

- [ ] All 20 failing scenarios pass
- [ ] No regression in currently passing scenarios (229 scenarios)
- [ ] Total test run shows: 249 scenarios (249 passed)
- [ ] No new test flakiness introduced
- [ ] Fix execution time: < 2 hours for phases 1-3, depends on backend for phase 4
