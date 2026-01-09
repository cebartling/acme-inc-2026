package com.acme.notification.infrastructure.client.dto

import java.time.Instant

/**
 * DTO for customer data from the Customer Service API.
 *
 * @property customerId The unique identifier for the customer (UUID format).
 * @property userId The corresponding user ID from the Identity Service (UUID format).
 * @property customerNumber Human-readable customer number (format: ACME-YYYYMM-NNNNNN).
 * @property name The customer's name details.
 * @property email The customer's email details.
 * @property status Current lifecycle status of the customer. Possible values: PENDING_VERIFICATION, ACTIVE, SUSPENDED, DELETED.
 * @property preferences The customer's communication and privacy preferences.
 * @property profileCompleteness Percentage of profile completion (0-100).
 * @property registeredAt Timestamp when the customer registered.
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

/**
 * DTO for customer name information.
 *
 * @property firstName The customer's first/given name.
 * @property lastName The customer's last/family name.
 * @property displayName The customer's display name (typically "FirstName LastName").
 */
data class NameDto(
    val firstName: String,
    val lastName: String,
    val displayName: String
)

/**
 * DTO for customer email information.
 *
 * @property address The customer's email address.
 * @property verified Whether the email address has been verified.
 */
data class EmailDto(
    val address: String,
    val verified: Boolean
)

/**
 * DTO for customer preferences.
 *
 * @property communication The customer's communication preferences.
 */
data class PreferencesDto(
    val communication: CommunicationPreferencesDto
)

/**
 * DTO for customer communication preferences.
 *
 * @property email Whether the customer has enabled email notifications.
 * @property sms Whether the customer has enabled SMS notifications.
 * @property push Whether the customer has enabled push notifications.
 * @property marketing Whether the customer has opted in to marketing communications.
 */
data class CommunicationPreferencesDto(
    val email: Boolean,
    val sms: Boolean,
    val push: Boolean,
    val marketing: Boolean
)
