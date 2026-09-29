package com.acme.cart.infrastructure.product

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.acme.cart.domain.CartError
import com.acme.cart.domain.PriceTier
import com.acme.cart.domain.VariantPricing
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.math.BigDecimal
import java.util.UUID

/**
 * Reads a variant's authoritative price from the product service
 * (`GET /api/v1/prices/{variantId}`), so the cart never trusts a client-supplied price.
 */
@Component
class ProductPricingClient(private val productRestClient: RestClient) {

    private val logger = LoggerFactory.getLogger(ProductPricingClient::class.java)

    fun getPricing(variantId: UUID): Either<CartError, VariantPricing> =
        try {
            val response = productRestClient.get()
                .uri("/api/v1/prices/{variantId}", variantId)
                .retrieve()
                .body(PriceResponse::class.java)
                ?: return CartError.PricingUnavailable(variantId).left()

            VariantPricing(
                price = response.price,
                tiers = response.tierPricing.map { PriceTier(it.minQuantity, it.price) }
            ).right()
        } catch (ex: HttpClientErrorException) {
            if (ex.statusCode == HttpStatus.NOT_FOUND && isVariantNotFound(ex)) {
                CartError.VariantNotFound(variantId).left()
            } else {
                logger.warn("Product service rejected price lookup for variant {}: {}", variantId, ex.statusCode, ex)
                CartError.PricingUnavailable(variantId).left()
            }
        } catch (ex: RestClientException) {
            logger.warn("Price lookup failed for variant {}", variantId, ex)
            CartError.PricingUnavailable(variantId).left()
        }

    /**
     * Only the product service's own `VARIANT_NOT_FOUND` 404 means the variant is gone (PIN-306):
     * a merge drops such a line for good, so a 404 from a gateway or a wrong URL must not count.
     * A body that isn't the product service's JSON error, e.g. a gateway's HTML page, is not it.
     */
    private fun isVariantNotFound(ex: HttpClientErrorException): Boolean =
        try {
            ex.getResponseBodyAs(ErrorResponse::class.java)?.code == VARIANT_NOT_FOUND
        } catch (_: RestClientException) {
            false
        }

    internal data class ErrorResponse(val code: String? = null)

    internal data class PriceResponse(
        val variantId: UUID,
        val price: BigDecimal,
        val tierPricing: List<TierEntry> = emptyList()
    )

    internal data class TierEntry(val minQuantity: Int, val price: BigDecimal)

    companion object {
        /** The product service's code for a variant it doesn't find, e.g. an archived product's. */
        private const val VARIANT_NOT_FOUND = "VARIANT_NOT_FOUND"
    }
}
