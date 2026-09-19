package com.acme.customer.api.v1

import com.acme.customer.api.v1.dto.AddAddressRequest
import com.acme.customer.application.AddAddressResult
import com.acme.customer.application.AddAddressUseCase
import com.acme.customer.application.RemoveAddressUseCase
import com.acme.customer.application.UpdateAddressUseCase
import com.acme.customer.infrastructure.persistence.AddressRepository
import com.acme.customer.infrastructure.persistence.CustomerRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Web-layer tests for [AddressController] that go through real JSON deserialization.
 *
 * Guards PIN-250: Kotlin default values in [AddAddressRequest] must apply when the
 * client omits those fields, which requires the Jackson 3 Kotlin module.
 */
@WebMvcTest(AddressController::class)
class AddressControllerWebMvcTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val addAddressUseCase: AddAddressUseCase
) {

    @TestConfiguration
    class MockBeans {
        @Bean
        fun customerRepository(): CustomerRepository = mockk()

        @Bean
        fun addressRepository(): AddressRepository = mockk()

        @Bean
        fun addAddressUseCase(): AddAddressUseCase = mockk()

        @Bean
        fun updateAddressUseCase(): UpdateAddressUseCase = mockk()

        @Bean
        fun removeAddressUseCase(): RemoveAddressUseCase = mockk()
    }

    @Test
    fun `addAddress should apply Kotlin defaults when optional fields are omitted`() {
        // Given
        val customerId = UUID.randomUUID()
        val requestSlot = slot<AddAddressRequest>()
        every {
            addAddressUseCase.execute(any(), any(), capture(requestSlot), any())
        } returns AddAddressResult.CustomerNotFound(customerId)

        // When
        mockMvc.post("/api/v1/customers/$customerId/addresses") {
            contentType = MediaType.APPLICATION_JSON
            header("X-User-Id", UUID.randomUUID().toString())
            content = """
                {
                  "type": "SHIPPING",
                  "street": {"line1": "123 Main St"},
                  "city": "Minneapolis",
                  "state": "MN",
                  "postalCode": "55401",
                  "country": "US"
                }
            """.trimIndent()
        }.andExpect {
            status { isNotFound() }
        }

        // Then
        val captured = requestSlot.captured
        assertEquals(false, captured.isDefault)
        assertNull(captured.label)
        assertNull(captured.street.line2)
    }
}
