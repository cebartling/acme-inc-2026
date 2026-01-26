package com.acme.customer.application

import com.acme.customer.api.v1.dto.CustomerResponse
import com.acme.customer.domain.Customer
import com.acme.customer.domain.CustomerPreferences
import com.acme.customer.domain.CustomerStatus
import com.acme.customer.domain.CustomerType
import com.acme.customer.domain.NotificationFrequency
import com.acme.customer.infrastructure.cache.CustomerCacheService
import com.acme.customer.infrastructure.persistence.CustomerPreferencesRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import com.acme.customer.infrastructure.tracking.CustomerActivityTracker
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GetCustomerProfileUseCaseTest {

    private lateinit var customerRepository: CustomerRepository
    private lateinit var customerPreferencesRepository: CustomerPreferencesRepository
    private lateinit var customerCacheService: CustomerCacheService
    private lateinit var activityTracker: CustomerActivityTracker
    private lateinit var useCase: GetCustomerProfileUseCase

    private val userId = UUID.randomUUID()
    private val customerId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        customerRepository = mockk()
        customerPreferencesRepository = mockk()
        customerCacheService = mockk()
        activityTracker = mockk()

        useCase = GetCustomerProfileUseCase(
            customerRepository = customerRepository,
            customerPreferencesRepository = customerPreferencesRepository,
            customerCacheService = customerCacheService,
            activityTracker = activityTracker,
            meterRegistry = SimpleMeterRegistry()
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
            profileCompleteness = 75
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

    @Test
    fun `execute should return cached profile when available`() {
        // Given
        val customer = createCustomer()
        val preferences = createPreferences()
        val cachedResponse = CustomerResponse.fromDomain(customer, preferences)

        every { customerCacheService.get(userId) } returns cachedResponse
        every { activityTracker.trackAsync(userId) } just Runs

        // When
        val result = useCase.execute(userId)

        // Then
        assertTrue(result is GetCustomerProfileResult.Success)
        assertEquals(cachedResponse, (result as GetCustomerProfileResult.Success).profile)

        verify(exactly = 1) { customerCacheService.get(userId) }
        verify(exactly = 1) { activityTracker.trackAsync(userId) }
        verify(exactly = 0) { customerRepository.findByUserId(any()) }
    }

    @Test
    fun `execute should query database on cache miss`() {
        // Given
        val customer = createCustomer()
        val preferences = createPreferences()

        every { customerCacheService.get(userId) } returns null
        every { customerRepository.findByUserId(userId) } returns customer
        every { customerPreferencesRepository.findById(customerId) } returns Optional.of(preferences)
        every { customerCacheService.put(userId, any()) } just Runs
        every { activityTracker.trackAsync(userId) } just Runs

        // When
        val result = useCase.execute(userId)

        // Then
        assertTrue(result is GetCustomerProfileResult.Success)
        val success = result as GetCustomerProfileResult.Success
        assertEquals(customerId.toString(), success.profile.customerId)
        assertEquals("John Doe", success.profile.name.displayName)
        assertEquals("ACME-202601-000001", success.profile.customerNumber)

        verify(exactly = 1) { customerRepository.findByUserId(userId) }
        verify(exactly = 1) { customerPreferencesRepository.findById(customerId) }
        verify(exactly = 1) { customerCacheService.put(userId, any()) }
        verify(exactly = 1) { activityTracker.trackAsync(userId) }
    }

    @Test
    fun `execute should return NotFound when customer does not exist`() {
        // Given
        every { customerCacheService.get(userId) } returns null
        every { customerRepository.findByUserId(userId) } returns null

        // When
        val result = useCase.execute(userId)

        // Then
        assertTrue(result is GetCustomerProfileResult.NotFound)
        assertEquals(userId, (result as GetCustomerProfileResult.NotFound).userId)

        verify(exactly = 0) { customerCacheService.put(any(), any()) }
        verify(exactly = 0) { activityTracker.trackAsync(any()) }
    }

    @Test
    fun `execute should return PreferencesNotFound when preferences do not exist`() {
        // Given
        val customer = createCustomer()

        every { customerCacheService.get(userId) } returns null
        every { customerRepository.findByUserId(userId) } returns customer
        every { customerPreferencesRepository.findById(customerId) } returns Optional.empty()

        // When
        val result = useCase.execute(userId)

        // Then
        assertTrue(result is GetCustomerProfileResult.PreferencesNotFound)
        assertEquals(customerId, (result as GetCustomerProfileResult.PreferencesNotFound).customerId)

        verify(exactly = 0) { customerCacheService.put(any(), any()) }
        verify(exactly = 0) { activityTracker.trackAsync(any()) }
    }

    @Test
    fun `execute should cache profile after database query`() {
        // Given
        val customer = createCustomer()
        val preferences = createPreferences()
        val capturedProfile = slot<CustomerResponse>()

        every { customerCacheService.get(userId) } returns null
        every { customerRepository.findByUserId(userId) } returns customer
        every { customerPreferencesRepository.findById(customerId) } returns Optional.of(preferences)
        every { customerCacheService.put(userId, capture(capturedProfile)) } just Runs
        every { activityTracker.trackAsync(userId) } just Runs

        // When
        val result = useCase.execute(userId)

        // Then
        assertTrue(result is GetCustomerProfileResult.Success)

        verify(exactly = 1) { customerCacheService.put(userId, any()) }
        assertNotNull(capturedProfile.captured)
        assertEquals(customerId.toString(), capturedProfile.captured.customerId)
    }

    @Test
    fun `execute should track activity asynchronously for cache hit`() {
        // Given
        val customer = createCustomer()
        val preferences = createPreferences()
        val cachedResponse = CustomerResponse.fromDomain(customer, preferences)

        every { customerCacheService.get(userId) } returns cachedResponse
        every { activityTracker.trackAsync(userId) } just Runs

        // When
        useCase.execute(userId)

        // Then
        verify(exactly = 1) { activityTracker.trackAsync(userId) }
    }

    @Test
    fun `execute should track activity asynchronously for cache miss`() {
        // Given
        val customer = createCustomer()
        val preferences = createPreferences()

        every { customerCacheService.get(userId) } returns null
        every { customerRepository.findByUserId(userId) } returns customer
        every { customerPreferencesRepository.findById(customerId) } returns Optional.of(preferences)
        every { customerCacheService.put(userId, any()) } just Runs
        every { activityTracker.trackAsync(userId) } just Runs

        // When
        useCase.execute(userId)

        // Then
        verify(exactly = 1) { activityTracker.trackAsync(userId) }
    }

    @Test
    fun `execute should include all customer fields in response`() {
        // Given
        val customer = createCustomer().apply {
            phoneCountryCode = "+1"
            phoneNumber = "5551234567"
            phoneVerified = true
            dateOfBirth = java.time.LocalDate.of(1990, 5, 15)
            gender = "male"
            preferredLocale = "en-US"
            timezone = "America/New_York"
            preferredCurrency = "USD"
        }
        val preferences = createPreferences()

        every { customerCacheService.get(userId) } returns null
        every { customerRepository.findByUserId(userId) } returns customer
        every { customerPreferencesRepository.findById(customerId) } returns Optional.of(preferences)
        every { customerCacheService.put(userId, any()) } just Runs
        every { activityTracker.trackAsync(userId) } just Runs

        // When
        val result = useCase.execute(userId)

        // Then
        assertTrue(result is GetCustomerProfileResult.Success)
        val profile = (result as GetCustomerProfileResult.Success).profile

        assertEquals("John", profile.name.firstName)
        assertEquals("Doe", profile.name.lastName)
        assertEquals("John Doe", profile.name.displayName)
        assertEquals("test@example.com", profile.email.address)
        assertEquals(true, profile.email.verified)
        assertNotNull(profile.phone)
        assertEquals("+1", profile.phone?.countryCode)
        assertEquals("5551234567", profile.phone?.number)
        assertEquals(true, profile.phone?.verified)
        assertEquals(java.time.LocalDate.of(1990, 5, 15), profile.profile.dateOfBirth)
        assertEquals("male", profile.profile.gender)
        assertEquals("en-US", profile.profile.preferredLocale)
        assertEquals("America/New_York", profile.profile.timezone)
        assertEquals("USD", profile.profile.preferredCurrency)
        assertEquals(75, profile.profileCompleteness)
    }

    @Test
    fun `execute should include preferences in response`() {
        // Given
        val customer = createCustomer()
        val preferences = createPreferences().apply {
            emailNotifications = true
            smsNotifications = true
            pushNotifications = true
            marketingCommunications = true
            notificationFrequency = NotificationFrequency.DAILY_DIGEST
            shareDataWithPartners = true
            allowAnalytics = false
            allowPersonalization = false
            language = "es-ES"
            currency = "EUR"
            timezone = "Europe/Madrid"
        }

        every { customerCacheService.get(userId) } returns null
        every { customerRepository.findByUserId(userId) } returns customer
        every { customerPreferencesRepository.findById(customerId) } returns Optional.of(preferences)
        every { customerCacheService.put(userId, any()) } just Runs
        every { activityTracker.trackAsync(userId) } just Runs

        // When
        val result = useCase.execute(userId)

        // Then
        assertTrue(result is GetCustomerProfileResult.Success)
        val profile = (result as GetCustomerProfileResult.Success).profile

        assertEquals(true, profile.preferences.communication.email)
        assertEquals(true, profile.preferences.communication.sms)
        assertEquals(true, profile.preferences.communication.push)
        assertEquals(true, profile.preferences.communication.marketing)
        assertEquals("DAILY_DIGEST", profile.preferences.communication.frequency)
        assertEquals(true, profile.preferences.privacy.shareDataWithPartners)
        assertEquals(false, profile.preferences.privacy.allowAnalytics)
        assertEquals(false, profile.preferences.privacy.allowPersonalization)
        assertEquals("es-ES", profile.preferences.display.language)
        assertEquals("EUR", profile.preferences.display.currency)
        assertEquals("Europe/Madrid", profile.preferences.display.timezone)
    }
}
