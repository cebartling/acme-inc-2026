package com.acme.product.api.v1

import com.acme.product.domain.Product
import com.acme.product.domain.ProductVariant
import com.acme.product.domain.VariantNotFoundException
import com.acme.product.infrastructure.persistence.ProductVariantRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class InventoryControllerTest {

    private lateinit var variantRepository: ProductVariantRepository
    private lateinit var controller: InventoryController

    @BeforeEach
    fun setUp() {
        variantRepository = mockk()
        controller = InventoryController(variantRepository)
    }

    private fun makeProduct(): Product = Product(
        id = UUID.randomUUID(),
        slug = "gadget-pro",
        name = "Gadget Pro",
        price = BigDecimal("119.99")
    )

    private fun makeVariant(product: Product, inStock: Boolean): ProductVariant = ProductVariant(
        id = UUID.randomUUID(),
        product = product,
        sku = "ACME-GP-BLK",
        name = "Black",
        color = "Black",
        isDefault = true,
        inStock = inStock
    )

    @Test
    fun `getAvailability returns IN_STOCK for in-stock variant`() {
        val variantId = UUID.randomUUID()
        val variant = makeVariant(makeProduct(), inStock = true)
        every { variantRepository.findById(variantId) } returns Optional.of(variant)

        val response = controller.getAvailability(variantId)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body
        assertNotNull(body)
        assertEquals(variantId, body.variantId)
        assertEquals("IN_STOCK", body.availability)
    }

    @Test
    fun `getAvailability returns OUT_OF_STOCK for out-of-stock variant`() {
        val variantId = UUID.randomUUID()
        val variant = makeVariant(makeProduct(), inStock = false)
        every { variantRepository.findById(variantId) } returns Optional.of(variant)

        val response = controller.getAvailability(variantId)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("OUT_OF_STOCK", response.body?.availability)
    }

    @Test
    fun `getAvailability throws VariantNotFoundException for unknown variant`() {
        val variantId = UUID.randomUUID()
        every { variantRepository.findById(variantId) } returns Optional.empty()

        assertThrows<VariantNotFoundException> {
            controller.getAvailability(variantId)
        }
    }
}
