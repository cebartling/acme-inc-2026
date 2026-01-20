# ADR-0002: SMS MFA Implementation Design

## Status

Accepted

## Context

As part of user story US-0003-06, we need to implement SMS-based multi-factor authentication as an alternative to TOTP. This provides users who don't want to use an authenticator app with a secure second factor for authentication.

Key design decisions needed to be made regarding:
- SMS provider selection and integration
- Code generation and storage strategy
- Rate limiting approach
- Resend cooldown mechanism
- Frontend UX for method switching

## Decision

### SMS Provider Strategy: Pluggable Interface

We implemented a **pluggable SMS provider interface** (`SmsProvider`) with two implementations:

| Implementation | Purpose | Configuration |
|---------------|---------|---------------|
| `TwilioSmsProvider` | Production SMS delivery | `identity.sms.provider=twilio` |
| `MockSmsProvider` | Testing and development | `identity.sms.provider=mock` |

**Rationale:** This allows easy testing without sending real SMS, and provides flexibility to switch providers (e.g., to AWS SNS) in the future.

### Code Storage: SHA-256 Hash

SMS verification codes are **hashed with SHA-256** before storage, similar to how passwords would be handled:

```kotlin
private fun hashCode(code: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(code.toByteArray())
    return hashBytes.joinToString("") { "%02x".format(it) }
}
```

**Rationale:** Even though SMS codes are short-lived (5 minutes), hashing prevents exposure if the database is compromised during an active MFA challenge.

### Rate Limiting: Database-Backed Sliding Window

We implemented **database-backed rate limiting** with a sliding window approach:

| Limit Type | Threshold | Duration |
|------------|-----------|----------|
| Hourly SMS limit | 3 SMS | 1 hour sliding window |
| Resend cooldown | 1 resend | 60 seconds |

**Implementation:**
- `SmsRateLimit` entity tracks each SMS sent (userId, phoneNumber, sentAt)
- `SmsRateLimiter` service queries the database for rate limit checks
- Records older than 1 hour are cleaned up by a scheduled task

**Rationale:** Database-backed rate limiting was chosen over Redis because:
- Consistent with existing infrastructure (no new dependencies)
- Provides audit trail of SMS delivery attempts
- Simpler deployment and operations

### MFA Challenge Reuse

Rather than creating a separate entity for SMS challenges, we **extended the existing `MfaChallenge` entity** with:

```kotlin
@Column(name = "code_hash", length = 64)
var codeHash: String? = null

@Column(name = "last_sent_at")
var lastSentAt: Instant? = null
```

**Rationale:** This maintains consistency with TOTP challenge handling while adding SMS-specific fields. The challenge lifecycle (creation, verification, expiration, max attempts) is identical across both methods.

### Method Priority in Authentication

When a user has both TOTP and SMS MFA enabled, **TOTP takes priority**:

```kotlin
when {
    user.totpEnabled -> // Create TOTP challenge
    user.smsMfaEnabled -> // Create SMS challenge
    else -> // No MFA configured
}
```

**Rationale:** TOTP is more secure (not susceptible to SIM swapping) and doesn't incur SMS costs. Users can still manually switch to SMS on the frontend if needed.

### Frontend Method Switching

The MFA verification page shows a **method switcher** when both methods are available:

- Buttons to switch between "Authenticator" and "SMS"
- Separate UI states for each method
- SMS includes resend button with cooldown timer

**Rationale:** Provides flexibility for users who lose access to their authenticator app temporarily.

## Alternatives Considered

### Alternative 1: Redis-Based Rate Limiting

Use Redis for rate limiting with TTL-based key expiration.

**Rejected because:**
- Adds operational complexity (new service to manage)
- Database approach provides better audit trail
- Rate limiting load is minimal (only on signin)

### Alternative 2: Separate SMS Challenge Entity

Create a dedicated `SmsMfaChallenge` entity separate from `MfaChallenge`.

**Rejected because:**
- Code duplication for similar challenge lifecycle
- More complex verification routing
- Existing MfaChallenge entity was easily extensible

### Alternative 3: AWS SNS as Primary Provider

Use AWS SNS instead of Twilio for SMS delivery.

**Rejected because:**
- Twilio has better developer experience and documentation
- Twilio provides better international SMS delivery
- Can easily add SNS as an alternative provider later

### Alternative 4: Combined Challenge Creation

Create challenges for all available methods at signin time.

**Rejected because:**
- Unnecessary SMS costs if user has TOTP
- More complex challenge management
- TOTP is preferred for security and cost reasons

## Consequences

### Positive

- SMS MFA provides alternative to authenticator apps
- Pluggable provider architecture for flexibility
- Consistent challenge handling across MFA methods
- Rate limiting prevents abuse and controls costs
- Hashed codes protect against database exposure

### Negative

- SMS delivery costs (mitigated by rate limiting)
- SIM swapping vulnerability (mitigated by TOTP priority)
- Additional database storage for rate limit records

### Neutral

- Frontend needs to handle method switching
- Test infrastructure needs mock SMS provider support
- Phone number verification flow needed for production

## Implementation Notes

Key files:

**Backend:**
- `SmsProvider.kt` - Interface for SMS providers
- `TwilioSmsProvider.kt` - Twilio implementation
- `MockSmsProvider.kt` - Mock for testing
- `SmsRateLimiter.kt` - Rate limiting service
- `SmsMfaService.kt` - Core SMS MFA logic
- `MfaController.kt` - API endpoints (verify, resend)

**Frontend:**
- `mfa-verify.tsx` - Updated for SMS support
- `MfaVerificationForm.tsx` - Method-aware form
- `api.ts` - Added resend endpoint

**Database:**
- `V8__add_mfa_sms_support.sql` - Migration for SMS fields

## Configuration

```yaml
identity:
  sms:
    enabled: ${SMS_MFA_ENABLED:true}
    provider: ${SMS_PROVIDER:mock}
    twilio:
      account-sid: ${TWILIO_ACCOUNT_SID:}
      auth-token: ${TWILIO_AUTH_TOKEN:}
      from-number: ${TWILIO_FROM_NUMBER:}
    rate-limiting:
      max-sms-per-hour: 3
      resend-cooldown-seconds: 60
    code:
      length: 6
      expiry-seconds: 300
```

## References

- [US-0003-06: MFA Challenge (SMS)](../user-stories/0003-customer-signin/US-0003-06-mfa-sms.md)
- [Twilio Java SDK Documentation](https://www.twilio.com/docs/libraries/java)
- [OWASP MFA Best Practices](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html)
