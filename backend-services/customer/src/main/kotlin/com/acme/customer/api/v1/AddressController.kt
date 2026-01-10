package com.acme.customer.api.v1

import com.acme.customer.api.v1.dto.AddAddressRequest
import com.acme.customer.api.v1.dto.AddressResponse
import com.acme.customer.api.v1.dto.UpdateAddressRequest
import com.acme.customer.application.AddAddressResult
import com.acme.customer.application.AddAddressUseCase
import com.acme.customer.application.RemoveAddressResult
import com.acme.customer.application.RemoveAddressUseCase
import com.acme.customer.application.UpdateAddressResult
import com.acme.customer.application.UpdateAddressUseCase
import com.acme.customer.domain.Address
import com.acme.customer.domain.AddressType
import com.acme.customer.infrastructure.persistence.AddressRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI
import java.util.UUID

/**
 * REST controller for customer address operations.
 *
 * Provides CRUD endpoints for managing customer addresses including
 * shipping and billing addresses.
 */
@RestController
@RequestMapping("/api/v1/customers/{customerId}/addresses")
class AddressController(
    private val customerRepository: CustomerRepository,
    private val addressRepository: AddressRepository,
    private val addAddressUseCase: AddAddressUseCase,
    private val updateAddressUseCase: UpdateAddressUseCase,
    private val removeAddressUseCase: RemoveAddressUseCase
) {
    private val logger = LoggerFactory.getLogger(AddressController::class.java)

    /**
     * Lists all addresses for a customer.
     *
     * @param customerId The customer ID (UUID).
     * @param userId The user ID from the Authorization header (for authorization).
     * @param type Optional filter by address type (SHIPPING or BILLING).
     * @return List of addresses or appropriate error response.
     */
    @GetMapping
    fun listAddresses(
        @PathVariable customerId: String,
        @RequestHeader("X-User-Id") userId: String,
        @RequestParam(required = false) type: String?
    ): ResponseEntity<Any> {
        logger.debug("Listing addresses for customer: {}", customerId)

        val parsedCustomerId = parseUUID(customerId, "customer ID") ?: return invalidIdResponse("customer ID")
        val parsedUserId = parseUUID(userId, "user ID") ?: return invalidIdResponse("user ID")

        // Find customer and verify authorization
        val customer = customerRepository.findById(parsedCustomerId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        if (customer.userId != parsedUserId) {
            return ResponseEntity.status(403)
                .body(mapOf("error" to "You are not authorized to view these addresses"))
        }

        val addresses = if (type != null) {
            val addressType = try {
                AddressType.valueOf(type.uppercase())
            } catch (e: IllegalArgumentException) {
                return ResponseEntity.badRequest()
                    .body(mapOf("error" to "Invalid address type. Allowed values: SHIPPING, BILLING"))
            }
            addressRepository.findByCustomerIdAndType(parsedCustomerId, addressType)
        } else {
            addressRepository.findByCustomerId(parsedCustomerId)
        }

        val responseList = addresses.map { AddressResponse.fromEntity(it) }
        return ResponseEntity.ok(responseList)
    }

    /**
     * Gets a specific address by ID.
     *
     * @param customerId The customer ID (UUID).
     * @param addressId The address ID (UUID).
     * @param userId The user ID from the Authorization header (for authorization).
     * @return The address or appropriate error response.
     */
    @GetMapping("/{addressId}")
    fun getAddress(
        @PathVariable customerId: String,
        @PathVariable addressId: String,
        @RequestHeader("X-User-Id") userId: String
    ): ResponseEntity<Any> {
        logger.debug("Getting address {} for customer {}", addressId, customerId)

        val parsedCustomerId = parseUUID(customerId, "customer ID") ?: return invalidIdResponse("customer ID")
        val parsedAddressId = parseUUID(addressId, "address ID") ?: return invalidIdResponse("address ID")
        val parsedUserId = parseUUID(userId, "user ID") ?: return invalidIdResponse("user ID")

        // Find customer and verify authorization
        val customer = customerRepository.findById(parsedCustomerId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        if (customer.userId != parsedUserId) {
            return ResponseEntity.status(403)
                .body(mapOf("error" to "You are not authorized to view this address"))
        }

        // Find address
        val address = addressRepository.findById(parsedAddressId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        // Verify address belongs to customer
        if (address.customerId != parsedCustomerId) {
            return ResponseEntity.notFound().build()
        }

        return ResponseEntity.ok(AddressResponse.fromEntity(address))
    }

    /**
     * Adds a new address for a customer.
     *
     * @param customerId The customer ID (UUID).
     * @param userId The user ID from the Authorization header (for authorization).
     * @param correlationId Optional correlation ID for distributed tracing.
     * @param request The add address request.
     * @return The created address or appropriate error response.
     */
    @PostMapping
    fun addAddress(
        @PathVariable customerId: String,
        @RequestHeader("X-User-Id") userId: String,
        @RequestHeader("X-Correlation-Id", required = false) correlationId: String?,
        @RequestBody request: AddAddressRequest
    ): ResponseEntity<Any> {
        logger.debug("Adding address for customer: {}", customerId)

        val parsedCustomerId = parseUUID(customerId, "customer ID") ?: return invalidIdResponse("customer ID")
        val parsedUserId = parseUUID(userId, "user ID") ?: return invalidIdResponse("user ID")
        val parsedCorrelationId = parseCorrelationId(correlationId)

        return when (val result = addAddressUseCase.execute(parsedCustomerId, parsedUserId, request, parsedCorrelationId)) {
            is AddAddressResult.Success -> {
                val response = AddressResponse.fromEntity(result.address)
                ResponseEntity.created(URI.create("/api/v1/customers/$customerId/addresses/${result.address.id}"))
                    .body(response)
            }
            is AddAddressResult.CustomerNotFound -> {
                ResponseEntity.notFound().build()
            }
            is AddAddressResult.Unauthorized -> {
                ResponseEntity.status(403)
                    .body(mapOf("error" to "You are not authorized to add addresses to this customer"))
            }
            is AddAddressResult.ValidationFailed -> {
                ResponseEntity.badRequest()
                    .body(mapOf("error" to "VALIDATION_ERROR", "errors" to result.errors))
            }
            is AddAddressResult.AddressValidationNeeded -> {
                ResponseEntity.badRequest()
                    .body(mapOf(
                        "error" to "ADDRESS_VALIDATION_FAILED",
                        "message" to result.message,
                        "suggestions" to result.suggestions,
                        "validationDetails" to result.validationDetails
                    ))
            }
            is AddAddressResult.DuplicateLabel -> {
                ResponseEntity.badRequest()
                    .body(mapOf("error" to "DUPLICATE_LABEL", "message" to "Address label already exists: ${result.label}"))
            }
            is AddAddressResult.MaxAddressesReached -> {
                ResponseEntity.badRequest()
                    .body(mapOf(
                        "error" to "MAX_ADDRESSES_REACHED",
                        "message" to "Maximum of ${result.maxAllowed} addresses per type allowed",
                        "type" to result.type
                    ))
            }
            is AddAddressResult.POBoxNotAllowedForShipping -> {
                ResponseEntity.badRequest()
                    .body(mapOf(
                        "error" to "PO_BOX_NOT_ALLOWED",
                        "message" to "PO Box addresses cannot be used for shipping"
                    ))
            }
            is AddAddressResult.Failure -> {
                logger.error("Failed to add address: {}", result.message, result.cause)
                ResponseEntity.internalServerError()
                    .body(mapOf("error" to "An internal error occurred"))
            }
        }
    }

    /**
     * Updates an existing address.
     *
     * @param customerId The customer ID (UUID).
     * @param addressId The address ID (UUID).
     * @param userId The user ID from the Authorization header (for authorization).
     * @param correlationId Optional correlation ID for distributed tracing.
     * @param request The update address request.
     * @return The updated address or appropriate error response.
     */
    @PutMapping("/{addressId}")
    fun updateAddress(
        @PathVariable customerId: String,
        @PathVariable addressId: String,
        @RequestHeader("X-User-Id") userId: String,
        @RequestHeader("X-Correlation-Id", required = false) correlationId: String?,
        @RequestBody request: UpdateAddressRequest
    ): ResponseEntity<Any> {
        logger.debug("Updating address {} for customer {}", addressId, customerId)

        val parsedCustomerId = parseUUID(customerId, "customer ID") ?: return invalidIdResponse("customer ID")
        val parsedAddressId = parseUUID(addressId, "address ID") ?: return invalidIdResponse("address ID")
        val parsedUserId = parseUUID(userId, "user ID") ?: return invalidIdResponse("user ID")
        val parsedCorrelationId = parseCorrelationId(correlationId)

        return when (val result = updateAddressUseCase.execute(parsedCustomerId, parsedAddressId, parsedUserId, request, parsedCorrelationId)) {
            is UpdateAddressResult.Success -> {
                ResponseEntity.ok(AddressResponse.fromEntity(result.address))
            }
            is UpdateAddressResult.AddressNotFound -> {
                ResponseEntity.notFound().build()
            }
            is UpdateAddressResult.CustomerNotFound -> {
                ResponseEntity.notFound().build()
            }
            is UpdateAddressResult.Unauthorized -> {
                ResponseEntity.status(403)
                    .body(mapOf("error" to "You are not authorized to update this address"))
            }
            is UpdateAddressResult.ValidationFailed -> {
                ResponseEntity.badRequest()
                    .body(mapOf("error" to "VALIDATION_ERROR", "errors" to result.errors))
            }
            is UpdateAddressResult.DuplicateLabel -> {
                ResponseEntity.badRequest()
                    .body(mapOf("error" to "DUPLICATE_LABEL", "message" to "Address label already exists: ${result.label}"))
            }
            is UpdateAddressResult.POBoxNotAllowedForShipping -> {
                ResponseEntity.badRequest()
                    .body(mapOf(
                        "error" to "PO_BOX_NOT_ALLOWED",
                        "message" to "PO Box addresses cannot be used for shipping"
                    ))
            }
            is UpdateAddressResult.NoUpdates -> {
                ResponseEntity.badRequest()
                    .body(mapOf("error" to "No updates provided"))
            }
            is UpdateAddressResult.Failure -> {
                logger.error("Failed to update address: {}", result.message, result.cause)
                ResponseEntity.internalServerError()
                    .body(mapOf("error" to "An internal error occurred"))
            }
        }
    }

    /**
     * Deletes an address.
     *
     * @param customerId The customer ID (UUID).
     * @param addressId The address ID (UUID).
     * @param userId The user ID from the Authorization header (for authorization).
     * @param correlationId Optional correlation ID for distributed tracing.
     * @return 204 No Content on success or appropriate error response.
     */
    @DeleteMapping("/{addressId}")
    fun deleteAddress(
        @PathVariable customerId: String,
        @PathVariable addressId: String,
        @RequestHeader("X-User-Id") userId: String,
        @RequestHeader("X-Correlation-Id", required = false) correlationId: String?
    ): ResponseEntity<Any> {
        logger.debug("Deleting address {} for customer {}", addressId, customerId)

        val parsedCustomerId = parseUUID(customerId, "customer ID") ?: return invalidIdResponse("customer ID")
        val parsedAddressId = parseUUID(addressId, "address ID") ?: return invalidIdResponse("address ID")
        val parsedUserId = parseUUID(userId, "user ID") ?: return invalidIdResponse("user ID")
        val parsedCorrelationId = parseCorrelationId(correlationId)

        return when (val result = removeAddressUseCase.execute(parsedCustomerId, parsedAddressId, parsedUserId, parsedCorrelationId)) {
            is RemoveAddressResult.Success -> {
                ResponseEntity.noContent().build()
            }
            is RemoveAddressResult.AddressNotFound -> {
                ResponseEntity.notFound().build()
            }
            is RemoveAddressResult.CustomerNotFound -> {
                ResponseEntity.notFound().build()
            }
            is RemoveAddressResult.Unauthorized -> {
                ResponseEntity.status(403)
                    .body(mapOf("error" to "You are not authorized to delete this address"))
            }
            is RemoveAddressResult.Failure -> {
                logger.error("Failed to delete address: {}", result.message, result.cause)
                ResponseEntity.internalServerError()
                    .body(mapOf("error" to "An internal error occurred"))
            }
        }
    }

    /**
     * Gets the default address for a specific type.
     *
     * @param customerId The customer ID (UUID).
     * @param type The address type (SHIPPING or BILLING).
     * @param userId The user ID from the Authorization header (for authorization).
     * @return The default address or 404 if none exists.
     */
    @GetMapping("/default/{type}")
    fun getDefaultAddress(
        @PathVariable customerId: String,
        @PathVariable type: String,
        @RequestHeader("X-User-Id") userId: String
    ): ResponseEntity<Any> {
        logger.debug("Getting default {} address for customer {}", type, customerId)

        val parsedCustomerId = parseUUID(customerId, "customer ID") ?: return invalidIdResponse("customer ID")
        val parsedUserId = parseUUID(userId, "user ID") ?: return invalidIdResponse("user ID")

        val addressType = try {
            AddressType.valueOf(type.uppercase())
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest()
                .body(mapOf("error" to "Invalid address type. Allowed values: SHIPPING, BILLING"))
        }

        // Find customer and verify authorization
        val customer = customerRepository.findById(parsedCustomerId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        if (customer.userId != parsedUserId) {
            return ResponseEntity.status(403)
                .body(mapOf("error" to "You are not authorized to view this address"))
        }

        val address = addressRepository.findByCustomerIdAndTypeAndIsDefaultTrue(parsedCustomerId, addressType)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(AddressResponse.fromEntity(address))
    }

    // Helper methods

    private fun parseUUID(value: String, fieldName: String): UUID? {
        return try {
            UUID.fromString(value)
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid {} format: {}", fieldName, value)
            null
        }
    }

    private fun invalidIdResponse(fieldName: String): ResponseEntity<Any> {
        return ResponseEntity.badRequest()
            .body(mapOf("error" to "Invalid $fieldName format"))
    }

    private fun parseCorrelationId(correlationId: String?): UUID {
        return correlationId?.let {
            try {
                UUID.fromString(it)
            } catch (e: IllegalArgumentException) {
                UUID.randomUUID()
            }
        } ?: UUID.randomUUID()
    }
}
