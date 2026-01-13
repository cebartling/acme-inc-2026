package com.acme.customer.application

import com.acme.customer.domain.ConsentRecord
import com.acme.customer.domain.ConsentSource
import com.acme.customer.domain.ConsentType
import com.acme.customer.domain.Customer
import com.acme.customer.domain.CustomerStatus
import com.acme.customer.domain.CustomerType
import com.acme.customer.infrastructure.persistence.ConsentRecordRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExportConsentHistoryUseCaseTest {

    private lateinit var customerRepository: CustomerRepository
    private lateinit var consentRecordRepository: ConsentRecordRepository
    private lateinit var useCase: ExportConsentHistoryUseCase

    private val customerId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        customerRepository = mockk()
        consentRecordRepository = mockk()

        useCase = ExportConsentHistoryUseCase(
            customerRepository = customerRepository,
            consentRecordRepository = consentRecordRepository,
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
    fun `execute should return CustomerNotFound when customer does not exist`() {
        // Given
        every { customerRepository.findById(customerId) } returns Optional.empty()

        // When
        val result = useCase.execute(customerId, userId)

        // Then
        assertTrue(result is ExportConsentHistoryResult.CustomerNotFound)
        assertEquals(customerId, (result as ExportConsentHistoryResult.CustomerNotFound).customerId)
    }

    @Test
    fun `execute should return Unauthorized when user does not own customer`() {
        // Given
        val otherUserId = UUID.randomUUID()
        every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())

        // When
        val result = useCase.execute(customerId, otherUserId)

        // Then
        assertTrue(result is ExportConsentHistoryResult.Unauthorized)
    }

    @Test
    fun `execute should return empty history when no records exist`() {
        // Given
        every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())
        every { consentRecordRepository.findByCustomerId(customerId) } returns emptyList()

        // When
        val result = useCase.execute(customerId, userId)

        // Then
        assertTrue(result is ExportConsentHistoryResult.Success)
        val success = result as ExportConsentHistoryResult.Success
        assertEquals(customerId, success.export.customerId)
        assertNotNull(success.export.exportedAt)
        assertEquals(0, success.export.totalRecords)
        assertTrue(success.export.consentHistory.isEmpty())
    }

    @Test
    fun `execute should return full consent history`() {
        // Given
        val record1 = ConsentRecord.create(
            id = UUID.randomUUID(),
            customerId = customerId,
            consentType = ConsentType.DATA_PROCESSING,
            granted = true,
            source = ConsentSource.REGISTRATION,
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            version = 1
        )
        val record2 = ConsentRecord.create(
            id = UUID.randomUUID(),
            customerId = customerId,
            consentType = ConsentType.MARKETING,
            granted = true,
            source = ConsentSource.PROFILE_WIZARD,
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            version = 1
        )
        val record3 = ConsentRecord.create(
            id = UUID.randomUUID(),
            customerId = customerId,
            consentType = ConsentType.MARKETING,
            granted = false,
            source = ConsentSource.PRIVACY_SETTINGS,
            ipAddress = "10.0.0.1",
            userAgent = "Chrome/100",
            version = 2
        )

        every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())
        every { consentRecordRepository.findByCustomerId(customerId) } returns listOf(record1, record2, record3)

        // When
        val result = useCase.execute(customerId, userId)

        // Then
        assertTrue(result is ExportConsentHistoryResult.Success)
        val success = result as ExportConsentHistoryResult.Success
        assertEquals(3, success.export.totalRecords)
        assertEquals(3, success.export.consentHistory.size)

        // Verify first record
        val firstEntry = success.export.consentHistory[0]
        assertEquals(record1.id, firstEntry.consentId)
        assertEquals(ConsentType.DATA_PROCESSING.name, firstEntry.consentType)
        assertTrue(firstEntry.granted)
        assertEquals("REGISTRATION", firstEntry.source)
        assertEquals("192.168.1.1", firstEntry.ipAddress)
        assertEquals("Mozilla/5.0", firstEntry.userAgent)

        // Verify third record (revocation)
        val thirdEntry = success.export.consentHistory[2]
        assertEquals(record3.id, thirdEntry.consentId)
        assertEquals(ConsentType.MARKETING.name, thirdEntry.consentType)
        assertEquals(false, thirdEntry.granted)
        assertEquals("PRIVACY_SETTINGS", thirdEntry.source)
    }

    @Test
    fun `execute should include all GDPR required fields`() {
        // Given
        val record = ConsentRecord.create(
            id = UUID.randomUUID(),
            customerId = customerId,
            consentType = ConsentType.ANALYTICS,
            granted = true,
            source = ConsentSource.API,
            ipAddress = "203.0.113.42",
            userAgent = "Test Agent",
            version = 1
        )

        every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())
        every { consentRecordRepository.findByCustomerId(customerId) } returns listOf(record)

        // When
        val result = useCase.execute(customerId, userId)

        // Then
        assertTrue(result is ExportConsentHistoryResult.Success)
        val export = (result as ExportConsentHistoryResult.Success).export
        val entry = export.consentHistory.first()

        // All GDPR required fields should be present
        assertNotNull(entry.consentId)
        assertNotNull(entry.consentType)
        assertNotNull(entry.granted)
        assertNotNull(entry.timestamp)
        assertNotNull(entry.source)
        assertNotNull(entry.ipAddress)
        // userAgent may be null, but should be present if captured
        assertNotNull(entry.userAgent)
    }
}
