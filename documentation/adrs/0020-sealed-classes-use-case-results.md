# ADR-0020: Sealed Classes for Use Case Results

## Status

Accepted

## Context

Use cases in the application layer need to communicate outcomes to the API layer. Traditional approaches include:

1. **Exceptions for errors**: Throw different exception types for different failures
2. **Nullable returns**: Return `null` for failure, value for success
3. **Status codes/enums**: Return a status code alongside the result
4. **Result wrapper classes**: Generic `Result<T, E>` or similar patterns

Each approach has drawbacks:

- **Exceptions**: Control flow via exceptions is expensive, hard to track all possible outcomes, easy to forget handling
- **Nullable returns**: No information about why the operation failed, only binary success/failure
- **Status codes**: Disconnected from the actual result data, easy to ignore, not type-safe
- **Generic Result wrappers**: Lose domain-specific semantics, error types become stringly-typed

The platform needs a pattern that:

- Makes all possible outcomes explicit and discoverable
- Provides compile-time guarantees that all outcomes are handled
- Carries domain-specific information with each outcome
- Avoids exception-based control flow for expected business outcomes
- Enables clear, testable code

## Decision

We will use **Kotlin sealed classes** to model all possible outcomes of use case operations.

### Pattern Structure

```kotlin
sealed class UseCaseResult {
    data class Success(val data: DomainData) : UseCaseResult()
    data class ValidationFailed(val errors: List<ValidationError>) : UseCaseResult()
    data class NotFound(val identifier: String) : UseCaseResult()
    data class BusinessRuleViolation(val rule: String, val details: String) : UseCaseResult()
}
```

### Real Example: Email Verification

```kotlin
sealed class VerifyEmailResult {
    data class Success(val userId: UUID, val activatedAt: Instant) : VerifyEmailResult()
    data class TokenExpired(val email: String, val expiredAt: Instant) : VerifyEmailResult()
    data object TokenInvalid : VerifyEmailResult()
    data class AlreadyVerified(val userId: UUID, val verifiedAt: Instant) : VerifyEmailResult()
}
```

### Usage in Use Cases

```kotlin
@Service
class VerifyEmailUseCase(
    private val tokenRepository: VerificationTokenRepository,
    private val userRepository: UserRepository
) {
    fun execute(token: String): VerifyEmailResult {
        val verificationToken = tokenRepository.findByToken(token)
            ?: return VerifyEmailResult.TokenInvalid

        if (verificationToken.isExpired()) {
            return VerifyEmailResult.TokenExpired(
                email = verificationToken.email,
                expiredAt = verificationToken.expiresAt
            )
        }

        val user = userRepository.findById(verificationToken.userId)
            ?: return VerifyEmailResult.TokenInvalid

        if (user.emailVerified) {
            return VerifyEmailResult.AlreadyVerified(
                userId = user.id,
                verifiedAt = user.verifiedAt!!
            )
        }

        user.markEmailAsVerified()
        userRepository.save(user)

        return VerifyEmailResult.Success(
            userId = user.id,
            activatedAt = user.verifiedAt!!
        )
    }
}
```

### Usage in Controllers

```kotlin
@RestController
class VerificationController(private val verifyEmailUseCase: VerifyEmailUseCase) {

    @GetMapping("/verify")
    fun verify(@RequestParam token: String): ResponseEntity<Unit> {
        return when (val result = verifyEmailUseCase.execute(token)) {
            is VerifyEmailResult.Success ->
                redirect("/login?verified=true")

            is VerifyEmailResult.TokenExpired ->
                redirect("/verify/resend?error=expired")

            is VerifyEmailResult.TokenInvalid ->
                redirect("/verify/resend?error=invalid")

            is VerifyEmailResult.AlreadyVerified ->
                redirect("/login?already_verified=true")
        }
    }
}
```

### Guidelines

1. **Name outcomes descriptively**: Use domain language (`TokenExpired` not `Error2`)
2. **Include relevant data**: Each outcome carries the information needed to handle it
3. **Use `data object` for singletons**: When no additional data is needed
4. **Keep sealed classes focused**: One per use case or closely related operations
5. **Avoid nested sealed classes**: Keep the hierarchy flat for clarity
6. **Reserve exceptions for unexpected failures**: Database errors, network failures, etc.

## Consequences

### Positive

- **Exhaustive Handling**: Kotlin compiler ensures all outcomes are handled in `when` expressions
- **Self-Documenting**: The sealed class definition is the contract of possible outcomes
- **Type-Safe**: Each outcome carries strongly-typed, relevant data
- **Testable**: Easy to test each outcome path independently
- **Refactoring Safety**: Adding a new outcome causes compiler errors at all call sites
- **No Exception Overhead**: Expected business outcomes don't use expensive exception machinery
- **IDE Support**: Autocomplete shows all possible outcomes

### Negative

- **More Classes**: Each use case needs its own result sealed class
- **Kotlin-Specific**: Pattern doesn't translate directly to Java callers
- **Learning Curve**: Developers familiar with exception-based patterns need adjustment

### Mitigations

- Establish naming conventions and templates for consistency
- Place result classes alongside their use cases for discoverability
- Provide examples and code review guidelines
- For Java interop (if needed), provide extension functions that throw exceptions

## Related Decisions

- ADR-0010: Kotlin with Spring Boot for Backend Services
- ADR-0002: CQRS Pattern (use cases are command/query handlers)
