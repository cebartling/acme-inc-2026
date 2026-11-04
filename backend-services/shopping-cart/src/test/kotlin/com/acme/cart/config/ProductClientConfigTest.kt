package com.acme.cart.config

import com.sun.net.httpserver.HttpServer
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress

class ProductClientConfigTest {

    private val server = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
        createContext("/ping") { exchange ->
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        start()
    }

    @AfterEach
    fun stopServer() = server.stop(0)

    @Test
    fun `the product client is built from the injected builder, so its calls are observed for tracing`() {
        val observedNames = mutableListOf<String?>()
        val registry = ObservationRegistry.create()
        registry.observationConfig().observationHandler(object : ObservationHandler<Observation.Context> {
            override fun supportsContext(context: Observation.Context) = true
            override fun onStart(context: Observation.Context) {
                observedNames.add(context.name)
            }
        })

        val client = ProductClientConfig("http://localhost:${server.address.port}")
            .productRestClient(RestClient.builder().observationRegistry(registry))
        client.get().uri("/ping").retrieve().toBodilessEntity()

        assertEquals(listOf("http.client.requests"), observedNames)
    }
}
