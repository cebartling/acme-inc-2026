# Device Trust and Password Change Integration

## Overview

When a user changes their password, all trusted devices MUST be revoked for security reasons. This ensures that if a password was compromised, the attacker cannot use previously trusted devices to bypass MFA.

## Current State

As of 2026-01-24, password change functionality is **NOT YET IMPLEMENTED** in the identity service.

## Required Integration

When password change is implemented, the following integration is required:

### ChangePasswordUseCase Requirements

The password change use case MUST:

1. **Revoke all device trusts** after successful password change:
   ```kotlin
   // After password update succeeds
   deviceTrustService.revokeAllDevices(
       userId = user.id,
       reason = DeviceRevocationReason.PASSWORD_CHANGED,
       correlationId = context.correlationId
   )
   ```

2. **Invalidate all active sessions** (if not already done):
   ```kotlin
   sessionService.invalidateAllUserSessions(
       userId = user.id,
       reason = "PASSWORD_CHANGED"
   )
   ```

3. **Publish events** for audit trail:
   - PasswordChanged event
   - DeviceRevoked events (published by DeviceTrustService)
   - SessionInvalidated events (published by SessionService)

### Security Rationale

**Why revoke device trusts on password change?**

- **Compromised Password**: If the old password was compromised, the attacker may have already completed MFA on their device. Revoking device trusts ensures they must complete MFA again with the new password.

- **Account Takeover Prevention**: Prevents an attacker who briefly gained access from maintaining persistent access through trusted devices.

- **User Control**: When a user changes their password (especially after suspicious activity), they expect all access points to be invalidated.

- **Compliance**: Many security frameworks require re-authentication after credential changes.

### User Experience

After password change:
1. User is logged out from all devices
2. User must sign in with new password
3. User must complete MFA on all devices (no device trust bypass)
4. User can choose to trust devices again after MFA

### Implementation Checklist

- [ ] Create ChangePasswordUseCase
- [ ] Add password change API endpoint
- [ ] Integrate deviceTrustService.revokeAllDevices()
- [ ] Add password change acceptance tests
- [ ] Update user documentation

## Related Files

- `DeviceTrustService.kt` - Contains `revokeAllDevices()` method
- `DeviceRevocationReason.kt` - Contains `PASSWORD_CHANGED` enum value
- User domain model - Will need `updatePassword()` method

## Testing Requirements

When implementing password change, ensure:

1. **Unit tests** verify device trust revocation is called
2. **Integration tests** verify all devices are actually removed from Redis
3. **Acceptance tests** verify user flow:
   - Change password
   - Sign out
   - Sign in with new password
   - MFA required (device trust bypassed)
   - Complete MFA
   - Can choose to trust device again
