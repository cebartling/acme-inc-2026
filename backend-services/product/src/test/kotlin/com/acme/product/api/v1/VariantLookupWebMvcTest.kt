package com.acme.product.api.v1

import com.acme.product.infrastructure.persistence.ProductVariantRepository
import com.acme.product.domain.ProductStatus
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.Optional
import java.util.UUID

/**
 * Web-layer tests for [PricingController] and [InventoryController], which look a variant up
 * by an ID in the path.
 */
@WebMvcTest(PricingController::class, InventoryController::class)
class VariantLookupWebMvcTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val variantRepository: ProductVariantRepository
) {

    @TestConfiguration
    class MockBeans {
        @Bean
        fun variantRepository(): ProductVariantRepository = mockk()
    }

    // The mock bean is shared across tests, so verify(exactly = 0) must not see earlier calls
    @BeforeEach
    fun setUp() {
        clearMocks(variantRepository)
    }

    // PIN-306: the cart leaves a guest line out of a merge only on this code, so a 404 from
    // anything else (a wrong URL, a gateway) is never mistaken for a variant that is gone
    @ParameterizedTest
    @ValueSource(strings = ["/api/v1/prices", "/api/v1/inventory/availability"])
    fun `a variant that is not found is a 404 VARIANT_NOT_FOUND`(base: String) {
        val id = UUID.randomUUID()
        every { variantRepository.findByIdAndProductStatus(id, ProductStatus.PUBLISHED) } returns Optional.empty()

        mockMvc.get("$base/$id").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value(GlobalExceptionHandler.VARIANT_NOT_FOUND) }
        }
    }

    // PIN-305: a variant ID that is no UUID is a 400 naming it, not the UUID parser's message
    @ParameterizedTest
    @ValueSource(strings = ["/api/v1/prices/not-a-uuid", "/api/v1/inventory/availability/not-a-uuid"])
    fun `a variant ID that is no UUID is a 400 INVALID_REQUEST naming variantId`(path: String) {
        mockMvc.get(path).andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Invalid request: variantId") }
            jsonPath("$.code") { value(GlobalExceptionHandler.INVALID_REQUEST) }
        }

        verify(exactly = 0) { variantRepository.findByIdAndProductStatus(any(), any()) }
    }
}
