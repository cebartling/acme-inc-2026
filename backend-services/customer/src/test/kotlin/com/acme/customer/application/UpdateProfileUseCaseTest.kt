package com.acme.customer.application

import com.acme.customer.api.v1.dto.PhoneRequest
import com.acme.customer.api.v1.dto.UpdateProfileRequest
import com.acme.customer.domain.Customer
import com.acme.customer.domain.CustomerPreferences
import com.acme.customer.infrastructure.messaging.OutboxWriter
import com.acme.customer.infrastructure.persistence.CustomerPreferencesRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import com.acme.customer.infrastructure.persistence.EventStoreRepository
import com.acme.customer.infrastructure.projection.CustomerReadModelProjector
import com.acme.customer.infrastructure.validation.PhoneNumberValidator
import com.acme.customer.infrastructure.validation.PhoneValidationResult
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateProfileUseCaseTest {

    private lateinit var customerRepository: CustomerRepository
    private lateinit var customerPreferencesRepository: CustomerPreferencesRepository
    private lateinit var eventStoreRepository: EventStoreRepository
    private lateinit var customerReadModelProjector: CustomerReadModelProjector
    private lateinit var outboxWriter: OutboxWriter
    private lateinit var phoneNumberValidator: PhoneNumberValidator
    private lateinit var profileCompletionService: ProfileCompletionService
    private lateinit var customerCacheService: com.acme.customer.infrastructure.cache.CustomerCacheService
    private lateinit var useCase: UpdateProfileUseCase

    @BeforeEach
    fun setUp() {
        customerRepository = mockk()
        customerPreferencesRepository = mockk()
        eventStoreRepository = mockk()
        customerReadModelProjector = mockk()
        outboxWriter = mockk()
        phoneNumberValidator = mockk()
        profileCompletionService = mockk()
        customerCacheService = mockk()

        every { profileCompletionService.checkAndUpdateCompletion(any(), any(), any(), any()) } returns null
        every { customerCacheService.invalidate(any()) } just Runs

        useCase = UpdateProfileUseCase(
            customerRepository = customerRepository,
            customerPreferencesRepository = customerPreferencesRepository,
            eventStoreRepository = eventStoreRepository,
            customerReadModelProjector = customerReadModelProjector,
            outboxWriter = outboxWriter,
            phoneNumberValidator = phoneNumberValidator,
            profileCompletionService = profileCompletionService,
            customerCacheService = customerCacheService,
            meterRegistry = SimpleMeterRegistry()
        )
    }

    @Test
    fun `execute should update profile successfully with phone number`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createTestCustomer(customerId, userId)
        val preferences = CustomerPreferences.createDefault(customerId, false)

        val request = UpdateProfileRequest(
            phone = PhoneRequest(countryCode = "+1", number = "5551234567")
        )

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { phoneNumberValidator.validate("+1", "5551234567") } returns PhoneValidationResult.Valid(
            formattedNumber = "+15551234567",
            countryCode = "+1",
            nationalNumber = "5551234567"
        )
        every { eventStoreRepository.append(any()) } just Runs
        every { customerRepository.save(any()) } answers { firstArg() }
        every { customerPreferencesRepository.findById(customerId) } returns Optional.of(preferences)
        every { customerReadModelProjector.projectCustomer(any(), any()) } returns CompletableFuture.completedFuture(null)
        every { outboxWriter.write(any(), any()) } just Runs

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateProfileResult.Success)
        val success = result as UpdateProfileResult.Success
        assertEquals("+1", success.customer.phoneCountryCode)
        assertEquals("5551234567", success.customer.phoneNumber)
        assertEquals(listOf("phone"), success.changedFields)

        verify { eventStoreRepository.append(any()) }
        verify { customerRepository.save(any()) }
        verify { outboxWriter.write(any(), any()) }
    }

    @Test
    fun `execute should update profile successfully with date of birth`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createTestCustomer(customerId, userId)
        val preferences = CustomerPreferences.createDefault(customerId, false)
        val dateOfBirth = LocalDate.of(1990, 5, 15)

        val request = UpdateProfileRequest(dateOfBirth = dateOfBirth)

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { eventStoreRepository.append(any()) } just Runs
        every { customerRepository.save(any()) } answers { firstArg() }
        every { customerPreferencesRepository.findById(customerId) } returns Optional.of(preferences)
        every { customerReadModelProjector.projectCustomer(any(), any()) } returns CompletableFuture.completedFuture(null)
        every { outboxWriter.write(any(), any()) } just Runs

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateProfileResult.Success)
        val success = result as UpdateProfileResult.Success
        assertEquals(dateOfBirth, success.customer.dateOfBirth)
        assertEquals(listOf("dateOfBirth"), success.changedFields)
    }

    @Test
    fun `execute should update multiple fields at once`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createTestCustomer(customerId, userId)
        val preferences = CustomerPreferences.createDefault(customerId, false)

        val request = UpdateProfileRequest(
            gender = "FEMALE",
            preferredLocale = "es",
            timezone = "America/New_York"
        )

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { eventStoreRepository.append(any()) } just Runs
        every { customerRepository.save(any()) } answers { firstArg() }
        every { customerPreferencesRepository.findById(customerId) } returns Optional.of(preferences)
        every { customerReadModelProjector.projectCustomer(any(), any()) } returns CompletableFuture.completedFuture(null)
        every { outboxWriter.write(any(), any()) } just Runs

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateProfileResult.Success)
        val success = result as UpdateProfileResult.Success
        assertEquals("FEMALE", success.customer.gender)
        assertEquals("es", success.customer.preferredLocale)
        assertEquals("America/New_York", success.customer.timezone)
        assertEquals(3, success.changedFields.size)
        assertTrue(success.changedFields.containsAll(listOf("gender", "preferredLocale", "timezone")))
    }

    @Test
    fun `execute should return NotFound when customer does not exist`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val request = UpdateProfileRequest(gender = "MALE")

        every { customerRepository.findById(customerId) } returns Optional.empty()

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateProfileResult.NotFound)
        assertEquals(customerId, (result as UpdateProfileResult.NotFound).customerId)
    }

    @Test
    fun `execute should return Unauthorized when user does not own the profile`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createTestCustomer(customerId, otherUserId)
        val request = UpdateProfileRequest(gender = "MALE")

        every { customerRepository.findById(customerId) } returns Optional.of(customer)

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateProfileResult.Unauthorized)
        val unauthorized = result as UpdateProfileResult.Unauthorized
        assertEquals(customerId, unauthorized.customerId)
        assertEquals(userId, unauthorized.userId)
    }

    @Test
    fun `execute should return NoUpdates when request has no updates`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val request = UpdateProfileRequest() // Empty request

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateProfileResult.NoUpdates)
    }

    @Test
    fun `execute should return ValidationFailed for invalid phone number`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createTestCustomer(customerId, userId)

        val request = UpdateProfileRequest(
            phone = PhoneRequest(countryCode = "+1", number = "invalid")
        )

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { phoneNumberValidator.validate("+1", "invalid") } returns PhoneValidationResult.Invalid(
            message = "Invalid phone number format"
        )

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateProfileResult.ValidationFailed)
        val validationFailed = result as UpdateProfileResult.ValidationFailed
        assertTrue(validationFailed.errors.containsKey("phone"))
        assertEquals("Invalid phone number format", validationFailed.errors["phone"])
    }

    @Test
    fun `execute should return ValidationFailed when age is below minimum`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createTestCustomer(customerId, userId)

        // Under 13 years old
        val dateOfBirth = LocalDate.now().minusYears(10)
        val request = UpdateProfileRequest(dateOfBirth = dateOfBirth)

        every { customerRepository.findById(customerId) } returns Optional.of(customer)

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateProfileResult.ValidationFailed)
        val validationFailed = result as UpdateProfileResult.ValidationFailed
        assertTrue(validationFailed.errors.containsKey("dateOfBirth"))
        assertTrue(validationFailed.errors["dateOfBirth"]!!.contains("13 years old"))
    }

    @Test
    fun `execute should return ValidationFailed for invalid gender`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createTestCustomer(customerId, userId)

        val request = UpdateProfileRequest(gender = "INVALID_GENDER")

        every { customerRepository.findById(customerId) } returns Optional.of(customer)

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateProfileResult.ValidationFailed)
        val validationFailed = result as UpdateProfileResult.ValidationFailed
        assertTrue(validationFailed.errors.containsKey("gender"))
    }

    @Test
    fun `execute should return ValidationFailed for invalid timezone`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createTestCustomer(customerId, userId)

        val request = UpdateProfileRequest(timezone = "Invalid/Timezone")

        every { customerRepository.findById(customerId) } returns Optional.of(customer)

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateProfileResult.ValidationFailed)
        val validationFailed = result as UpdateProfileResult.ValidationFailed
        assertTrue(validationFailed.errors.containsKey("timezone"))
    }

    @Test
    fun `execute should return ValidationFailed for invalid locale format`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createTestCustomer(customerId, userId)

        val request = UpdateProfileRequest(preferredLocale = "invalid-locale-format")

        every { customerRepository.findById(customerId) } returns Optional.of(customer)

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateProfileResult.ValidationFailed)
        val validationFailed = result as UpdateProfileResult.ValidationFailed
        assertTrue(validationFailed.errors.containsKey("preferredLocale"))
    }

    @Test
    fun `execute should recalculate profile completeness after update`() {
        // Given
        val customerId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val correlationId = UUID.randomUUID()
        val customer = createTestCustomer(customerId, userId)
        val preferences = CustomerPreferences.createDefault(customerId, false)
        assertEquals(25, customer.profileCompleteness) // Initial completeness

        val request = UpdateProfileRequest(
            phone = PhoneRequest(countryCode = "+1", number = "5551234567"),
            dateOfBirth = LocalDate.of(1990, 5, 15),
            gender = "MALE"
        )

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { phoneNumberValidator.validate("+1", "5551234567") } returns PhoneValidationResult.Valid(
            formattedNumber = "+15551234567",
            countryCode = "+1",
            nationalNumber = "5551234567"
        )
        every { eventStoreRepository.append(any()) } just Runs
        every { customerRepository.save(any()) } answers { firstArg() }
        every { customerPreferencesRepository.findById(customerId) } returns Optional.of(preferences)
        every { customerReadModelProjector.projectCustomer(any(), any()) } returns CompletableFuture.completedFuture(null)
        every { outboxWriter.write(any(), any()) } just Runs

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdateProfileResult.Success)
        val success = result as UpdateProfileResult.Success
        // Base 25 + phone 15 + dateOfBirth 15 + gender 10 = 65
        assertEquals(65, success.customer.profileCompleteness)
    }

    private fun createTestCustomer(customerId: UUID, userId: UUID): Customer {
        return Customer.createFromRegistration(
            id = customerId,
            userId = userId,
            customerNumber = "ACME-202601-000001",
            email = "test@example.com",
            firstName = "Jane",
            lastName = "Doe",
            registeredAt = Instant.now()
        )
    }
}
