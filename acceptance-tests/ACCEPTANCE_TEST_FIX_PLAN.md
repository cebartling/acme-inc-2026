# Acceptance Test Fix Plan

## Summary

2 acceptance test scenarios are failing in the Device Trust API:
- **Device Trust API**: 2 failures (backend business logic and authentication issues)

The root causes are:
1. **Business Logic - lastUsedAt**: Device trust lastUsedAt not updating when used for MFA bypass
2. **Authentication Flow**: 401 error when creating session after MFA verification with rememberDevice

---

## Remaining Issues

### Issue 1: Device Trust lastUsedAt Not Updated

**Scenario:** Update lastUsedAt when device trust is used for MFA bypass
**Location:** `features/api/device-trust-api.feature:168`

**Problem:**
When a device trust is used to bypass MFA, the `lastUsedAt` timestamp should be updated to the current time, but it remains at the original creation time.

**Expected Behavior:**
lastUsedAt should be within 5 seconds of current time when device trust is used.

**Actual Behavior:**
lastUsedAt remains at creation time (5 days ago = 432,000 seconds difference).

**Error:**
```
Expected: < 5
Received: 432000.084
```

**Fix Location:**
Backend signin/MFA flow where device trust cookie is verified

**Fix Requirements:**
The `DeviceTrustService.verifyTrust()` method (lines 119-158) already updates lastUsedAt using the `.copy()` pattern:
```kotlin
val updatedDeviceTrust = deviceTrust.copy(lastUsedAt = Instant.now())
deviceTrustRepository.save(updatedDeviceTrust)
```

However, the test is still failing after this fix was deployed (commit dbe52a6). This suggests:
1. The fix may not have been deployed correctly
2. The `verifyTrust()` method is not being called in the signin flow
3. There's a different code path being used for MFA bypass

**Investigation Needed:**
- Verify the signin flow calls `verifyTrust()` when device_trust cookie is present
- Check if MFA bypass is using a different code path
- Add logging to confirm `verifyTrust()` is being called during signin

---

### Issue 2: 401 Unauthorized After MFA Verification with rememberDevice

**Scenario:** Parse human-readable device name from user agent
**Location:** `features/api/device-trust-api.feature:187`

**Problem:**
After signing in, verifying MFA with `rememberDevice: true`, the next request to create an authenticated session returns 401 Unauthorized instead of 200.

**Test Flow:**
1. ✅ Sign in with email/password → status MFA_REQUIRED
2. ✅ Verify MFA with TOTP code and rememberDevice=true → status 200
3. ❌ Request to create authenticated session → 401 (expected 200)

**Error:**
```
Expected: 200
Received: 401
```

**Affected Steps:**
```gherkin
When I signin with email "devicename@acme.com" and password "ValidP@ss123!" with user agent "..."
And I verify MFA with the correct TOTP code and rememberDevice set to true
And I have an active session for "devicename@acme.com"  # <-- Fails here with 401
```

**Fix Location:**
Backend MFA verification endpoint when `rememberDevice: true`

**Investigation Needed:**
- Check if MFA verification with `rememberDevice: true` is returning Set-Cookie headers
- Verify access_token cookie is being set in the response
- Check if device_trust cookie is being created
- Review the "I have an active session" step to see what auth it's using

**Possible Causes:**
1. MFA verification response not including access_token cookie when rememberDevice=true
2. Cookie extraction in test step not handling the response correctly
3. Session creation logic has different behavior with rememberDevice flag

---

## Testing Strategy

### Verification Commands

**Issue 1 - lastUsedAt update:**
```bash
npm run test -- features/api/device-trust-api.feature:168
```

**Issue 2 - Auth after MFA with rememberDevice:**
```bash
npm run test -- features/api/device-trust-api.feature:187
```

**All device trust tests:**
```bash
npm run test -- --tags '@device-trust'
```
Expected: 18 scenarios (18 passed)

---

## Success Criteria

- [ ] All 2 failing scenarios pass
- [ ] No regression in currently passing scenarios (16 scenarios)
- [ ] Total device trust test run shows: 18 scenarios (18 passed)

---

## Fix History

### Completed Fixes

1. **Device Revocation 404 Error** (commit 08b5ebd)
   - Fixed `DeviceTrustService.revokeDevice()` to use `findById()` + ownership check
   - Spring Data Redis cannot auto-generate `findByIdAndUserId()` compound queries

2. **Missing isCurrent Field** (commit de76bdb)
   - Added `@field:JsonProperty("isCurrent")` annotation to DeviceTrustInfo DTO
   - Jackson was auto-stripping "is" prefix from boolean field names
   - Fixed 2 scenarios (lines 84, 208)

3. **Password Change Not Revoking Device Trusts** (commit 6a3ddd2)
   - Implemented POST /api/v1/auth/change-password endpoint
   - Implemented ChangePasswordUseCase with password verification and device trust revocation
   - Added User.updatePassword() method for immutable password updates
   - Created ChangePasswordRequest and ChangePasswordResponse DTOs
   - After successful password change, all device trusts are revoked using DeviceTrustService.revokeAllDevices()
   - Fixed 1 scenario (line 142)

**Current Status:** 16 of 18 scenarios passing (89% pass rate)
