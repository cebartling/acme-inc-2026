package com.acme.notification.infrastructure.client

import com.acme.notification.infrastructure.client.dto.CustomerDto
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.util.UUID

/**
 * Result type for customer service client operations.
 */
sealed class CustomerQueryResult {
    /**
     * Customer was found successfully.
     */
    data class Success(val customer: CustomerDto) : CustomerQueryResult()

    /**
     * Customer was not found.
     */
    data object NotFound : CustomerQueryResult()

    /**
     * An error occurred while fetching the customer.
     */
    data class Error(val message: String, val cause: Throwable? = null) : CustomerQueryResult()
}

/**
 * REST client for communicating with the Customer Service.
 *
 * Fetches customer details needed for sending welcome emails.
 * Uses Circuit Breaker pattern for resilience against Customer Service failures.
 */
@Component
class CustomerServiceClient(
    @Value("\${notification.customer-service.base-url}")
    private val baseUrl: String,
    @Value("\${notification.customer-service.timeout-ms:5000}")
    private val timeoutMs: Long,
    meterRegistry: MeterRegistry,
    circuitBreakerRegistry: CircuitBreakerRegistry
) {
    private val logger = LoggerFactory.getLogger(CustomerServiceClient::class.java)

    private val client: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .build()

    private val circuitBreaker: CircuitBreaker = circuitBreakerRegistry.circuitBreaker("customerService")

    private val requestTimer: Timer = Timer.builder("customer_service_request_duration_seconds")
        .tag("operation", "get_customer")
        .register(meterRegistry)

    private val successCounter: Counter = Counter.builder("customer_service_requests_total")
        .tag("operation", "get_customer")
        .tag("status", "success")
        .register(meterRegistry)

    private val notFoundCounter: Counter = Counter.builder("customer_service_requests_total")
        .tag("operation", "get_customer")
        .tag("status", "not_found")
        .register(meterRegistry)

    private val errorCounter: Counter = Counter.builder("customer_service_requests_total")
        .tag("operation", "get_customer")
        .tag("status", "error")
        .register(meterRegistry)

    private val circuitBreakerOpenCounter: Counter = Counter.builder("customer_service_requests_total")
        .tag("operation", "get_customer")
        .tag("status", "circuit_breaker_open")
        .register(meterRegistry)

    /**
     * Fetches customer details by customer ID.
     *
     * @param customerId The customer's unique identifier.
     * @return The customer query result.
     */
    fun getCustomerById(customerId: UUID): CustomerQueryResult {
        return requestTimer.record<CustomerQueryResult> {
            try {
                logger.debug("Fetching customer details for ID: {}", customerId)

                // Execute the REST call within the circuit breaker
                val result = circuitBreaker.executeSupplier {
                    try {
                        val response = client.get()
                            .uri("/api/v1/customers/{id}", customerId.toString())
                            .retrieve()
                            .toEntity(CustomerDto::class.java)

                        if (response.statusCode == HttpStatus.OK && response.body != null) {
                            successCounter.increment()
                            logger.debug("Successfully fetched customer: {}", customerId)
                            CustomerQueryResult.Success(response.body!!)
                        } else {
                            notFoundCounter.increment()
                            logger.warn("Customer not found: {}", customerId)
                            CustomerQueryResult.NotFound
                        }
                    } catch (e: HttpClientErrorException) {
                        // Handle 404s outside circuit breaker scope - these are not service failures
                        if (e.statusCode == HttpStatus.NOT_FOUND) {
                            notFoundCounter.increment()
                            logger.warn("Customer not found: {}", customerId)
                            CustomerQueryResult.NotFound
                        } else {
                            // Other 4xx errors are also client errors, not service failures
                            // The circuit breaker is configured to ignore these via ignore-exceptions
                            throw e
                        }
                    } catch (e: RestClientException) {
                        // Other RestClientExceptions (server errors, timeouts, etc.) are service failures
                        // Let the circuit breaker record them
                        throw e
                    }
                }
                result
            } catch (e: CallNotPermittedException) {
                // Circuit breaker is open - fail fast
                circuitBreakerOpenCounter.increment()
                logger.error("Circuit breaker is OPEN for customer service, failing fast for customer {}", customerId)
                CustomerQueryResult.Error("Customer service is currently unavailable (circuit breaker open)", e)
            } catch (e: Exception) {
                errorCounter.increment()
                logger.error("Unexpected error fetching customer {}: {}", customerId, e.message, e)
                CustomerQueryResult.Error("Unexpected error: ${e.message}", e)
            }
        } ?: CustomerQueryResult.Error("Timer returned null result")
    }
}
