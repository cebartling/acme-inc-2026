package com.acme.cart.domain

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal
import kotlin.test.assertEquals

class VariantPricingTest {

    // Tiers deliberately out of order: selection must not depend on list order.
    private val pricing = VariantPricing(
        price = BigDecimal("69.99"),
        tiers = listOf(PriceTier(10, BigDecimal("59.99")), PriceTier(3, BigDecimal("64.99")))
    )

    @ParameterizedTest(name = "quantity {0} costs {1}")
    @CsvSource("1, 69.99", "2, 69.99", "3, 64.99", "9, 64.99", "10, 59.99", "50, 59.99")
    fun `unit price uses the highest tier the quantity reaches`(quantity: Int, expected: String) {
        assertEquals(BigDecimal(expected), pricing.unitPriceFor(quantity))
    }
}
