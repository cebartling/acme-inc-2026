package com.acme.customer.infrastructure.projection

import com.acme.customer.api.v1.dto.AddressResponse
import com.acme.customer.api.v1.dto.CustomerResponse
import com.acme.customer.api.v1.dto.ProfileCompletenessResponse
import com.acme.customer.application.ProfileCompletenessCalculator
import com.acme.customer.infrastructure.persistence.AddressRepository
import com.acme.customer.infrastructure.persistence.ConsentRecordRepository
import com.acme.customer.infrastructure.persistence.CustomerPreferencesRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Reads a customer's current state from PostgreSQL and builds the [CustomerSnapshot].
 *
 * Call it inside the transaction that changed the customer, after the change is saved, so the
 * snapshot matches what that transaction commits.
 */
@Component
class CustomerSnapshotFactory(
    private val customerRepository: CustomerRepository,
    private val preferencesRepository: CustomerPreferencesRepository,
    private val addressRepository: AddressRepository,
    private val consentRecordRepository: ConsentRecordRepository,
    private val completenessCalculator: ProfileCompletenessCalculator
) {

    fun build(customerId: UUID): CustomerSnapshot {
        val customer = customerRepository.findById(customerId).orElse(null)
            ?: throw IllegalStateException("Cannot snapshot customer $customerId: not found")
        val preferences = preferencesRepository.findById(customerId).orElse(null)
            ?: throw IllegalStateException("Cannot snapshot customer $customerId: no preferences")

        return CustomerSnapshot(
            customer = CustomerResponse.fromDomain(customer, preferences),
            preferencesUpdatedAt = preferences.updatedAt,
            addresses = addressRepository.findByCustomerId(customerId).map { AddressResponse.fromEntity(it) },
            consents = consentRecordRepository.findByCustomerId(customerId).map { ConsentRecordSnapshot.fromDomain(it) },
            completeness = ProfileCompletenessResponse.fromDomain(completenessCalculator.calculate(customer))
        )
    }
}
