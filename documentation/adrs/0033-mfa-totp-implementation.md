# ADR-0033: MFA TOTP Implementation

## Status

Accepted

## Context

The platform requires Multi-Factor Authentication (MFA) using Time-based One-Time Passwords (TOTP) as specified in US-0003-05. This involves several technical decisions:

1. **TOTP Algorithm Configuration**: Which parameters to use for code generation
2. **Challenge Management**: How to track and expire MFA challenges
3. **Replay Attack Prevention**: How to prevent reuse of valid TOTP codes
4. **Attempt Limiting**: How to limit verification attempts per challenge
5. **Library Selection**: Which TOTP library to use in the backend
6. **Frontend State Management**: How to pass MFA state between signin and verification pages

The MFA flow must protect against:

- TOTP code replay attacks (reusing a valid code)
- Brute force attacks on the 6-digit code space
- Challenge token theft and reuse
- Timing-based attacks during verification

## Decision

### TOTP Algorithm Configuration

We use RFC 6238 TOTP with the following parameters:

| Parameter      | Value   | Rationale                                          |
|----------------|---------|---------------------------------------------------|
| Algorithm      | SHA-1   | Standard for TOTP, widely supported by authenticators |
| Digit Count    | 6       | Standard length, balances security and usability  |
| Time Step      | 30s     | Industry standard, matches most authenticator apps |
| Time Tolerance | ±1 step | Allows for clock skew while limiting window       |

SHA-1 is used despite SHA-256 being available because:
- RFC 6238 specifies SHA-1 as the default
- All major authenticator apps (Google, Microsoft, Authy) default to SHA-1
- The 6-digit output doesn't benefit from SHA-256's larger hash size

### Library Selection

We use **dev.samstevens.totp** version 1.7.1 for TOTP operations:

```kotlin
implementation("dev.samstevens.totp:totp:1.7.1")
```

This library was chosen because:
- Pure Java implementation (works with Kotlin)
- Implements RFC 6238 and RFC 4226 correctly
- Provides secret generation, code generation, and verification
- Configurable algorithm, digits, and time step
- No additional dependencies

### MFA Challenge Entity

Challenges are stored in the database with the following structure:

```kotlin
@Entity
@Table(name = "mfa_challenges")
class MfaChallenge(
    @Id val id: UUID,
    val userId: UUID,
    val token: String,          // Secure random token for API reference
    val method: MfaMethod,      // TOTP (future: SMS, EMAIL)
    val expiresAt: Instant,     // Default: 5 minutes from creation
    var attempts: Int = 0,      // Tracks failed attempts
    val maxAttempts: Int = 3,   // Maximum allowed attempts
    val createdAt: Instant
)
```

Challenges expire after 5 minutes or 3 failed attempts, whichever comes first.

### Replay Attack Prevention

To prevent code reuse, we track used TOTP codes:

```kotlin
@Entity
@Table(name = "used_totp_codes")
class UsedTotpCode(
    @Id val id: UUID,
    val userId: UUID,
    val codeHash: String,       // SHA-256 hash of the code
    val timeStep: Long,         // TOTP time step when code was used
    val usedAt: Instant
)
```

Key design decisions:
- **Hash storage**: Codes are hashed with SHA-256 before storage (not plain text)
- **Time step tracking**: Codes are only blocked within the same TOTP time step
- **User scoping**: Prevents replay only for the same user
- **Automatic cleanup**: Old entries can be purged after 24 hours

The verification flow checks:
```kotlin
if (usedTotpCodeRepository.existsByUserIdAndCodeHashAndTimeStep(userId, codeHash, timeStep)) {
    return Either.Left(MfaVerificationError.CodeAlreadyUsed(remainingAttempts))
}
```

### Attempt Limiting

MFA challenges track attempt counts:

```kotlin
challenge.incrementAttempts()
if (challenge.hasExceededMaxAttempts()) {
    // Delete challenge, return MFA_EXPIRED error
}
```

The remaining attempts are communicated to users to help legitimate users understand their situation.

