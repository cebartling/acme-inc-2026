# ADR-0039: Device Trust Implementation for MFA Bypass

## Status

Accepted

## Context

### Problem Statement

Users with multi-factor authentication (MFA) enabled must complete MFA verification on every signin, even from devices they use regularly (e.g., personal laptop, work computer). This creates friction in the user experience, particularly for users who sign in frequently from the same trusted devices.

We need a mechanism to allow users to "remember" trusted devices and bypass MFA for a limited time period while maintaining strong security guarantees.

### Requirements

From US-0003-08:

- Allow users to trust devices and bypass MFA for 30 days
- Store device trust tokens securely with automatic expiration
- Validate device identity using fingerprint and user agent
- Enforce a maximum of 10 trusted devices per user
- Provide API endpoints for users to view and revoke trusted devices
- Revoke all device trusts when user changes password
- Publish audit events for all device trust lifecycle operations
- Use secure HttpOnly cookies to prevent XSS attacks

### Constraints

- Must integrate with existing MFA flow (TOTP and SMS)
- Must work with existing Redis infrastructure
- Must follow existing session management patterns
- Must not weaken overall authentication security
- Must provide complete audit trail for compliance

## Decision

### 1. Token Storage Strategy: UUID Tokens in Redis (Not JWTs)

**Decision**: Use simple UUID tokens stored in Redis with `@TimeToLive` annotation for automatic expiration.

**Rationale**:
- **Consistency**: Follows existing Session pattern used in codebase
- **Revocation**: Easy to revoke individual tokens by deleting Redis keys
- **Performance**: No cryptographic overhead for token validation
- **Simplicity**: Token ID serves as Redis key directly (`device_trusts:{uuid}`)
- **Auto-cleanup**: Redis TTL handles expiration automatically

**Rejected Alternative**: JWTs
- Harder to revoke (requires blacklist)
- Added crypto overhead on every request
- More complex to implement device limit enforcement

**Implementation**:
```kotlin
@RedisHash("device_trusts")
data class DeviceTrust(
    @Id val id: String,
    @Indexed val userId: UUID,
    val deviceFingerprint: String,
    val userAgent: String,
    val ipAddress: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    var lastUsedAt: Instant,
    @TimeToLive(unit = TimeUnit.SECONDS) val ttl: Long
)
```

### 2. Device Identification: Fingerprint + User Agent (Not IP)

**Decision**: Validate BOTH deviceFingerprint AND userAgent. Log IP address but do NOT enforce it.

**Rationale**:

**Fingerprint Validation** (enforced):
- Browser-generated unique identifier
- Relatively stable across sessions
- Difficult to spoof without significant effort
- Changes indicate different device/browser

**User Agent Validation** (enforced):
- Captures browser and OS information
- Changes when browser is updated → forces re-authentication
- This is a security feature: browser updates may include security patches
- Ensures users re-authenticate after major software changes

**IP Address** (logged but not enforced):
- Mobile networks change IPs frequently (cell tower handoffs)
- VPN usage changes IPs legitimately
- Corporate networks use NAT with IP rotation
- Enforcing IP would break device trust for legitimate users
- Still logged for security monitoring and forensics

**Fingerprint Hashing**:
- Device fingerprints are hashed using SHA-256 before storage
- Prevents rainbow table attacks if Redis is compromised
- One-way hashing means original fingerprint cannot be recovered

**Implementation**:
```kotlin
fun matches(deviceFingerprint: String, userAgent: String): Boolean {
    val hashedFingerprint = hashFingerprint(deviceFingerprint)
    return this.deviceFingerprint == hashedFingerprint &&
           this.userAgent == userAgent
}

private fun hashFingerprint(fingerprint: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(fingerprint.toByteArray())
    return hash.joinToString("") { "%02x".format(it) }
}
```

### 3. MFA Bypass Location: Before MFA Challenge Creation

**Decision**: Check device trust in `AuthenticateUserUseCase` at line 367, BEFORE creating MFA challenge.

**Rationale**:
- **Early exit**: Prevents unnecessary MFA challenge creation
- **Cleaner flow**: Avoids creating state that will never be used
- **Consistent**: Device trust is credential-level validation, not MFA-level
- **Performance**: Skips MFA challenge creation overhead

**Implementation Location**:
```kotlin
// In AuthenticateUserUseCase.kt, line 367
if (user.mfaEnabled) {
    // Check for device trust BEFORE creating MFA challenge
    if (request.deviceTrustToken != null && request.deviceFingerprint != null) {
        val trustedDevice = deviceTrustService.verifyTrust(...)
        if (trustedDevice != null) {
            // Device trust verified - bypass MFA
            logger.info("Device trust verified for user {}, bypassing MFA", user.id)
            return@either SigninResponse.success(userId = user.id)
        }
    }
    // Device trust not verified - require MFA
    when { ... }
}
```

