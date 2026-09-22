package com.acme.cart.infrastructure.product

import com.acme.cart.domain.CartError
import com.acme.cart.domain.PriceTier
import com.acme.cart.domain.VariantPricing
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals

class ProductPricingClientTest {

    private val builder = RestClient.builder().baseUrl("http://product")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = ProductPricingClient(builder.build())
    private val variantId = UUID.randomUUID()
    private val url = "http://product/api/v1/prices/$variantId"

    @Test
    fun `maps the product service price response, including tiers`() {
        server.expect(requestTo(url)).andRespond(
            withSuccess(
                """
                {"variantId":"$variantId","price":69.99,"originalPrice":null,
                 "tierPricing":[{"minQuantity":3,"price":64.99}]}
                """.trimIndent(),
                MediaType.APPLICATION_JSON
            )
        )

        val pricing = client.getPricing(variantId).getOrNull()

        assertEquals(
            VariantPricing(BigDecimal("69.99"), listOf(PriceTier(3, BigDecimal("64.99")))),
            pricing
        )
    }

    @Test
    fun `a 404 means the variant does not exist`() {
        server.expect(requestTo(url)).andRespond(withResourceNotFound())

        assertEquals(CartError.VariantNotFound(variantId), client.getPricing(variantId).leftOrNull())
    }

    @Test
    fun `a server error means pricing is unavailable`() {
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertEquals(CartError.PricingUnavailable(variantId), client.getPricing(variantId).leftOrNull())
    }
}
