package com.acme.customer.application

import com.acme.customer.domain.ConsentRecord
import com.acme.customer.domain.ConsentSource
import com.acme.customer.domain.ConsentType
import com.acme.customer.domain.Customer
import com.acme.customer.domain.CustomerStatus
import com.acme.customer.domain.CustomerType
import com.acme.customer.infrastructure.persistence.ConsentRecordRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GetConsentsUseCaseTest {

    private lateinit var customerRepository: CustomerRepository
    private lateinit var consentRecordRepository: ConsentRecordRepository
    private lateinit var useCase: GetConsentsUseCase

    private val customerId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        customerRepository = mockk()
        consentRecordRepository = mockk()

        useCase = GetConsentsUseCase(
            customerRepository = customerRepository,
            consentRecordRepository = consentRecordRepository
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
        assertTrue(result is GetConsentsResult.CustomerNotFound)
        assertEquals(customerId, (result as GetConsentsResult.CustomerNotFound).customerId)
    }

    @Test
    fun `execute should return Unauthorized when user does not own customer`() {
        // Given
        val otherUserId = UUID.randomUUID()
        every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())

        // When
        val result = useCase.execute(customerId, otherUserId)

        // Then
        assertTrue(result is GetConsentsResult.Unauthorized)
    }

    @Test
    fun `execute should return all consent types with statuses`() {
        // Given
        val marketingRecord = ConsentRecord.create(
            id = UUID.randomUUID(),
            customerId = customerId,
            consentType = ConsentType.MARKETING,
            granted = true,
            source = ConsentSource.PROFILE_WIZARD,
            ipAddress = "192.168.1.1",
            version = 1
        )
        val dataProcessingRecord = ConsentRecord.create(
            id = UUID.randomUUID(),
            customerId = customerId,
            consentType = ConsentType.DATA_PROCESSING,
            granted = true,
            source = ConsentSource.REGISTRATION,
            ipAddress = "192.168.1.1",
            version = 1
        )

        every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())
        every { consentRecordRepository.findCurrentConsentsByCustomerId(customerId) } returns listOf(
            marketingRecord,
            dataProcessingRecord
        )

        // When
        val result = useCase.execute(customerId, userId)

        // Then
        assertTrue(result is GetConsentsResult.Success)
        val success = result as GetConsentsResult.Success
        assertEquals(customerId, success.customerId)
        assertEquals(ConsentType.entries.size, success.consents.size)

        // Check MARKETING consent
        val marketingConsent = success.consents.find { it.consentType == ConsentType.MARKETING }!!
        assertTrue(marketingConsent.currentStatus)
        assertFalse(marketingConsent.required)

        // Check DATA_PROCESSING consent
        val dataProcessingConsent = success.consents.find { it.consentType == ConsentType.DATA_PROCESSING }!!
        assertTrue(dataProcessingConsent.currentStatus)
        assertTrue(dataProcessingConsent.required)

        // Check ANALYTICS consent (not in records, should be false)
        val analyticsConsent = success.consents.find { it.consentType == ConsentType.ANALYTICS }!!
        assertFalse(analyticsConsent.currentStatus)
        assertEquals(0, analyticsConsent.version)
    }

    @Test
    fun `execute should return empty consents when no records exist`() {
        // Given
        every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())
        every { consentRecordRepository.findCurrentConsentsByCustomerId(customerId) } returns emptyList()

        // When
        val result = useCase.execute(customerId, userId)

        // Then
        assertTrue(result is GetConsentsResult.Success)
        val success = result as GetConsentsResult.Success
        assertEquals(ConsentType.entries.size, success.consents.size)

        // All should be false (not granted)
        success.consents.forEach { consent ->
            assertFalse(consent.currentStatus)
            assertEquals(0, consent.version)
        }
    }

    @Test
    fun `execute should correctly identify required consents`() {
        // Given
        every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())
        every { consentRecordRepository.findCurrentConsentsByCustomerId(customerId) } returns emptyList()

        // When
        val result = useCase.execute(customerId, userId)

        // Then
        assertTrue(result is GetConsentsResult.Success)
        val success = result as GetConsentsResult.Success

        val requiredConsents = success.consents.filter { it.required }
        assertEquals(1, requiredConsents.size)
        assertEquals(ConsentType.DATA_PROCESSING, requiredConsents.first().consentType)
    }
}
