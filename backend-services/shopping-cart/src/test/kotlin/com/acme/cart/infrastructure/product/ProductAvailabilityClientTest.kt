package com.acme.cart.infrastructure.product

import arrow.core.right
import com.acme.cart.domain.AvailabilityIssue
import com.acme.cart.domain.CartError
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.util.UUID
import kotlin.test.assertEquals

class ProductAvailabilityClientTest {

    private val builder = RestClient.builder().baseUrl("http://product")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = ProductAvailabilityClient(builder.build())
    private val variantId = UUID.randomUUID()
    private val url = "http://product/api/v1/inventory/availability/$variantId"

    private fun respondWith(availability: String) = server.expect(requestTo(url)).andRespond(
        withSuccess("""{"variantId":"$variantId","availability":"$availability"}""", MediaType.APPLICATION_JSON)
    )

    @Test
    fun `an in-stock variant has no issue`() {
        respondWith("IN_STOCK")

        // Right(null), not a failure: getOrNull() alone can't tell the two apart
        assertEquals(null.right(), client.issueWith(variantId))
    }

    @Test
    fun `an out-of-stock variant is OUT_OF_STOCK`() {
        respondWith("OUT_OF_STOCK")

        assertEquals(AvailabilityIssue.OUT_OF_STOCK, client.issueWith(variantId).getOrNull())
    }

    // PIN-306: only the product service's own coded 404 means the variant is gone
    @Test
    fun `a 404 VARIANT_NOT_FOUND means the variant is not available`() {
        server.expect(requestTo(url)).andRespond(
            withResourceNotFound()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"error":"Variant not found: $variantId","code":"VARIANT_NOT_FOUND"}""")
        )

        assertEquals(AvailabilityIssue.NOT_AVAILABLE, client.issueWith(variantId).getOrNull())
    }

    @Test
    fun `a 404 without that code means availability is unavailable`() {
        server.expect(requestTo(url)).andRespond(withResourceNotFound())

        assertEquals(CartError.AvailabilityUnavailable(variantId), client.issueWith(variantId).leftOrNull())
    }

    @Test
    fun `a server error means availability is unavailable`() {
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertEquals(CartError.AvailabilityUnavailable(variantId), client.issueWith(variantId).leftOrNull())
    }

    @Test
    fun `an availability value it doesn't know means availability is unavailable`() {
        respondWith("BACKORDERED")

        assertEquals(CartError.AvailabilityUnavailable(variantId), client.issueWith(variantId).leftOrNull())
    }
}
