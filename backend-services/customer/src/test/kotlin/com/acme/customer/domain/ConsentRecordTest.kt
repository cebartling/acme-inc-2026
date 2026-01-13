package com.acme.customer.domain

import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConsentRecordTest {

    private val customerId = UUID.randomUUID()

    @Test
    fun `create should create a consent record with all fields`() {
        // Given
        val id = UUID.randomUUID()
        val ipAddress = "192.168.1.1"
        val userAgent = "Mozilla/5.0"
        val expiresAt = Instant.now().plus(365, ChronoUnit.DAYS)

        // When
        val record = ConsentRecord.create(
            id = id,
            customerId = customerId,
            consentType = ConsentType.MARKETING,
            granted = true,
            source = ConsentSource.PROFILE_WIZARD,
            ipAddress = ipAddress,
            userAgent = userAgent,
            expiresAt = expiresAt,
            version = 1
        )

        // Then
        assertEquals(id, record.id)
        assertEquals(customerId, record.customerId)
        assertEquals(ConsentType.MARKETING, record.consentType)
        assertTrue(record.granted)
        assertEquals(ConsentSource.PROFILE_WIZARD, record.source)
        assertEquals(ipAddress, record.ipAddress)
        assertEquals(userAgent, record.userAgent)
        assertEquals(expiresAt, record.expiresAt)
        assertEquals(1, record.version)
    }

    @Test
    fun `createInitialDataProcessingConsent should create DATA_PROCESSING consent`() {
        // Given
        val id = UUID.randomUUID()
        val ipAddress = "192.168.1.1"

        // When
        val record = ConsentRecord.createInitialDataProcessingConsent(
            id = id,
            customerId = customerId,
            ipAddress = ipAddress
        )

        // Then
        assertEquals(ConsentType.DATA_PROCESSING, record.consentType)
        assertTrue(record.granted)
        assertEquals(ConsentSource.REGISTRATION, record.source)
        assertNull(record.expiresAt)
        assertEquals(1, record.version)
    }

    @Test
    fun `isExpired should return false when expiresAt is null`() {
        // Given
        val record = ConsentRecord.create(
            id = UUID.randomUUID(),
            customerId = customerId,
            consentType = ConsentType.DATA_PROCESSING,
            granted = true,
            source = ConsentSource.REGISTRATION,
            ipAddress = "192.168.1.1",
            expiresAt = null,
            version = 1
        )

        // When/Then
        assertFalse(record.isExpired())
    }

    @Test
    fun `isExpired should return false when expiresAt is in the future`() {
        // Given
        val record = ConsentRecord.create(
            id = UUID.randomUUID(),
            customerId = customerId,
            consentType = ConsentType.MARKETING,
            granted = true,
            source = ConsentSource.PROFILE_WIZARD,
            ipAddress = "192.168.1.1",
            expiresAt = Instant.now().plus(1, ChronoUnit.DAYS),
            version = 1
        )

        // When/Then
        assertFalse(record.isExpired())
    }

    @Test
    fun `isExpired should return true when expiresAt is in the past`() {
        // Given
        val record = ConsentRecord.create(
            id = UUID.randomUUID(),
            customerId = customerId,
            consentType = ConsentType.MARKETING,
            granted = true,
            source = ConsentSource.PROFILE_WIZARD,
            ipAddress = "192.168.1.1",
            expiresAt = Instant.now().minus(1, ChronoUnit.DAYS),
            version = 1
        )

        // When/Then
        assertTrue(record.isExpired())
    }

    @Test
    fun `isEffective should return true when granted and not expired`() {
        // Given
        val record = ConsentRecord.create(
            id = UUID.randomUUID(),
            customerId = customerId,
            consentType = ConsentType.MARKETING,
            granted = true,
            source = ConsentSource.PROFILE_WIZARD,
            ipAddress = "192.168.1.1",
            expiresAt = Instant.now().plus(1, ChronoUnit.DAYS),
            version = 1
        )

        // When/Then
        assertTrue(record.isEffective())
    }

    @Test
    fun `isEffective should return false when not granted`() {
        // Given
        val record = ConsentRecord.create(
            id = UUID.randomUUID(),
            customerId = customerId,
            consentType = ConsentType.MARKETING,
            granted = false,
            source = ConsentSource.PRIVACY_SETTINGS,
            ipAddress = "192.168.1.1",
            expiresAt = null,
            version = 2
        )

        // When/Then
        assertFalse(record.isEffective())
    }

    @Test
    fun `isEffective should return false when granted but expired`() {
        // Given
        val record = ConsentRecord.create(
            id = UUID.randomUUID(),
            customerId = customerId,
            consentType = ConsentType.MARKETING,
            granted = true,
            source = ConsentSource.PROFILE_WIZARD,
            ipAddress = "192.168.1.1",
            expiresAt = Instant.now().minus(1, ChronoUnit.DAYS),
            version = 1
        )

        // When/Then
        assertFalse(record.isEffective())
    }

    @Test
    fun `getConsentId should return type-safe ConsentId`() {
        // Given
        val id = UUID.randomUUID()
        val record = ConsentRecord.create(
            id = id,
            customerId = customerId,
            consentType = ConsentType.MARKETING,
            granted = true,
            source = ConsentSource.PROFILE_WIZARD,
            ipAddress = "192.168.1.1",
            version = 1
        )

        // When
        val consentId = record.getConsentId()

        // Then
        assertEquals(id, consentId.value)
    }

    @Test
    fun `getCustomerId should return type-safe CustomerId`() {
        // Given
        val record = ConsentRecord.create(
            id = UUID.randomUUID(),
            customerId = customerId,
            consentType = ConsentType.MARKETING,
            granted = true,
            source = ConsentSource.PROFILE_WIZARD,
            ipAddress = "192.168.1.1",
            version = 1
        )

        // When
        val custId = record.getCustomerId()

        // Then
        assertEquals(customerId, custId.value)
    }

    @Test
    fun `equals should return true for same id`() {
        // Given
        val id = UUID.randomUUID()
        val record1 = ConsentRecord.create(
            id = id,
            customerId = customerId,
            consentType = ConsentType.MARKETING,
            granted = true,
            source = ConsentSource.PROFILE_WIZARD,
            ipAddress = "192.168.1.1",
            version = 1
        )
        val record2 = ConsentRecord.create(
            id = id,
            customerId = customerId,
            consentType = ConsentType.ANALYTICS,
            granted = false,
            source = ConsentSource.API,
            ipAddress = "10.0.0.1",
            version = 2
        )

        // When/Then
        assertEquals(record1, record2)
    }

    @Test
    fun `hashCode should be same for same id`() {
        // Given
        val id = UUID.randomUUID()
        val record1 = ConsentRecord.create(
            id = id,
            customerId = customerId,
            consentType = ConsentType.MARKETING,
            granted = true,
            source = ConsentSource.PROFILE_WIZARD,
            ipAddress = "192.168.1.1",
            version = 1
        )
        val record2 = ConsentRecord.create(
            id = id,
            customerId = customerId,
            consentType = ConsentType.ANALYTICS,
            granted = false,
            source = ConsentSource.API,
            ipAddress = "10.0.0.1",
            version = 2
        )

        // When/Then
        assertEquals(record1.hashCode(), record2.hashCode())
    }
}
