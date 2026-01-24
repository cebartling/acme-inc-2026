package com.acme.identity.infrastructure.persistence

import com.acme.identity.domain.DeviceTrust
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Spring Data Redis repository for [DeviceTrust] entities.
 *
 * Provides CRUD operations and custom query methods for device trust persistence.
 * Device trusts are stored in Redis with automatic TTL-based expiration (30 days).
 *
 * The repository supports:
 * - Finding all trusted devices for a specific user
 * - Automatic trust expiration via Redis TTL
 * - Indexed queries on userId for efficient lookups
 */
@Repository
interface DeviceTrustRepository : CrudRepository<DeviceTrust, String> {

    /**
     * Finds all active device trusts for a given user.
     *
     * This is used for:
     * - Enforcing concurrent device trust limits (max 10 per user)
     * - Identifying the oldest device trust for eviction
     * - Device management UI (list trusted devices)
     *
     * @param userId The user's unique identifier.
     * @return A list of all active device trusts for the user.
     */
    fun findByUserId(userId: UUID): List<DeviceTrust>

    /**
     * Finds a specific device trust by ID and user ID.
     *
     * Used for revocation to ensure users can only revoke their own devices.
     *
     * @param id The device trust ID.
     * @param userId The user's unique identifier.
     * @return The device trust if found and owned by the user, or null.
     */
    fun findByIdAndUserId(id: String, userId: UUID): DeviceTrust?

    /**
     * Counts the number of active device trusts for a given user.
     *
     * This is a more efficient alternative to calling findByUserId
     * when you only need the count for limit enforcement.
     *
     * @param userId The user's unique identifier.
     * @return The number of active device trusts for the user.
     */
    fun countByUserId(userId: UUID): Long
}
