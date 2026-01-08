# ADR-0021: Information Disclosure Prevention

## Status

Accepted

## Context

Web applications frequently expose information through API responses that can be exploited by attackers:

- **Email Enumeration**: Attackers discover valid email addresses by observing different responses for existing vs non-existing accounts
- **Account Probing**: Determining which usernames/emails are registered
- **Timing Attacks**: Measuring response times to infer information about data existence
- **Error Message Leakage**: Detailed error messages revealing system internals

Common vulnerable patterns include:

```
# Login endpoint - reveals email existence
POST /login { email: "test@example.com" }
→ 404 "User not found"           # Email doesn't exist
→ 401 "Invalid password"          # Email exists!

# Password reset - reveals email existence
POST /password/reset { email: "test@example.com" }
→ 404 "No account with this email"  # Doesn't exist
→ 200 "Reset email sent"            # Exists!

# Registration - reveals email existence
POST /register { email: "test@example.com" }
→ 409 "Email already registered"    # Exists!
→ 201 "Account created"             # Didn't exist
```

These patterns enable attackers to:
- Build lists of valid user emails for phishing campaigns
- Target specific users for credential stuffing attacks
- Gather intelligence for social engineering
- Violate user privacy (is this person a customer?)

## Decision

We will implement **uniform response patterns** for all endpoints that could reveal account existence or user information.

### Core Principle

**Responses for existing and non-existing resources must be indistinguishable to an external observer.**

### Implementation Patterns

#### 1. Generic Success Messages

Return the same success response regardless of whether the resource exists:

```kotlin
// Resend verification email
@PostMapping("/verify/resend")
fun resendVerification(@RequestBody request: ResendRequest): ResponseEntity<ResendResponse> {
    // Always return success, even if email doesn't exist
    useCaseResult = resendVerificationUseCase.execute(request.email)

    return ResponseEntity.ok(ResendResponse(
        message = "If an account exists with this email, a verification link has been sent.",
        timestamp = Instant.now()
    ))
}
```

#### 2. Uniform Response Structure

All outcomes return the same HTTP status and response structure:

```kotlin
// Password reset - always 200 OK
POST /password/reset
→ 200 { "message": "If an account exists, a reset link has been sent." }

// Never reveal whether email exists
// ❌ 404 "User not found"
// ❌ 200 "Email sent to user@example.com"
```

#### 3. Constant-Time Operations

Ensure response times don't reveal information:

```kotlin
fun checkCredentials(email: String, password: String): AuthResult {
    val user = userRepository.findByEmail(email)

    // Always perform password hash comparison, even for non-existent users
    val hashToCompare = user?.passwordHash ?: DUMMY_HASH
    val passwordValid = passwordEncoder.matches(password, hashToCompare)

    return if (user != null && passwordValid) {
        AuthResult.Success(user)
    } else {
        AuthResult.InvalidCredentials  // Same response for both cases
    }
}
```

#### 4. Rate Limiting

Apply rate limiting uniformly to prevent enumeration through repeated attempts:

```kotlin
@RateLimited(requests = 5, per = Duration.ofMinutes(1))
@PostMapping("/password/reset")
fun resetPassword(@RequestBody request: ResetRequest): ResponseEntity<*> {
    // Rate limit applies regardless of email validity
}
```

### Endpoint Guidelines

| Endpoint Type | Secure Pattern |
|--------------|----------------|
| Login | "Invalid email or password" (never specify which) |
| Registration | Accept all, send verification email if new, ignore if exists |
| Password Reset | "If account exists, email sent" |
| Email Verification Resend | "If account exists, email sent" |
| Username/Email Check | Don't provide this endpoint |
| Account Lookup | Require authentication first |

### What NOT To Do

```kotlin
// ❌ Different messages reveal information
if (userExists) {
    return "Password incorrect"
} else {
    return "User not found"
}

// ❌ Different status codes reveal information
if (userExists) {
    return ResponseEntity.status(401).body("Unauthorized")
} else {
    return ResponseEntity.status(404).body("Not found")
}

// ❌ Timing differences reveal information
if (userExists) {
    verifyPassword(password)  // Slow operation
    return "Invalid credentials"
} else {
    return "Invalid credentials"  // Fast, no password check
}
```

### Logging and Monitoring

While responses are uniform, internal logging should capture the actual outcome for debugging and security monitoring:

```kotlin
fun resendVerification(email: String): ResponseEntity<*> {
    val result = resendUseCase.execute(email)

    // Log actual outcome for security monitoring
    when (result) {
        is ResendResult.Sent -> logger.info("Verification email sent to existing user")
        is ResendResult.UserNotFound -> logger.info("Resend attempted for non-existent email")
        is ResendResult.AlreadyVerified -> logger.info("Resend attempted for verified user")
        is ResendResult.RateLimited -> logger.warn("Rate limit exceeded for resend")
    }

    // Return uniform response
    return ResponseEntity.ok(genericSuccessMessage)
}
```

## Consequences

### Positive

- **Security**: Prevents email enumeration and account probing attacks
- **Privacy**: Users cannot determine if others are customers
- **Compliance**: Helps meet GDPR and privacy requirements
- **Reduced Attack Surface**: Less information for social engineering

### Negative

- **User Experience**: Users don't get specific feedback (e.g., "email already registered")
- **Support Overhead**: Users may not realize they have existing accounts
- **Debugging Complexity**: Need to check logs to understand actual behavior
- **Implementation Effort**: Requires careful attention to response uniformity

### Mitigations

- Provide clear, helpful generic messages that guide users appropriately
- Implement good logging for support and debugging
- For registration, send "account exists" email to existing users instead of error
- Consider authenticated endpoints for account management features
- Document the pattern clearly for developers

## Related Decisions

- ADR-0019: Persistent Rate Limiting (rate limiting supports this pattern)
- ADR-0020: Sealed Classes for Use Case Results (internal handling of different outcomes)

## References

- OWASP Authentication Cheat Sheet
- CWE-204: Observable Response Discrepancy
- NIST SP 800-63B: Digital Identity Guidelines
