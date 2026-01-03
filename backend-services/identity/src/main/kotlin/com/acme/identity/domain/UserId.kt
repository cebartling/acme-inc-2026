package com.acme.identity.domain

import java.util.UUID

@JvmInline
value class UserId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun fromString(id: String): UserId = UserId(UUID.fromString(id))
    }
}
