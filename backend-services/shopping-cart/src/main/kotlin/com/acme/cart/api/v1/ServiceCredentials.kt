package com.acme.cart.api.v1

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * The order service's shared secret (PIN-382, ADR-0061): only a caller that sends it in
 * [HEADER] may read a checkout session or convert a cart. The customer's browser never has it,
 * so it can't convert its own cart into a made-up order.
 */
@Component
class ServiceCredentials(
    @Value("\${acme.cart.order-service-token}") orderServiceToken: String
) {
    private val expected = orderServiceToken.toByteArray()

    init {
        require(orderServiceToken.isNotBlank()) { "acme.cart.order-service-token (ACME_ORDER_SERVICE_TOKEN) must be set" }
    }

    /** Compared in constant time, so the answer's timing doesn't reveal how much matched. */
    fun isOrderService(header: String?): Boolean =
        header != null && MessageDigest.isEqual(header.toByteArray(), expected)

    companion object {
        const val HEADER = "X-Service-Token"
    }
}
