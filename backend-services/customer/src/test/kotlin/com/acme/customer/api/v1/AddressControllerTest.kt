package com.acme.customer.api.v1

import com.acme.customer.api.v1.dto.AddAddressRequest
import com.acme.customer.api.v1.dto.StreetDto
import com.acme.customer.api.v1.dto.UpdateAddressRequest
import com.acme.customer.application.AddAddressResult
import com.acme.customer.application.AddAddressUseCase
import com.acme.customer.application.AddressSuggestion
import com.acme.customer.application.RemoveAddressResult
import com.acme.customer.application.RemoveAddressUseCase
import com.acme.customer.application.UpdateAddressResult
import com.acme.customer.application.UpdateAddressUseCase
import com.acme.customer.domain.Address
import com.acme.customer.domain.AddressType
import com.acme.customer.domain.Customer
import com.acme.customer.domain.CustomerStatus
import com.acme.customer.domain.CustomerType
import com.acme.customer.infrastructure.persistence.AddressRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AddressControllerTest {

    private lateinit var customerRepository: CustomerRepository
    private lateinit var addressRepository: AddressRepository
    private lateinit var addAddressUseCase: AddAddressUseCase
    private lateinit var updateAddressUseCase: UpdateAddressUseCase
    private lateinit var removeAddressUseCase: RemoveAddressUseCase
    private lateinit var controller: AddressController

    private val customerId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val addressId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        customerRepository = mockk()
        addressRepository = mockk()
        addAddressUseCase = mockk()
        updateAddressUseCase = mockk()
        removeAddressUseCase = mockk()

        controller = AddressController(
            customerRepository = customerRepository,
            addressRepository = addressRepository,
            addAddressUseCase = addAddressUseCase,
            updateAddressUseCase = updateAddressUseCase,
            removeAddressUseCase = removeAddressUseCase
        )
    }

    private fun createCustomer(): Customer {
        val now = Instant.now()
        return Customer(
            id = customerId,
            userId = userId,
            customerNumber = "ACME-202601-000001",
            email = "test@example.com",
            firstName = "John",
            lastName = "Doe",
            displayName = "John Doe",
            status = CustomerStatus.ACTIVE,
            type = CustomerType.INDIVIDUAL,
            emailVerified = true,
            phoneVerified = true,
            registeredAt = now,
            lastActivityAt = now,
            profileCompleteness = 25
        )
    }

    private fun createAddress(): Address {
        return Address(
            id = addressId,
            customerId = customerId,
            type = AddressType.SHIPPING,
            label = "Home",
            streetLine1 = "123 Main St",
            streetLine2 = "Apt 4B",
            city = "New York",
            state = "NY",
            postalCode = "10001",
            country = "US",
            isDefault = true,
            isValidated = true,
            createdAt = Instant.now()
        )
    }

    @Nested
    inner class ListAddresses {

        @Test
        fun `should return 400 for invalid customer ID format`() {
            val response = controller.listAddresses(
                customerId = "invalid-uuid",
                userId = userId.toString(),
                type = null
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("Invalid customer ID format", body["error"])
        }

        @Test
        fun `should return 400 for invalid user ID format`() {
            val response = controller.listAddresses(
                customerId = customerId.toString(),
                userId = "invalid-uuid",
                type = null
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("Invalid user ID format", body["error"])
        }

        @Test
        fun `should return 404 when customer not found`() {
            every { customerRepository.findById(customerId) } returns Optional.empty()

            val response = controller.listAddresses(
                customerId = customerId.toString(),
                userId = userId.toString(),
                type = null
            )

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `should return 403 when not authorized`() {
            val otherUserId = UUID.randomUUID()
            every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())

            val response = controller.listAddresses(
                customerId = customerId.toString(),
                userId = otherUserId.toString(),
                type = null
            )

            assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("You are not authorized to view these addresses", body["error"])
        }

        @Test
        fun `should return 400 for invalid address type`() {
            every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())

            val response = controller.listAddresses(
                customerId = customerId.toString(),
                userId = userId.toString(),
                type = "INVALID"
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("Invalid address type. Allowed values: SHIPPING, BILLING", body["error"])
        }

        @Test
        fun `should return 200 with all addresses when no type filter`() {
            every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())
            every { addressRepository.findByCustomerId(customerId) } returns listOf(createAddress())

            val response = controller.listAddresses(
                customerId = customerId.toString(),
                userId = userId.toString(),
                type = null
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            assertNotNull(response.body)
            @Suppress("UNCHECKED_CAST")
            val addresses = response.body as List<*>
            assertEquals(1, addresses.size)
        }

        @Test
        fun `should return 200 with filtered addresses when type specified`() {
            every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())
            every { addressRepository.findByCustomerIdAndType(customerId, AddressType.SHIPPING) } returns listOf(createAddress())

            val response = controller.listAddresses(
                customerId = customerId.toString(),
                userId = userId.toString(),
                type = "SHIPPING"
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            verify { addressRepository.findByCustomerIdAndType(customerId, AddressType.SHIPPING) }
        }
    }

    @Nested
    inner class GetAddress {

        @Test
        fun `should return 400 for invalid customer ID format`() {
            val response = controller.getAddress(
                customerId = "invalid-uuid",
                addressId = addressId.toString(),
                userId = userId.toString()
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        }

        @Test
        fun `should return 400 for invalid address ID format`() {
            val response = controller.getAddress(
                customerId = customerId.toString(),
                addressId = "invalid-uuid",
                userId = userId.toString()
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        }

        @Test
        fun `should return 400 for invalid user ID format`() {
            val response = controller.getAddress(
                customerId = customerId.toString(),
                addressId = addressId.toString(),
                userId = "invalid-uuid"
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        }

        @Test
        fun `should return 404 when customer not found`() {
            every { customerRepository.findById(customerId) } returns Optional.empty()

            val response = controller.getAddress(
                customerId = customerId.toString(),
                addressId = addressId.toString(),
                userId = userId.toString()
            )

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `should return 403 when not authorized`() {
            val otherUserId = UUID.randomUUID()
            every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())

            val response = controller.getAddress(
                customerId = customerId.toString(),
                addressId = addressId.toString(),
                userId = otherUserId.toString()
            )

            assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        }

        @Test
        fun `should return 404 when address not found`() {
            every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())
            every { addressRepository.findById(addressId) } returns Optional.empty()

            val response = controller.getAddress(
                customerId = customerId.toString(),
                addressId = addressId.toString(),
                userId = userId.toString()
            )

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `should return 404 when address belongs to different customer`() {
            val otherCustomerAddress = Address(
                id = addressId,
                customerId = UUID.randomUUID(), // Different customer
                type = AddressType.SHIPPING,
                streetLine1 = "123 Main St",
                city = "New York",
                state = "NY",
                postalCode = "10001",
                country = "US"
            )
            every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())
            every { addressRepository.findById(addressId) } returns Optional.of(otherCustomerAddress)

            val response = controller.getAddress(
                customerId = customerId.toString(),
                addressId = addressId.toString(),
                userId = userId.toString()
            )

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `should return 200 with address on success`() {
            every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())
            every { addressRepository.findById(addressId) } returns Optional.of(createAddress())

            val response = controller.getAddress(
                customerId = customerId.toString(),
                addressId = addressId.toString(),
                userId = userId.toString()
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            assertNotNull(response.body)
        }
    }

    @Nested
    inner class AddAddress {

        private val request = AddAddressRequest(
            type = "SHIPPING",
            label = "Home",
            street = StreetDto(line1 = "123 Main St", line2 = "Apt 4B"),
            city = "New York",
            state = "NY",
            postalCode = "10001",
            country = "US",
            isDefault = true
        )

        @Test
        fun `should return 400 for invalid customer ID format`() {
            val response = controller.addAddress(
                customerId = "invalid-uuid",
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        }

        @Test
        fun `should return 400 for invalid user ID format`() {
            val response = controller.addAddress(
                customerId = customerId.toString(),
                userId = "invalid-uuid",
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        }

        @Test
        fun `should return 404 when customer not found`() {
            every {
                addAddressUseCase.execute(customerId, userId, request, any())
            } returns AddAddressResult.CustomerNotFound(customerId)

            val response = controller.addAddress(
                customerId = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `should return 403 when not authorized`() {
            every {
                addAddressUseCase.execute(customerId, userId, request, any())
            } returns AddAddressResult.Unauthorized(customerId, userId)

            val response = controller.addAddress(
                customerId = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("You are not authorized to add addresses to this customer", body["error"])
        }

        @Test
        fun `should return 400 when validation fails`() {
            val errors = mapOf("postalCode" to "Invalid postal code format")
            every {
                addAddressUseCase.execute(customerId, userId, request, any())
            } returns AddAddressResult.ValidationFailed(errors)

            val response = controller.addAddress(
                customerId = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("VALIDATION_ERROR", body["error"])
        }

        @Test
        fun `should return 400 when address validation needed`() {
            val suggestions = listOf(
                AddressSuggestion(
                    streetLine1 = "123 Main Street",
                    streetLine2 = "Apt 4B",
                    city = "New York",
                    state = "NY",
                    postalCode = "10001-1234",
                    country = "US"
                )
            )
            every {
                addAddressUseCase.execute(customerId, userId, request, any())
            } returns AddAddressResult.AddressValidationNeeded(
                message = "Address could not be verified",
                suggestions = suggestions,
                validationDetails = mapOf("postalCode" to "Extended postal code available")
            )

            val response = controller.addAddress(
                customerId = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("ADDRESS_VALIDATION_FAILED", body["error"])
        }

        @Test
        fun `should return 400 when duplicate label`() {
            every {
                addAddressUseCase.execute(customerId, userId, request, any())
            } returns AddAddressResult.DuplicateLabel("Home")

            val response = controller.addAddress(
                customerId = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("DUPLICATE_LABEL", body["error"])
        }

        @Test
        fun `should return 400 when max addresses reached`() {
            every {
                addAddressUseCase.execute(customerId, userId, request, any())
            } returns AddAddressResult.MaxAddressesReached("SHIPPING", 10)

            val response = controller.addAddress(
                customerId = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("MAX_ADDRESSES_REACHED", body["error"])
        }

        @Test
        fun `should return 400 when PO Box for shipping`() {
            every {
                addAddressUseCase.execute(customerId, userId, request, any())
            } returns AddAddressResult.POBoxNotAllowedForShipping

            val response = controller.addAddress(
                customerId = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("PO_BOX_NOT_ALLOWED", body["error"])
        }

        @Test
        fun `should return 500 on failure`() {
            every {
                addAddressUseCase.execute(customerId, userId, request, any())
            } returns AddAddressResult.Failure("Database error", RuntimeException("Connection failed"))

            val response = controller.addAddress(
                customerId = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("An internal error occurred", body["error"])
        }

        @Test
        fun `should return 201 with created address on success`() {
            val address = createAddress()
            every {
                addAddressUseCase.execute(customerId, userId, request, any())
            } returns AddAddressResult.Success(address)

            val response = controller.addAddress(
                customerId = customerId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.CREATED, response.statusCode)
            assertNotNull(response.body)
            assertTrue(response.headers.location.toString().contains(addressId.toString()))
        }
    }

    @Nested
    inner class UpdateAddress {

        private val request = UpdateAddressRequest(
            label = "Work",
            city = "Brooklyn"
        )

        @Test
        fun `should return 400 for invalid customer ID format`() {
            val response = controller.updateAddress(
                customerId = "invalid-uuid",
                addressId = addressId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        }

        @Test
        fun `should return 400 for invalid address ID format`() {
            val response = controller.updateAddress(
                customerId = customerId.toString(),
                addressId = "invalid-uuid",
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        }

        @Test
        fun `should return 404 when address not found`() {
            every {
                updateAddressUseCase.execute(customerId, addressId, userId, request, any())
            } returns UpdateAddressResult.AddressNotFound(addressId)

            val response = controller.updateAddress(
                customerId = customerId.toString(),
                addressId = addressId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `should return 404 when customer not found`() {
            every {
                updateAddressUseCase.execute(customerId, addressId, userId, request, any())
            } returns UpdateAddressResult.CustomerNotFound(customerId)

            val response = controller.updateAddress(
                customerId = customerId.toString(),
                addressId = addressId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `should return 403 when not authorized`() {
            every {
                updateAddressUseCase.execute(customerId, addressId, userId, request, any())
            } returns UpdateAddressResult.Unauthorized(customerId, userId)

            val response = controller.updateAddress(
                customerId = customerId.toString(),
                addressId = addressId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        }

        @Test
        fun `should return 400 when validation fails`() {
            every {
                updateAddressUseCase.execute(customerId, addressId, userId, request, any())
            } returns UpdateAddressResult.ValidationFailed(mapOf("city" to "City name too long"))

            val response = controller.updateAddress(
                customerId = customerId.toString(),
                addressId = addressId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        }

        @Test
        fun `should return 400 when duplicate label`() {
            every {
                updateAddressUseCase.execute(customerId, addressId, userId, request, any())
            } returns UpdateAddressResult.DuplicateLabel("Work")

            val response = controller.updateAddress(
                customerId = customerId.toString(),
                addressId = addressId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("DUPLICATE_LABEL", body["error"])
        }

        @Test
        fun `should return 400 when PO Box for shipping`() {
            every {
                updateAddressUseCase.execute(customerId, addressId, userId, request, any())
            } returns UpdateAddressResult.POBoxNotAllowedForShipping

            val response = controller.updateAddress(
                customerId = customerId.toString(),
                addressId = addressId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        }

        @Test
        fun `should return 400 when no updates provided`() {
            every {
                updateAddressUseCase.execute(customerId, addressId, userId, request, any())
            } returns UpdateAddressResult.NoUpdates

            val response = controller.updateAddress(
                customerId = customerId.toString(),
                addressId = addressId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("No updates provided", body["error"])
        }

        @Test
        fun `should return 500 on failure`() {
            every {
                updateAddressUseCase.execute(customerId, addressId, userId, request, any())
            } returns UpdateAddressResult.Failure("Database error", RuntimeException("Connection failed"))

            val response = controller.updateAddress(
                customerId = customerId.toString(),
                addressId = addressId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        }

        @Test
        fun `should return 200 with updated address on success`() {
            val address = createAddress()
            every {
                updateAddressUseCase.execute(customerId, addressId, userId, request, any())
            } returns UpdateAddressResult.Success(address, listOf("label", "city"))

            val response = controller.updateAddress(
                customerId = customerId.toString(),
                addressId = addressId.toString(),
                userId = userId.toString(),
                correlationId = null,
                request = request
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            assertNotNull(response.body)
        }
    }

    @Nested
    inner class DeleteAddress {

        @Test
        fun `should return 400 for invalid customer ID format`() {
            val response = controller.deleteAddress(
                customerId = "invalid-uuid",
                addressId = addressId.toString(),
                userId = userId.toString(),
                correlationId = null
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        }

        @Test
        fun `should return 404 when address not found`() {
            every {
                removeAddressUseCase.execute(customerId, addressId, userId, any())
            } returns RemoveAddressResult.AddressNotFound(addressId)

            val response = controller.deleteAddress(
                customerId = customerId.toString(),
                addressId = addressId.toString(),
                userId = userId.toString(),
                correlationId = null
            )

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `should return 404 when customer not found`() {
            every {
                removeAddressUseCase.execute(customerId, addressId, userId, any())
            } returns RemoveAddressResult.CustomerNotFound(customerId)

            val response = controller.deleteAddress(
                customerId = customerId.toString(),
                addressId = addressId.toString(),
                userId = userId.toString(),
                correlationId = null
            )

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `should return 403 when not authorized`() {
            every {
                removeAddressUseCase.execute(customerId, addressId, userId, any())
            } returns RemoveAddressResult.Unauthorized(customerId, userId)

            val response = controller.deleteAddress(
                customerId = customerId.toString(),
                addressId = addressId.toString(),
                userId = userId.toString(),
                correlationId = null
            )

            assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        }

        @Test
        fun `should return 500 on failure`() {
            every {
                removeAddressUseCase.execute(customerId, addressId, userId, any())
            } returns RemoveAddressResult.Failure("Database error", RuntimeException("Connection failed"))

            val response = controller.deleteAddress(
                customerId = customerId.toString(),
                addressId = addressId.toString(),
                userId = userId.toString(),
                correlationId = null
            )

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        }

        @Test
        fun `should return 204 on success`() {
            every {
                removeAddressUseCase.execute(customerId, addressId, userId, any())
            } returns RemoveAddressResult.Success(addressId, wasDefault = false)

            val response = controller.deleteAddress(
                customerId = customerId.toString(),
                addressId = addressId.toString(),
                userId = userId.toString(),
                correlationId = null
            )

            assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        }
    }

    @Nested
    inner class GetDefaultAddress {

        @Test
        fun `should return 400 for invalid customer ID format`() {
            val response = controller.getDefaultAddress(
                customerId = "invalid-uuid",
                type = "SHIPPING",
                userId = userId.toString()
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        }

        @Test
        fun `should return 400 for invalid user ID format`() {
            val response = controller.getDefaultAddress(
                customerId = customerId.toString(),
                type = "SHIPPING",
                userId = "invalid-uuid"
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        }

        @Test
        fun `should return 400 for invalid address type`() {
            val response = controller.getDefaultAddress(
                customerId = customerId.toString(),
                type = "INVALID",
                userId = userId.toString()
            )

            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
            @Suppress("UNCHECKED_CAST")
            val body = response.body as Map<String, Any>
            assertEquals("Invalid address type. Allowed values: SHIPPING, BILLING", body["error"])
        }

        @Test
        fun `should return 404 when customer not found`() {
            every { customerRepository.findById(customerId) } returns Optional.empty()

            val response = controller.getDefaultAddress(
                customerId = customerId.toString(),
                type = "SHIPPING",
                userId = userId.toString()
            )

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `should return 403 when not authorized`() {
            val otherUserId = UUID.randomUUID()
            every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())

            val response = controller.getDefaultAddress(
                customerId = customerId.toString(),
                type = "SHIPPING",
                userId = otherUserId.toString()
            )

            assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        }

        @Test
        fun `should return 404 when no default address exists`() {
            every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())
            every { addressRepository.findByCustomerIdAndTypeAndIsDefaultTrue(customerId, AddressType.SHIPPING) } returns null

            val response = controller.getDefaultAddress(
                customerId = customerId.toString(),
                type = "SHIPPING",
                userId = userId.toString()
            )

            assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        }

        @Test
        fun `should return 200 with default shipping address on success`() {
            every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())
            every { addressRepository.findByCustomerIdAndTypeAndIsDefaultTrue(customerId, AddressType.SHIPPING) } returns createAddress()

            val response = controller.getDefaultAddress(
                customerId = customerId.toString(),
                type = "SHIPPING",
                userId = userId.toString()
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            assertNotNull(response.body)
        }

        @Test
        fun `should return 200 with default billing address on success`() {
            val billingAddress = Address(
                id = addressId,
                customerId = customerId,
                type = AddressType.BILLING,
                streetLine1 = "456 Oak Ave",
                city = "Los Angeles",
                state = "CA",
                postalCode = "90001",
                country = "US",
                isDefault = true
            )
            every { customerRepository.findById(customerId) } returns Optional.of(createCustomer())
            every { addressRepository.findByCustomerIdAndTypeAndIsDefaultTrue(customerId, AddressType.BILLING) } returns billingAddress

            val response = controller.getDefaultAddress(
                customerId = customerId.toString(),
                type = "BILLING",
                userId = userId.toString()
            )

            assertEquals(HttpStatus.OK, response.statusCode)
        }
    }
}
