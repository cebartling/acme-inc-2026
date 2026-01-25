package com.acme.customer.infrastructure.cache

import com.acme.customer.api.v1.dto.*
import com.acme.customer.domain.CustomerStatus
import com.acme.customer.domain.CustomerType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CustomerCacheServiceTest {

    private lateinit var redisTemplate: RedisTemplate<String, Any>
    private lateinit var valueOperations: ValueOperations<String, Any>
    private lateinit var cacheService: CustomerCacheService

    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        redisTemplate = mockk()
        valueOperations = mockk()
        every { redisTemplate.opsForValue() } returns valueOperations

        cacheService = CustomerCacheService(redisTemplate)
    }

    private fun createCustomerResponse(): CustomerResponse {
        return CustomerResponse(
            customerId = UUID.randomUUID().toString(),
            userId = userId.toString(),
            customerNumber = "ACME-202601-000001",
            name = NameResponse(
                firstName = "John",
                lastName = "Doe",
                displayName = "John Doe"
            ),
            email = EmailResponse(
                address = "test@example.com",
                verified = true
            ),
            phone = PhoneResponse(
                countryCode = "+1",
                number = "5551234567",
                verified = true
            ),
            status = CustomerStatus.ACTIVE,
            type = CustomerType.INDIVIDUAL,
            profile = ProfileResponse(
                dateOfBirth = null,
                gender = null,
                preferredLocale = "en-US",
                timezone = "UTC",
                preferredCurrency = "USD"
            ),
            preferences = PreferencesResponse(
                communication = CommunicationPreferencesResponse(
                    email = true,
                    sms = false,
                    push = false,
                    marketing = false,
                    frequency = "IMMEDIATE"
                ),
                privacy = PrivacyPreferencesResponse(
                    shareDataWithPartners = false,
                    allowAnalytics = true,
                    allowPersonalization = true
                ),
                display = DisplayPreferencesResponse(
                    language = "en-US",
                    currency = "USD",
                    timezone = "UTC"
                )
            ),
            profileCompleteness = 75,
            registeredAt = Instant.now(),
            lastActivityAt = Instant.now()
        )
    }

    @Test
    fun `get should return cached profile when present`() {
        // Given
        val profile = createCustomerResponse()
        val expectedKey = "customer:profile:$userId"

        every { valueOperations.get(expectedKey) } returns profile

        // When
        val result = cacheService.get(userId)

        // Then
        assertNotNull(result)
        assertEquals(profile, result)
        verify(exactly = 1) { valueOperations.get(expectedKey) }
    }

    @Test
    fun `get should return null when profile not in cache`() {
        // Given
        val expectedKey = "customer:profile:$userId"

        every { valueOperations.get(expectedKey) } returns null

        // When
        val result = cacheService.get(userId)

        // Then
        assertNull(result)
        verify(exactly = 1) { valueOperations.get(expectedKey) }
    }

    @Test
    fun `get should return null on Redis error`() {
        // Given
        val expectedKey = "customer:profile:$userId"

        every { valueOperations.get(expectedKey) } throws RuntimeException("Redis connection failed")

        // When
        val result = cacheService.get(userId)

        // Then
        assertNull(result)
        verify(exactly = 1) { valueOperations.get(expectedKey) }
    }

    @Test
    fun `put should cache profile with TTL`() {
        // Given
        val profile = createCustomerResponse()
        val expectedKey = "customer:profile:$userId"
        val expectedTtl = Duration.ofMinutes(5)
        val keySlot = slot<String>()
        val profileSlot = slot<Any>()
        val ttlSlot = slot<Duration>()

        every { valueOperations.set(capture(keySlot), capture(profileSlot), capture(ttlSlot)) } returns Unit

        // When
        cacheService.put(userId, profile)

        // Then
        verify(exactly = 1) { valueOperations.set(any(), any(), any<Duration>()) }
        assertEquals(expectedKey, keySlot.captured)
        assertEquals(profile, profileSlot.captured)
        assertEquals(expectedTtl, ttlSlot.captured)
    }

    @Test
    fun `put should not throw on Redis error`() {
        // Given
        val profile = createCustomerResponse()
        val expectedKey = "customer:profile:$userId"

        every { valueOperations.set(expectedKey, profile, any<Duration>()) } throws RuntimeException("Redis connection failed")

        // When / Then - should not throw
        cacheService.put(userId, profile)

        verify(exactly = 1) { valueOperations.set(expectedKey, profile, any<Duration>()) }
    }

    @Test
    fun `invalidate should delete cache entry`() {
        // Given
        val expectedKey = "customer:profile:$userId"

        every { redisTemplate.delete(expectedKey) } returns true

        // When
        cacheService.invalidate(userId)

        // Then
        verify(exactly = 1) { redisTemplate.delete(expectedKey) }
    }

    @Test
    fun `invalidate should handle non-existent cache entry`() {
        // Given
        val expectedKey = "customer:profile:$userId"

        every { redisTemplate.delete(expectedKey) } returns false

        // When
        cacheService.invalidate(userId)

        // Then
        verify(exactly = 1) { redisTemplate.delete(expectedKey) }
    }

    @Test
    fun `invalidate should not throw on Redis error`() {
        // Given
        val expectedKey = "customer:profile:$userId"

        every { redisTemplate.delete(expectedKey) } throws RuntimeException("Redis connection failed")

        // When / Then - should not throw
        cacheService.invalidate(userId)

        verify(exactly = 1) { redisTemplate.delete(expectedKey) }
    }

    @Test
    fun `cache key format should use correct prefix`() {
        // Given
        val profile = createCustomerResponse()
        val keySlot = slot<String>()

        every { valueOperations.set(capture(keySlot), any(), any<Duration>()) } returns Unit

        // When
        cacheService.put(userId, profile)

        // Then
        val expectedKey = "customer:profile:$userId"
        assertEquals(expectedKey, keySlot.captured)
    }

    @Test
    fun `TTL should be 5 minutes`() {
        // Given
        val profile = createCustomerResponse()
        val ttlSlot = slot<Duration>()

        every { valueOperations.set(any(), any(), capture(ttlSlot)) } returns Unit

        // When
        cacheService.put(userId, profile)

        // Then
        assertEquals(Duration.ofMinutes(5), ttlSlot.captured)
    }
}
