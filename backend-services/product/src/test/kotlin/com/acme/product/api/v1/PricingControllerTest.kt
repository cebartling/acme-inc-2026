package com.acme.product.api.v1

import com.acme.product.domain.Product
import com.acme.product.domain.ProductVariant
import com.acme.product.domain.ProductVariantTierPricing
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
import kotlin.test.assertNull

class PricingControllerTest {

    private lateinit var variantRepository: ProductVariantRepository
    private lateinit var controller: PricingController

    @BeforeEach
    fun setUp() {
        variantRepository = mockk()
        controller = PricingController(variantRepository)
    }

    private fun makeProduct(price: BigDecimal = BigDecimal("119.99")): Product = Product(
        id = UUID.randomUUID(),
        slug = "gadget-pro",
        name = "Gadget Pro",
        price = price
    )

    @Test
    fun `getPrice returns product base price when variant has no price override`() {
        val variantId = UUID.randomUUID()
        val product = makeProduct(BigDecimal("119.99"))
        val variant = ProductVariant(
            id = variantId,
            product = product,
            sku = "ACME-GP-BLK",
            name = "Black",
            isDefault = true,
            inStock = true,
            priceOverride = null
        )
        every { variantRepository.findById(variantId) } returns Optional.of(variant)

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
        val product = makeProduct(BigDecimal("199.99"))
        val variant = ProductVariant(
            id = variantId,
            product = product,
            sku = "ACME-NCH-WHT",
            name = "Pearl White",
            isDefault = false,
            inStock = true,
            priceOverride = BigDecimal("189.99")
        )
        every { variantRepository.findById(variantId) } returns Optional.of(variant)

        val response = controller.getPrice(variantId)

        val body = response.body
        assertNotNull(body)
        assertEquals(BigDecimal("189.99"), body.price)
        assertEquals(BigDecimal("199.99"), body.originalPrice)
    }

    @Test
    fun `getPrice does not set originalPrice when override is higher than base price`() {
        val variantId = UUID.randomUUID()
        val product = makeProduct(BigDecimal("199.99"))
        val variant = ProductVariant(
            id = variantId,
            product = product,
            sku = "ACME-NCH-NVY-LTD",
            name = "Navy Limited Edition",
            isDefault = false,
            inStock = false,
            priceOverride = BigDecimal("219.99")
        )
        every { variantRepository.findById(variantId) } returns Optional.of(variant)

        val response = controller.getPrice(variantId)

        val body = response.body
        assertNotNull(body)
        assertEquals(BigDecimal("219.99"), body.price)
        assertNull(body.originalPrice)
    }

    @Test
    fun `getPrice returns tier pricing rows sorted by minQuantity`() {
        val variantId = UUID.randomUUID()
        val product = makeProduct()
        val tierRow1 = ProductVariantTierPricing(
            id = UUID.randomUUID(),
            variant = mockk(relaxed = true),
            minQuantity = 3,
            price = BigDecimal("109.99")
        )
        val tierRow2 = ProductVariantTierPricing(
            id = UUID.randomUUID(),
            variant = mockk(relaxed = true),
            minQuantity = 10,
            price = BigDecimal("99.99")
        )
        val variant = ProductVariant(
            id = variantId,
            product = product,
            sku = "ACME-GP-BLK",
            name = "Black",
            isDefault = true,
            inStock = true,
            priceOverride = null,
            tierPricing = listOf(tierRow1, tierRow2)
        )
        every { variantRepository.findById(variantId) } returns Optional.of(variant)

        val response = controller.getPrice(variantId)

        val body = response.body
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
        every { variantRepository.findById(variantId) } returns Optional.empty()

        assertThrows<VariantNotFoundException> {
            controller.getPrice(variantId)
        }
    }
}
