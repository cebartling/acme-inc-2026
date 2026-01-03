package com.acme.identity.application

import com.acme.identity.api.v1.dto.RegisterUserRequest
import com.acme.identity.api.v1.dto.RegisterUserResponse
import com.acme.identity.domain.RegistrationSource
import com.acme.identity.domain.User
import com.acme.identity.domain.UserStatus
import com.acme.identity.domain.VerificationToken
import com.acme.identity.domain.events.UserRegistered
import com.acme.identity.infrastructure.messaging.UserEventPublisher
import com.acme.identity.infrastructure.persistence.EventStoreRepository
import com.acme.identity.infrastructure.persistence.UserRepository
import com.acme.identity.infrastructure.persistence.VerificationTokenRepository
import com.acme.identity.infrastructure.security.PasswordHasher
import com.acme.identity.infrastructure.security.UserIdGenerator
import com.acme.identity.infrastructure.security.VerificationTokenGenerator
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

sealed class RegisterUserResult {
    data class Success(val response: RegisterUserResponse) : RegisterUserResult()
    data class DuplicateEmail(val message: String) : RegisterUserResult()
    data class Error(val message: String) : RegisterUserResult()
}

@Service
class RegisterUserUseCase(
    private val userRepository: UserRepository,
    private val verificationTokenRepository: VerificationTokenRepository,
    private val eventStoreRepository: EventStoreRepository,
    private val userEventPublisher: UserEventPublisher,
    private val passwordHasher: PasswordHasher,
    private val userIdGenerator: UserIdGenerator,
    private val verificationTokenGenerator: VerificationTokenGenerator,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(RegisterUserUseCase::class.java)

    private val registrationTimer = Timer.builder("registration_duration_seconds")
        .description("Time taken to process registration")
        .register(meterRegistry)

    private val passwordHashTimer = Timer.builder("password_hash_duration_seconds")
        .description("Time taken to hash password")
        .register(meterRegistry)

    @Transactional
    fun execute(
        request: RegisterUserRequest,
        registrationSource: RegistrationSource = RegistrationSource.WEB,
        correlationId: UUID = UUID.randomUUID()
    ): RegisterUserResult {
        return registrationTimer.record<RegisterUserResult> {
            try {
                executeInternal(request, registrationSource, correlationId)
            } catch (e: Exception) {
                logger.error("Registration failed for email: {}", request.email, e)
                incrementRegistrationCounter("error")
                RegisterUserResult.Error("Registration failed: ${e.message}")
            }
        }!!
    }

    private fun executeInternal(
        request: RegisterUserRequest,
        registrationSource: RegistrationSource,
        correlationId: UUID
    ): RegisterUserResult {
        // Check for duplicate email
        if (userRepository.existsByEmail(request.email.lowercase())) {
            logger.info("Registration attempt with existing email: {}", request.email)
            incrementRegistrationCounter("duplicate")
            return RegisterUserResult.DuplicateEmail("An account with this email already exists")
        }

        // Generate user ID (UUID v7)
        val userId = userIdGenerator.generateRaw()

        // Hash password with Argon2id
        val passwordHash = passwordHashTimer.record<String> {
            passwordHasher.hash(request.password)
        }!!

        // Create user entity
        val user = User(
            id = userId,
            email = request.email.lowercase(),
            passwordHash = passwordHash,
            firstName = request.firstName,
            lastName = request.lastName,
            status = UserStatus.PENDING_VERIFICATION,
            tosAcceptedAt = request.tosAcceptedAt,
            marketingOptIn = request.marketingOptIn,
            registrationSource = registrationSource
        )

        // Generate verification token
        val verificationToken = VerificationToken(
            id = UUID.randomUUID(),
            userId = userId,
            token = verificationTokenGenerator.generate(),
            expiresAt = verificationTokenGenerator.calculateExpiration()
        )

        // Create domain event
        val event = UserRegistered.create(
            userId = userId,
            email = user.email,
            firstName = user.firstName,
            lastName = user.lastName,
            tosAcceptedAt = user.tosAcceptedAt,
            marketingOptIn = user.marketingOptIn,
            registrationSource = registrationSource,
            correlationId = correlationId
        )

        // Persist to event store FIRST (before response)
        eventStoreRepository.append(event)

        // Persist user and verification token
        userRepository.save(user)
        verificationTokenRepository.save(verificationToken)

        // Publish event to Kafka (async, but we log failures)
        userEventPublisher.publish(event)

        logger.info("User registered successfully: {} with ID: {}", user.email, userId)
        incrementRegistrationCounter("success")

        return RegisterUserResult.Success(
            RegisterUserResponse(
                userId = userId,
                email = user.email,
                status = user.status,
                createdAt = user.createdAt
            )
        )
    }

    private fun incrementRegistrationCounter(status: String) {
        meterRegistry.counter("registration_attempts_total", "status", status).increment()
    }
}
