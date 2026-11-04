package com.acme.notification.infrastructure.client

import com.sun.net.httpserver.HttpServer
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.util.UUID

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
            restClientBuilder = RestClient.builder().observationRegistry(registry),
            meterRegistry = SimpleMeterRegistry(),
            circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults()
        )
        val result = client.getCustomerById(UUID.randomUUID())

        assertEquals(CustomerQueryResult.NotFound, result)
        assertEquals(listOf("http.client.requests"), observedNames)
    }
}