### Error Handling with Arrow Either

MFA verification uses Arrow's Either pattern for type-safe error handling:

```kotlin
sealed interface MfaVerificationError {
    data object InvalidToken : MfaVerificationError
    data object Expired : MfaVerificationError
    data class InvalidCode(val remainingAttempts: Int) : MfaVerificationError
    data class CodeAlreadyUsed(val remainingAttempts: Int) : MfaVerificationError
}
```

This ensures all error cases are explicitly handled at compile time.

### Domain Events

Three MFA-specific domain events are published:

**MFAChallengeInitiated**: Published when MFA is required after credential validation
```kotlin
data class MFAChallengeInitiatedPayload(
    val challengeId: UUID,
    val userId: UUID,
    val method: String,
    val expiresAt: String,
    val ipAddress: String,
    val userAgent: String
)
```

**MFAVerificationSucceeded**: Published on successful TOTP verification
```kotlin
data class MFAVerificationSucceededPayload(
    val challengeId: UUID,
    val userId: UUID,
    val method: String,
    val deviceTrusted: Boolean,
    val ipAddress: String,
    val userAgent: String
)
```

**MFAVerificationFailed**: Published on failed TOTP verification
```kotlin
data class MFAVerificationFailedPayload(
    val challengeId: UUID,
    val userId: UUID,
    val reason: MFAVerificationFailureReason,
    val remainingAttempts: Int,
    val ipAddress: String,
    val userAgent: String
)
```

### Frontend State Management

MFA state is passed between signin and verification pages using sessionStorage:

```typescript
// In signin page after MFA_REQUIRED response
sessionStorage.setItem("mfaState", JSON.stringify({
    mfaToken: response.mfaToken,
    email: data.email,
    redirect: search.redirect,
}));
navigate({ to: "/mfa-verify" });

// In MFA verify page
const storedState = sessionStorage.getItem("mfaState");
```

SessionStorage was chosen over:
- **URL parameters**: Exposes token in browser history and logs
- **React state/context**: Lost on page refresh
- **LocalStorage**: Persists too long for security-sensitive data

### OTP Input Component

The frontend uses a 6-digit OTP input with:
- Auto-advance between digits
- Paste support for copying from authenticator apps
- Numeric-only validation
- Backspace navigation to previous digit
- Visual feedback for remaining attempts

## Consequences

### Positive

- **Replay Attack Resistant**: Single-use codes prevent token reuse
- **Brute Force Resistant**: 3-attempt limit with 5-minute expiry
- **Standards Compliant**: RFC 6238 TOTP with standard parameters
- **Authenticator Compatible**: Works with Google Authenticator, Microsoft Authenticator, Authy, etc.
- **Type-Safe Errors**: Compiler ensures all verification outcomes are handled
- **Audit Trail**: All MFA events are recorded for security analysis

### Negative

- **Database Writes**: Each verification attempt requires database updates
- **Clock Sensitivity**: Requires user devices to have reasonably accurate time
- **Code Complexity**: Multiple entities (challenge, used codes) to manage
- **Storage Growth**: Used codes table grows over time (mitigated by cleanup)

### Mitigations

- Used codes are automatically cleaned up after 24 hours
- Database indexes on token and userId ensure fast lookups
- ±1 time step tolerance handles minor clock skew
- Clear error messages guide users through clock issues

## Related Decisions

- ADR-0020: Sealed Classes for Use Case Results
- ADR-0029: Arrow Functional Programming
- ADR-0031: Credential Validation Security Patterns
- ADR-0032: Account Lockout Mechanism

## References

- [RFC 6238: TOTP Algorithm](https://datatracker.ietf.org/doc/html/rfc6238)
- [RFC 4226: HOTP Algorithm](https://datatracker.ietf.org/doc/html/rfc4226)
- [OWASP MFA Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html)
- [US-0003-05: MFA TOTP User Story](../user-stories/0003-customer-signin/US-0003-05-mfa-totp.md)
- [dev.samstevens.totp Library](https://github.com/samdjstevens/java-totp)
