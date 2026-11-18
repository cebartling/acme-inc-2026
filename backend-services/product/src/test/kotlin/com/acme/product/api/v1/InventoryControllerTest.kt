package com.acme.product.api.v1

import com.acme.product.domain.CatalogVariant
import com.acme.product.domain.VariantNotFoundException
import com.acme.product.domain.VariantOfProduct
import com.acme.product.infrastructure.projection.CatalogReadModelQueries
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class InventoryControllerTest {

    private lateinit var queries: CatalogReadModelQueries
    private lateinit var controller: InventoryController

    @BeforeEach
    fun setUp() {
        queries = mockk()
        controller = InventoryController(queries)
    }

    private fun makeVariant(id: UUID, inStock: Boolean) = VariantOfProduct(
        variant = CatalogVariant(
            id = id,
            sku = "ACME-GP-BLK",
            name = "Black",
            color = "Black",
            size = null,
            isDefault = true,
            inStock = inStock,
            priceOverride = null,
            images = emptyList(),
            tierPricing = emptyList()
        ),
        productPrice = BigDecimal("119.99")
    )

    @Test
    fun `getAvailability returns IN_STOCK for in-stock variant`() {
        val variantId = UUID.randomUUID()
        every { queries.findVariant(variantId) } returns makeVariant(variantId, inStock = true)

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
        every { queries.findVariant(variantId) } returns makeVariant(variantId, inStock = false)

        val response = controller.getAvailability(variantId)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("OUT_OF_STOCK", response.body?.availability)
    }

    @Test
    fun `getAvailability throws VariantNotFoundException for unknown variant`() {
        val variantId = UUID.randomUUID()
        every { queries.findVariant(variantId) } returns null

        assertThrows<VariantNotFoundException> {
            controller.getAvailability(variantId)
        }
    }
}
