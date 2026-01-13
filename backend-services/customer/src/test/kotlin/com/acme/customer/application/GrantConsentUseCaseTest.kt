package com.acme.customer.application

import com.acme.customer.domain.ConsentRecord
import com.acme.customer.domain.ConsentSource
import com.acme.customer.domain.ConsentType
import com.acme.customer.domain.Customer
import com.acme.customer.domain.CustomerStatus
import com.acme.customer.domain.CustomerType
import com.acme.customer.domain.events.ConsentGranted
import com.acme.customer.domain.events.ConsentRevoked
import com.acme.customer.infrastructure.messaging.CustomerEventPublisher
import com.acme.customer.infrastructure.persistence.ConsentRecordRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GrantConsentUseCaseTest {

    private lateinit var customerRepository: CustomerRepository
    private lateinit var consentRecordRepository: ConsentRecordRepository
    private lateinit var eventPublisher: CustomerEventPublisher
    private lateinit var useCase: GrantConsentUseCase

    private val customerId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val correlationId = UUID.randomUUID()
    private val context = ConsentUpdateContext(ipAddress = "192.168.1.1", userAgent = "Mozilla/5.0")

    @BeforeEach
    fun setUp() {
        customerRepository = mockk()
        consentRecordRepository = mockk()
        eventPublisher = mockk()

        useCase = GrantConsentUseCase(
            customerRepository = customerRepository,
            consentRecordRepository = consentRecordRepository,
            eventPublisher = eventPublisher,
            meterRegistry = SimpleMeterRegistry()
        )
        useCase.initMetrics()
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
            registeredAt = now,
            lastActivityAt = now
        )
    }

    @Test
    fun `execute should return InvalidConsentType for invalid type`() {
        // When
        val result = useCase.execute(
            customerId = customerId,
            userId = userId,
            consentTypeString = "INVALID_TYPE",
            granted = true,
            sourceString = "PROFILE_WIZARD",
            correlationId = correlationId,
            context = context
        )

        // Then
        assertTrue(result is GrantConsentResult.InvalidConsentType)
        assertEquals("INVALID_TYPE", (result as GrantConsentResult.InvalidConsentType).consentType)
    }

    @Test
    fun `execute should return InvalidConsentSource for invalid source`() {
        // When
        val result = useCase.execute(
            customerId = customerId,
            userId = userId,
            consentTypeString = "MARKETING",
            granted = true,
            sourceString = "INVALID_SOURCE",
            correlationId = correlationId,
            context = context
        )

        // Then
        assertTrue(result is GrantConsentResult.InvalidConsentSource)
        assertEquals("INVALID_SOURCE", (result as GrantConsentResult.InvalidConsentSource).source)
    }

    @Test
    fun `execute should return RequiredConsentCannotBeRevoked when revoking DATA_PROCESSING`() {
        // When
        val result = useCase.execute(
            customerId = customerId,
            userId = userId,
            consentTypeString = "DATA_PROCESSING",
            granted = false,
            sourceString = "PRIVACY_SETTINGS",
            correlationId = correlationId,
            context = context
        )

        // Then
        assertTrue(result is GrantConsentResult.RequiredConsentCannotBeRevoked)
        assertEquals(ConsentType.DATA_PROCESSING, (result as GrantConsentResult.RequiredConsentCannotBeRevoked).consentType)
    }

    @Test
    fun `execute should return CustomerNotFound when customer does not exist`() {
        // Given
        every { customerRepository.findById(customerId) } returns Optional.empty()

        // When
        val result = useCase.execute(
            customerId = customerId,
            userId = userId,
            consentTypeString = "MARKETING",
            granted = true,
            sourceString = "PROFILE_WIZARD",
            correlationId = correlationId,
            context = context
        )

        // Then
        assertTrue(result is GrantConsentResult.CustomerNotFound)
        assertEquals(customerId, (result as GrantConsentResult.CustomerNotFound).customerId)
    }

    @Test
    fun `execute should return Unauthorized when user does not own customer`() {
        // Given
        val otherUserId = UUID.randomUUID()
        every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())

        // When
        val result = useCase.execute(
            customerId = customerId,
            userId = otherUserId,
            consentTypeString = "MARKETING",
            granted = true,
            sourceString = "PROFILE_WIZARD",
            correlationId = correlationId,
            context = context
        )

        // Then
        assertTrue(result is GrantConsentResult.Unauthorized)
    }

    @Test
    fun `execute should return NoChange when consent already in requested state`() {
        // Given
        val existingRecord = ConsentRecord.create(
            id = UUID.randomUUID(),
            customerId = customerId,
            consentType = ConsentType.MARKETING,
            granted = true,
            source = ConsentSource.PROFILE_WIZARD,
            ipAddress = "192.168.1.1",
            version = 1
        )

        every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())
        every { consentRecordRepository.findLatestByCustomerIdAndConsentType(customerId, ConsentType.MARKETING) } returns existingRecord

        // When
        val result = useCase.execute(
            customerId = customerId,
            userId = userId,
            consentTypeString = "MARKETING",
            granted = true,
            sourceString = "PROFILE_WIZARD",
            correlationId = correlationId,
            context = context
        )

        // Then
        assertTrue(result is GrantConsentResult.NoChange)
        assertEquals(ConsentType.MARKETING, (result as GrantConsentResult.NoChange).consentType)
        assertTrue(result.currentStatus)
    }

    @Test
    fun `execute should successfully grant new consent`() {
        // Given
        every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())
        every { consentRecordRepository.findLatestByCustomerIdAndConsentType(customerId, ConsentType.MARKETING) } returns null
        every { consentRecordRepository.save(any()) } answers { firstArg() }
        every { eventPublisher.publish(any<ConsentGranted>()) } just Runs

        // When
        val result = useCase.execute(
            customerId = customerId,
            userId = userId,
            consentTypeString = "MARKETING",
            granted = true,
            sourceString = "PROFILE_WIZARD",
            correlationId = correlationId,
            context = context
        )

        // Then
        assertTrue(result is GrantConsentResult.Success)
        val success = result as GrantConsentResult.Success
        assertEquals(ConsentType.MARKETING, success.consentRecord.consentType)
        assertTrue(success.consentRecord.granted)
        assertEquals(1, success.consentRecord.version)
        assertNotNull(success.consentRecord.expiresAt)

        verify { consentRecordRepository.save(any()) }
        verify { eventPublisher.publish(any<ConsentGranted>()) }
    }

    @Test
    fun `execute should successfully revoke existing consent`() {
        // Given
        val existingRecord = ConsentRecord.create(
            id = UUID.randomUUID(),
            customerId = customerId,
            consentType = ConsentType.MARKETING,
            granted = true,
            source = ConsentSource.PROFILE_WIZARD,
            ipAddress = "192.168.1.1",
            version = 1
        )

        every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())
        every { consentRecordRepository.findLatestByCustomerIdAndConsentType(customerId, ConsentType.MARKETING) } returns existingRecord
        every { consentRecordRepository.save(any()) } answers { firstArg() }
        every { eventPublisher.publish(any<ConsentRevoked>()) } just Runs

        // When
        val result = useCase.execute(
            customerId = customerId,
            userId = userId,
            consentTypeString = "MARKETING",
            granted = false,
            sourceString = "PRIVACY_SETTINGS",
            correlationId = correlationId,
            context = context
        )

        // Then
        assertTrue(result is GrantConsentResult.Success)
        val success = result as GrantConsentResult.Success
        assertEquals(ConsentType.MARKETING, success.consentRecord.consentType)
        assertEquals(false, success.consentRecord.granted)
        assertEquals(2, success.consentRecord.version)

        verify { consentRecordRepository.save(any()) }
        verify { eventPublisher.publish(any<ConsentRevoked>()) }
    }

    @Test
    fun `execute should increment version when updating consent`() {
        // Given
        val existingRecord = ConsentRecord.create(
            id = UUID.randomUUID(),
            customerId = customerId,
            consentType = ConsentType.ANALYTICS,
            granted = false,
            source = ConsentSource.REGISTRATION,
            ipAddress = "192.168.1.1",
            version = 3
        )
        val savedRecord = slot<ConsentRecord>()

        every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())
        every { consentRecordRepository.findLatestByCustomerIdAndConsentType(customerId, ConsentType.ANALYTICS) } returns existingRecord
        every { consentRecordRepository.save(capture(savedRecord)) } answers { firstArg() }
        every { eventPublisher.publish(any<ConsentGranted>()) } just Runs

        // When
        val result = useCase.execute(
            customerId = customerId,
            userId = userId,
            consentTypeString = "ANALYTICS",
            granted = true,
            sourceString = "API",
            correlationId = correlationId,
            context = context
        )

        // Then
        assertTrue(result is GrantConsentResult.Success)
        assertEquals(4, savedRecord.captured.version)
    }

    @Test
    fun `execute should capture IP address and user agent`() {
        // Given
        val savedRecord = slot<ConsentRecord>()

        every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())
        every { consentRecordRepository.findLatestByCustomerIdAndConsentType(customerId, ConsentType.PERSONALIZATION) } returns null
        every { consentRecordRepository.save(capture(savedRecord)) } answers { firstArg() }
        every { eventPublisher.publish(any<ConsentGranted>()) } just Runs

        // When
        val result = useCase.execute(
            customerId = customerId,
            userId = userId,
            consentTypeString = "PERSONALIZATION",
            granted = true,
            sourceString = "PROFILE_WIZARD",
            correlationId = correlationId,
            context = context
        )

        // Then
        assertTrue(result is GrantConsentResult.Success)
        assertEquals("192.168.1.1", savedRecord.captured.ipAddress)
        assertEquals("Mozilla/5.0", savedRecord.captured.userAgent)
    }
}
