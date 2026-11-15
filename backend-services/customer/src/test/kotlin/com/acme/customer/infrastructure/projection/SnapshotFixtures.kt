package com.acme.customer.infrastructure.projection

import com.acme.customer.domain.Address
import com.acme.customer.domain.AddressType
import com.acme.customer.domain.ConsentRecord
import com.acme.customer.domain.ConsentSource
import com.acme.customer.domain.ConsentType
import com.acme.customer.domain.Customer
import com.acme.customer.domain.CustomerPreferences
import com.acme.customer.domain.ProfileCompleteness
import java.time.Instant
import java.util.UUID

/** Domain objects shared by the snapshot tests. */
object SnapshotFixtures {
    val now: Instant = Instant.parse("2026-11-15T12:00:00Z")

    fun customer(id: UUID = UUID.randomUUID()) = Customer(
        id = id,
        userId = UUID.randomUUID(),
        customerNumber = "CUST-000001",
        firstName = "Ada",
        lastName = "Lovelace",
        displayName = "Ada L.",
        email = "ada@example.com",
        registeredAt = now,
        lastActivityAt = now
    )

    fun preferences(customerId: UUID) = CustomerPreferences(customerId = customerId)

    fun address(customerId: UUID) = Address(
        id = UUID.randomUUID(),
        customerId = customerId,
        type = AddressType.SHIPPING,
        streetLine1 = "1 Main St",
        city = "Springfield",
        state = "IL",
        postalCode = "62701",
        country = "US",
        isDefault = true
    )

    fun consent(customerId: UUID) = ConsentRecord(
        id = UUID.randomUUID(),
        customerId = customerId,
        consentType = ConsentType.MARKETING,
        granted = true,
        source = ConsentSource.REGISTRATION,
        ipAddress = "203.0.113.7",
        userAgent = "test-agent",
        version = 1
    )

    fun completeness(customerId: UUID) = ProfileCompleteness(
        customerId = customerId,
        overallScore = 25,
        sections = emptyList(),
        nextAction = null,
        updatedAt = now
    )
}
