package com.acme.cart.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * HTTP client for the product service. Short timeouts keep a slow product service from
 * holding the customer's Add to Cart click open.
 */
@Configuration
class ProductClientConfig(
    @Value("\${acme.product.base-url}") private val baseUrl: String
) {
    @Bean
    fun productRestClient(): RestClient {
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
