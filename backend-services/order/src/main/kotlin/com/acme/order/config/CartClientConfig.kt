package com.acme.order.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * HTTP client for the cart service, like the cart's own `ProductClientConfig`. Short
 * timeouts keep a slow cart from holding the customer's Place Order click open; a timeout
 * is answered as "cart unavailable" and the order can be retried with the same key.
 */
@Configuration
class CartClientConfig(
    @Value("\${acme.cart.base-url}") private val baseUrl: String
) {
    @Bean
    fun cartRestClient(): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(2))
            setReadTimeout(Duration.ofSeconds(2))
        }
        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build()
    }
}
