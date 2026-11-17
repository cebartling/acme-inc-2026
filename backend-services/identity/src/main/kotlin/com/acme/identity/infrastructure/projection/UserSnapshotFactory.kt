package com.acme.identity.infrastructure.projection

import com.acme.identity.infrastructure.persistence.UserRepository
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Builds a user's snapshot from the database. Call it in the transaction that changed the user,
 * after the change is saved, so the snapshot holds the new state.
 */
@Component
class UserSnapshotFactory(private val userRepository: UserRepository) {

    fun build(userId: UUID): UserSnapshot {
        val user = userRepository.findById(userId).orElseThrow {
            IllegalStateException("Cannot snapshot user $userId: not found")
        }
        return UserSnapshot.fromDomain(user)
    }
}
