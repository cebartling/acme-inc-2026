package com.acme.customer.application

import com.acme.customer.api.v1.dto.UpdatePreferencesRequest
import com.acme.customer.domain.Customer
import com.acme.customer.domain.CustomerPreferences
import com.acme.customer.domain.NotificationFrequency
import com.acme.customer.domain.PreferenceChange
import com.acme.customer.domain.PreferenceChangeLog
import com.acme.customer.domain.events.PreferencesUpdated
import com.acme.customer.infrastructure.messaging.CustomerEventPublisher
import com.acme.customer.infrastructure.persistence.CustomerPreferencesRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import com.acme.customer.infrastructure.persistence.PreferenceChangeLogRepository
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Result type for the UpdatePreferencesUseCase.
 */
sealed class UpdatePreferencesResult {
    /**
     * Preferences updated successfully.
     */
    data class Success(
        val preferences: CustomerPreferences,
        val changedPreferences: Map<String, PreferenceChange>
    ) : UpdatePreferencesResult()

    /**
     * Customer not found.
     */
    data class NotFound(val customerId: UUID) : UpdatePreferencesResult()

    /**
     * User is not authorized to update this customer's preferences.
     */
    data class Unauthorized(val customerId: UUID, val userId: UUID) : UpdatePreferencesResult()

    /**
     * No updates were provided in the request.
     */
    data object NoUpdates : UpdatePreferencesResult()

    /**
     * Validation failed.
     */
    data class ValidationFailed(val errors: List<String>) : UpdatePreferencesResult()

    /**
     * SMS requires verified phone number.
     */
    data object PhoneNotVerified : UpdatePreferencesResult()

    /**
     * Unsupported language.
     */
    data class UnsupportedLanguage(val locale: String) : UpdatePreferencesResult()

    /**
     * Unexpected failure.
     */
    data class Failure(val message: String, val cause: Throwable? = null) : UpdatePreferencesResult()
}

/**
 * Request context for preference updates.
 */
data class PreferenceUpdateContext(
    val ipAddress: String? = null,
    val userAgent: String? = null
)

/**
 * Use case for updating customer preferences.
 *
 * Handles partial updates, change detection, GDPR compliance logging,
 * and event publishing.
 */
