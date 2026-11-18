package com.acme.product.api.v1

import com.acme.product.domain.CatalogVariant
import com.acme.product.domain.TierPrice
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
import kotlin.test.assertNull

class PricingControllerTest {

    private lateinit var queries: CatalogReadModelQueries
    private lateinit var controller: PricingController

    @BeforeEach
    fun setUp() {
        queries = mockk()
        controller = PricingController(queries)
    }

    private fun lookup(
        variantId: UUID,
        productPrice: String,
        priceOverride: String? = null,
        inStock: Boolean = true,
        tiers: List<TierPrice> = emptyList()
    ) = VariantOfProduct(
        variant = CatalogVariant(
            id = variantId,
            sku = "ACME-GP-BLK",
            name = "Black",
            color = null,
            size = null,
            isDefault = true,
            inStock = inStock,
            priceOverride = priceOverride?.let { BigDecimal(it) },
            images = emptyList(),
            tierPricing = tiers
        ),
        productPrice = BigDecimal(productPrice)
    )

    @Test
    fun `getPrice returns product base price when variant has no price override`() {
        val variantId = UUID.randomUUID()
        every { queries.findVariant(variantId) } returns lookup(variantId, "119.99")

        val response = controller.getPrice(variantId)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body
        assertNotNull(body)
        assertEquals(variantId, body.variantId)
        assertEquals(BigDecimal("119.99"), body.price)
        assertNull(body.originalPrice)
        assertEquals(0, body.tierPricing.size)
    }

    @Test
    fun `getPrice returns override price and original product price when override is a discount`() {
        val variantId = UUID.randomUUID()
        every { queries.findVariant(variantId) } returns lookup(variantId, "199.99", priceOverride = "189.99")

        val body = controller.getPrice(variantId).body

        assertNotNull(body)
        assertEquals(BigDecimal("189.99"), body.price)
        assertEquals(BigDecimal("199.99"), body.originalPrice)
    }

    @Test
    fun `getPrice does not set originalPrice when override is higher than base price`() {
        val variantId = UUID.randomUUID()
        every { queries.findVariant(variantId) } returns
            lookup(variantId, "199.99", priceOverride = "219.99", inStock = false)

        val body = controller.getPrice(variantId).body

        assertNotNull(body)
        assertEquals(BigDecimal("219.99"), body.price)
        assertNull(body.originalPrice)
    }

    // The read model stores the tiers sorted by minQuantity; the controller passes them through
    @Test
    fun `getPrice returns the tier pricing rows in the order they are held`() {
        val variantId = UUID.randomUUID()
        every { queries.findVariant(variantId) } returns lookup(
            variantId,
            "119.99",
            tiers = listOf(TierPrice(3, BigDecimal("109.99")), TierPrice(10, BigDecimal("99.99")))
        )

        val body = controller.getPrice(variantId).body

        assertNotNull(body)
        assertEquals(2, body.tierPricing.size)
        assertEquals(3, body.tierPricing[0].minQuantity)
        assertEquals(BigDecimal("109.99"), body.tierPricing[0].price)
        assertEquals(10, body.tierPricing[1].minQuantity)
        assertEquals(BigDecimal("99.99"), body.tierPricing[1].price)
    }

    @Test
    fun `getPrice throws VariantNotFoundException for unknown variant`() {
        val variantId = UUID.randomUUID()
        every { queries.findVariant(variantId) } returns null

        assertThrows<VariantNotFoundException> {
            controller.getPrice(variantId)
        }
    }
}
