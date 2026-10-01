package com.acme.cart.infrastructure.product

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.acme.cart.domain.AvailabilityIssue
import com.acme.cart.domain.CartError
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.util.UUID

/**
 * Checks whether a variant can be ordered, from the product service
 * (`GET /api/v1/inventory/availability/{variantId}`), before checkout locks a cart (PIN-329).
 * The service only knows in or out of stock, not quantities.
 */
@Component
class ProductAvailabilityClient(private val productRestClient: RestClient) {

    private val logger = LoggerFactory.getLogger(ProductAvailabilityClient::class.java)

    /**
     * @return null when the variant is in stock, or why it can't be ordered; on the left,
     *   [CartError.AvailabilityUnavailable] when the product service could not answer.
     */
    fun issueWith(variantId: UUID): Either<CartError, AvailabilityIssue?> =
        try {
            val response = productRestClient.get()
                .uri("/api/v1/inventory/availability/{variantId}", variantId)
                .retrieve()
                .body(AvailabilityResponse::class.java)
                ?: return CartError.AvailabilityUnavailable(variantId).left()

            when (response.availability) {
                IN_STOCK -> null.right()
                OUT_OF_STOCK -> AvailabilityIssue.OUT_OF_STOCK.right()
                else -> {
                    logger.warn("Unknown availability {} for variant {}", response.availability, variantId)
                    CartError.AvailabilityUnavailable(variantId).left()
                }
            }
        } catch (ex: HttpClientErrorException) {
            if (ex.statusCode == HttpStatus.NOT_FOUND && isVariantNotFound(ex)) {
                AvailabilityIssue.NOT_AVAILABLE.right()
            } else {
                logger.warn("Product service rejected availability lookup for variant {}: {}", variantId, ex.statusCode, ex)
                CartError.AvailabilityUnavailable(variantId).left()
            }
        } catch (ex: RestClientException) {
            logger.warn("Availability lookup failed for variant {}", variantId, ex)
            CartError.AvailabilityUnavailable(variantId).left()
        }

    /**
     * Only the product service's own `VARIANT_NOT_FOUND` 404 means the variant is gone, e.g. an
     * archived product's (PIN-306); a 404 from a gateway or a wrong URL must not count.
     */
    private fun isVariantNotFound(ex: HttpClientErrorException): Boolean =
        try {
            ex.getResponseBodyAs(ErrorResponse::class.java)?.code == VARIANT_NOT_FOUND
        } catch (_: RestClientException) {
            false
        }

    internal data class ErrorResponse(val code: String? = null)

    internal data class AvailabilityResponse(val variantId: UUID, val availability: String)

    companion object {
        private const val IN_STOCK = "IN_STOCK"
        private const val OUT_OF_STOCK = "OUT_OF_STOCK"

        /** The product service's code for a variant it doesn't find, e.g. an archived product's. */
        private const val VARIANT_NOT_FOUND = "VARIANT_NOT_FOUND"
    }
}
