# ADR-0031: Credential Validation Security Patterns

## Status

Accepted

## Context

The platform needs to implement user authentication (signin) for the customer portal. This involves several security-critical decisions:

1. **Password Verification**: How to securely verify passwords against stored hashes
2. **User Enumeration Prevention**: How to prevent attackers from discovering valid email addresses
3. **Brute Force Protection**: How to protect against automated credential stuffing attacks
4. **Account Status Validation**: How to handle users in various account states
5. **Event Publishing**: What events to publish for security monitoring and audit trails
6. **Error Messages**: How to communicate failures without leaking security information

The authentication flow must be secure against:

- Timing attacks that reveal user existence
- Brute force password guessing
- User enumeration attacks
- Account lockout denial-of-service
- Session hijacking (future concern)

## Decision

### Password Verification with Argon2id

We will use **Argon2id** for password hashing and verification with the following parameters:

| Parameter   | Value  | Rationale                              |
|-------------|--------|----------------------------------------|
| Memory Cost | 64 MB  | OWASP recommended minimum              |
| Time Cost   | 3      | Balance between security and UX        |
| Parallelism | 4      | Matches typical server CPU cores       |
| Hash Length | 32     | Standard output length                 |

The existing `PasswordHasher` component already implements this with the `password4j` library.

### Timing Attack Prevention

**Problem**: If we return immediately when a user doesn't exist, attackers can measure response times to enumerate valid email addresses.

**Solution**: Perform a dummy password verification against a pre-computed hash when the user doesn't exist:

```kotlin
if (user == null) {
    passwordHasher.verify("dummy_password", dummyPasswordHash)
    publishFailedEvent(...)
    raise(AuthenticationError.InvalidCredentials(remainingAttempts = 0))
}
```

This ensures response times are consistent (within 50ms variance) regardless of whether the email exists.

### Failed Attempts Tracking

We track failed signin attempts per user with automatic reset on success:

```kotlin
// On failed password verification
user.incrementFailedAttempts()
userRepository.save(user)

// On successful authentication
user.resetFailedAttempts()
user.updateLastLogin(deviceFingerprint)
userRepository.save(user)
```

The remaining attempts are communicated in error responses to help legitimate users understand their situation.

### Account Status Validation

Authentication checks account status AFTER password verification to prevent user enumeration:

| Status               | HTTP Status | Error Code        | Behavior                    |
|----------------------|-------------|-------------------|-----------------------------|
| ACTIVE               | 200         | -                 | Allow signin                |
| PENDING_VERIFICATION | 403         | ACCOUNT_INACTIVE  | Prompt email verification   |
| SUSPENDED            | 403         | ACCOUNT_INACTIVE  | Direct to support           |
| DEACTIVATED          | 403         | ACCOUNT_INACTIVE  | Offer reactivation          |
| LOCKED               | 423         | ACCOUNT_LOCKED    | Show lockout duration       |

### Generic Error Messages

For security, we use the same error message for both non-existent users and wrong passwords:

```json
{
  "error": "INVALID_CREDENTIALS",
  "message": "Invalid email or password",
  "remainingAttempts": 4
}
```

This prevents attackers from determining whether an email is registered.

### Event-Driven Security Monitoring

We publish domain events for both successful and failed authentication attempts:

**AuthenticationFailed Event**:
```kotlin
data class AuthenticationFailedPayload(
    val email: String,
    val reason: AuthenticationFailureReason,
    val ipAddress: String,
    val userAgent: String,
    val failedAttemptCount: Int,
    val deviceFingerprint: String?
)
```

**AuthenticationSucceeded Event**:
```kotlin
data class AuthenticationSucceededPayload(
    val userId: UUID,
    val email: String,
    val ipAddress: String,
    val userAgent: String,
    val mfaRequired: Boolean,
    val deviceFingerprint: String?
)
```

Events are persisted to the event store BEFORE returning to the client, ensuring durability even if Kafka publishing fails.

### Rate Limiting Strategy

Rate limiting is applied per IP address + email combination to prevent:
- Credential stuffing attacks targeting multiple accounts
- Brute force attacks on a single account from distributed IPs

```kotlin
val rateLimitKey = "$clientIp:${request.email.lowercase()}"
if (!rateLimiter.tryAcquire(rateLimitKey)) {
    return 429 Too Many Requests
}
```

### Arrow Either for Type-Safe Error Handling

Authentication errors use Arrow's `Either<AuthenticationError, SigninResponse>` pattern:

```kotlin
sealed interface AuthenticationError {
    data class InvalidCredentials(val remainingAttempts: Int) : AuthenticationError
    data class AccountInactive(val status: UserStatus, val reason: String) : AuthenticationError
    data class AccountLocked(val lockedUntil: String) : AuthenticationError
    data object RateLimited : AuthenticationError
}
```

This ensures all error cases are explicitly handled in the controller layer.

## Consequences

### Positive

- **Timing Attack Resistant**: Consistent response times prevent email enumeration
- **Brute Force Resistant**: Failed attempt tracking and rate limiting slow attackers
- **Audit Trail**: All authentication attempts are recorded for security analysis
- **Type-Safe Errors**: Compiler ensures all authentication outcomes are handled
- **Secure Password Storage**: Argon2id with OWASP-recommended parameters
- **Defense in Depth**: Multiple layers of protection work together

### Negative

- **Performance Overhead**: Dummy password checks add ~300ms latency for non-existent users
- **Complexity**: Multiple error types and event types to maintain
- **Database Writes**: Every failed attempt requires a database update

### Mitigations

- Password verification is inherently slow; the overhead is acceptable
- Error types are well-documented and tested
- Database writes are small and indexed efficiently

## Related Decisions

- ADR-0019: Persistent Rate Limiting
- ADR-0020: Sealed Classes for Use Case Results
- ADR-0021: Information Disclosure Prevention
- ADR-0029: Arrow Functional Programming

## References

- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [US-0003-02: Credential Validation User Story](../user-stories/0003-customer-signin/US-0003-02-credential-validation.md)
