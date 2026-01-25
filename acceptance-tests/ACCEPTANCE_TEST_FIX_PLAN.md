# Acceptance Test Fix Plan

## Summary

All acceptance test scenarios in the Device Trust API are now passing! ✅

**Final Status:** 18 of 18 scenarios passing (100% pass rate)

---

## Remaining Issues

None! All device trust acceptance tests are passing.

---

## Testing Strategy

### Verification Commands

**Issue 1 - Auth after MFA with rememberDevice:**
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

- [x] The 1 failing scenario passes
- [x] No regression in currently passing scenarios (17 scenarios)
- [x] Total device trust test run shows: 18 scenarios (18 passed)

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

4. **Device Trust lastUsedAt Not Updated** (commit c558b84)
   - Root cause: Two issues prevented lastUsedAt from updating
     1. Spring Data Redis not detecting changes when using .copy() on data classes
     2. Test step not storing userAgent, causing verification to fail due to mismatch
   - Fixed DeviceTrustService.verifyTrust() to create new DeviceTrust instance instead of using .copy()
   - Fixed test step to store deviceUserAgent when creating test device trust
   - Fixed 1 scenario (line 168)

5. **401 After MFA Verification with rememberDevice and Device Name Parsing**
   - Root cause: Two issues caused test failure
     1. Test step "I have an active session" was doing fresh signin instead of using existing access_token
     2. MFA verification step not sending User-Agent header, causing device trust to be created with "unknown" user agent
   - Fixed "I have an active session" step to reuse existing access_token from previous MFA verification
   - Fixed signin step to store userAgent in test data
   - Fixed MFA verification step to send User-Agent header from signin request
   - Device trust now created with correct user agent, enabling proper device name parsing
   - Fixed 1 scenario (line 187)

**Current Status:** 18 of 18 scenarios passing (100% pass rate) ✅
