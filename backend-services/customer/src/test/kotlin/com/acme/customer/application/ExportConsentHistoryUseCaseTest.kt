package com.acme.customer.application

import com.acme.customer.domain.ConsentRecord
import com.acme.customer.domain.ConsentSource
import com.acme.customer.domain.ConsentType
import com.acme.customer.infrastructure.projection.CustomerReadModelQueries
import com.acme.customer.infrastructure.projection.CustomerSnapshot
import com.acme.customer.infrastructure.projection.ReadModelBehindException
import com.acme.customer.infrastructure.projection.SnapshotFixtures
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExportConsentHistoryUseCaseTest {

    private lateinit var readModel: CustomerReadModelQueries
    private lateinit var useCase: ExportConsentHistoryUseCase

    private val customerId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        readModel = mockk()

        useCase = ExportConsentHistoryUseCase(
            readModel = readModel,
            meterRegistry = SimpleMeterRegistry()
        )
        useCase.initMetrics()
    }

    private fun snapshotWith(vararg records: ConsentRecord): CustomerSnapshot =
        SnapshotFixtures.snapshot(
            customer = SnapshotFixtures.customer(customerId, userId),
            consents = records.toList()
        )

    @Test
    fun `execute should return CustomerNotFound when customer does not exist`() {
        // Given
        every { readModel.findById(customerId, null) } returns null

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
        every { readModel.findById(customerId, null) } returns snapshotWith()

        // When
        val result = useCase.execute(customerId, otherUserId)

        // Then
        assertTrue(result is ExportConsentHistoryResult.Unauthorized)
    }

    @Test
    fun `execute should return empty history when no records exist`() {
        // Given
        every { readModel.findById(customerId, null) } returns snapshotWith()

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

        every { readModel.findById(customerId, null) } returns snapshotWith(record1, record2, record3)

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

        every { readModel.findById(customerId, null) } returns snapshotWith(record)

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

    @Test
    fun `execute should wait for the event the caller read after`() {
        val readAfter = UUID.randomUUID()
        every { readModel.findById(customerId, readAfter) } returns snapshotWith()

        useCase.execute(customerId, userId, readAfter)

        verify(exactly = 1) { readModel.findById(customerId, readAfter) }
    }

    @Test
    fun `execute should let ReadModelBehindException through rather than report a failure`() {
        val readAfter = UUID.randomUUID()
        every { readModel.findById(customerId, readAfter) } throws ReadModelBehindException(readAfter)

        assertThrows<ReadModelBehindException> { useCase.execute(customerId, userId, readAfter) }
    }
}
