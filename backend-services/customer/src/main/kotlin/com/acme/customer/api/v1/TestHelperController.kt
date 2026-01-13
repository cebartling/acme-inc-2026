package com.acme.customer.api.v1

import com.acme.customer.domain.Customer
import com.acme.customer.domain.CustomerPreferences
import com.acme.customer.domain.CustomerStatus
import com.acme.customer.domain.CustomerType
import com.acme.customer.domain.NotificationFrequency
import com.acme.customer.infrastructure.persistence.CustomerPreferencesRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/**
 * Test helper controller for acceptance tests.
 *
 * This controller is only available in the "test" profile and provides
 * endpoints for setting up test data that would otherwise require complex
 * flows (like phone verification).
 *
 * SECURITY: This controller MUST NOT be enabled in production.
 */
@RestController
@RequestMapping("/api/v1/test")
@Profile("test")
class TestHelperController(
    private val customerRepository: CustomerRepository,
    private val preferencesRepository: CustomerPreferencesRepository
) {
    private val logger = LoggerFactory.getLogger(TestHelperController::class.java)

    /**
     * Creates a test customer with specified properties.
     *
     * @param request The test customer creation request.
     * @return The created customer ID.
     */
    @PostMapping("/customers")
    fun createTestCustomer(@RequestBody request: CreateTestCustomerRequest): ResponseEntity<TestCustomerResponse> {
        logger.info("Creating test customer: {}", request.customerId)

        val customerId = UUID.fromString(request.customerId)
        val userId = UUID.fromString(request.userId)
        val now = Instant.now()

        // Check if customer already exists
        if (customerRepository.existsById(customerId)) {
            logger.info("Test customer already exists: {}", customerId)
            return ResponseEntity.ok(TestCustomerResponse(customerId.toString(), "already_exists"))
        }

        // Generate a short customer number that fits in 20 chars (DB constraint)
        // Format: T-{6 hex chars from customer ID} = 8 chars total
        val shortId = customerId.toString().replace("-", "").take(12)
        val customer = Customer(
            id = customerId,
            userId = userId,
            customerNumber = "T-$shortId",
            email = request.email ?: "test-${customerId}@example.com",
            firstName = request.firstName ?: "Test",
            lastName = request.lastName ?: "Customer",
            displayName = request.displayName ?: "Test Customer",
            status = CustomerStatus.ACTIVE,
            type = CustomerType.INDIVIDUAL,
            phoneNumber = request.phoneNumber,
            phoneCountryCode = request.phoneCountryCode,
            phoneVerified = request.phoneVerified ?: false,
            emailVerified = request.emailVerified ?: true,
            registeredAt = now,
            lastActivityAt = now
        )

        customerRepository.save(customer)

        // Create default preferences
        val preferences = CustomerPreferences(
            customerId = customerId,
            emailNotifications = true,
            smsNotifications = false,
            pushNotifications = false,
            marketingCommunications = false,
            notificationFrequency = NotificationFrequency.IMMEDIATE,
            shareDataWithPartners = false,
            allowAnalytics = true,
            allowPersonalization = true,
            language = "en-US",
            currency = "USD",
            timezone = "UTC"
        )

        preferencesRepository.save(preferences)

        logger.info("Created test customer: {}", customerId)
        return ResponseEntity.ok(TestCustomerResponse(customerId.toString(), "created"))
    }

    /**
     * Updates a test customer's phone verification status.
     *
     * @param customerId The customer ID.
     * @param request The phone verification update request.
     * @return Success or error response.
     */
    @PutMapping("/customers/{customerId}/phone-verification")
    fun updatePhoneVerification(
        @PathVariable customerId: String,
        @RequestBody request: UpdatePhoneVerificationRequest
    ): ResponseEntity<Any> {
        logger.info("Updating phone verification for customer: {} to {}", customerId, request.verified)

        val parsedCustomerId = try {
            UUID.fromString(customerId)
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest()
                .body(mapOf("error" to "Invalid customer ID format"))
        }

        val customer = customerRepository.findById(parsedCustomerId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        // Update phone verification status directly on the entity
        customer.phoneVerified = request.verified
        if (request.phoneNumber != null) {
            customer.phoneNumber = request.phoneNumber
        }
        if (request.phoneCountryCode != null) {
            customer.phoneCountryCode = request.phoneCountryCode
        }

        customerRepository.save(customer)

        logger.info("Updated phone verification for customer: {}", customerId)
        return ResponseEntity.ok(mapOf("status" to "updated", "phoneVerified" to request.verified))
    }
}

/**
 * Request DTO for creating a test customer.
 */
data class CreateTestCustomerRequest(
    val customerId: String,
    val userId: String,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val displayName: String? = null,
    val phoneNumber: String? = null,
    val phoneCountryCode: String? = null,
    val phoneVerified: Boolean? = null,
    val emailVerified: Boolean? = null
)

/**
 * Response DTO for test customer creation.
 */
data class TestCustomerResponse(
    val customerId: String,
    val status: String
)

/**
 * Request DTO for updating phone verification status.
 */
data class UpdatePhoneVerificationRequest(
    val verified: Boolean,
    val phoneNumber: String? = null,
    val phoneCountryCode: String? = null
)
