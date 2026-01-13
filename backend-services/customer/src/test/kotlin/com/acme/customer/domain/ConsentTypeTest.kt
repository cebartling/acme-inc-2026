package com.acme.customer.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConsentTypeTest {

    @Test
    fun `DATA_PROCESSING should be required`() {
        assertTrue(ConsentType.DATA_PROCESSING.required)
    }

    @Test
    fun `DATA_PROCESSING should be granted by default`() {
        assertTrue(ConsentType.DATA_PROCESSING.defaultGranted)
    }

    @Test
    fun `MARKETING should not be required`() {
        assertFalse(ConsentType.MARKETING.required)
    }

    @Test
    fun `MARKETING should not be granted by default`() {
        assertFalse(ConsentType.MARKETING.defaultGranted)
    }

    @Test
    fun `ANALYTICS should not be required`() {
        assertFalse(ConsentType.ANALYTICS.required)
    }

    @Test
    fun `ANALYTICS should not be granted by default`() {
        assertFalse(ConsentType.ANALYTICS.defaultGranted)
    }

    @Test
    fun `THIRD_PARTY should not be required`() {
        assertFalse(ConsentType.THIRD_PARTY.required)
    }

    @Test
    fun `THIRD_PARTY should not be granted by default`() {
        assertFalse(ConsentType.THIRD_PARTY.defaultGranted)
    }

    @Test
    fun `PERSONALIZATION should not be required`() {
        assertFalse(ConsentType.PERSONALIZATION.required)
    }

    @Test
    fun `PERSONALIZATION should not be granted by default`() {
        assertFalse(ConsentType.PERSONALIZATION.defaultGranted)
    }

    @Test
    fun `fromString should return correct type for exact match`() {
        assertEquals(ConsentType.DATA_PROCESSING, ConsentType.fromString("DATA_PROCESSING"))
        assertEquals(ConsentType.MARKETING, ConsentType.fromString("MARKETING"))
        assertEquals(ConsentType.ANALYTICS, ConsentType.fromString("ANALYTICS"))
        assertEquals(ConsentType.THIRD_PARTY, ConsentType.fromString("THIRD_PARTY"))
        assertEquals(ConsentType.PERSONALIZATION, ConsentType.fromString("PERSONALIZATION"))
    }

    @Test
    fun `fromString should be case insensitive`() {
        assertEquals(ConsentType.DATA_PROCESSING, ConsentType.fromString("data_processing"))
        assertEquals(ConsentType.MARKETING, ConsentType.fromString("Marketing"))
        assertEquals(ConsentType.ANALYTICS, ConsentType.fromString("AnAlYtIcS"))
    }

    @Test
    fun `fromString should return null for invalid type`() {
        assertNull(ConsentType.fromString("INVALID"))
        assertNull(ConsentType.fromString(""))
        assertNull(ConsentType.fromString("marketing_stuff"))
    }

    @Test
    fun `all consent types should have descriptions`() {
        ConsentType.entries.forEach { type ->
            assertTrue(type.description.isNotBlank(), "Description for $type should not be blank")
        }
    }

    @Test
    fun `only DATA_PROCESSING should be required`() {
        val requiredTypes = ConsentType.entries.filter { it.required }
        assertEquals(1, requiredTypes.size)
        assertEquals(ConsentType.DATA_PROCESSING, requiredTypes.first())
    }

    @Test
    fun `only DATA_PROCESSING should be granted by default`() {
        val defaultGrantedTypes = ConsentType.entries.filter { it.defaultGranted }
        assertEquals(1, defaultGrantedTypes.size)
        assertEquals(ConsentType.DATA_PROCESSING, defaultGrantedTypes.first())
    }
}
