package com.acme.identity.infrastructure.security

import com.password4j.Argon2Function
import com.password4j.Password
import com.password4j.types.Argon2
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class PasswordHasher(
    @Value("\${identity.password.argon2.memory:65536}")
    private val memory: Int,

    @Value("\${identity.password.argon2.iterations:3}")
    private val iterations: Int,

    @Value("\${identity.password.argon2.parallelism:4}")
    private val parallelism: Int,

    @Value("\${identity.password.argon2.hash-length:32}")
    private val hashLength: Int
) {
    private val argon2Function: Argon2Function by lazy {
        Argon2Function.getInstance(
            memory,
            iterations,
            parallelism,
            hashLength,
            Argon2.ID
        )
    }

    fun hash(password: String): String {
        return Password.hash(password)
            .with(argon2Function)
            .result
    }

    fun verify(password: String, hash: String): Boolean {
        return Password.check(password, hash)
            .with(argon2Function)
    }
}
