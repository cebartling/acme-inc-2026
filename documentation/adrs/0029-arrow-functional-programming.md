# ADR-0029: Arrow for Functional Programming in Kotlin

## Status

Accepted

## Context

The codebase already uses Kotlin sealed classes for modeling use case outcomes (see ADR-0020), which provides compile-time safety and exhaustive pattern matching. However, as the application grows in complexity, we encounter additional functional programming needs:

1. **Error Accumulation**: Validating multiple fields and collecting all errors rather than failing on the first
2. **Railway-Oriented Programming**: Chaining operations that may fail without nested conditionals
3. **Nullable Value Handling**: More expressive handling of optional values beyond `?.let {}` chains
4. **Parallel Validation**: Running independent validations concurrently and combining results
5. **Immutable Data Transformations**: Updating deeply nested immutable data structures

Current approaches using standard Kotlin:

- **Error accumulation**: Manual collection of errors in mutable lists, error-prone
- **Operation chaining**: Nested `when` expressions or early returns, reduces readability
- **Optional handling**: Scattered null checks, potential for NullPointerException
- **Nested updates**: Verbose copy() chains for deeply nested data classes

Arrow-kt is the standard functional programming library for Kotlin, providing:

- Type-safe error handling with `Either<E, A>` and `Raise<E>` context
- Error accumulation with `Either.zipOrAccumulate` and `Validated`
- Null-safe operations with `Option<A>`
- Coroutine-integrated effects system
- Optics for immutable data manipulation

## Decision

We will adopt **Arrow** as the functional programming library for Kotlin services, using it to complement (not replace) the existing sealed class pattern.

### Integration Strategy

Arrow will be used for:

1. **Validation logic** - Accumulating multiple validation errors
2. **Complex operation chains** - When multiple sequential operations can fail
3. **Cross-cutting concerns** - Retry, timeout, and resource management
4. **Data transformations** - When modifying deeply nested immutable structures

Sealed classes remain the standard for:

1. **Use case results** - Public API of use cases (per ADR-0020)
2. **Domain events** - Event type hierarchies
3. **Simple success/failure** - When error accumulation isn't needed

### Dependency Configuration

Add to `build.gradle.kts`:

```kotlin
dependencies {
    implementation(platform("io.arrow-kt:arrow-stack:1.2.4"))
    implementation("io.arrow-kt:arrow-core")
    implementation("io.arrow-kt:arrow-fx-coroutines")
    // Optional: for optics
    implementation("io.arrow-kt:arrow-optics")
    ksp("io.arrow-kt:arrow-optics-ksp-plugin:1.2.4")
}
```

### Pattern: Validation with Error Accumulation

```kotlin
import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.zipOrAccumulate

data class ValidationError(val field: String, val message: String)

fun validateRegistration(
    email: String,
    password: String,
    name: String
): Either<List<ValidationError>, ValidatedRegistration> = either {
    zipOrAccumulate(
        { validateEmail(email).bind() },
        { validatePassword(password).bind() },
        { validateName(name).bind() }
    ) { validEmail, validPassword, validName ->
        ValidatedRegistration(validEmail, validPassword, validName)
    }
}
```

### Pattern: Operation Chaining with Raise

```kotlin
import arrow.core.raise.Raise
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull

context(Raise<OrderError>)
fun processOrder(orderId: UUID): ProcessedOrder {
    val order = orderRepository.findById(orderId)
    ensureNotNull(order) { OrderError.NotFound(orderId) }

    ensure(order.status == OrderStatus.PENDING) {
        OrderError.InvalidStatus(order.status)
    }

    val inventory = inventoryService.reserve(order.items)
    ensureNotNull(inventory) { OrderError.InsufficientStock(order.items) }

    val payment = paymentService.charge(order.total)
    ensure(payment.successful) { OrderError.PaymentFailed(payment.error) }

    return order.markProcessed(payment.transactionId)
}
```

### Pattern: Converting to Sealed Class Results

Internal Arrow types convert to sealed class results at use case boundaries:

```kotlin
@Service
class ProcessOrderUseCase(
    private val orderProcessor: OrderProcessor
) {
    fun execute(orderId: UUID): ProcessOrderResult {
        return either { orderProcessor.process(orderId) }
            .fold(
                ifLeft = { error -> error.toResult() },
                ifRight = { order -> ProcessOrderResult.Success(order) }
            )
    }
}

private fun OrderError.toResult(): ProcessOrderResult = when (this) {
    is OrderError.NotFound -> ProcessOrderResult.OrderNotFound(orderId)
    is OrderError.InvalidStatus -> ProcessOrderResult.InvalidOrderStatus(status)
    is OrderError.InsufficientStock -> ProcessOrderResult.OutOfStock(items)
    is OrderError.PaymentFailed -> ProcessOrderResult.PaymentDeclined(reason)
}
```

### Guidelines

1. **Use Arrow internally, sealed classes externally**: Use cases expose sealed class results; Arrow stays within implementation
2. **Prefer `Raise` over `Either` for new code**: Cleaner syntax, better coroutine integration
3. **Accumulate validation errors**: Use `zipOrAccumulate` when validating multiple fields
4. **Keep error types domain-specific**: Don't use generic `String` or `Throwable` as error types
5. **Document Arrow patterns**: Add team documentation for common patterns
6. **Use optics sparingly**: Only for deeply nested (3+ levels) immutable updates

## Consequences

### Positive

- **Cleaner validation code**: Error accumulation without manual list management
- **Readable operation chains**: No nested `when` expressions or early returns
- **Type-safe error handling**: Compiler-enforced error handling throughout call chain
- **Coroutine integration**: Arrow effects work seamlessly with Kotlin coroutines
- **Industry standard**: Arrow is the de facto functional programming library for Kotlin
- **Gradual adoption**: Can introduce incrementally without rewriting existing code

### Negative

- **Learning curve**: Team members unfamiliar with functional programming need training
- **Additional dependency**: Adds Arrow libraries to the dependency tree
- **Context receivers**: `Raise` context uses experimental Kotlin features
- **Potential overuse**: Risk of using Arrow where simpler Kotlin constructs suffice

### Mitigations

- Provide team training sessions on Arrow fundamentals
- Create internal documentation with approved patterns and examples
- Code review guidelines to prevent overuse
- Use stable Arrow features; avoid experimental modules
- Keep sealed classes as the public API to limit Arrow exposure

## Related Decisions

- ADR-0010: Kotlin with Spring Boot for Backend Services
- ADR-0020: Sealed Classes for Use Case Results
- ADR-0026: Phone Number Validation with libphonenumber
