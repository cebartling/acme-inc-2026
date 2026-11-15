package com.acme.customer.api.v1

import com.acme.customer.api.v1.dto.PhoneRequest
import com.acme.customer.api.v1.dto.UpdateProfileRequest
import com.acme.customer.application.UpdateProfileResult
import com.acme.customer.application.UpdateProfileUseCase
import com.acme.customer.domain.Customer
import com.acme.customer.domain.CustomerPreferences
import com.acme.customer.domain.CustomerStatus
import com.acme.customer.domain.CustomerType
import com.acme.customer.domain.NotificationFrequency
import com.acme.customer.infrastructure.projection.CustomerReadModelQueries
import com.acme.customer.infrastructure.projection.CustomerSnapshot
import com.acme.customer.infrastructure.projection.SnapshotFixtures
import com.acme.customer.infrastructure.tracking.CustomerActivityTracker
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CustomerControllerTest {

    private lateinit var readModel: CustomerReadModelQueries
    private lateinit var updateProfileUseCase: UpdateProfileUseCase
    private lateinit var activityTracker: CustomerActivityTracker
    private lateinit var controller: CustomerController

    private val customerId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        readModel = mockk()
        updateProfileUseCase = mockk()
        activityTracker = mockk(relaxed = true)

        controller = CustomerController(
            readModel = readModel,
            updateProfileUseCase = updateProfileUseCase,
            activityTracker = activityTracker
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
            emailVerified = true,
            phoneVerified = true,
            registeredAt = now,
            lastActivityAt = now,
            profileCompleteness = 25
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

    private fun snapshot(): CustomerSnapshot = SnapshotFixtures.snapshot(createCustomer(), createPreferences())

    @Nested
    inner class GetCustomer {

        @Test
        fun `should return 400 for invalid customer ID format`() {
            val response = controller.getCustomer("invalid-uuid")

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        }

        @Test
        fun `should return 404 when customer not found`() {
            every { readModel.findById(customerId, null) } returns null

            val response = controller.getCustomer(customerId.toString())

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `should return 200 with customer on success`() {
            every { readModel.findById(customerId, null) } returns snapshot()

            val response = controller.getCustomer(customerId.toString())

            assertEquals(HttpStatus.OK, response.statusCode)
            assertNotNull(response.body)
            assertEquals(customerId.toString(), response.body!!.customerId)
            assertEquals("John", response.body!!.name.firstName)
        }

        @Test
        fun `should wait for the event the caller read after`() {
            val readAfter = UUID.randomUUID()
            every { readModel.findById(customerId, readAfter) } returns snapshot()

            val response = controller.getCustomer(customerId.toString(), readAfter)

            assertEquals(HttpStatus.OK, response.statusCode)
            verify(exactly = 1) { readModel.findById(customerId, readAfter) }
        }
    }

    @Nested
    inner class GetCustomerByUserId {

        @Test
        fun `should return 400 for invalid user ID format`() {
            val response = controller.getCustomerByUserId("invalid-uuid")

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        }

        @Test
        fun `should return 404 when customer not found for user`() {
            every { readModel.findByUserId(userId, null) } returns null

            val response = controller.getCustomerByUserId(userId.toString())

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `should return 200 with customer on success`() {
            every { readModel.findByUserId(userId, null) } returns snapshot()

            val response = controller.getCustomerByUserId(userId.toString())

            assertEquals(HttpStatus.OK, response.statusCode)
            assertNotNull(response.body)
            assertEquals(customerId.toString(), response.body!!.customerId)
        }
    }

    @Nested
    inner class GetCustomerByNumber {

        private val customerNumber = "ACME-202601-000001"

        @Test
        fun `should return 404 when customer not found`() {
            every { readModel.findByCustomerNumber(customerNumber, null) } returns null

            val response = controller.getCustomerByNumber(customerNumber)

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `should return 200 with customer on success`() {
            every { readModel.findByCustomerNumber(customerNumber, null) } returns snapshot()

            val response = controller.getCustomerByNumber(customerNumber)

            assertEquals(HttpStatus.OK, response.statusCode)
            assertNotNull(response.body)
            assertEquals(customerNumber, response.body!!.customerNumber)
        }
    }

    @Nested
    inner class GetCustomerByEmail {

        private val email = "test@example.com"

        @Test
        fun `should return 404 when customer not found`() {
            every { readModel.findByEmail(email, null) } returns null

            val response = controller.getCustomerByEmail(email)

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `should return 200 with customer on success`() {
            every { readModel.findByEmail(email, null) } returns snapshot()

            val response = controller.getCustomerByEmail(email)

            assertEquals(HttpStatus.OK, response.statusCode)
            assertNotNull(response.body)
            assertEquals(email, response.body!!.email.address)
        }
    }

    @Nested
    inner class UpdateProfile {

        private val request = UpdateProfileRequest(
            dateOfBirth = LocalDate.of(1990, 5, 15)
        )

        @Test
        fun `should return 400 for invalid customer ID format`() {
            val response = controller.updateProfile(
                id = "invalid-uuid",
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("Invalid customer ID format", body["error"])
        }

        @Test
        fun `should return 400 for invalid user ID format`() {
            val response = controller.updateProfile(
                id = customerId.toString(),
                userId = "invalid-uuid",
                correlationId = null,
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
                updateProfileUseCase.execute(customerId, userId, request, any())
            } returns UpdateProfileResult.NotFound(customerId)

            val response = controller.updateProfile(
                id = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `should return 403 when unauthorized`() {
            every {
                updateProfileUseCase.execute(customerId, userId, request, any())
            } returns UpdateProfileResult.Unauthorized(customerId, userId)

            val response = controller.updateProfile(
                id = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("You are not authorized to update this profile", body["error"])
        }

        @Test
        fun `should return 400 when no updates provided`() {
            every {
                updateProfileUseCase.execute(customerId, userId, request, any())
            } returns UpdateProfileResult.NoUpdates

            val response = controller.updateProfile(
                id = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("No updates provided", body["error"])
        }

        @Test
        fun `should return 400 when validation fails`() {
            val errors = mapOf("dateOfBirth" to "You must be at least 13 years old")
            every {
                updateProfileUseCase.execute(customerId, userId, request, any())
            } returns UpdateProfileResult.ValidationFailed(errors)

            val response = controller.updateProfile(
                id = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals(errors, body["errors"])
        }

        @Test
        fun `should return 500 on internal failure`() {
            every {
                updateProfileUseCase.execute(customerId, userId, request, any())
            } returns UpdateProfileResult.Failure("Database error", RuntimeException("Connection failed"))

            val response = controller.updateProfile(
                id = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("An internal error occurred", body["error"])
        }

        @Test
        fun `should return 200 with updated profile on success`() {
            val customer = createCustomer()
            every {
                updateProfileUseCase.execute(customerId, userId, request, any())
            } returns UpdateProfileResult.Success(customer, listOf("dateOfBirth"))

            val response = controller.updateProfile(
                id = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            assertNotNull(response.body)
        }

        @Test
        fun `should use provided correlation ID when valid`() {
            val correlationId = UUID.randomUUID()
            val customer = createCustomer()
            every {
                updateProfileUseCase.execute(customerId, userId, request, correlationId)
            } returns UpdateProfileResult.Success(customer, listOf("dateOfBirth"))

            controller.updateProfile(
                id = customerId.toString(),
                userId = userId.toString(),
                correlationId = correlationId.toString(),
                request = request
            )

            verify {
                updateProfileUseCase.execute(customerId, userId, request, correlationId)
            }
        }

        @Test
        fun `should generate correlation ID when invalid format provided`() {
            val customer = createCustomer()
            every {
                updateProfileUseCase.execute(customerId, userId, request, any())
            } returns UpdateProfileResult.Success(customer, listOf("dateOfBirth"))

            controller.updateProfile(
                id = customerId.toString(),
                userId = userId.toString(),
                correlationId = "invalid-uuid",
                request = request
            )

            verify {
                updateProfileUseCase.execute(customerId, userId, request, any())
            }
        }

        @Test
        fun `should update phone number successfully`() {
            val phoneRequest = UpdateProfileRequest(
                phone = PhoneRequest(countryCode = "+1", number = "5551234567")
            )
            val customer = createCustomer()
            every {
                updateProfileUseCase.execute(customerId, userId, phoneRequest, any())
            } returns UpdateProfileResult.Success(customer, listOf("phone"))

            val response = controller.updateProfile(
                id = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = phoneRequest
            )

            assertEquals(HttpStatus.OK, response.statusCode)
        }
    }

    @Nested
    inner class GetProfileCompleteness {

        @Test
        fun `should return 400 for invalid customer ID format`() {
            val response = controller.getProfileCompleteness("invalid-uuid", userId.toString())

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        }

        @Test
        fun `should return 404 when customer not found`() {
            every { readModel.findById(customerId, null) } returns null

            val response = controller.getProfileCompleteness(customerId.toString(), userId.toString())

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `should return 404 when the customer belongs to another user`() {
            every { readModel.findById(customerId, null) } returns snapshot()

            val response = controller.getProfileCompleteness(customerId.toString(), UUID.randomUUID().toString())

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `should return 200 with the completeness from the snapshot`() {
            every { readModel.findById(customerId, null) } returns snapshot()

            val response = controller.getProfileCompleteness(customerId.toString(), userId.toString())

            assertEquals(HttpStatus.OK, response.statusCode)
            assertNotNull(response.body)
            assertEquals(customerId.toString(), response.body!!.customerId)
            assertEquals(25, response.body!!.overallScore)
        }
    }

    @Nested
    inner class GetCurrentCustomer {

        private fun accessToken(): Jwt =
            Jwt.withTokenValue("token").header("alg", "RS256").subject(userId.toString()).build()

        @Test
        fun `should return 404 when customer not found, and not record activity`() {
            every { readModel.findByUserId(userId, null) } returns null

            val response = controller.getCurrentCustomer(accessToken())

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
            verify(exactly = 0) { activityTracker.trackAsync(any()) }
        }

        @Test
        fun `should return 200 with customer profile and record activity`() {
            every { readModel.findByUserId(userId, null) } returns snapshot()

            val response = controller.getCurrentCustomer(accessToken())

            assertEquals(HttpStatus.OK, response.statusCode)
            assertNotNull(response.body)
            assertEquals(customerId.toString(), response.body!!.customerId)
            assertEquals(userId.toString(), response.body!!.userId)
            assertEquals("John", response.body!!.name.firstName)
            assertEquals("Doe", response.body!!.name.lastName)
            assertEquals("test@example.com", response.body!!.email.address)
            verify(exactly = 1) { activityTracker.trackAsync(userId) }
        }

        @Test
        fun `should include preferences in response`() {
            every { readModel.findByUserId(userId, null) } returns snapshot()

            val response = controller.getCurrentCustomer(accessToken())

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(true, response.body!!.preferences.communication.email)
            assertEquals("en-US", response.body!!.preferences.display.language)
        }

        @Test
        fun `should include profile completeness in response`() {
            every { readModel.findByUserId(userId, null) } returns snapshot()

            val response = controller.getCurrentCustomer(accessToken())

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(25, response.body!!.profileCompleteness)
        }

        @Test
        fun `should wait for the event the caller read after`() {
            val readAfter = UUID.randomUUID()
            every { readModel.findByUserId(userId, readAfter) } returns snapshot()

            val response = controller.getCurrentCustomer(accessToken(), readAfter)

            assertEquals(HttpStatus.OK, response.statusCode)
            verify(exactly = 1) { readModel.findByUserId(userId, readAfter) }
        }
    }
}
