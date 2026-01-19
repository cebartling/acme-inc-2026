# ADR-0032: Account Lockout Mechanism

## Status

Accepted

## Context

To protect against brute-force credential attacks, we need to implement account lockout functionality that temporarily blocks signin attempts after repeated failures. Key design decisions include:

1. **Lockout Threshold**: When to trigger account lockout
2. **Lockout Duration**: How long accounts remain locked
3. **Lockout Recovery**: How users can regain access
4. **Event Publishing**: What events to publish for security monitoring
5. **User Experience**: How to communicate lockout status to users
6. **Lockout Bypass**: When lockout should be bypassed

The mechanism must balance security against usability—preventing attackers from guessing passwords while not overly frustrating legitimate users who mistype their passwords.

## Decision

### Lockout Threshold: 5 Consecutive Failed Attempts

After **5 consecutive failed signin attempts**, the account is locked:

```kotlin
if (user.failedAttempts >= maxFailedAttempts) {
    val lockoutDuration = Duration.ofMinutes(lockoutDurationMinutes)
    user.lock(lockoutDuration)
    userRepository.save(user)
    publishLockEvent(user, AccountLockReason.EXCESSIVE_FAILED_ATTEMPTS, ...)
}
```

This threshold provides:
- 5 attempts for legitimate users who mistype passwords
- Significant slowdown for brute force attacks (5 attempts per 15 minutes)
- Alignment with OWASP recommendations

### Lockout Duration: 15 Minutes

Accounts are locked for **15 minutes** by default (configurable via `identity.lockout.lockout-duration-minutes`):

```kotlin
@Value("\${identity.lockout.lockout-duration-minutes:15}")
private val lockoutDurationMinutes: Long = 15
```

Rationale:
- Long enough to significantly slow brute force attacks
- Short enough that legitimate users can wait and retry
- Industry-standard duration (many systems use 15-30 minutes)

### Automatic Lockout Expiration

Lockouts expire automatically when checked during signin:

```kotlin
if (user.isLocked()) {
    val lockedUntil = user.lockedUntil
    if (lockedUntil != null && lockedUntil.isBefore(Instant.now())) {
        // Lockout has expired - unlock the account
        user.unlock()
        userRepository.save(user)
        publishUnlockEvent(user, AccountUnlockReason.LOCKOUT_EXPIRED, ...)
    }
}
```

This approach:
- Avoids scheduled jobs for lockout expiration
- Ensures accurate lockout duration
- Publishes audit events for lockout lifecycle

### HTTP 423 Locked Response

When an account is locked, the API returns HTTP 423 (Locked) with detailed information:

```json
{
  "error": "ACCOUNT_LOCKED",
  "message": "Account is locked due to too many failed signin attempts",
  "lockedUntil": "2026-01-18T19:30:00Z",
  "lockoutRemainingSeconds": 895,
  "passwordResetUrl": "https://www.acme.com/forgot-password",
  "supportUrl": "https://www.acme.com/support"
}
```

The response includes:
- `lockoutRemainingSeconds`: For client-side countdown timer
- `passwordResetUrl`: Bypass option for users who forgot their password
- `supportUrl`: Help for users who believe they're locked incorrectly

### Password Reset Bypasses Lockout

Users can bypass lockout by resetting their password. This will:
1. Generate a password reset token
2. Allow setting a new password
3. Unlock the account (future US-0003-05 implementation)
4. Publish `AccountUnlocked` event with reason `PASSWORD_RESET`

### Domain Events for Security Monitoring

**AccountLocked Event** (published when lockout triggers):

```kotlin
data class AccountLockedPayload(
    val userId: UUID,
    val email: String,
    val reason: AccountLockReason,
    val failedAttemptCount: Int,
    val lockedUntil: Instant,
    val ipAddress: String,
    val userAgent: String
)
```

**AccountUnlocked Event** (published when lockout expires or is manually cleared):

```kotlin
data class AccountUnlockedPayload(
    val userId: UUID,
    val email: String,
    val reason: AccountUnlockReason,
    val unlockedAt: Instant,
    val previousLockReason: AccountLockReason?
)
```

Events are published to `identity.account.events` Kafka topic for:
- Security monitoring and alerting
- Audit trail compliance
- Notification Service to send lockout emails

### Frontend Lockout Display

The signin page displays a lockout banner with:
- Clear explanation of why the account is locked
- Live countdown timer showing remaining lockout time
- Link to password reset for immediate access recovery
- Disabled form fields to prevent further attempts

```tsx
{lockout?.isLocked && (
  <div data-testid="lockout-message">
    <h3>Account Locked</h3>
    <p>Your account has been temporarily locked due to too many failed signin attempts.</p>
    <p>Please try again in {formatRemainingTime(lockout.remainingSeconds)}</p>
    <a href={lockout.passwordResetUrl}>Reset your password</a> to unlock immediately.
  </div>
)}
```

### Failed Attempts Reset

Failed attempts are reset in two scenarios:

1. **Successful signin before lockout**: User provides correct password
2. **Lockout expiration**: Counter implicitly resets via `user.unlock()`

```kotlin
// On successful authentication
user.resetFailedAttempts()
user.updateLastLogin(deviceFingerprint)
userRepository.save(user)
```

## Consequences

### Positive

- **Brute Force Protection**: 5 attempts per 15 minutes limits credential guessing to ~20 attempts/hour
- **Clear User Communication**: Countdown timer and reset link provide actionable information
- **Audit Compliance**: All lockout events are recorded for security analysis
- **Automatic Recovery**: No manual intervention needed for lockout expiration
- **Configurable**: Threshold and duration can be adjusted without code changes
- **Self-Service Bypass**: Password reset provides immediate recovery path

### Negative

- **Denial of Service Risk**: Attackers could intentionally lock legitimate users' accounts
- **Complexity**: Multiple event types and frontend state management
- **Database Writes**: Every signin attempt updates the database

### Mitigations

- **DoS Mitigation**: Rate limiting (ADR-0019) prevents rapid lockout triggering; password reset bypass allows legitimate users to recover
- **Complexity**: Well-defined event types and clear frontend state machine
- **Database Efficiency**: Indexed queries, minimal data changes

## Related Decisions

- ADR-0019: Persistent Rate Limiting (rate limiting works alongside lockout)
- ADR-0031: Credential Validation Security (lockout integrates with authentication flow)
- ADR-0029: Arrow Functional Programming (error handling pattern)

## References

- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [NIST SP 800-63B Digital Identity Guidelines](https://pages.nist.gov/800-63-3/sp800-63b.html)
- [US-0003-04: Account Lockout User Story](../user-stories/0003-customer-signin/US-0003-04-account-lockout.md)
