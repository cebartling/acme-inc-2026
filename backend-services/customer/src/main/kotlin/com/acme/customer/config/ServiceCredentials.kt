package com.acme.customer.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * The shared secret of services that read customers (ADR-0061's pattern): only a caller that sends
 * it in [HEADER] may look a customer up by ID, user ID, number or email. A browser never has it.
 */
@Component
class ServiceCredentials(
    @Value("\${acme.customer.service-token}") serviceToken: String
) {
    private val expected = serviceToken.toByteArray()

    // Padding can never match: the server trims it off the incoming header value
    init {
        require(serviceToken.isNotBlank() && serviceToken == serviceToken.trim()) {
            "acme.customer.service-token (ACME_CUSTOMER_SERVICE_TOKEN) must be set, with no leading or trailing whitespace"
        }
    }

    /** Compared in constant time, so the answer's timing doesn't reveal how much matched. */
    fun isService(header: String?): Boolean =
        header != null && MessageDigest.isEqual(header.toByteArray(), expected)

    companion object {
        const val HEADER = "X-Service-Token"
    }
}
