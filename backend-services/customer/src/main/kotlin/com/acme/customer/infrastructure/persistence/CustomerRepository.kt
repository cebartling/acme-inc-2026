package com.acme.customer.infrastructure.persistence

import com.acme.customer.domain.Customer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Spring Data JPA repository for [Customer] entities.
 *
 * Provides standard CRUD operations plus custom query methods
 * for customer data access.
 */
@Repository
interface CustomerRepository : JpaRepository<Customer, UUID> {

    /**
     * Finds a customer by their corresponding user ID from the Identity Service.
     *
     * @param userId The user ID to search for.
     * @return The customer if found, null otherwise.
     */
    fun findByUserId(userId: UUID): Customer?

    /**
     * Checks if a customer exists with the given user ID.
     *
     * @param userId The user ID to check.
     * @return True if a customer exists with this user ID.
     */
    fun existsByUserId(userId: UUID): Boolean

    /**
     * Finds a customer by their customer number.
     *
     * @param customerNumber The customer number to search for.
     * @return The customer if found, null otherwise.
     */
    fun findByCustomerNumber(customerNumber: String): Customer?

    /**
     * Finds a customer by their email address.
     *
     * @param email The email address to search for.
     * @return The customer if found, null otherwise.
     */
    fun findByEmail(email: String): Customer?
}
