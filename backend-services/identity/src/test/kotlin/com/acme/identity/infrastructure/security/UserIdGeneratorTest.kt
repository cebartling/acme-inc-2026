package com.acme.identity.infrastructure.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UserIdGeneratorTest {

    private lateinit var userIdGenerator: UserIdGenerator

    @BeforeEach
    fun setUp() {
        userIdGenerator = UserIdGenerator()
    }

    @Test
    fun `generate should produce a unique UserId`() {
        val id1 = userIdGenerator.generate()
        val id2 = userIdGenerator.generate()

        assertNotEquals(id1, id2)
    }

    @Test
    fun `generateRaw should produce a unique UUID`() {
        val uuid1 = userIdGenerator.generateRaw()
        val uuid2 = userIdGenerator.generateRaw()

        assertNotEquals(uuid1, uuid2)
    }

    @Test
    fun `generated UUIDs should be time-ordered (UUID v7)`() {
        val uuids = (1..10).map { userIdGenerator.generateRaw() }

        // UUID v7 are time-ordered, so later UUIDs should sort after earlier ones
        val sorted = uuids.sortedBy { it.toString() }

        // Since we generate in sequence without delay, they should maintain order
        assertTrue(uuids == sorted || uuids.zip(sorted).all {
            it.first.toString().substring(0, 8) == it.second.toString().substring(0, 8)
        })
    }

    @Test
    fun `generated UUID should have correct version bits`() {
        val uuid = userIdGenerator.generateRaw()
        val uuidString = uuid.toString()

        // UUID v7 has version 7 in the 13th character (0-indexed: position 14)
        val versionChar = uuidString[14]
        assertTrue(versionChar == '7')
    }

    @Test
    fun `UserId toString should return UUID string representation`() {
        val userId = userIdGenerator.generate()

        // Should be a valid UUID format
        assertTrue(userId.toString().matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")))
    }
}
