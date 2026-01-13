package com.acme.customer.api.v1

import com.acme.customer.api.v1.dto.CommunicationPreferencesRequest
import com.acme.customer.api.v1.dto.UpdatePreferencesRequest
import com.acme.customer.application.UpdatePreferencesResult
import com.acme.customer.application.UpdatePreferencesUseCase
import com.acme.customer.domain.Customer
import com.acme.customer.domain.CustomerPreferences
import com.acme.customer.domain.CustomerStatus
import com.acme.customer.domain.CustomerType
import com.acme.customer.domain.NotificationFrequency
import com.acme.customer.domain.PreferenceChange
import com.acme.customer.infrastructure.persistence.CustomerPreferencesRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PreferencesControllerTest {

    private lateinit var customerRepository: CustomerRepository
    private lateinit var preferencesRepository: CustomerPreferencesRepository
    private lateinit var updatePreferencesUseCase: UpdatePreferencesUseCase
    private lateinit var controller: PreferencesController

    private val customerId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        customerRepository = mockk()
        preferencesRepository = mockk()
        updatePreferencesUseCase = mockk()

        controller = PreferencesController(
            customerRepository = customerRepository,
            preferencesRepository = preferencesRepository,
            updatePreferencesUseCase = updatePreferencesUseCase
        )
    }

    private fun createCustomer(): Customer {
        val now = Instant.now()
        return Customer(
            id = customerId,
            userId = userId,
            customerNumber = "ACME-202601-000001",
            email = "test@example.com",
            firstName = "John",
            lastName = "Doe",
            displayName = "John Doe",
            status = CustomerStatus.ACTIVE,
            type = CustomerType.INDIVIDUAL,
            phoneVerified = true,
            registeredAt = now,
            lastActivityAt = now
        )
    }

    private fun createPreferences(): CustomerPreferences {
        return CustomerPreferences(
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
    }

    @Nested
    inner class GetPreferences {

        @Test
        fun `should return 400 for invalid customer ID format`() {
            val response = controller.getPreferences("invalid-uuid", userId.toString())

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("Invalid customer ID format", body["error"])
        }

        @Test
        fun `should return 400 for invalid user ID format`() {
            val response = controller.getPreferences(customerId.toString(), "invalid-uuid")

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("Invalid user ID format", body["error"])
        }

        @Test
        fun `should return 404 when customer not found`() {
            every { customerRepository.findById(customerId) } returns Optional.empty()

            val response = controller.getPreferences(customerId.toString(), userId.toString())

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `should return 403 when user is not authorized`() {
            val otherUserId = UUID.randomUUID()
            every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())

            val response = controller.getPreferences(customerId.toString(), otherUserId.toString())

            assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("You are not authorized to view these preferences", body["error"])
        }

        @Test
        fun `should return 500 when preferences not found`() {
            every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())
            every { preferencesRepository.findById(customerId) } returns Optional.empty()

            val response = controller.getPreferences(customerId.toString(), userId.toString())

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("Preferences not found", body["error"])
        }

        @Test
        fun `should return 200 with preferences on success`() {
            val preferences = createPreferences()
            every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())
            every { preferencesRepository.findById(customerId) } returns Optional.of(preferences)

            val response = controller.getPreferences(customerId.toString(), userId.toString())

            assertEquals(HttpStatus.OK, response.statusCode)
            assertNotNull(response.body)
        }
    }

    @Nested
    inner class UpdatePreferences {

        private val request = UpdatePreferencesRequest(
            communication = CommunicationPreferencesRequest(email = false)
        )

        @Test
        fun `should return 400 for invalid customer ID format`() {
            val response = controller.updatePreferences(
                id = "invalid-uuid",
                userId = userId.toString(),
                correlationId = null,
                clientIp = null,
                userAgent = null,
                request = request
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("Invalid customer ID format", body["error"])
        }

        @Test
        fun `should return 400 for invalid user ID format`() {
            val response = controller.updatePreferences(
                id = customerId.toString(),
                userId = "invalid-uuid",
                correlationId = null,
                clientIp = null,
                userAgent = null,
                request = request
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("Invalid user ID format", body["error"])
        }

        @Test
        fun `should return 404 when customer not found`() {
            every {
                updatePreferencesUseCase.execute(any(), any(), any(), any(), any())
            } returns UpdatePreferencesResult.NotFound(customerId)

            val response = controller.updatePreferences(
                id = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                clientIp = null,
                userAgent = null,
                request = request
            )

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `should return 403 when unauthorized`() {
            every {
                updatePreferencesUseCase.execute(any(), any(), any(), any(), any())
            } returns UpdatePreferencesResult.Unauthorized(customerId, userId)

            val response = controller.updatePreferences(
                id = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                clientIp = null,
                userAgent = null,
                request = request
            )

            assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("You are not authorized to update these preferences", body["error"])
        }

        @Test
        fun `should return 400 when no updates provided`() {
            every {
                updatePreferencesUseCase.execute(any(), any(), any(), any(), any())
            } returns UpdatePreferencesResult.NoUpdates

            val response = controller.updatePreferences(
                id = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                clientIp = null,
                userAgent = null,
                request = request
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("No updates provided", body["error"])
        }

        @Test
        fun `should return 400 when validation fails`() {
            val errors = listOf("Invalid frequency value")
            every {
                updatePreferencesUseCase.execute(any(), any(), any(), any(), any())
            } returns UpdatePreferencesResult.ValidationFailed(errors)

            val response = controller.updatePreferences(
                id = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                clientIp = null,
                userAgent = null,
                request = request
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals(errors, body["errors"])
        }

        @Test
        fun `should return 400 when phone not verified for SMS`() {
            every {
                updatePreferencesUseCase.execute(any(), any(), any(), any(), any())
            } returns UpdatePreferencesResult.PhoneNotVerified

            val response = controller.updatePreferences(
                id = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                clientIp = null,
                userAgent = null,
                request = request
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("PHONE_NOT_VERIFIED", body["error"])
            assertEquals("Phone number must be verified to enable SMS notifications", body["message"])
        }

        @Test
        fun `should return 400 when language is unsupported`() {
            every {
                updatePreferencesUseCase.execute(any(), any(), any(), any(), any())
            } returns UpdatePreferencesResult.UnsupportedLanguage("xx-XX")

            val response = controller.updatePreferences(
                id = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                clientIp = null,
                userAgent = null,
                request = request
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("UNSUPPORTED_LANGUAGE", body["error"])
            assertEquals("Unsupported language: xx-XX", body["message"])
        }

        @Test
        fun `should return 500 on internal failure`() {
            every {
                updatePreferencesUseCase.execute(any(), any(), any(), any(), any())
            } returns UpdatePreferencesResult.Failure("Database error", RuntimeException("Connection failed"))

            val response = controller.updatePreferences(
                id = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                clientIp = null,
                userAgent = null,
                request = request
            )

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("An internal error occurred", body["error"])
        }

        @Test
        fun `should return 200 with updated preferences on success`() {
            val updatedPreferences = createPreferences()
            val changedPreferences = mapOf(
                "communication.email" to PreferenceChange(oldValue = "true", newValue = "false")
            )
            every {
                updatePreferencesUseCase.execute(any(), any(), any(), any(), any())
            } returns UpdatePreferencesResult.Success(updatedPreferences, changedPreferences)

            val response = controller.updatePreferences(
                id = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                clientIp = null,
                userAgent = null,
                request = request
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            assertNotNull(response.body)
        }

        @Test
        fun `should use provided correlation ID when valid`() {
            val correlationId = UUID.randomUUID()
            val updatedPreferences = createPreferences()
            every {
                updatePreferencesUseCase.execute(any(), any(), any(), eq(correlationId), any())
            } returns UpdatePreferencesResult.Success(updatedPreferences, emptyMap())

            controller.updatePreferences(
                id = customerId.toString(),
                userId = userId.toString(),
                correlationId = correlationId.toString(),
                clientIp = null,
                userAgent = null,
                request = request
            )

            verify {
                updatePreferencesUseCase.execute(any(), any(), any(), correlationId, any())
            }
        }

        @Test
        fun `should generate correlation ID when not provided`() {
            val updatedPreferences = createPreferences()
            every {
                updatePreferencesUseCase.execute(any(), any(), any(), any(), any())
            } returns UpdatePreferencesResult.Success(updatedPreferences, emptyMap())

            controller.updatePreferences(
                id = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                clientIp = null,
                userAgent = null,
                request = request
            )

            verify {
                updatePreferencesUseCase.execute(any(), any(), any(), any(), any())
            }
        }

        @Test
        fun `should extract first IP from X-Forwarded-For header`() {
            val updatedPreferences = createPreferences()
            every {
                updatePreferencesUseCase.execute(any(), any(), any(), any(), match {
                    it.ipAddress == "192.168.1.1"
                })
            } returns UpdatePreferencesResult.Success(updatedPreferences, emptyMap())

            controller.updatePreferences(
                id = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                clientIp = "192.168.1.1, 10.0.0.1, 172.16.0.1",
                userAgent = null,
                request = request
            )

            verify {
                updatePreferencesUseCase.execute(any(), any(), any(), any(), match {
                    it.ipAddress == "192.168.1.1"
                })
            }
        }

        @Test
        fun `should pass user agent to use case`() {
            val updatedPreferences = createPreferences()
            val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
            every {
                updatePreferencesUseCase.execute(any(), any(), any(), any(), match {
                    it.userAgent == userAgent
                })
            } returns UpdatePreferencesResult.Success(updatedPreferences, emptyMap())

            controller.updatePreferences(
                id = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                clientIp = null,
                userAgent = userAgent,
                request = request
            )

            verify {
                updatePreferencesUseCase.execute(any(), any(), any(), any(), match {
                    it.userAgent == userAgent
                })
            }
        }
    }
}
