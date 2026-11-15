package com.acme.customer.application

import com.acme.customer.domain.ConsentRecord
import com.acme.customer.domain.ConsentSource
import com.acme.customer.domain.ConsentType
import com.acme.customer.infrastructure.projection.CustomerReadModelQueries
import com.acme.customer.infrastructure.projection.CustomerSnapshot
import com.acme.customer.infrastructure.projection.ReadModelBehindException
import com.acme.customer.infrastructure.projection.SnapshotFixtures
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GetConsentsUseCaseTest {

    private lateinit var readModel: CustomerReadModelQueries
    private lateinit var useCase: GetConsentsUseCase

    private val customerId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        readModel = mockk()
        useCase = GetConsentsUseCase(readModel)
    }

    private fun snapshotWith(vararg records: ConsentRecord): CustomerSnapshot =
        SnapshotFixtures.snapshot(
            customer = SnapshotFixtures.customer(customerId, userId),
            consents = records.toList()
        )

    private fun record(
        type: ConsentType,
        granted: Boolean,
        createdAt: Instant,
        version: Int = 1,
        source: ConsentSource = ConsentSource.REGISTRATION
    ) = ConsentRecord(
        id = UUID.randomUUID(),
        customerId = customerId,
        consentType = type,
        granted = granted,
        source = source,
        ipAddress = "192.168.1.1",
        createdAt = createdAt,
        version = version
    )

    @Test
    fun `execute should return CustomerNotFound when customer does not exist`() {
        every { readModel.findById(customerId, null) } returns null

        val result = useCase.execute(customerId, userId)

        assertTrue(result is GetConsentsResult.CustomerNotFound)
        assertEquals(customerId, result.customerId)
    }

    @Test
    fun `execute should return Unauthorized when user does not own customer`() {
        every { readModel.findById(customerId, null) } returns snapshotWith()

        val result = useCase.execute(customerId, UUID.randomUUID())

        assertTrue(result is GetConsentsResult.Unauthorized)
    }

    @Test
    fun `execute should return all consent types with statuses`() {
        val now = Instant.now()
        every { readModel.findById(customerId, null) } returns snapshotWith(
            record(ConsentType.MARKETING, true, now, source = ConsentSource.PROFILE_WIZARD),
            record(ConsentType.DATA_PROCESSING, true, now)
        )

        val result = useCase.execute(customerId, userId)

        assertTrue(result is GetConsentsResult.Success)
        assertEquals(customerId, result.customerId)
        assertEquals(ConsentType.entries.size, result.consents.size)

        val marketing = result.consents.single { it.consentType == ConsentType.MARKETING }
        assertTrue(marketing.currentStatus)
        assertFalse(marketing.required)
        assertEquals("PROFILE_WIZARD", marketing.source)

        val dataProcessing = result.consents.single { it.consentType == ConsentType.DATA_PROCESSING }
        assertTrue(dataProcessing.currentStatus)
        assertTrue(dataProcessing.required)

        // ANALYTICS has no record, so it is not granted
        val analytics = result.consents.single { it.consentType == ConsentType.ANALYTICS }
        assertFalse(analytics.currentStatus)
        assertEquals(0, analytics.version)
        assertEquals("NONE", analytics.source)
    }

    @Test
    fun `execute should use the latest record of a type as its current status`() {
        val now = Instant.now()
        every { readModel.findById(customerId, null) } returns snapshotWith(
            record(ConsentType.MARKETING, granted = true, createdAt = now.minusSeconds(60), version = 1),
            record(ConsentType.MARKETING, granted = false, createdAt = now, version = 2)
        )

        val result = useCase.execute(customerId, userId) as GetConsentsResult.Success

        val marketing = result.consents.single { it.consentType == ConsentType.MARKETING }
        assertFalse(marketing.currentStatus)
        assertEquals(2, marketing.version)
    }

    @Test
    fun `execute should return all types not granted when no records exist`() {
        every { readModel.findById(customerId, null) } returns snapshotWith()

        val result = useCase.execute(customerId, userId)

        assertTrue(result is GetConsentsResult.Success)
        assertEquals(ConsentType.entries.size, result.consents.size)
        result.consents.forEach { consent ->
            assertFalse(consent.currentStatus)
            assertEquals(0, consent.version)
        }
    }

    @Test
    fun `execute should correctly identify required consents`() {
        every { readModel.findById(customerId, null) } returns snapshotWith()

        val result = useCase.execute(customerId, userId) as GetConsentsResult.Success

        val required = result.consents.filter { it.required }
        assertEquals(listOf(ConsentType.DATA_PROCESSING), required.map { it.consentType })
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
