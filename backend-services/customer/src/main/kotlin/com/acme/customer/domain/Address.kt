package com.acme.customer.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * JPA entity representing a customer address.
 *
 * This entity stores address information including street details, city, state,
 * postal code, country, and optional geocoordinates. Addresses can be marked
 * as default for their type (SHIPPING or BILLING), and only one address can
 * be default per type per customer.
 *
 * @property id Unique identifier for the address (UUID v7, time-ordered).
 * @property customerId The ID of the customer who owns this address.
 * @property type The type of address (SHIPPING or BILLING).
 * @property label Optional human-readable label for the address (e.g., "Home", "Work").
 * @property streetLine1 Primary street address line.
 * @property streetLine2 Secondary street address line (apartment, suite, etc.).
 * @property city City name.
 * @property state State or province.
 * @property postalCode Postal/ZIP code.
 * @property country ISO 3166-1 alpha-2 country code (e.g., "US").
 * @property isDefault Whether this is the default address for its type.
 * @property isValidated Whether the address has been validated against postal standards.
 * @property latitude Geographic latitude coordinate (optional).
 * @property longitude Geographic longitude coordinate (optional).
 * @property validationDetails JSON details from address validation (optional).
 * @property createdAt Timestamp when the address was created.
 * @property updatedAt Timestamp when the address was last modified.
 */
@Entity
@Table(name = "customer_addresses")
class Address(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "customer_id", nullable = false, updatable = false)
    val customerId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    var type: AddressType,

    @Column(name = "label", length = 50)
    var label: String? = null,

    @Column(name = "street_line1", nullable = false, length = 100)
    var streetLine1: String,

    @Column(name = "street_line2", length = 100)
    var streetLine2: String? = null,

    @Column(name = "city", nullable = false, length = 50)
    var city: String,

    @Column(name = "state", nullable = false, length = 50)
    var state: String,

    @Column(name = "postal_code", nullable = false, length = 20)
    var postalCode: String,

    @Column(name = "country", nullable = false, length = 2)
    var country: String,

    @Column(name = "is_default", nullable = false)
    var isDefault: Boolean = false,

    @Column(name = "is_validated", nullable = false)
    var isValidated: Boolean = false,

    @Column(name = "latitude", precision = 10, scale = 8)
    var latitude: BigDecimal? = null,

    @Column(name = "longitude", precision = 11, scale = 8)
    var longitude: BigDecimal? = null,

    @Column(name = "validation_details", columnDefinition = "jsonb")
    var validationDetails: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    /**
     * Returns the address ID wrapped in a type-safe [AddressId] value class.
     *
     * @return The address identifier as an [AddressId].
     */
    fun getAddressId(): AddressId = AddressId(id)

    /**
     * Returns the customer ID wrapped in a type-safe [CustomerId] value class.
     *
     * @return The customer identifier as a [CustomerId].
     */
    fun getCustomerId(): CustomerId = CustomerId(customerId)

    /**
     * Checks if this address is a PO Box address.
     *
     * @return True if the street address appears to be a PO Box.
     */
    fun isPOBox(): Boolean {
        val poBoxPatterns = listOf(
            Regex("^P\\.?\\s*O\\.?\\s*Box", RegexOption.IGNORE_CASE),
            Regex("^Post\\s*Office\\s*Box", RegexOption.IGNORE_CASE),
            Regex("^PO\\s*Box", RegexOption.IGNORE_CASE)
        )
        return poBoxPatterns.any { it.containsMatchIn(streetLine1) }
    }

    /**
     * Updates the address details.
     *
     * @param streetLine1 Primary street address line.
     * @param streetLine2 Secondary street address line (optional).
     * @param city City name.
     * @param state State or province.
     * @param postalCode Postal/ZIP code.
     * @param country ISO 3166-1 alpha-2 country code.
     * @param label Optional label for the address.
     */
    fun updateDetails(
        streetLine1: String,
        streetLine2: String?,
        city: String,
        state: String,
        postalCode: String,
        country: String,
        label: String?
    ) {
        this.streetLine1 = streetLine1
        this.streetLine2 = streetLine2
        this.city = city
        this.state = state
        this.postalCode = postalCode
        this.country = country
        this.label = label
        this.isValidated = false // Reset validation when address changes
        this.latitude = null
        this.longitude = null
        this.validationDetails = null
        this.updatedAt = Instant.now()
    }

    /**
     * Updates the address type.
     *
     * @param type The new address type.
     */
    fun updateType(type: AddressType) {
        this.type = type
        this.updatedAt = Instant.now()
    }

    /**
     * Sets the validation result for this address.
     *
     * @param isValid Whether the address is valid.
     * @param latitude Optional latitude coordinate.
     * @param longitude Optional longitude coordinate.
     * @param details Optional validation details JSON.
     */
    fun setValidationResult(
        isValid: Boolean,
        latitude: BigDecimal? = null,
        longitude: BigDecimal? = null,
        details: String? = null
    ) {
        this.isValidated = isValid
        this.latitude = latitude
        this.longitude = longitude
        this.validationDetails = details
        this.updatedAt = Instant.now()
    }

    /**
     * Sets this address as the default for its type.
     *
     * @param isDefault Whether this address should be the default.
     */
    fun setAsDefault(isDefault: Boolean) {
        this.isDefault = isDefault
        this.updatedAt = Instant.now()
    }

    /**
     * Returns the full address as a formatted string.
     *
     * @return The formatted address string.
     */
    fun getFormattedAddress(): String {
        return buildString {
            append(streetLine1)
            if (!streetLine2.isNullOrBlank()) {
                append(", ")
                append(streetLine2)
            }
            append(", ")
            append(city)
            append(", ")
            append(state)
            append(" ")
            append(postalCode)
            append(", ")
            append(country)
        }
    }

    /**
     * Checks if this address has geocoordinates.
     *
     * @return True if both latitude and longitude are set.
     */
    fun hasCoordinates(): Boolean = latitude != null && longitude != null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Address) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    companion object {
        /** Maximum number of addresses allowed per type per customer. */
        const val MAX_ADDRESSES_PER_TYPE = 10

        /**
         * Creates a new address for a customer.
         *
         * @param id The address UUID (should be UUID v7).
         * @param customerId The customer's UUID.
         * @param type The address type.
         * @param streetLine1 Primary street address.
         * @param streetLine2 Secondary street address (optional).
         * @param city City name.
         * @param state State or province.
         * @param postalCode Postal/ZIP code.
         * @param country ISO 3166-1 alpha-2 country code.
         * @param label Optional label for the address.
         * @param isDefault Whether this is the default address.
         * @return A new [Address] instance.
         */
        fun create(
            id: UUID,
            customerId: UUID,
            type: AddressType,
            streetLine1: String,
            streetLine2: String? = null,
            city: String,
            state: String,
            postalCode: String,
            country: String,
            label: String? = null,
            isDefault: Boolean = false
        ): Address {
            return Address(
                id = id,
                customerId = customerId,
                type = type,
                label = label,
                streetLine1 = streetLine1,
                streetLine2 = streetLine2,
                city = city,
                state = state,
                postalCode = postalCode,
                country = country,
                isDefault = isDefault,
                isValidated = false
            )
        }
    }
}