### 4. Device Limit Enforcement: Maximum 10 Devices, FIFO Eviction

**Decision**: Enforce a maximum of 10 trusted devices per user. When limit is exceeded, evict the oldest device (by `createdAt`).

**Rationale**:
- **Security**: Prevents unbounded token accumulation
- **Balance**: 10 devices accommodates: personal phone, work phone, personal laptop, work laptop, tablet, home desktop, plus 4 shared devices
- **Consistency**: Similar to existing session limit (max 5 sessions per user)
- **FIFO Strategy**: Oldest devices are least likely to be in active use
- **User Control**: Users can manually revoke devices if needed

**Known Race Condition** (documented in code):
- Two concurrent signins could both see 9 devices and create 10th
- Result: user has 11 devices temporarily
- Mitigation: Acceptable for MVP, will be cleaned up on next signin
- Alternative solution (Redis WATCH) adds significant complexity

**Implementation**:
```kotlin
private fun evictOldestDeviceIfNeeded(userId: UUID, correlationId: UUID) {
    val deviceCount = deviceTrustRepository.countByUserId(userId)
    if (deviceCount >= config.maxDevicesPerUser) {
        val devices = deviceTrustRepository.findByUserId(userId)
        val oldestDevice = devices.minByOrNull { it.createdAt }
        if (oldestDevice != null) {
            deviceTrustRepository.delete(oldestDevice)
            publishDeviceRevokedEvent(oldestDevice, DeviceRevocationReason.LIMIT_EXCEEDED, correlationId)
        }
    }
}
```

### 5. Expiration Period: 30 Days

**Decision**: Device trust expires after 30 days from creation.

**Rationale**:
- **Security**: Limits exposure window for compromised tokens
- **Balance**: Long enough to provide convenience, short enough to enforce periodic re-authentication
- **Industry Standard**: Common practice (GitHub, Google, Microsoft use 30-90 days)
- **Compliance**: Meets reasonable security audit requirements

**Auto-cleanup**: Redis TTL automatically deletes expired tokens, no manual cleanup needed.

### 6. Cookie Security: HttpOnly, Secure, SameSite=Strict

**Decision**: Deliver device trust tokens via HttpOnly cookies with strict security attributes.

**Rationale**:
- **HttpOnly**: Prevents JavaScript access, protects against XSS attacks
- **Secure**: Cookie only sent over HTTPS, prevents MITM attacks
- **SameSite=Strict**: Prevents CSRF attacks, cookie not sent on cross-site requests
- **30-day Max-Age**: Matches token TTL, browser enforces expiration

**Implementation**:
```kotlin
fun buildDeviceTrustCookie(token: String): ResponseCookie {
    return ResponseCookie.from("device_trust", token)
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .path("/")
        .maxAge(2592000L) // 30 days
        .build()
}
```

### 7. Event-Driven Audit Trail

**Decision**: Publish domain events for all device trust lifecycle operations.

**Events**:
1. **DeviceRemembered**: Published when device trust is created
   - Payload: userId, deviceTrustId, deviceFingerprint (hashed), userAgent, ipAddress, trustedUntil
   - Use case: Audit trail, security monitoring, compliance reporting

2. **DeviceRevoked**: Published when device trust is revoked
   - Payload: userId, deviceTrustId, reason, revokedAt
   - Reasons: USER_REVOKED, USER_REVOKED_ALL, EXPIRED, PASSWORD_CHANGED, LIMIT_EXCEEDED, ADMIN_REVOKED
   - Use case: Audit trail, security alerts, forensics

**Kafka Topic**: `identity.device.events` (same as other identity events)

**Event Store**: All events persisted to PostgreSQL event_store table

**Rationale**:
- **Audit**: Complete history of device trust lifecycle for compliance
- **Monitoring**: Security team can detect suspicious patterns
- **Forensics**: Investigate security incidents
- **Integration**: Other services can react to device trust events

### 8. Password Change Integration

**Decision**: Revoke all device trusts when user changes password.

**Current Status**: Password change functionality not yet implemented in codebase.

**Future Implementation**:
```kotlin
// In ChangePasswordUseCase (to be implemented)
fun execute(request: ChangePasswordRequest): Either<ChangePasswordError, ChangePasswordResponse> {
    // 1. Validate current password
    // 2. Hash new password
    // 3. Update user password
    // 4. Revoke all device trusts
    deviceTrustService.revokeAllDevices(
        userId = userId,
        reason = DeviceRevocationReason.PASSWORD_CHANGED,
        correlationId = correlationId
    )
    // 5. Invalidate all sessions
    // 6. Publish PasswordChanged event
}
```

