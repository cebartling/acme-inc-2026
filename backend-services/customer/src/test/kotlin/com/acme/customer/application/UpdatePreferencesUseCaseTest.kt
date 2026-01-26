package com.acme.customer.application

import com.acme.customer.api.v1.dto.CommunicationPreferencesRequest
import com.acme.customer.api.v1.dto.DisplayPreferencesRequest
import com.acme.customer.api.v1.dto.PrivacyPreferencesRequest
import com.acme.customer.api.v1.dto.UpdatePreferencesRequest
import com.acme.customer.domain.Customer
import com.acme.customer.domain.CustomerPreferences
import com.acme.customer.domain.CustomerStatus
import com.acme.customer.domain.CustomerType
import com.acme.customer.domain.NotificationFrequency
import com.acme.customer.infrastructure.messaging.CustomerEventPublisher
import com.acme.customer.infrastructure.persistence.CustomerPreferencesRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import com.acme.customer.infrastructure.persistence.PreferenceChangeLogRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdatePreferencesUseCaseTest {

    private lateinit var customerRepository: CustomerRepository
    private lateinit var preferencesRepository: CustomerPreferencesRepository
    private lateinit var changeLogRepository: PreferenceChangeLogRepository
    private lateinit var eventPublisher: CustomerEventPublisher
    private lateinit var customerCacheService: com.acme.customer.infrastructure.cache.CustomerCacheService
    private lateinit var useCase: UpdatePreferencesUseCase

    private val customerId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val correlationId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        customerRepository = mockk()
        preferencesRepository = mockk()
        changeLogRepository = mockk()
        eventPublisher = mockk()
        customerCacheService = mockk()

        useCase = UpdatePreferencesUseCase(
            customerRepository = customerRepository,
            preferencesRepository = preferencesRepository,
            changeLogRepository = changeLogRepository,
            eventPublisher = eventPublisher,
            customerCacheService = customerCacheService,
            meterRegistry = SimpleMeterRegistry()
        )
        // Initialize metrics (normally done by @PostConstruct)
        useCase.initMetrics()
    }

    private fun createCustomer(phoneVerified: Boolean = true): Customer {
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
            phoneVerified = phoneVerified,
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

    @Test
    fun `execute should return NoUpdates when request has no updates`() {
        // Given
        val request = UpdatePreferencesRequest()

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdatePreferencesResult.NoUpdates)
    }

    @Test
    fun `execute should return NotFound when customer does not exist`() {
        // Given
        val request = UpdatePreferencesRequest(
            communication = CommunicationPreferencesRequest(email = false)
        )
        every { customerRepository.findById(customerId) } returns Optional.empty()

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdatePreferencesResult.NotFound)
        assertEquals(customerId, (result as UpdatePreferencesResult.NotFound).customerId)
    }

    @Test
    fun `execute should return Unauthorized when user does not own customer`() {
        // Given
        val otherUserId = UUID.randomUUID()
        val request = UpdatePreferencesRequest(
            communication = CommunicationPreferencesRequest(email = false)
        )
        every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())

        // When
        val result = useCase.execute(customerId, otherUserId, request, correlationId)

        // Then
        assertTrue(result is UpdatePreferencesResult.Unauthorized)
    }

    @Test
    fun `execute should return PhoneNotVerified when enabling SMS without verified phone`() {
        // Given
        val customer = createCustomer(phoneVerified = false)
        val preferences = createPreferences()
        val request = UpdatePreferencesRequest(
            communication = CommunicationPreferencesRequest(sms = true)
        )

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { preferencesRepository.findById(customerId) } returns Optional.of(preferences)

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdatePreferencesResult.PhoneNotVerified)
    }

    @Test
    fun `execute should return UnsupportedLanguage for invalid language`() {
        // Given
        val customer = createCustomer()
        val preferences = createPreferences()
        val request = UpdatePreferencesRequest(
            display = DisplayPreferencesRequest(language = "xx-XX")
        )

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { preferencesRepository.findById(customerId) } returns Optional.of(preferences)

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdatePreferencesResult.UnsupportedLanguage)
        assertEquals("xx-XX", (result as UpdatePreferencesResult.UnsupportedLanguage).locale)
    }

    @Test
    fun `execute should return ValidationFailed for invalid frequency`() {
        // Given
        val customer = createCustomer()
        val preferences = createPreferences()
        val request = UpdatePreferencesRequest(
            communication = CommunicationPreferencesRequest(frequency = "INVALID")
        )

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { preferencesRepository.findById(customerId) } returns Optional.of(preferences)

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdatePreferencesResult.ValidationFailed)
    }

    @Test
    fun `execute should successfully update email preference`() {
        // Given
        val customer = createCustomer()
        val preferences = createPreferences()
        val request = UpdatePreferencesRequest(
            communication = CommunicationPreferencesRequest(email = false)
        )

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { preferencesRepository.findById(customerId) } returns Optional.of(preferences)
        every { changeLogRepository.save(any()) } answers { firstArg() }
        every { preferencesRepository.save(any()) } answers { firstArg() }
        every { customerCacheService.invalidate(userId) } just Runs
        every { eventPublisher.publish(any<com.acme.customer.domain.events.PreferencesUpdated>()) } just Runs

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdatePreferencesResult.Success)
        val success = result as UpdatePreferencesResult.Success
        assertEquals(false, success.preferences.emailNotifications)
        assertTrue(success.changedPreferences.containsKey("communication.email"))

        verify { changeLogRepository.save(any()) }
        verify { customerCacheService.invalidate(userId) }
        verify { eventPublisher.publish(any<com.acme.customer.domain.events.PreferencesUpdated>()) }
    }

    @Test
    fun `execute should successfully update multiple preferences`() {
        // Given
        val customer = createCustomer(phoneVerified = true)
        val preferences = createPreferences()
        val request = UpdatePreferencesRequest(
            communication = CommunicationPreferencesRequest(
                sms = true,
                marketing = true,
                frequency = "DAILY_DIGEST"
            ),
            privacy = PrivacyPreferencesRequest(
                shareDataWithPartners = true
            ),
            display = DisplayPreferencesRequest(
                language = "es-ES",
                currency = "EUR",
                timezone = "Europe/Madrid"
            )
        )

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { preferencesRepository.findById(customerId) } returns Optional.of(preferences)
        every { changeLogRepository.save(any()) } answers { firstArg() }
        every { preferencesRepository.save(any()) } answers { firstArg() }
        every { customerCacheService.invalidate(userId) } just Runs
        every { eventPublisher.publish(any<com.acme.customer.domain.events.PreferencesUpdated>()) } just Runs

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdatePreferencesResult.Success)
        val success = result as UpdatePreferencesResult.Success

        assertEquals(true, success.preferences.smsNotifications)
        assertEquals(true, success.preferences.marketingCommunications)
        assertEquals(NotificationFrequency.DAILY_DIGEST, success.preferences.notificationFrequency)
        assertEquals(true, success.preferences.shareDataWithPartners)
        assertEquals("es-ES", success.preferences.language)
        assertEquals("EUR", success.preferences.currency)
        assertEquals("Europe/Madrid", success.preferences.timezone)

        // Verify all changes are logged
        assertEquals(7, success.changedPreferences.size)
        verify(exactly = 7) { changeLogRepository.save(any()) }
        verify { customerCacheService.invalidate(userId) }
    }

    @Test
    fun `execute should return NoUpdates when values are same as current`() {
        // Given
        val customer = createCustomer()
        val preferences = createPreferences()
        val request = UpdatePreferencesRequest(
            communication = CommunicationPreferencesRequest(
                email = true // Same as current
            )
        )

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { preferencesRepository.findById(customerId) } returns Optional.of(preferences)

        // When
        val result = useCase.execute(customerId, userId, request, correlationId)

        // Then
        assertTrue(result is UpdatePreferencesResult.NoUpdates)
    }

    @Test
    fun `execute should include IP and user agent in change log`() {
        // Given
        val customer = createCustomer()
        val preferences = createPreferences()
        val request = UpdatePreferencesRequest(
            communication = CommunicationPreferencesRequest(email = false)
        )
        val context = PreferenceUpdateContext(
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0"
        )
        val capturedLog = slot<com.acme.customer.domain.PreferenceChangeLog>()

        every { customerRepository.findById(customerId) } returns Optional.of(customer)
        every { preferencesRepository.findById(customerId) } returns Optional.of(preferences)
        every { changeLogRepository.save(capture(capturedLog)) } answers { firstArg() }
        every { preferencesRepository.save(any()) } answers { firstArg() }
        every { customerCacheService.invalidate(userId) } just Runs
        every { eventPublisher.publish(any<com.acme.customer.domain.events.PreferencesUpdated>()) } just Runs

        // When
        val result = useCase.execute(customerId, userId, request, correlationId, context)

        // Then
        assertTrue(result is UpdatePreferencesResult.Success)
        assertEquals("192.168.1.1", capturedLog.captured.ipAddress)
        assertEquals("Mozilla/5.0", capturedLog.captured.userAgent)
    }
}
