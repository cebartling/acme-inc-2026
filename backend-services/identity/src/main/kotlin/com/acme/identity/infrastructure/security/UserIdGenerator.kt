package com.acme.identity.infrastructure.security

import com.acme.identity.domain.UserId
import com.fasterxml.uuid.Generators
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class UserIdGenerator {
    private val uuidGenerator = Generators.timeBasedEpochGenerator()

    fun generate(): UserId {
        return UserId(uuidGenerator.generate())
    }

    fun generateRaw(): UUID {
        return uuidGenerator.generate()
    }
}
