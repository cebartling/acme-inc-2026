package com.acme.notification.infrastructure.client.dto

import java.time.Instant

/**
 * DTO for customer data from the Customer Service API.
 */
data class CustomerDto(
    val customerId: String,
    val userId: String,
    val customerNumber: String,
    val name: NameDto,
    val email: EmailDto,
    val status: String,
    val preferences: PreferencesDto,
    val profileCompleteness: Int,
    val registeredAt: Instant
)

data class NameDto(
    val firstName: String,
    val lastName: String,
    val displayName: String
)

data class EmailDto(
    val address: String,
    val verified: Boolean
)

data class PreferencesDto(
    val communication: CommunicationPreferencesDto
)

data class CommunicationPreferencesDto(
    val email: Boolean,
    val sms: Boolean,
    val push: Boolean,
    val marketing: Boolean
)
