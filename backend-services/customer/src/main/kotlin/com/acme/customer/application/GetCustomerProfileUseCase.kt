package com.acme.customer.application

import com.acme.customer.api.v1.dto.CustomerResponse
import com.acme.customer.infrastructure.cache.CustomerCacheService
import com.acme.customer.infrastructure.persistence.CustomerPreferencesRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import com.acme.customer.infrastructure.tracking.CustomerActivityTracker
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Use case for retrieving the current customer's profile.
 *
 * This use case orchestrates the profile retrieval flow:
 * 1. Check Redis cache for the customer profile
 * 2. If cache miss, fetch from database (PostgreSQL)
 * 3. Build CustomerResponse DTO
 * 4. Cache the response for future requests (5-minute TTL)
 * 5. Track user activity asynchronously (non-blocking)
 *
 * Performance target: p95 < 500ms (including cache miss scenarios)
 */
@Service
class GetCustomerProfileUseCase(
    private val customerRepository: CustomerRepository,
    private val customerPreferencesRepository: CustomerPreferencesRepository,
    private val customerCacheService: CustomerCacheService,
    private val activityTracker: CustomerActivityTracker,
    meterRegistry: MeterRegistry
) {
    private val logger = LoggerFactory.getLogger(GetCustomerProfileUseCase::class.java)

    private val cacheHitCounter: Counter = Counter.builder("customer.profile.cache")
        .tag("result", "hit")
        .register(meterRegistry)

    private val cacheMissCounter: Counter = Counter.builder("customer.profile.cache")
        .tag("result", "miss")
        .register(meterRegistry)

    private val profileFetchTimer: Timer = Timer.builder("customer.profile.fetch.duration")
        .register(meterRegistry)

    /**
     * Retrieves a customer profile by user ID.
     *
     * @param userId The user ID from the Identity Service.
     * @return The result of the operation.
     */
    fun execute(userId: UUID): GetCustomerProfileResult {
        return profileFetchTimer.record<GetCustomerProfileResult> {
            logger.debug("Fetching customer profile for user: {}", userId)

            // 1. Try cache first
            val cached = customerCacheService.get(userId)
            if (cached != null) {
                cacheHitCounter.increment()
                logger.debug("Returning cached profile for user: {}", userId)

                // Track activity async (fire-and-forget)
                activityTracker.trackAsync(userId)

                return@record GetCustomerProfileResult.Success(cached)
            }

            cacheMissCounter.increment()
            logger.debug("Cache miss for user: {}, fetching from database", userId)

            // 2. Fetch from database
            val customer = customerRepository.findByUserId(userId)
                ?: run {
                    logger.warn("Customer not found for user: {}", userId)
                    return@record GetCustomerProfileResult.NotFound(userId)
                }

            val preferences = customerPreferencesRepository.findById(customer.id).orElse(null)
                ?: run {
                    logger.error("Preferences not found for customer: {}", customer.id)
                    return@record GetCustomerProfileResult.PreferencesNotFound(customer.id)
                }

            // 3. Build response DTO
            val response = CustomerResponse.fromDomain(customer, preferences)

            // 4. Cache the profile
            customerCacheService.put(userId, response)

            // 5. Track activity asynchronously
            activityTracker.trackAsync(userId)

            logger.info("Successfully fetched profile for user: {}", userId)
            GetCustomerProfileResult.Success(response)
        }!!
    }
}

/**
 * Result types for the GetCustomerProfile use case.
 */
sealed class GetCustomerProfileResult {
    /**
     * Profile retrieved successfully.
     */
    data class Success(val profile: CustomerResponse) : GetCustomerProfileResult()

    /**
     * Customer not found for the given user ID.
     */
    data class NotFound(val userId: UUID) : GetCustomerProfileResult()

    /**
     * Customer exists but preferences are missing (data integrity issue).
     */
    data class PreferencesNotFound(val customerId: UUID) : GetCustomerProfileResult()
}
