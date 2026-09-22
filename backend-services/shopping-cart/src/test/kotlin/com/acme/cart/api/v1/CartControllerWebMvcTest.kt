package com.acme.cart.api.v1

import arrow.core.left
import arrow.core.right
import com.acme.cart.application.AddItemToCartCommand
import com.acme.cart.application.AddItemToCartUseCase
import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartError
import com.acme.cart.domain.VariantPricing
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@WebMvcTest(CartController::class)
class CartControllerWebMvcTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val useCase: AddItemToCartUseCase
) {

    @TestConfiguration
    class Beans {
        @Bean
        fun addItemToCartUseCase(): AddItemToCartUseCase = mockk()

        @Bean
        fun objectMapper(): ObjectMapper = jacksonObjectMapper()
    }

    private val variantId = UUID.randomUUID()
    private val command = slot<AddItemToCartCommand>()

    private val body = """
        {"variantId":"$variantId","quantity":2,
         "productSnapshot":{"productId":"${UUID.randomUUID()}","name":"ACME Gaming Mouse Pro",
           "sku":"ACME-GM-PRO-BLK","variantName":"Black","imageUrl":"/img/mouse.png",
           "attributes":{"color":"Black"}}}
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        clearMocks(useCase)
        command.clear()
        every { useCase.execute(capture(command), any()) } answers {
            val cmd = firstArg<AddItemToCartCommand>()
            val cart = Cart(id = UUID.randomUUID(), sessionId = cmd.sessionId)
            cart.addItem(cmd.variantId, cmd.quantity, VariantPricing(BigDecimal("69.99")), json(cmd), 10)
            cart.right()
        }
    }

    private fun json(cmd: AddItemToCartCommand) = jacksonObjectMapper().writeValueAsString(cmd.productSnapshot)

    private fun postItem(cookie: String? = null, content: String = body) =
        mockMvc.post("/api/v1/carts/items") {
            contentType = MediaType.APPLICATION_JSON
            this.content = content
            cookie?.let { cookie(Cookie(CartController.SESSION_COOKIE, it)) }
        }

    @Test
    fun `first add sets an HttpOnly Secure SameSite=Lax session cookie for 30 days`() {
        val result = postItem().andExpect {
            status { isCreated() }
            jsonPath("$.summary.itemCount") { value(2) }
            jsonPath("$.summary.subtotal") { value(139.98) }
            jsonPath("$.summary.currency") { value("USD") }
            jsonPath("$.items[0].lineTotal") { value(139.98) }
            jsonPath("$.items[0].productSnapshot.sku") { value("ACME-GM-PRO-BLK") }
        }.andReturn()

        val setCookie = result.response.getHeader(HttpHeaders.SET_COOKIE)!!
        assertTrue(setCookie.startsWith("${CartController.SESSION_COOKIE}=${command.captured.sessionId};"), setCookie)
        assertTrue("HttpOnly" in setCookie, setCookie)
        assertTrue("Secure" in setCookie, setCookie)
        assertTrue("SameSite=Lax" in setCookie, setCookie)
        assertTrue("Max-Age=2592000" in setCookie, setCookie)
    }

    @Test
    fun `an existing session cookie is reused and not re-set`() {
        val sessionId = UUID.randomUUID().toString()

        val result = postItem(cookie = sessionId).andExpect { status { isCreated() } }.andReturn()

        assertEquals(sessionId, command.captured.sessionId)
        assertEquals(null, result.response.getHeader(HttpHeaders.SET_COOKIE))
    }

    @Test
    fun `a session cookie this service did not mint is replaced`() {
        val result = postItem(cookie = "not-a-uuid").andExpect { status { isCreated() } }.andReturn()

        assertNotEquals("not-a-uuid", command.captured.sessionId)
        assertTrue(result.response.getHeader(HttpHeaders.SET_COOKIE)!!.contains(command.captured.sessionId))
    }

    @Test
    fun `exceeding the max quantity is a 422 with the customer-facing message`() {
        every { useCase.execute(any(), any()) } returns CartError.MaxQuantityExceeded(5).left()

        postItem().andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.error") { value("Maximum order quantity is 5 for this item") }
        }
    }

    @Test
    fun `an unknown variant is a 404`() {
        every { useCase.execute(any(), any()) } returns CartError.VariantNotFound(variantId).left()

        postItem().andExpect { status { isNotFound() } }
    }

    @Test
    fun `pricing being unavailable is a 503`() {
        every { useCase.execute(any(), any()) } returns CartError.PricingUnavailable(variantId).left()

        postItem().andExpect { status { isServiceUnavailable() } }
    }

    @Test
    fun `a zero quantity is rejected before reaching the use case`() {
        postItem(content = body.replace("\"quantity\":2", "\"quantity\":0")).andExpect { status { isBadRequest() } }

        assertTrue(!command.isCaptured)
    }

    @Test
    fun `a missing product snapshot is rejected`() {
        postItem(content = """{"variantId":"$variantId","quantity":1}""").andExpect { status { isBadRequest() } }
    }
}
