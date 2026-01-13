package com.acme.customer.application

import com.acme.customer.domain.ConsentRecord
import com.acme.customer.domain.ConsentSource
import com.acme.customer.domain.ConsentType
import com.acme.customer.domain.events.ConsentGranted
import com.acme.customer.domain.events.ConsentRevoked
import com.acme.customer.infrastructure.messaging.CustomerEventPublisher
import com.acme.customer.infrastructure.persistence.ConsentRecordRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Result type for the GrantConsentUseCase.
 */
sealed class GrantConsentResult {
    /**
     * Consent successfully granted or revoked.
     */
    data class Success(
        val consentRecord: ConsentRecord
    ) : GrantConsentResult()

    /**
     * Customer not found.
     */
    data class CustomerNotFound(val customerId: UUID) : GrantConsentResult()

    /**
     * User is not authorized to update this customer's consents.
     */
    data class Unauthorized(val customerId: UUID, val userId: UUID) : GrantConsentResult()

    /**
     * Attempted to revoke a required consent.
     */
    data class RequiredConsentCannotBeRevoked(
        val consentType: ConsentType,
        val message: String = "This consent is required for service delivery. To remove it, please close your account."
    ) : GrantConsentResult()

    /**
     * Invalid consent type.
     */
    data class InvalidConsentType(val consentType: String) : GrantConsentResult()

    /**
     * Invalid consent source.
     */
    data class InvalidConsentSource(val source: String) : GrantConsentResult()

    /**
     * No change detected (consent already in the requested state).
     */
    data class NoChange(
        val consentType: ConsentType,
        val currentStatus: Boolean
    ) : GrantConsentResult()

    /**
     * Unexpected failure.
     */
    data class Failure(val message: String, val cause: Throwable? = null) : GrantConsentResult()
}

/**
 * Request context for consent updates.
 */
data class ConsentUpdateContext(
    val ipAddress: String,
    val userAgent: String? = null
)

/**
 * Use case for granting or revoking customer consent.
 *
 * This use case handles the creation of immutable consent records,
 * ensuring GDPR compliance through audit trails and event publishing.
 *
 * Key behaviors:
 * - Creates append-only consent records (never updates/deletes)
 * - Prevents revocation of required consents (DATA_PROCESSING)
 * - Calculates expiration for optional consents (1 year default)
 * - Publishes ConsentGranted/ConsentRevoked events
 * - Tracks metrics for compliance monitoring
 */
@Service
class GrantConsentUseCase(
    private val customerRepository: CustomerRepository,
    private val consentRecordRepository: ConsentRecordRepository,
    private val eventPublisher: CustomerEventPublisher,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(GrantConsentUseCase::class.java)

    private lateinit var consentGrantedCounter: Counter
    private lateinit var consentRevokedCounter: Counter

    @PostConstruct
    fun initMetrics() {
        consentGrantedCounter = Counter.builder("consent_granted_total")
            .description("Total number of consents granted")
            .register(meterRegistry)

        consentRevokedCounter = Counter.builder("consent_revoked_total")
            .description("Total number of consents revoked")
            .register(meterRegistry)
    }

    /**
     * Executes the consent grant or revocation.
     *
     * @param customerId The customer ID.
     * @param userId The user ID (for authorization).
     * @param consentTypeString The type of consent.
     * @param granted Whether to grant (true) or revoke (false) the consent.
     * @param sourceString Where the consent change originated.
     * @param correlationId Correlation ID for distributed tracing.
     * @param context Request context with IP address and user agent.
     * @return The result of the operation.
     */
    @Transactional
    fun execute(
        customerId: UUID,
        userId: UUID,
        consentTypeString: String,
        granted: Boolean,
        sourceString: String,
        correlationId: UUID,
        context: ConsentUpdateContext
    ): GrantConsentResult {
        logger.debug(
            "Processing consent {} for customer {}: type={}, granted={}",
            if (granted) "grant" else "revocation",
            customerId,
            consentTypeString,
            granted
        )

        // Parse consent type
        val consentType = ConsentType.fromString(consentTypeString)
            ?: run {
                logger.warn("Invalid consent type: {}", consentTypeString)
                return GrantConsentResult.InvalidConsentType(consentTypeString)
            }

        // Parse source
        val source = try {
            ConsentSource.valueOf(sourceString)
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid consent source: {}", sourceString)
            return GrantConsentResult.InvalidConsentSource(sourceString)
        }

        // Check if required consent is being revoked
        if (consentType.required && !granted) {
            logger.warn(
                "Attempted to revoke required consent {} for customer {}",
                consentType,
                customerId
            )
            return GrantConsentResult.RequiredConsentCannotBeRevoked(consentType)
        }

        // Find customer
        val customer = customerRepository.findById(customerId).orElse(null)
            ?: run {
                logger.debug("Customer not found: {}", customerId)
                return GrantConsentResult.CustomerNotFound(customerId)
            }

        // Authorization check
        if (customer.userId != userId) {
            logger.warn(
                "User {} not authorized to update consents for customer {}",
                userId,
                customerId
            )
            return GrantConsentResult.Unauthorized(customerId, userId)
        }

        // Check current consent status
        val currentConsent = consentRecordRepository.findLatestByCustomerIdAndConsentType(
            customerId,
            consentType
        )

        // Check if this is a no-op (consent already in requested state)
        if (currentConsent != null && currentConsent.granted == granted) {
            logger.debug(
                "Consent {} for customer {} already in state {}",
                consentType,
                customerId,
                granted
            )
            return GrantConsentResult.NoChange(consentType, granted)
        }

        return try {
            // Get current version
            val currentVersion = currentConsent?.version ?: 0

            // Calculate expiration (only for non-required consents when granting)
            val expiresAt = if (granted && !consentType.required) {
                Instant.now().plus(ConsentRecord.DEFAULT_EXPIRATION_DAYS, ChronoUnit.DAYS)
            } else {
                null
            }

            val now = Instant.now()

            // Create immutable consent record
            val consentRecord = ConsentRecord.create(
                id = UUID.randomUUID(),
                customerId = customerId,
                consentType = consentType,
                granted = granted,
                source = source,
                ipAddress = context.ipAddress,
                userAgent = context.userAgent,
                expiresAt = expiresAt,
                version = currentVersion + 1
            )

            // Append record (never update)
            consentRecordRepository.save(consentRecord)

            // Publish event
            if (granted) {
                val event = ConsentGranted.create(
                    customerId = customerId,
                    consentId = consentRecord.id,
                    consentType = consentType,
                    grantedAt = now,
                    source = source,
                    expiresAt = expiresAt,
                    version = consentRecord.version,
                    correlationId = correlationId
                )
                eventPublisher.publish(event)
                consentGrantedCounter.increment()
            } else {
                val event = ConsentRevoked.create(
                    customerId = customerId,
                    consentId = consentRecord.id,
                    consentType = consentType,
                    revokedAt = now,
                    source = source,
                    version = consentRecord.version,
                    correlationId = correlationId
                )
                eventPublisher.publish(event)
                consentRevokedCounter.increment()
            }

            logger.info(
                "Consent {} {} for customer {}: type={}, version={}",
                if (granted) "granted" else "revoked",
                consentRecord.id,
                customerId,
                consentType,
                consentRecord.version
            )

            GrantConsentResult.Success(consentRecord)
        } catch (e: Exception) {
            logger.error(
                "Failed to process consent for customer {}: {}",
                customerId,
                e.message,
                e
            )
            GrantConsentResult.Failure("Failed to process consent: ${e.message}", e)
        }
    }
}
