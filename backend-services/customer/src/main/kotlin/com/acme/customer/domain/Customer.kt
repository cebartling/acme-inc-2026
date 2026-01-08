package com.acme.customer.domain

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * JPA entity representing a customer in the customer management system.
 *
 * This entity stores customer profile information including personal details,
 * contact information, and preferences. It is created when a UserRegistered
 * event is consumed from the Identity Service.
 *
 * @property id Unique identifier for the customer (UUID v7, time-ordered).
 * @property userId The corresponding user ID from the Identity Service.
 * @property customerNumber Human-readable customer number (ACME-YYYYMM-NNNNNN).
 * @property firstName Customer's first/given name.
 * @property lastName Customer's last/family name.
 * @property displayName Customer's display name (typically "FirstName LastName").
 * @property email Customer's email address.
 * @property emailVerified Whether the email has been verified.
 * @property phoneCountryCode Country code for phone number (e.g., "+1").
 * @property phoneNumber Phone number without country code.
 * @property phoneVerified Whether the phone has been verified.
 * @property status Current lifecycle status of the customer.
 * @property type Type of customer account (INDIVIDUAL or BUSINESS).
 * @property dateOfBirth Customer's date of birth (optional).
 * @property gender Customer's gender (optional).
 * @property preferredLocale Customer's preferred locale (e.g., "en-US").
 * @property timezone Customer's timezone (e.g., "UTC").
 * @property preferredCurrency Customer's preferred currency (e.g., "USD").
 * @property profileCompleteness Percentage of profile completion (0-100).
 * @property registeredAt Timestamp when the customer registered.
 * @property lastActivityAt Timestamp of last customer activity.
 * @property createdAt Timestamp when the record was created.
 * @property updatedAt Timestamp when the record was last modified.
 */
@Entity
@Table(name = "customers")
class Customer(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    val userId: UUID,

    @Column(name = "customer_number", nullable = false, unique = true, updatable = false, length = 20)
    val customerNumber: String,

    @Column(name = "first_name", nullable = false, length = 50)
    val firstName: String,

    @Column(name = "last_name", nullable = false, length = 50)
    val lastName: String,

    @Column(name = "display_name", nullable = false, length = 100)
    val displayName: String,

    @Column(name = "email", nullable = false)
    val email: String,

    @Column(name = "email_verified", nullable = false)
    var emailVerified: Boolean = false,

    @Column(name = "phone_country_code", length = 5)
    val phoneCountryCode: String? = null,

    @Column(name = "phone_number", length = 20)
    val phoneNumber: String? = null,

    @Column(name = "phone_verified")
    val phoneVerified: Boolean? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    var status: CustomerStatus = CustomerStatus.PENDING_VERIFICATION,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    val type: CustomerType = CustomerType.INDIVIDUAL,

    @Column(name = "date_of_birth")
    val dateOfBirth: LocalDate? = null,

    @Column(name = "gender", length = 20)
    val gender: String? = null,

    @Column(name = "preferred_locale", nullable = false, length = 10)
    val preferredLocale: String = "en-US",

    @Column(name = "timezone", nullable = false, length = 50)
    val timezone: String = "UTC",

    @Column(name = "preferred_currency", nullable = false, length = 3)
    val preferredCurrency: String = "USD",

    @Column(name = "profile_completeness", nullable = false)
    val profileCompleteness: Int = 25,

    @Column(name = "registered_at", nullable = false, updatable = false)
    val registeredAt: Instant,

    @Column(name = "last_activity_at", nullable = false)
    var lastActivityAt: Instant,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    /**
     * Returns the customer's ID wrapped in a type-safe [CustomerId] value class.
     *
     * @return The customer's identifier as a [CustomerId].
     */
    fun getCustomerId(): CustomerId = CustomerId(id)

    /**
     * Activates the customer profile.
     *
     * Updates the status to ACTIVE, sets emailVerified to true,
     * and updates the lastActivityAt and updatedAt timestamps.
     *
     * @param activatedAt The timestamp when the activation occurred.
     * @throws IllegalStateException If the customer is not in PENDING_VERIFICATION status.
     */
    fun activate(activatedAt: Instant) {
        if (status != CustomerStatus.PENDING_VERIFICATION) {
            if (status == CustomerStatus.ACTIVE) {
                return // Already active, idempotent operation
            }
            throw IllegalStateException(
                "Cannot activate customer $id: current status is $status, expected PENDING_VERIFICATION"
            )
        }
        status = CustomerStatus.ACTIVE
        emailVerified = true
        lastActivityAt = activatedAt
        updatedAt = Instant.now()
    }

    /**
     * Checks if the customer is in an activatable state.
     *
     * @return True if the customer can be activated.
     */
    fun canBeActivated(): Boolean = status == CustomerStatus.PENDING_VERIFICATION

    /**
     * Checks if the customer is already active.
     *
     * @return True if the customer is active.
     */
    fun isActive(): Boolean = status == CustomerStatus.ACTIVE

    /**
     * Returns the customer number as a type-safe [CustomerNumber] value class.
     *
     * @return The customer number.
     */
    fun getCustomerNumber(): CustomerNumber = CustomerNumber(customerNumber)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Customer) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    companion object {
        /**
         * Creates a new customer from registration data.
         *
         * @param id The customer's UUID (should be UUID v7).
         * @param userId The corresponding user ID from Identity Service.
         * @param customerNumber The generated customer number.
         * @param email The customer's email address.
         * @param firstName The customer's first name.
         * @param lastName The customer's last name.
         * @param registeredAt When the customer registered.
         * @return A new [Customer] instance with default values applied.
         */
        fun createFromRegistration(
            id: UUID,
            userId: UUID,
            customerNumber: String,
            email: String,
            firstName: String,
            lastName: String,
            registeredAt: Instant
        ): Customer {
            val displayName = "$firstName $lastName"
            return Customer(
                id = id,
                userId = userId,
                customerNumber = customerNumber,
                firstName = firstName,
                lastName = lastName,
                displayName = displayName,
                email = email,
                emailVerified = false,
                status = CustomerStatus.PENDING_VERIFICATION,
                type = CustomerType.INDIVIDUAL,
                preferredLocale = "en-US",
                timezone = "UTC",
                preferredCurrency = "USD",
                profileCompleteness = 25,
                registeredAt = registeredAt,
                lastActivityAt = registeredAt
            )
        }
    }
}
