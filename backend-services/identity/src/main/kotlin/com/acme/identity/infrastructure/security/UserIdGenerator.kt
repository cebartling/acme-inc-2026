package com.acme.identity.infrastructure.security

import com.acme.identity.domain.UserId
import com.fasterxml.uuid.Generators
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Generator for UUID v7 (time-ordered) user identifiers.
 *
 * UUID v7 provides several advantages over random UUIDs:
 * - Time-ordered: IDs sort chronologically, improving database index performance
 * - Timestamp embedded: Creation time can be extracted from the ID
 * - Globally unique: No coordination required between distributed systems
 *
 * The implementation uses the java-uuid-generator library which provides
 * RFC-compliant UUID v7 generation.
 *
 * @see <a href="https://www.ietf.org/archive/id/draft-peabody-dispatch-new-uuid-format-04.html">UUID v7 Draft</a>
 */
@Component
class UserIdGenerator {
    private val uuidGenerator = Generators.timeBasedEpochGenerator()

    /**
     * Generates a new user ID wrapped in a type-safe [UserId] value class.
     *
     * @return A new unique [UserId] instance.
     */
    fun generate(): UserId {
        return UserId(uuidGenerator.generate())
    }

    /**
     * Generates a new user ID as a raw [UUID].
     *
     * Use this method when working with JPA entities or other APIs
     * that expect a standard UUID type.
     *
     * @return A new unique UUID v7 instance.
     */
    fun generateRaw(): UUID {
        return uuidGenerator.generate()
    }
}
