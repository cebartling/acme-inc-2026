package com.acme.notification.infrastructure.client

import com.sun.net.httpserver.HttpServer
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CustomerServiceClientTest {

    private val server = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
        createContext("/") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        start()
    }

    @AfterEach
    fun stopServer() = server.stop(0)

    @Test
    fun `the customer client is built from the injected builder, so its calls are observed for tracing`() {
        val observedNames = mutableListOf<String?>()
        val registry = ObservationRegistry.create()
        registry.observationConfig().observationHandler(object : ObservationHandler<Observation.Context> {
            override fun supportsContext(context: Observation.Context) = true
            override fun onStart(context: Observation.Context) {
                observedNames.add(context.name)
            }
        })

        val client = CustomerServiceClient(
            baseUrl = "http://localhost:${server.address.port}",
            serviceToken = "test-token",
            timeoutMs = 5000,
            restClientBuilder = RestClient.builder().observationRegistry(registry),
            meterRegistry = SimpleMeterRegistry(),
            circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults()
        )
        val result = client.getCustomerById(UUID.randomUUID())

        assertEquals(CustomerQueryResult.NotFound, result)
        assertEquals(listOf("http.client.requests"), observedNames)
    }

    @Test
    fun `a customer service that never answers fails the call at the configured timeout`() {
        // Held open until the test ends, so stopping the server doesn't wait for the handler.
        val release = CountDownLatch(1)
        val slowServer = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            createContext("/") { exchange ->
                release.await(10, TimeUnit.SECONDS)
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
            }
            start()
        }

        try {
            val client = CustomerServiceClient(
                baseUrl = "http://localhost:${slowServer.address.port}",
                serviceToken = "test-token",
                timeoutMs = 200,
                restClientBuilder = RestClient.builder(),
                meterRegistry = SimpleMeterRegistry(),
                circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults()
            )

            val startedAt = System.nanoTime()
            val result = client.getCustomerById(UUID.randomUUID())
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

            val error = assertInstanceOf(CustomerQueryResult.Error::class.java, result, "expected Error but was $result")
            assertInstanceOf(
                SocketTimeoutException::class.java,
                error.cause?.cause,
                "expected a read timeout but the failure was ${error.cause}"
            )
            assertTrue(elapsedMs < 2000, "call took ${elapsedMs}ms; the 200ms timeout was not applied")
        } finally {
            release.countDown()
            slowServer.stop(0)
        }
    }

    @Test
    fun `a timeout that would switch the timeout off is rejected at startup`() {
        listOf(0L, -1L, Int.MAX_VALUE + 1L).forEach { timeoutMs ->
            val failure = assertThrows(IllegalArgumentException::class.java) {
                CustomerServiceClient(
                    baseUrl = "http://localhost:${server.address.port}",
                    serviceToken = "test-token",
                    timeoutMs = timeoutMs,
                    restClientBuilder = RestClient.builder(),
                    meterRegistry = SimpleMeterRegistry(),
                    circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults()
                )
            }
            assertTrue(failure.message!!.contains("CUSTOMER_SERVICE_TIMEOUT_MS"), "message was: ${failure.message}")
        }
    }

    @Test
    fun `5xx responses open the circuit breaker, which then fails fast without calling the service`() {
        val hits = AtomicInteger()
        val failingServer = serverAnswering(500, hits)

        try {
            val breakers = smallBreakerRegistry()
            val client = clientFor(failingServer.address.port, breakers)

            repeat(BREAKER_MIN_CALLS) { client.getCustomerById(UUID.randomUUID()) }
            assertEquals(CircuitBreaker.State.OPEN, breakers.circuitBreaker("customerService").state)

            val hitsWhenOpened = hits.get()
            val result = client.getCustomerById(UUID.randomUUID())

            val error = assertInstanceOf(CustomerQueryResult.Error::class.java, result, "expected Error but was $result")
            assertTrue(error.message.contains("circuit breaker open"), "message was: ${error.message}")
            assertEquals(hitsWhenOpened, hits.get(), "an open breaker must not call the customer service")
        } finally {
            failingServer.stop(0)
        }
    }

    @Test
    fun `connection failures open the circuit breaker`() {
        val deadPort = server.address.port
        server.stop(0)

        val breakers = smallBreakerRegistry()
        val client = clientFor(deadPort, breakers)

        repeat(BREAKER_MIN_CALLS) { client.getCustomerById(UUID.randomUUID()) }

        assertEquals(CircuitBreaker.State.OPEN, breakers.circuitBreaker("customerService").state)
        val result = client.getCustomerById(UUID.randomUUID())
        val error = assertInstanceOf(CustomerQueryResult.Error::class.java, result, "expected Error but was $result")
        assertTrue(error.message.contains("circuit breaker open"), "message was: ${error.message}")
    }

    @Test
    fun `404 and other 4xx responses do not open the circuit breaker`() {
        listOf(404, 403).forEach { status ->
            val clientErrorServer = serverAnswering(status, AtomicInteger())

            try {
                val breakers = smallBreakerRegistry()
                val client = clientFor(clientErrorServer.address.port, breakers)

                repeat(BREAKER_MIN_CALLS * 2) { client.getCustomerById(UUID.randomUUID()) }

                assertEquals(
                    CircuitBreaker.State.CLOSED,
                    breakers.circuitBreaker("customerService").state,
                    "a $status must not count as a breaker failure"
                )
            } finally {
                clientErrorServer.stop(0)
            }
        }
    }

    private fun serverAnswering(status: Int, hits: AtomicInteger): HttpServer =
        HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            createContext("/") { exchange ->
                hits.incrementAndGet()
                exchange.sendResponseHeaders(status, -1)
                exchange.close()
            }
            start()
        }

    // The slow-call settings are left at their defaults, so only recorded failures can open the breaker.
    private fun smallBreakerRegistry(): CircuitBreakerRegistry =
        CircuitBreakerRegistry.of(
            CircuitBreakerConfig.custom()
                .failureRateThreshold(50f)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(BREAKER_MIN_CALLS)
                .minimumNumberOfCalls(BREAKER_MIN_CALLS)
                .build()
        )

    private fun clientFor(port: Int, breakers: CircuitBreakerRegistry) = CustomerServiceClient(
        baseUrl = "http://localhost:$port",
        serviceToken = "test-token",
        timeoutMs = 1000,
        restClientBuilder = RestClient.builder(),
        meterRegistry = SimpleMeterRegistry(),
        circuitBreakerRegistry = breakers
    )

    private companion object {
        const val BREAKER_MIN_CALLS = 5
    }
}