**Rationale**:
- Password change indicates potential compromise
- Forces re-authentication on all devices
- Standard security practice (used by banks, email providers)

### 9. Device Management API

**Decision**: Provide REST endpoints for users to manage trusted devices.

**Endpoints**:
```
GET    /api/v1/auth/devices           - List all trusted devices
DELETE /api/v1/auth/devices/{id}      - Revoke single device
DELETE /api/v1/auth/devices           - Revoke all devices
```

**Authentication**: JWT from `access_token` cookie (existing auth mechanism)

**Authorization**: Users can only manage their own devices (userId extracted from JWT)

**Response Format**:
```json
{
  "devices": [
    {
      "id": "trust_abc123",
      "deviceName": "Chrome on macOS",
      "createdAt": "2026-01-15T10:30:00Z",
      "lastUsedAt": "2026-01-24T08:15:00Z",
      "expiresAt": "2026-02-14T10:30:00Z",
      "ipAddress": "192.168.1.100",
      "isCurrent": true
    }
  ]
}
```

**Device Name Parsing**: Extract human-readable name from User-Agent string (e.g., "Chrome on macOS", "Firefox on Windows", "Safari on iOS")

## Consequences

### Positive

1. **Improved UX**: Users with MFA no longer need to verify every signin from trusted devices
2. **Security Maintained**: Fingerprint + user agent validation prevents token theft
3. **Audit Trail**: Complete event history for compliance and security monitoring
4. **User Control**: Users can view and revoke devices via API and UI
5. **Auto-cleanup**: Redis TTL handles expiration automatically, no manual cleanup needed
6. **Consistent Patterns**: Follows existing session management patterns in codebase
7. **XSS Protection**: HttpOnly cookies prevent JavaScript access to tokens
8. **Simple Revocation**: Delete Redis key to revoke device trust
9. **Browser Update Security**: User agent changes force re-authentication after browser updates
10. **Mobile-Friendly**: IP address not enforced allows mobile network usage

### Negative

1. **Browser Update Friction**: Users must re-authenticate after browser updates (user agent changes)
2. **Device Limit**: 10-device limit may be too restrictive for power users
3. **Race Condition**: Concurrent signins could create 11 devices temporarily (acceptable for MVP)
4. **No Cross-Browser Trust**: Same device with different browsers requires separate trust tokens
5. **Fingerprint Stability**: Browser fingerprints may change with browser settings or extensions

### Mitigations

1. **Browser Update Friction**:
   - This is intentional: browser updates may include security patches
   - Re-authentication ensures users benefit from latest security features
   - Communicated in UI: "Remember this device for 30 days"

2. **Device Limit**:
   - 10 devices should accommodate most users (personal + work devices + family devices)
   - Users can manually revoke unused devices to free slots
   - Configurable via `identity.device-trust.max-devices-per-user` property
   - Can be increased if usage data shows need

3. **Race Condition**:
   - Acceptable for MVP: user has 11 devices briefly, cleaned up on next signin
   - Future enhancement: Use Redis WATCH for atomic limit enforcement
   - Monitoring: Log LIMIT_EXCEEDED events to track frequency

4. **Cross-Browser Trust**:
   - Intentional: different browsers have different security profiles
   - User can trust multiple browsers on same device (counts against 10-device limit)
   - Alternative (not chosen): Browser-agnostic fingerprinting would be less secure

5. **Fingerprint Stability**:
   - Frontend should use stable fingerprinting library (e.g., FingerprintJS)
   - Hash fingerprints before storage to prevent rainbow table attacks
   - Fall back to MFA if fingerprint mismatch (graceful degradation)

## Related Decisions

- [ADR-0030: MFA TOTP Implementation](0030-mfa-totp-implementation.md) - Device trust integrates with MFA flow
- [ADR-0033: MFA SMS Implementation](0033-mfa-sms-implementation.md) - Device trust applies to both TOTP and SMS
- [ADR-0026: Session Management](0026-session-management.md) - Similar Redis-based token pattern
- [ADR-0025: Event Sourcing](0025-event-sourcing.md) - Device trust events follow event sourcing pattern

## References

- User Story: US-0003-08 (Remember Device for MFA Bypass)
- Implementation Plan: `/Users/chris/.claude/plans/parallel-snuggling-hartmanis.md`
- Acceptance Tests: `acceptance-tests/features/api/device-trust-api.feature`
- Test Documentation: `acceptance-tests/docs/device-trust-testing.md`
- Password Integration: `documentation/device-trust-password-change-integration.md`
- OWASP: [Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
- NIST: [Digital Identity Guidelines - Authentication](https://pages.nist.gov/800-63-3/sp800-63b.html)
