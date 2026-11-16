package com.acme.customer.infrastructure.projection

import com.acme.customer.application.ProfileCompletenessCalculator
import com.acme.customer.infrastructure.persistence.AddressRepository
import com.acme.customer.infrastructure.persistence.ConsentRecordRepository
import com.acme.customer.infrastructure.persistence.CustomerPreferencesRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class CustomerSnapshotFactoryTest {

    private lateinit var customerRepository: CustomerRepository
    private lateinit var preferencesRepository: CustomerPreferencesRepository
    private lateinit var addressRepository: AddressRepository
    private lateinit var consentRecordRepository: ConsentRecordRepository
    private lateinit var completenessCalculator: ProfileCompletenessCalculator
    private lateinit var factory: CustomerSnapshotFactory

    @BeforeEach
    fun setUp() {
        customerRepository = mockk()
        preferencesRepository = mockk()
        addressRepository = mockk()
        consentRecordRepository = mockk()
        completenessCalculator = mockk()
        factory = CustomerSnapshotFactory(
            customerRepository,
            preferencesRepository,
            addressRepository,
            consentRecordRepository,
            completenessCalculator
        )
    }

    @Test
    fun `build collects the customer, preferences, addresses, consents and completeness`() {
        val customer = SnapshotFixtures.customer()
        val address = SnapshotFixtures.address(customer.id)
        val consent = SnapshotFixtures.consent(customer.id)
        every { customerRepository.findById(customer.id) } returns Optional.of(customer)
        every { preferencesRepository.findById(customer.id) } returns
            Optional.of(SnapshotFixtures.preferences(customer.id))
        every { addressRepository.findByCustomerId(customer.id) } returns listOf(address)
        every { consentRecordRepository.findByCustomerId(customer.id) } returns listOf(consent)
        every { completenessCalculator.calculate(customer) } returns SnapshotFixtures.completeness(customer.id)

        val snapshot = factory.build(customer.id)

        assertEquals(customer.id.toString(), snapshot.customer.customerId)
        assertEquals("ada@example.com", snapshot.customer.email.address)
        assertEquals(listOf(address.id.toString()), snapshot.addresses.map { it.addressId })
        assertEquals("1 Main St", snapshot.addresses.single().street.line1)
        assertEquals(consent.id.toString(), snapshot.consents.single().consentId)
        assertEquals("203.0.113.7", snapshot.consents.single().ipAddress)
        assertEquals(25, snapshot.completeness.overallScore)
    }

    @Test
    fun `build fails with the customer id when the customer does not exist`() {
        val id = UUID.randomUUID()
        every { customerRepository.findById(id) } returns Optional.empty()

        val error = assertThrows<IllegalStateException> { factory.build(id) }

        assertEquals("Cannot snapshot customer $id: not found", error.message)
    }

    @Test
    fun `build fails with the customer id when the preferences do not exist`() {
        val customer = SnapshotFixtures.customer()
        every { customerRepository.findById(customer.id) } returns Optional.of(customer)
        every { preferencesRepository.findById(customer.id) } returns Optional.empty()

        val error = assertThrows<IllegalStateException> { factory.build(customer.id) }

        assertEquals("Cannot snapshot customer ${customer.id}: no preferences", error.message)
    }
}
