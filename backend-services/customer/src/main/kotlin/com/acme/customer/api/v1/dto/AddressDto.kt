package com.acme.customer.api.v1.dto

import com.acme.customer.domain.Address
import java.math.BigDecimal
import java.time.Instant

/**
 * Street address component of an address request.
 */
data class StreetDto(
    val line1: String,
    val line2: String? = null
)

/**
 * Request DTO for adding a new address.
 */
data class AddAddressRequest(
    val type: String,
    val label: String? = null,
    val street: StreetDto,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String,
    val isDefault: Boolean = false
)

/**
 * Request DTO for updating an existing address.
 */
data class UpdateAddressRequest(
    val type: String? = null,
    val label: String? = null,
    val street: StreetDto? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    val isDefault: Boolean? = null,
    val overrideValidation: Boolean = false
) {
    /**
     * Checks if there are any updates in the request.
     */
    fun hasUpdates(): Boolean {
        return type != null ||
            label != null ||
            street != null ||
            city != null ||
            state != null ||
            postalCode != null ||
            country != null ||
            isDefault != null
    }
}

/**
 * Coordinates component of an address response.
 */
data class CoordinatesDto(
    val latitude: BigDecimal,
    val longitude: BigDecimal
)

/**
 * Response DTO for address data.
 */
data class AddressResponse(
    val addressId: String,
    val customerId: String,
    val type: String,
    val label: String?,
    val street: StreetDto,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String,
    val isDefault: Boolean,
    val isValidated: Boolean,
    val coordinates: CoordinatesDto?,
    val createdAt: Instant
) {
    companion object {
        /**
         * Creates an [AddressResponse] from an [Address] entity.
         */
        fun fromEntity(address: Address): AddressResponse {
            return AddressResponse(
                addressId = address.id.toString(),
                customerId = address.customerId.toString(),
                type = address.type.name,
                label = address.label,
                street = StreetDto(
                    line1 = address.streetLine1,
                    line2 = address.streetLine2
                ),
                city = address.city,
                state = address.state,
                postalCode = address.postalCode,
                country = address.country,
                isDefault = address.isDefault,
                isValidated = address.isValidated,
                coordinates = if (address.hasCoordinates()) {
                    CoordinatesDto(
                        latitude = address.latitude!!,
                        longitude = address.longitude!!
                    )
                } else null,
                createdAt = address.createdAt
            )
        }
    }
}

/**
 * Response DTO for address validation failure with suggestions.
 */
data class AddressValidationResponse(
    val error: String = "ADDRESS_VALIDATION_FAILED",
    val message: String,
    val suggestions: List<AddressSuggestionDto>,
    val validationDetails: Map<String, String>
)

/**
 * Suggested address correction.
 */
data class AddressSuggestionDto(
    val street: StreetDto,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String
)