@Service
class UpdatePreferencesUseCase(
    private val customerRepository: CustomerRepository,
    private val preferencesRepository: CustomerPreferencesRepository,
    private val changeLogRepository: PreferenceChangeLogRepository,
    private val eventPublisher: CustomerEventPublisher,
    private val customerCacheService: com.acme.customer.infrastructure.cache.CustomerCacheService,
    private val meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(UpdatePreferencesUseCase::class.java)

    private lateinit var preferencesUpdatedCounter: Counter
    private lateinit var marketingOptOutCounter: Counter
    private lateinit var smsEnabledCounter: Counter

    @PostConstruct
    fun initMetrics() {
        preferencesUpdatedCounter = Counter.builder("preferences_updated_total")
            .description("Total number of preference updates")
            .register(meterRegistry)

        marketingOptOutCounter = Counter.builder("marketing_opt_out_total")
            .description("Total number of marketing opt-outs")
            .register(meterRegistry)

        smsEnabledCounter = Counter.builder("sms_enabled_total")
            .description("Total number of SMS notification enables")
            .register(meterRegistry)
    }

    companion object {
        /** Supported languages (ISO 639-1 codes with regions). */
        val SUPPORTED_LANGUAGES = setOf(
            "en-US", "en-GB", "es-ES", "es-MX", "fr-FR", "fr-CA",
            "de-DE", "it-IT", "pt-BR", "pt-PT", "ja-JP", "zh-CN", "zh-TW", "ko-KR"
        )

        /** Supported currencies (ISO 4217 codes). */
        val SUPPORTED_CURRENCIES = setOf(
            "USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CNY", "KRW", "BRL", "MXN"
        )
    }

    /**
     * Executes the preference update.
     *
     * @param customerId The customer ID.
     * @param userId The user ID (for authorization).
     * @param request The update request.
     * @param correlationId Correlation ID for distributed tracing.
     * @param context Request context with IP address and user agent.
     * @return The result of the operation.
     */
    @Transactional
    fun execute(
        customerId: UUID,
        userId: UUID,
        request: UpdatePreferencesRequest,
        correlationId: UUID,
        context: PreferenceUpdateContext = PreferenceUpdateContext()
    ): UpdatePreferencesResult {
        logger.debug("Updating preferences for customer: {}", customerId)

        // Check for updates
        if (!request.hasUpdates()) {
            logger.debug("No updates provided in request")
            return UpdatePreferencesResult.NoUpdates
        }

        // Find customer
        val customer = customerRepository.findById(customerId).orElse(null)
            ?: run {
                logger.debug("Customer not found: {}", customerId)
                return UpdatePreferencesResult.NotFound(customerId)
            }

        // Authorization check
        if (customer.userId != userId) {
            logger.warn("User {} not authorized to update preferences for customer {}", userId, customerId)
            return UpdatePreferencesResult.Unauthorized(customerId, userId)
        }

        // Find preferences
        val preferences = preferencesRepository.findById(customerId).orElse(null)
            ?: run {
                logger.error("Preferences not found for customer: {}", customerId)
                return UpdatePreferencesResult.Failure("Preferences not found")
            }

        // Validate request
        val validationErrors = validateRequest(request, customer)
        if (validationErrors.isNotEmpty()) {
            return UpdatePreferencesResult.ValidationFailed(validationErrors)
        }

        // Check SMS eligibility
        if (request.communication?.sms == true && customer.phoneVerified != true) {
            logger.debug("Cannot enable SMS: phone not verified for customer {}", customerId)
            return UpdatePreferencesResult.PhoneNotVerified
        }

        // Check language support
        request.display?.language?.let { language ->
            if (language !in SUPPORTED_LANGUAGES) {
                return UpdatePreferencesResult.UnsupportedLanguage(language)
            }
        }

        // Parse notification frequency
        val notificationFrequency = request.communication?.frequency?.let {
            NotificationFrequency.fromString(it)
        }

        // Detect changes
        val changes = preferences.detectChanges(
            emailNotifications = request.communication?.email,
            smsNotifications = request.communication?.sms,
            pushNotifications = request.communication?.push,
            marketingCommunications = request.communication?.marketing,
            notificationFrequency = notificationFrequency,
            shareDataWithPartners = request.privacy?.shareDataWithPartners,
            allowAnalytics = request.privacy?.allowAnalytics,
            allowPersonalization = request.privacy?.allowPersonalization,
            language = request.display?.language,
            currency = request.display?.currency,
            timezone = request.display?.timezone
        )

        if (changes.isEmpty()) {
            logger.debug("No actual changes detected for customer {}", customerId)
            return UpdatePreferencesResult.NoUpdates
        }

        return try {
            // Apply changes
            preferences.applyChanges(
                emailNotifications = request.communication?.email,
                smsNotifications = request.communication?.sms,
                pushNotifications = request.communication?.push,
                marketingCommunications = request.communication?.marketing,
                notificationFrequency = notificationFrequency,
                shareDataWithPartners = request.privacy?.shareDataWithPartners,
                allowAnalytics = request.privacy?.allowAnalytics,
                allowPersonalization = request.privacy?.allowPersonalization,
                language = request.display?.language,
                currency = request.display?.currency,
                timezone = request.display?.timezone
            )

            // Log changes for GDPR compliance
            logChangesForCompliance(customerId, changes, context)

            // Persist preferences
            preferencesRepository.save(preferences)

            // Invalidate cache to ensure fresh data on next request
            customerCacheService.invalidate(customer.userId)

            // Create and publish event
            val event = PreferencesUpdated.create(
                customerId = customerId,
                changedPreferences = changes,
                correlationId = correlationId
            )
            eventPublisher.publish(event)

            // Update metrics
            preferencesUpdatedCounter.increment()

            // Track specific metrics
            if (changes.containsKey("communication.marketing") &&
                changes["communication.marketing"]?.newValue == "false") {
                marketingOptOutCounter.increment()
            }
            if (changes.containsKey("communication.sms") &&
                changes["communication.sms"]?.newValue == "true") {
                smsEnabledCounter.increment()
            }

            logger.info("Updated {} preferences for customer {}", changes.size, customerId)

            UpdatePreferencesResult.Success(preferences, changes)
        } catch (e: Exception) {
            logger.error("Failed to update preferences for customer {}: {}", customerId, e.message, e)
            UpdatePreferencesResult.Failure("Failed to update preferences: ${e.message}", e)
        }
    }

    /**
     * Validates the update request.
     */
    private fun validateRequest(request: UpdatePreferencesRequest, customer: Customer): List<String> {
        val errors = mutableListOf<String>()

        // Validate frequency
        request.communication?.frequency?.let { frequency ->
            if (NotificationFrequency.fromString(frequency) == null) {
                errors.add("Invalid notification frequency: $frequency. Valid values: ${NotificationFrequency.entries.joinToString()}")
            }
        }

        // Validate currency
        request.display?.currency?.let { currency ->
            if (currency !in SUPPORTED_CURRENCIES) {
                errors.add("Unsupported currency: $currency. Supported: ${SUPPORTED_CURRENCIES.joinToString()}")
            }
        }

        // Validate timezone (basic check)
        request.display?.timezone?.let { timezone ->
            try {
                java.time.ZoneId.of(timezone)
            } catch (e: Exception) {
                errors.add("Invalid timezone: $timezone")
            }
        }

        return errors
    }

    /**
     * Logs preference changes for GDPR compliance.
     */
    private fun logChangesForCompliance(
        customerId: UUID,
        changes: Map<String, PreferenceChange>,
        context: PreferenceUpdateContext
    ) {
        changes.forEach { (preferenceName, change) ->
            val logEntry = PreferenceChangeLog.create(
                customerId = customerId,
                preferenceName = preferenceName,
                oldValue = change.oldValue,
                newValue = change.newValue,
                ipAddress = context.ipAddress,
                userAgent = context.userAgent
            )
            changeLogRepository.save(logEntry)
        }
        logger.debug("Logged {} preference changes for GDPR compliance", changes.size)
    }
}
