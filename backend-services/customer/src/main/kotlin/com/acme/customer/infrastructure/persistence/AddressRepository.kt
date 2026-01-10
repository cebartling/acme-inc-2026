package com.acme.customer.infrastructure.persistence

import com.acme.customer.domain.Address
import com.acme.customer.domain.AddressType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Spring Data JPA repository for [Address] entities.
 *
 * Provides standard CRUD operations plus custom query methods
 * for address data access, including queries for default addresses
 * and address count limits.
 */
@Repository
interface AddressRepository : JpaRepository<Address, UUID> {

    /**
     * Finds all addresses for a specific customer.
     *
     * @param customerId The customer ID.
     * @return List of addresses for the customer.
     */
    fun findByCustomerId(customerId: UUID): List<Address>

    /**
     * Finds all addresses of a specific type for a customer.
     *
     * @param customerId The customer ID.
     * @param type The address type (SHIPPING or BILLING).
     * @return List of addresses matching the criteria.
     */
    fun findByCustomerIdAndType(customerId: UUID, type: AddressType): List<Address>

    /**
     * Finds the default address of a specific type for a customer.
     *
     * @param customerId The customer ID.
     * @param type The address type.
     * @return The default address if one exists, null otherwise.
     */
    fun findByCustomerIdAndTypeAndIsDefaultTrue(customerId: UUID, type: AddressType): Address?

    /**
     * Finds an address by customer ID and label.
     *
     * @param customerId The customer ID.
     * @param label The address label.
     * @return The address if found, null otherwise.
     */
    fun findByCustomerIdAndLabel(customerId: UUID, label: String): Address?

    /**
     * Counts the number of addresses of a specific type for a customer.
     *
     * @param customerId The customer ID.
     * @param type The address type.
     * @return The count of addresses.
     */
    fun countByCustomerIdAndType(customerId: UUID, type: AddressType): Int

    /**
     * Checks if an address exists with the given customer ID and label.
     *
     * @param customerId The customer ID.
     * @param label The address label.
     * @return True if an address exists with this label.
     */
    fun existsByCustomerIdAndLabel(customerId: UUID, label: String): Boolean

    /**
     * Checks if an address exists with the given customer ID and label,
     * excluding a specific address ID.
     *
     * @param customerId The customer ID.
     * @param label The address label.
     * @param excludeId The address ID to exclude from the check.
     * @return True if another address exists with this label.
     */
    @Query(
        """
        SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
        FROM Address a
        WHERE a.customerId = :customerId
        AND a.label = :label
        AND a.id <> :excludeId
        """
    )
    fun existsByCustomerIdAndLabelExcluding(
        @Param("customerId") customerId: UUID,
        @Param("label") label: String,
        @Param("excludeId") excludeId: UUID
    ): Boolean

    /**
     * Removes the default flag from all addresses of a specific type for a customer.
     * Used before setting a new default address.
     *
     * @param customerId The customer ID.
     * @param type The address type.
     * @return The number of addresses updated.
     */
    @Modifying
    @Query(
        """
        UPDATE Address a
        SET a.isDefault = false, a.updatedAt = CURRENT_TIMESTAMP
        WHERE a.customerId = :customerId
        AND a.type = :type
        AND a.isDefault = true
        """
    )
    fun clearDefaultForType(
        @Param("customerId") customerId: UUID,
        @Param("type") type: AddressType
    ): Int

    /**
     * Deletes all addresses for a customer.
     *
     * @param customerId The customer ID.
     * @return The number of addresses deleted.
     */
    fun deleteByCustomerId(customerId: UUID): Int
}
