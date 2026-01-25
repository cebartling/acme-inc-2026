package com.acme.customer.application

import com.acme.customer.api.v1.dto.UpdateProfileRequest
import com.acme.customer.domain.events.ProfileUpdated
import com.acme.customer.infrastructure.messaging.OutboxWriter
import com.acme.customer.infrastructure.persistence.CustomerPreferencesRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import com.acme.customer.infrastructure.persistence.EventStoreRepository
import com.acme.customer.infrastructure.projection.CustomerReadModelProjector
import com.acme.customer.infrastructure.validation.PhoneNumberValidator
import com.acme.customer.infrastructure.validation.PhoneValidationResult
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.Period
import java.util.TimeZone
import java.util.UUID

/**
 * Use case for updating a customer's profile.
 *
 * This use case orchestrates the profile update flow:
 * 1. Validate the customer exists and user is authorized
 * 2. Validate the update request (phone format, age, etc.)
 * 3. Apply changes to the customer entity
 * 4. Recalculate profile completeness
 * 5. Persist the changes
 * 6. Project to MongoDB
 * 7. Publish ProfileUpdated event via outbox
 */
@Service
class UpdateProfileUseCase(
    private val customerRepository: CustomerRepository,
    private val customerPreferencesRepository: CustomerPreferencesRepository,
    private val eventStoreRepository: EventStoreRepository,
    private val customerReadModelProjector: CustomerReadModelProjector,
    private val outboxWriter: OutboxWriter,
    private val phoneNumberValidator: PhoneNumberValidator,
    private val profileCompletionService: ProfileCompletionService,
    private val customerCacheService: com.acme.customer.infrastructure.cache.CustomerCacheService,
    meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(UpdateProfileUseCase::class.java)

    private val profileUpdateCounter: Counter = Counter.builder("profile.update")
        .tag("status", "success")
        .register(meterRegistry)

    private val profileUpdateFailureCounter: Counter = Counter.builder("profile.update")
        .tag("status", "failure")
        .register(meterRegistry)

    private val profileUpdateTimer: Timer = Timer.builder("profile.update.duration")
        .register(meterRegistry)

    companion object {
        /** Minimum age requirement in years. */
        const val MINIMUM_AGE_YEARS = 13

        /** Allowed gender values. */
        val ALLOWED_GENDERS = setOf("MALE", "FEMALE", "NON_BINARY", "PREFER_NOT_TO_SAY")
    }

    /**
     * Updates a customer's profile.
     *
     * @param customerId The customer ID to update.
     * @param userId The user ID making the request (for authorization).
     * @param request The update request containing fields to update.
     * @param correlationId Correlation ID for distributed tracing.
     * @return The result of the operation.
     */
    @Transactional
    fun execute(
        customerId: UUID,
        userId: UUID,
        request: UpdateProfileRequest,
        correlationId: UUID
    ): UpdateProfileResult {
        return profileUpdateTimer.record<UpdateProfileResult> {
            logger.info("Updating profile for customer {}", customerId)

            // Check if there are any updates to apply
            if (!request.hasUpdates()) {
                logger.debug("No updates provided for customer {}", customerId)
                return@record UpdateProfileResult.NoUpdates
            }

            // Find the customer
            val customer = customerRepository.findById(customerId).orElse(null)
                ?: run {
                    logger.warn("Customer not found: {}", customerId)
                    profileUpdateFailureCounter.increment()
                    return@record UpdateProfileResult.NotFound(customerId)
                }

            // Authorization check: verify the user owns this customer profile
            if (customer.userId != userId) {
                logger.warn(
                    "User {} attempted to update customer {} owned by user {}",
                    userId,
                    customerId,
                    customer.userId
                )
                profileUpdateFailureCounter.increment()
                return@record UpdateProfileResult.Unauthorized(customerId, userId)
            }

            // Validate the request
            val validationErrors = validateRequest(request)
            if (validationErrors.isNotEmpty()) {
                logger.debug("Validation failed for customer {}: {}", customerId, validationErrors)
                profileUpdateFailureCounter.increment()
                return@record UpdateProfileResult.ValidationFailed(validationErrors)
            }

            try {
                // Store previous completeness score for completion check
                val previousScore = customer.profileCompleteness

                // Track which fields were changed
                val changedFields = mutableListOf<String>()

                // Apply updates
                request.phone?.let { phone ->
                    customer.updatePhone(phone.countryCode, phone.number)
                    changedFields.add("phone")
                }

                request.dateOfBirth?.let { dob ->
                    customer.updateDateOfBirth(dob)
                    changedFields.add("dateOfBirth")
                }

                request.gender?.let { gender ->
                    customer.updateGender(gender.uppercase())
                    changedFields.add("gender")
                }

                request.preferredLocale?.let { locale ->
                    customer.updatePreferredLocale(locale)
                    changedFields.add("preferredLocale")
                }

                request.timezone?.let { tz ->
                    customer.updateTimezone(tz)
                    changedFields.add("timezone")
                }

                // Create domain event
                val event = ProfileUpdated.create(
                    customerId = customerId,
                    changedFields = changedFields,
                    profileCompleteness = customer.profileCompleteness,
                    correlationId = correlationId,
                    causationId = correlationId // Self-caused for API requests
                )

                // Persist event to event store
                eventStoreRepository.append(event)

                // Save customer
                customerRepository.save(customer)

                // Invalidate cache to ensure fresh data on next request
                customerCacheService.invalidate(customer.userId)

                logger.info(
                    "Updated profile for customer {}, changed fields: {}",
                    customerId,
                    changedFields
                )

                // Project to MongoDB and write to outbox
                val preferences = customerPreferencesRepository.findById(customerId).orElse(null)
                if (preferences != null) {
                    val projectionFuture = customerReadModelProjector.projectCustomer(customer, preferences)
                    projectionFuture.join()
                }

                // Write to outbox within the transaction
                outboxWriter.write(event, ProfileUpdated.TOPIC)

                // Check for profile completion and publish ProfileCompleted event if 100%
                profileCompletionService.checkAndUpdateCompletion(
                    customerId = customerId,
                    previousScore = previousScore,
                    correlationId = correlationId,
                    causationId = event.eventId
                )

                profileUpdateCounter.increment()

                UpdateProfileResult.Success(
                    customer = customer,
                    changedFields = changedFields
                )
            } catch (e: Exception) {
                logger.error(
                    "Failed to update profile for customer {}: {}",
                    customerId,
                    e.message,
                    e
                )
                profileUpdateFailureCounter.increment()
                UpdateProfileResult.Failure(
                    message = "Failed to update profile: ${e.message}",
                    cause = e
                )
            }
        }!!
    }

    /**
     * Validates the update request.
     *
     * @return Map of field names to error messages. Empty if validation passes.
     */
    private fun validateRequest(request: UpdateProfileRequest): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        // Validate phone number
        request.phone?.let { phone ->
            when (val result = phoneNumberValidator.validate(phone.countryCode, phone.number)) {
                is PhoneValidationResult.Invalid -> {
                    errors["phone"] = result.message
                }
                is PhoneValidationResult.Valid -> {
                    // Valid - no error
                }
            }
        }

        // Validate date of birth (minimum age)
        request.dateOfBirth?.let { dob ->
            val age = Period.between(dob, LocalDate.now()).years
            if (age < MINIMUM_AGE_YEARS) {
                errors["dateOfBirth"] = "You must be at least $MINIMUM_AGE_YEARS years old"
            }
        }

        // Validate gender
        request.gender?.let { gender ->
            if (gender.uppercase() !in ALLOWED_GENDERS) {
                errors["gender"] = "Invalid gender. Allowed values: ${ALLOWED_GENDERS.joinToString(", ")}"
            }
        }

        // Validate timezone
        request.timezone?.let { tz ->
            val availableIds = TimeZone.getAvailableIDs()
            if (tz !in availableIds) {
                errors["timezone"] = "Invalid timezone"
            }
        }

        // Validate locale format (basic check)
        request.preferredLocale?.let { locale ->
            if (!locale.matches(Regex("^[a-z]{2}(-[A-Z]{2})?$"))) {
                errors["preferredLocale"] = "Invalid locale format. Expected format: 'en' or 'en-US'"
            }
        }

        return errors
    }
}
