package com.acme.cart.api.v1

import com.acme.cart.domain.QuantityAdjustment
import com.acme.cart.domain.MergeResult
import com.acme.cart.application.MergeOutcome
import com.acme.cart.application.MergeCartsUseCase
import com.acme.cart.application.MergeCartsCommand
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.jwt.JwtValidationException
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.context.annotation.Import
import com.acme.cart.domain.newCartFor
import com.acme.cart.config.SecurityConfig
import com.acme.cart.domain.CartOwner
import com.acme.cart.domain.CartStatus
import arrow.core.left
import arrow.core.right
import com.acme.cart.application.AbandonCheckoutCommand
import com.acme.cart.application.AbandonCheckoutUseCase
import com.acme.cart.application.AddItemToCartCommand
import com.acme.cart.application.AddItemToCartUseCase
import com.acme.cart.application.ClearCartCommand
import com.acme.cart.application.ClearCartUseCase
import com.acme.cart.application.CheckoutStarted
import com.acme.cart.application.StartCheckoutCommand
import com.acme.cart.application.StartCheckoutUseCase
import com.acme.cart.application.RemoveCartItemCommand
import com.acme.cart.application.RemoveCartItemUseCase
import com.acme.cart.application.UpdateCartItemQuantityCommand
import com.acme.cart.application.UpdateCartItemQuantityUseCase
import com.acme.cart.domain.Cart
import com.acme.cart.domain.CartError
import com.acme.cart.domain.UnavailableItem
import com.acme.cart.domain.UnavailableLine
import com.acme.cart.domain.AvailabilityIssue
import com.acme.cart.domain.VariantPricing
import com.acme.cart.infrastructure.persistence.CartRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@WebMvcTest(CartController::class)
@Import(SecurityConfig::class, GuestSessionCookies::class)
class CartControllerWebMvcTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val useCase: AddItemToCartUseCase,
    @Autowired private val updateUseCase: UpdateCartItemQuantityUseCase,
    @Autowired private val removeUseCase: RemoveCartItemUseCase,
    @Autowired private val clearUseCase: ClearCartUseCase,
    @Autowired private val cartRepository: CartRepository,
    @Autowired private val mergeUseCase: MergeCartsUseCase,
    @Autowired private val checkoutUseCase: StartCheckoutUseCase,
    @Autowired private val abandonUseCase: AbandonCheckoutUseCase,
    @Autowired private val jwtDecoder: JwtDecoder
) {

    @TestConfiguration
    class Beans {
        @Bean
        fun addItemToCartUseCase(): AddItemToCartUseCase = mockk()

        @Bean
        fun updateCartItemQuantityUseCase(): UpdateCartItemQuantityUseCase = mockk()

        @Bean
        fun removeCartItemUseCase(): RemoveCartItemUseCase = mockk()

        @Bean
        fun clearCartUseCase(): ClearCartUseCase = mockk()

        @Bean
        fun mergeCartsUseCase(): MergeCartsUseCase = mockk()

        @Bean
        fun startCheckoutUseCase(): StartCheckoutUseCase = mockk()

        @Bean
        fun abandonCheckoutUseCase(): AbandonCheckoutUseCase = mockk()

        @Bean
        fun cartRepository(): CartRepository = mockk()

        @Bean
        fun objectMapper(): ObjectMapper = jacksonObjectMapper()

        /** Stands in for the JWKS-backed decoder; tests decide what a token decodes to. */
        @Bean
        fun jwtDecoder(): JwtDecoder = mockk()
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
        clearMocks(useCase, updateUseCase, removeUseCase, clearUseCase, cartRepository, jwtDecoder, mergeUseCase, checkoutUseCase, abandonUseCase)
        command.clear()
        every { cartRepository.touchGuestCart(any(), any(), any()) } returns 0
        every { useCase.execute(capture(command), any()) } answers {
            val cmd = firstArg<AddItemToCartCommand>()
            val cart = newCartFor(cmd.owner)
            cart.addItem(cmd.variantId, cmd.quantity, VariantPricing(BigDecimal("69.99")), json(cmd), 10)
            cart.right()
        }
    }

    private fun json(cmd: AddItemToCartCommand) = jacksonObjectMapper().writeValueAsString(cmd.productSnapshot)

    private fun guestSession(cmd: AddItemToCartCommand) = (cmd.owner as CartOwner.Guest).sessionId

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
        assertTrue(setCookie.startsWith("${CartController.SESSION_COOKIE}=${guestSession(command.captured)};"), setCookie)
        assertTrue("HttpOnly" in setCookie, setCookie)
        assertTrue("Secure" in setCookie, setCookie)
        assertTrue("SameSite=Lax" in setCookie, setCookie)
        assertTrue("Max-Age=2592000" in setCookie, setCookie)
    }

    @Test
    fun `first add tells the use case the session is new`() {
        postItem().andExpect { status { isCreated() } }

        assertTrue(command.captured.startedNewSession)
    }

    @Test
    fun `an existing session cookie is reused and re-issued for another 30 days`() {
        val sessionId = UUID.randomUUID().toString()

        val result = postItem(cookie = sessionId).andExpect { status { isCreated() } }.andReturn()

        assertEquals(sessionId, guestSession(command.captured))
        assertFalse(command.captured.startedNewSession)
        assertSessionReissued(result.response.getHeader(HttpHeaders.SET_COOKIE), sessionId)
    }

    @Test
    fun `a session cookie this service did not mint is replaced`() {
        val result = postItem(cookie = "not-a-uuid").andExpect { status { isCreated() } }.andReturn()

        assertNotEquals("not-a-uuid", guestSession(command.captured))
        assertTrue(result.response.getHeader(HttpHeaders.SET_COOKIE)!!.contains(guestSession(command.captured)))
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

        postItem().andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("VARIANT_NOT_FOUND") }
        }
    }

    @Test
    fun `pricing being unavailable is a 503`() {
        every { useCase.execute(any(), any()) } returns CartError.PricingUnavailable(variantId).left()

        postItem().andExpect { status { isServiceUnavailable() } }
    }

    @Test
    fun `a zero quantity is rejected before reaching the use case`() {
        postItem(content = body.replace("\"quantity\":2", "\"quantity\":0")).andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Invalid request: quantity") }
            jsonPath("$.code") { value(CartController.INVALID_REQUEST) }
        }

        assertTrue(!command.isCaptured)
    }

    // PIN-279: 2.9 used to be truncated to 2 and accepted
    @Test
    fun `a decimal quantity is rejected, not truncated`() {
        postItem(content = body.replace("\"quantity\":2", "\"quantity\":2.9")).andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Invalid request: quantity") }
            jsonPath("$.code") { value(CartController.INVALID_REQUEST) }
        }

        assertTrue(!command.isCaptured)
    }

    @Test
    fun `a missing product snapshot is rejected`() {
        postItem(content = """{"variantId":"$variantId","quantity":1}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Invalid request: productSnapshot") }
            jsonPath("$.code") { value(CartController.INVALID_REQUEST) }
        }
    }

    // PIN-303: no field to name, and the parser's message is not echoed
    @Test
    fun `malformed JSON is a 400 INVALID_REQUEST without parser details`() {
        postItem(content = """{"variantId":""").andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Invalid request") }
            jsonPath("$.code") { value(CartController.INVALID_REQUEST) }
        }
    }

    @Test
    fun `a nested snapshot field is named by its path`() {
        postItem(content = body.replace("\"sku\":\"ACME-GM-PRO-BLK\"", "\"sku\":\"\"")).andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Invalid request: productSnapshot.sku") }
            jsonPath("$.code") { value(CartController.INVALID_REQUEST) }
        }

        assertTrue(!command.isCaptured)
    }

    // --- US-0004-07: read, update and remove -------------------------------------------

    private val sessionId = UUID.randomUUID().toString()
    private val cartId = UUID.randomUUID()
    private val itemId = UUID.randomUUID()

    /** The guest cookie slides (PIN-268): same value, full attributes, a fresh 30 days. */
    private fun assertSessionReissued(setCookie: String?, session: String) {
        checkNotNull(setCookie) { "expected the session cookie to be re-issued" }
        assertTrue(setCookie.startsWith("${CartController.SESSION_COOKIE}=$session;"), setCookie)
        assertTrue("HttpOnly" in setCookie, setCookie)
        assertTrue("Secure" in setCookie, setCookie)
        assertTrue("SameSite=Lax" in setCookie, setCookie)
        assertTrue("Max-Age=2592000" in setCookie, setCookie)
    }

    private fun cartFor(session: String): Cart {
        val cart = Cart(id = cartId, sessionId = session)
        cart.addItem(variantId, 2, VariantPricing(BigDecimal("69.99")), """{"productId":"${UUID.randomUUID()}","name":"Mouse","sku":"SKU","variantName":"Black","imageUrl":null}""", 10)
        return cart
    }

    @Test
    fun `current cart is 204 without a session cookie`() {
        mockMvc.get("/api/v1/carts/current").andExpect { status { isNoContent() } }
    }

    @Test
    fun `current cart is 204 when the session has no cart yet`() {
        every { cartRepository.findBySessionIdAndStatusIn(sessionId, CartStatus.CURRENT) } returns null

        val result = mockMvc.get("/api/v1/carts/current") { cookie(Cookie(CartController.SESSION_COOKIE, sessionId)) }
            .andExpect { status { isNoContent() } }.andReturn()

        assertSessionReissued(result.response.getHeader(HttpHeaders.SET_COOKIE), sessionId)
    }

    @Test
    fun `reading the cart re-issues the session cookie`() {
        every { cartRepository.findBySessionIdAndStatusIn(sessionId, CartStatus.CURRENT) } returns cartFor(sessionId)

        val result = mockMvc.get("/api/v1/carts/current") { cookie(Cookie(CartController.SESSION_COOKIE, sessionId)) }
            .andExpect { status { isOk() } }.andReturn()

        assertSessionReissued(result.response.getHeader(HttpHeaders.SET_COOKIE), sessionId)
    }

    @Test
    fun `every guest view records activity, leaving the once-a-day throttle to the query`() {
        every { cartRepository.findBySessionIdAndStatusIn(sessionId, CartStatus.CURRENT) } returns cartFor(sessionId)

        mockMvc.get("/api/v1/carts/current") { cookie(Cookie(CartController.SESSION_COOKIE, sessionId)) }
            .andExpect { status { isOk() } }

        assertActivityRecorded(sessionId)
    }

    @Test
    fun `a signed-in caller viewing the cart does not touch the guest cart`() {
        signedIn()
        every { cartRepository.findByUserIdAndStatusIn(userId, CartStatus.CURRENT) } returns newCartFor(CartOwner.Customer(userId))

        mockMvc.get("/api/v1/carts/current") {
            cookie(accessToken(), Cookie(CartController.SESSION_COOKIE, sessionId))
        }.andExpect { status { isOk() } }

        verify(exactly = 0) { cartRepository.touchGuestCart(any(), any(), any()) }
    }

    /** Asserts a once-a-day activity touch for [session], as a write that extended its cookie must make. */
    private fun assertActivityRecorded(session: String) {
        val now = slot<Instant>()
        val staleBefore = slot<Instant>()
        verify(exactly = 1) { cartRepository.touchGuestCart(session, capture(now), capture(staleBefore)) }
        assertEquals(Duration.ofDays(1), Duration.between(staleBefore.captured, now.captured))
    }

    // PIN-287 review #1: every write that extends the cookie records activity, even when it
    // fails, so the cart can never expire while the cookie is still valid.
    @Test
    fun `a failed guest update still records activity, since it extended the cookie`() {
        every { updateUseCase.execute(any(), any()) } returns CartError.MaxQuantityExceeded(10).left()

        mockMvc.patch("/api/v1/carts/$cartId/items/$itemId") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
            contentType = MediaType.APPLICATION_JSON
            content = """{"quantity":11}"""
        }.andExpect { status { isUnprocessableContent() } }

        assertActivityRecorded(sessionId)
    }

    @Test
    fun `a failed guest remove still records activity, since it extended the cookie`() {
        every { removeUseCase.execute(any(), any()) } returns CartError.CartItemNotFound(itemId).left()

        mockMvc.delete("/api/v1/carts/$cartId/items/$itemId") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
        }.andExpect { status { isNotFound() } }

        assertActivityRecorded(sessionId)
    }

    @Test
    fun `a guest add to an existing session records activity`() {
        val existing = UUID.randomUUID().toString()

        postItem(cookie = existing).andExpect { status { isCreated() } }

        assertActivityRecorded(existing)
    }

    @Test
    fun `a first add with a brand-new session has no activity to record`() {
        postItem().andExpect { status { isCreated() } }

        verify(exactly = 0) { cartRepository.touchGuestCart(any(), any(), any()) }
    }

    @Test
    fun `a session cookie this service did not mint is not re-issued`() {
        val result = mockMvc.get("/api/v1/carts/current") { cookie(Cookie(CartController.SESSION_COOKIE, "not-a-uuid")) }
            .andExpect { status { isNoContent() } }.andReturn()

        assertEquals(null, result.response.getHeader(HttpHeaders.SET_COOKIE))
        verify(exactly = 0) { cartRepository.touchGuestCart(any(), any(), any()) }
    }

    // --- PIN-288: one interceptor extends the cookie and records activity -------------------

    private fun sessionCookies(result: org.springframework.test.web.servlet.MvcResult) =
        result.response.getHeaders(HttpHeaders.SET_COOKIE).filter { it.startsWith("${CartController.SESSION_COOKIE}=") }

    @Test
    fun `an update rejected as invalid (400) still extends the cookie and records activity`() {
        val result = mockMvc.patch("/api/v1/carts/$cartId/items/$itemId") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
            contentType = MediaType.APPLICATION_JSON
            content = """{"quantity":0}"""
        }.andExpect { status { isBadRequest() } }.andReturn()

        assertSessionReissued(result.response.getHeader(HttpHeaders.SET_COOKIE), sessionId)
        assertActivityRecorded(sessionId)
    }

    @Test
    fun `an add rejected as invalid (400) still extends the cookie and records activity`() {
        val result = postItem(cookie = sessionId, content = """{"variantId":"$variantId","quantity":1}""")
            .andExpect { status { isBadRequest() } }.andReturn()

        assertSessionReissued(result.response.getHeader(HttpHeaders.SET_COOKIE), sessionId)
        assertActivityRecorded(sessionId)
    }

    @Test
    fun `an add to an existing session sends the session cookie exactly once`() {
        val result = postItem(cookie = sessionId).andExpect { status { isCreated() } }.andReturn()

        assertEquals(1, sessionCookies(result).size, sessionCookies(result).toString())
    }

    @Test
    fun `a first add sends the new session cookie exactly once`() {
        val result = postItem().andExpect { status { isCreated() } }.andReturn()

        assertEquals(1, sessionCookies(result).size, sessionCookies(result).toString())
    }

    @Test
    fun `a replaced session cookie is sent once, for the new session only`() {
        val result = postItem(cookie = "not-a-uuid").andExpect { status { isCreated() } }.andReturn()

        assertEquals(listOf(guestSession(command.captured)), sessionCookies(result).map { it.substringAfter("=").substringBefore(";") })
    }

    @Test
    fun `a path that is no cart endpoint neither extends the cookie nor records activity`() {
        val result = mockMvc.get("/api/v1/carts/no-such-endpoint") { cookie(Cookie(CartController.SESSION_COOKIE, sessionId)) }
            .andExpect { status { isNotFound() } }.andReturn()

        assertEquals(null, result.response.getHeader(HttpHeaders.SET_COOKIE))
        verify(exactly = 0) { cartRepository.touchGuestCart(any(), any(), any()) }
    }

    @Test
    fun `updating a quantity re-issues the session cookie`() {
        every { updateUseCase.execute(any(), any()) } returns cartFor(sessionId).right()

        val result = mockMvc.patch("/api/v1/carts/$cartId/items/$itemId") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
            contentType = MediaType.APPLICATION_JSON
            content = """{"quantity":3}"""
        }.andExpect { status { isOk() } }.andReturn()

        assertSessionReissued(result.response.getHeader(HttpHeaders.SET_COOKIE), sessionId)
    }

    @Test
    fun `removing an item re-issues the session cookie`() {
        every { removeUseCase.execute(any(), any()) } returns Cart(id = cartId, sessionId = sessionId).right()

        val result = mockMvc.delete("/api/v1/carts/$cartId/items/$itemId") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
        }.andExpect { status { isOk() } }.andReturn()

        assertSessionReissued(result.response.getHeader(HttpHeaders.SET_COOKIE), sessionId)
    }

    @Test
    fun `clearing the cart re-issues the session cookie`() {
        every { clearUseCase.execute(any(), any()) } returns Cart(id = cartId, sessionId = sessionId).right()

        val result = mockMvc.delete("/api/v1/carts/$cartId/items") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
        }.andExpect { status { isOk() } }.andReturn()

        assertSessionReissued(result.response.getHeader(HttpHeaders.SET_COOKIE), sessionId)
    }

    @Test
    fun `current cart returns the session's cart`() {
        every { cartRepository.findBySessionIdAndStatusIn(sessionId, CartStatus.CURRENT) } returns cartFor(sessionId)

        mockMvc.get("/api/v1/carts/current") { cookie(Cookie(CartController.SESSION_COOKIE, sessionId)) }
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(cartId.toString()) }
                jsonPath("$.summary.itemCount") { value(2) }
            }
    }

    @Test
    fun `updating a quantity passes the session, cart, item and quantity through`() {
        val captured = slot<UpdateCartItemQuantityCommand>()
        every { updateUseCase.execute(capture(captured), any()) } returns cartFor(sessionId).right()

        mockMvc.patch("/api/v1/carts/$cartId/items/$itemId") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
            contentType = MediaType.APPLICATION_JSON
            content = """{"quantity":3}"""
        }.andExpect { status { isOk() } }

        assertEquals(UpdateCartItemQuantityCommand(CartOwner.Guest(sessionId), cartId, itemId, 3), captured.captured)
    }

    @Test
    fun `an over-max update is a 422 that tells the client the max`() {
        every { updateUseCase.execute(any(), any()) } returns CartError.MaxQuantityExceeded(10).left()

        mockMvc.patch("/api/v1/carts/$cartId/items/$itemId") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
            contentType = MediaType.APPLICATION_JSON
            content = """{"quantity":11}"""
        }.andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.error") { value("Maximum order quantity is 10 for this item") }
            jsonPath("$.maxQuantity") { value(10) }
        }
    }

    @Test
    fun `updating without a session cookie is a 404 and never reaches the use case`() {
        mockMvc.patch("/api/v1/carts/$cartId/items/$itemId") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"quantity":3}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CART_ITEM_NOT_FOUND") }
        }
    }

    /** A delisted variant is also a 404, but its line is still in the cart; the code tells them apart. */
    /** PIN-278: a conflict that survives the use case's one retry is a 409, never a 500. */
    @Test
    fun `a change that keeps losing to concurrent changes is a 409 CART_CONFLICT`() {
        every { updateUseCase.execute(any(), any()) } throws
            org.springframework.orm.ObjectOptimisticLockingFailureException(Cart::class.java, cartId)

        mockMvc.patch("/api/v1/carts/$cartId/items/$itemId") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
            contentType = MediaType.APPLICATION_JSON
            content = """{"quantity":3}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CART_CONFLICT") }
            jsonPath("$.error") { value("Your cart was changed at the same time. Please try again.") }
        }
    }

    @Test
    fun `updating a line whose variant is gone is a 404 VARIANT_NOT_FOUND`() {
        every { updateUseCase.execute(any(), any()) } returns CartError.VariantNotFound(variantId).left()

        mockMvc.patch("/api/v1/carts/$cartId/items/$itemId") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
            contentType = MediaType.APPLICATION_JSON
            content = """{"quantity":3}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("VARIANT_NOT_FOUND") }
            jsonPath("$.error") { value("Variant not found: $variantId") }
        }
    }

    @Test
    fun `a zero quantity update is rejected`() {
        mockMvc.patch("/api/v1/carts/$cartId/items/$itemId") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
            contentType = MediaType.APPLICATION_JSON
            content = """{"quantity":0}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Invalid request: quantity") }
            jsonPath("$.code") { value(CartController.INVALID_REQUEST) }
        }
    }

    // PIN-279: 2.9 used to be truncated to 2 and accepted
    @Test
    fun `a decimal quantity update is rejected, not truncated`() {
        // Stubbed so a regression fails on the status, not on a missing MockK answer
        every { updateUseCase.execute(any(), any()) } returns cartFor(sessionId).right()

        mockMvc.patch("/api/v1/carts/$cartId/items/$itemId") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
            contentType = MediaType.APPLICATION_JSON
            content = """{"quantity":2.9}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Invalid request: quantity") }
            jsonPath("$.code") { value(CartController.INVALID_REQUEST) }
        }

        verify(exactly = 0) { updateUseCase.execute(any(), any()) }
    }

    // PIN-305: a path ID that is no UUID is a 400 naming it, not Spring's default body
    @Test
    fun `updating with a cart ID that is no UUID is a 400 INVALID_REQUEST naming cartId`() {
        mockMvc.patch("/api/v1/carts/not-a-uuid/items/$itemId") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
            contentType = MediaType.APPLICATION_JSON
            content = """{"quantity":2}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Invalid request: cartId") }
            jsonPath("$.code") { value(CartController.INVALID_REQUEST) }
        }

        verify(exactly = 0) { updateUseCase.execute(any(), any()) }
    }

    @Test
    fun `updating with an item ID that is no UUID is a 400 INVALID_REQUEST naming itemId`() {
        mockMvc.patch("/api/v1/carts/$cartId/items/not-a-uuid") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
            contentType = MediaType.APPLICATION_JSON
            content = """{"quantity":2}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Invalid request: itemId") }
            jsonPath("$.code") { value(CartController.INVALID_REQUEST) }
        }

        verify(exactly = 0) { updateUseCase.execute(any(), any()) }
    }

    @Test
    fun `removing with a cart ID that is no UUID is a 400 INVALID_REQUEST naming cartId`() {
        mockMvc.delete("/api/v1/carts/not-a-uuid/items/$itemId") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Invalid request: cartId") }
            jsonPath("$.code") { value(CartController.INVALID_REQUEST) }
        }

        verify(exactly = 0) { removeUseCase.execute(any(), any()) }
    }

    @Test
    fun `clearing with a cart ID that is no UUID is a 400 INVALID_REQUEST naming cartId`() {
        mockMvc.delete("/api/v1/carts/not-a-uuid/items") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("Invalid request: cartId") }
            jsonPath("$.code") { value(CartController.INVALID_REQUEST) }
        }

        verify(exactly = 0) { clearUseCase.execute(any(), any()) }
    }

    @Test
    fun `removing an item returns the updated cart`() {
        val captured = slot<RemoveCartItemCommand>()
        every { removeUseCase.execute(capture(captured), any()) } returns Cart(id = cartId, sessionId = sessionId).right()

        mockMvc.delete("/api/v1/carts/$cartId/items/$itemId") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
        }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(0) }
            jsonPath("$.summary.itemCount") { value(0) }
        }

        assertEquals(RemoveCartItemCommand(CartOwner.Guest(sessionId), cartId, itemId), captured.captured)
    }

    @Test
    fun `removing from a cart the session does not own is a 404`() {
        every { removeUseCase.execute(any(), any()) } returns CartError.CartItemNotFound(itemId).left()

        mockMvc.delete("/api/v1/carts/$cartId/items/$itemId") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CART_ITEM_NOT_FOUND") }
        }
    }

    // --- PIN-294: clear the entire cart -------------------------------------------------

    @Test
    fun `clearing the cart returns the empty cart`() {
        val captured = slot<ClearCartCommand>()
        every { clearUseCase.execute(capture(captured), any()) } returns Cart(id = cartId, sessionId = sessionId).right()

        mockMvc.delete("/api/v1/carts/$cartId/items") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(cartId.toString()) }
            jsonPath("$.items.length()") { value(0) }
            jsonPath("$.summary.itemCount") { value(0) }
        }

        assertEquals(ClearCartCommand(CartOwner.Guest(sessionId), cartId), captured.captured)
    }

    @Test
    fun `clearing a cart the session does not own is a 404`() {
        every { clearUseCase.execute(any(), any()) } returns CartError.CartNotFound(cartId).left()

        mockMvc.delete("/api/v1/carts/$cartId/items") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CART_NOT_FOUND") }
        }
    }

    @Test
    fun `clearing without a session or token is a 404`() {
        mockMvc.delete("/api/v1/carts/$cartId/items").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CART_NOT_FOUND") }
        }
        verify(exactly = 0) { clearUseCase.execute(any(), any()) }
    }

    // --- US-0004-08: signed-in callers ------------------------------------------------

    private val userId = UUID.randomUUID()

    /** The access_token cookie decodes to a verified token for [userId]. */
    private fun signedIn() {
        every { jwtDecoder.decode("good-token") } returns Jwt.withTokenValue("good-token")
            .header("alg", "RS256")
            .subject(userId.toString())
            .build()
    }

    private fun accessToken(value: String = "good-token") = Cookie(SecurityConfig.ACCESS_TOKEN_COOKIE, value)

    @Test
    fun `a signed-in add goes to the user's cart and sets no guest cookie`() {
        signedIn()

        val result = mockMvc.post("/api/v1/carts/items") {
            contentType = MediaType.APPLICATION_JSON
            content = body
            cookie(accessToken())
        }.andExpect { status { isCreated() } }.andReturn()

        assertEquals(CartOwner.Customer(userId), command.captured.owner)
        assertEquals(null, result.response.getHeader(HttpHeaders.SET_COOKIE))
    }

    @Test
    fun `a signed-in caller's token wins over a guest session cookie`() {
        signedIn()

        mockMvc.post("/api/v1/carts/items") {
            contentType = MediaType.APPLICATION_JSON
            content = body
            cookie(accessToken(), Cookie(CartController.SESSION_COOKIE, sessionId))
        }.andExpect { status { isCreated() } }

        assertEquals(CartOwner.Customer(userId), command.captured.owner)
    }

    @Test
    fun `current cart for a signed-in caller is the user's cart`() {
        signedIn()
        every { cartRepository.findByUserIdAndStatusIn(userId, CartStatus.CURRENT) } returns newCartFor(CartOwner.Customer(userId))

        val result = mockMvc.get("/api/v1/carts/current") {
            cookie(accessToken(), Cookie(CartController.SESSION_COOKIE, sessionId))
        }.andExpect { status { isOk() } }.andReturn()

        assertEquals(null, result.response.getHeader(HttpHeaders.SET_COOKIE))
    }

    @Test
    fun `a signed-in update acts on the user's cart`() {
        signedIn()
        val captured = slot<UpdateCartItemQuantityCommand>()
        every { updateUseCase.execute(capture(captured), any()) } returns newCartFor(CartOwner.Customer(userId)).right()

        mockMvc.patch("/api/v1/carts/$cartId/items/$itemId") {
            cookie(accessToken())
            contentType = MediaType.APPLICATION_JSON
            content = """{"quantity":3}"""
        }.andExpect { status { isOk() } }

        assertEquals(CartOwner.Customer(userId), captured.captured.owner)
        verify(exactly = 0) { cartRepository.touchGuestCart(any(), any(), any()) }
    }

    @Test
    fun `an expired token is a 401 TOKEN_EXPIRED so the client refreshes and retries`() {
        every { jwtDecoder.decode("stale-token") } throws JwtValidationException(
            "An error occurred while attempting to decode the Jwt: Jwt expired at 2026-01-01T00:00:00Z",
            listOf(OAuth2Error("invalid_token", "Jwt expired at 2026-01-01T00:00:00Z", null))
        )

        mockMvc.get("/api/v1/carts/current") { cookie(accessToken("stale-token")) }
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.error") { value("TOKEN_EXPIRED") }
            }
    }

    @Test
    fun `a token that fails verification for another reason is a 401 INVALID_TOKEN`() {
        every { jwtDecoder.decode("forged-token") } throws JwtValidationException(
            "An error occurred while attempting to decode the Jwt: Signed JWT rejected",
            listOf(OAuth2Error("invalid_token", "Signed JWT rejected: Invalid signature", null))
        )

        mockMvc.get("/api/v1/carts/current") { cookie(accessToken("forged-token")) }
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.error") { value("INVALID_TOKEN") }
            }
    }

    // --- US-0004-08: merge on sign-in -------------------------------------------------

    @Test
    fun `merging requires a signed-in caller`() {
        val result = mockMvc.post("/api/v1/carts/merge") { cookie(Cookie(CartController.SESSION_COOKIE, sessionId)) }
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.error") { value("SIGN_IN_REQUIRED") }
            }.andReturn()

        // Still a guest cart request: the interceptor treats it like any other (PIN-288).
        assertSessionReissued(result.response.getHeader(HttpHeaders.SET_COOKIE), sessionId)
        assertActivityRecorded(sessionId)
    }

    @Test
    fun `a merge passes the user and guest session through and returns the cart with its result`() {
        signedIn()
        val captured = slot<MergeCartsCommand>()
        val merged = newCartFor(CartOwner.Customer(userId))
        merged.addItem(variantId, 10, VariantPricing(BigDecimal("59.99")), """{"productId":"${UUID.randomUUID()}","name":"Mouse","sku":"S","variantName":"Black","imageUrl":null}""", 10)
        every { mergeUseCase.execute(capture(captured), any()) } returns MergeOutcome(
            merged,
            MergeResult(itemsMerged = 1, quantitiesAdjusted = listOf(QuantityAdjustment(variantId, 12, 10)))
        ).right()

        val result = mockMvc.post("/api/v1/carts/merge") {
            cookie(accessToken(), Cookie(CartController.SESSION_COOKIE, sessionId))
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ACTIVE") }
            jsonPath("$.summary.itemCount") { value(10) }
            jsonPath("$.mergeResult.itemsMerged") { value(1) }
            jsonPath("$.mergeResult.quantitiesAdjusted[0].variantId") { value(variantId.toString()) }
            jsonPath("$.mergeResult.quantitiesAdjusted[0].requestedTotal") { value(12) }
            jsonPath("$.mergeResult.quantitiesAdjusted[0].adjustedTo") { value(10) }
            jsonPath("$.mergeResult.quantitiesAdjusted[0].reason") { value("MAX_ORDER_QUANTITY") }
        }.andReturn()

        assertEquals(MergeCartsCommand(userId, sessionId), captured.captured)
        assertEquals(null, result.response.getHeader(HttpHeaders.SET_COOKIE))
        verify(exactly = 0) { cartRepository.touchGuestCart(any(), any(), any()) }
    }

    // PIN-306: a line left out because its variant is gone carries its snapshot, so the
    // client can name it although it is not in the merged cart
    @Test
    fun `a merge reports lines left out with their product snapshot`() {
        signedIn()
        val gone = UUID.randomUUID()
        every { mergeUseCase.execute(any(), any()) } returns MergeOutcome(
            newCartFor(CartOwner.Customer(userId)),
            MergeResult(
                itemsMerged = 0,
                quantitiesAdjusted = emptyList(),
                itemsUnavailable = listOf(
                    UnavailableItem(gone, """{"productId":"${UUID.randomUUID()}","name":"Old Widget","sku":"W","variantName":"Blue","imageUrl":null}""")
                )
            )
        ).right()

        mockMvc.post("/api/v1/carts/merge") {
            cookie(accessToken(), Cookie(CartController.SESSION_COOKIE, sessionId))
        }.andExpect {
            status { isOk() }
            jsonPath("$.mergeResult.itemsUnavailable[0].variantId") { value(gone.toString()) }
            jsonPath("$.mergeResult.itemsUnavailable[0].productSnapshot.name") { value("Old Widget") }
        }
    }

    @Test
    fun `a merge with nothing to merge and no user cart is a 204`() {
        signedIn()
        every { mergeUseCase.execute(any(), any()) } returns MergeOutcome(cart = null, result = null).right()

        mockMvc.post("/api/v1/carts/merge") { cookie(accessToken()) }
            .andExpect { status { isNoContent() } }
    }

    @Test
    fun `a merge ignores a session cookie this service could not have minted`() {
        signedIn()
        val captured = slot<MergeCartsCommand>()
        every { mergeUseCase.execute(capture(captured), any()) } returns MergeOutcome(cart = null, result = null).right()

        mockMvc.post("/api/v1/carts/merge") {
            cookie(accessToken(), Cookie(CartController.SESSION_COOKIE, "not-a-uuid"))
        }.andExpect { status { isNoContent() } }

        assertEquals(null, captured.captured.guestSessionId)
    }

    // --- PIN-329: starting checkout ---------------------------------------------------

    @Test
    fun `starting checkout returns the session the cart is locked for`() {
        val cart = cartFor(sessionId)
        val session = cart.startCheckout(java.time.Duration.ofMinutes(30), now = java.time.Instant.parse("2026-10-01T15:00:00Z"))
            .getOrNull()!!
        val captured = slot<StartCheckoutCommand>()
        every { checkoutUseCase.execute(capture(captured), any()) } returns CheckoutStarted(cart, session).right()

        mockMvc.post("/api/v1/carts/$cartId/checkout") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
        }.andExpect {
            status { isOk() }
            jsonPath("$.checkoutSessionId") { value(session.id.toString()) }
            jsonPath("$.cartId") { value(cartId.toString()) }
            jsonPath("$.status") { value("INITIATED") }
            jsonPath("$.expiresAt") { value("2026-10-01T15:30:00Z") }
            jsonPath("$.cart.itemCount") { value(2) }
            jsonPath("$.cart.subtotal") { value(139.98) }
            jsonPath("$.cart.currency") { value("USD") }
        }

        assertEquals(StartCheckoutCommand(CartOwner.Guest(sessionId), cartId), captured.captured)
    }

    @Test
    fun `starting checkout on an empty cart is a 422 CART_EMPTY`() {
        every { checkoutUseCase.execute(any(), any()) } returns CartError.CartEmpty(cartId).left()

        mockMvc.post("/api/v1/carts/$cartId/checkout") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
        }.andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("CART_EMPTY") }
        }
    }

    @Test
    fun `unavailable lines refuse checkout with a 422 listing each one`() {
        val itemId = UUID.randomUUID()
        every { checkoutUseCase.execute(any(), any()) } returns CartError.CartUnavailableItems(
            listOf(UnavailableLine(itemId, variantId, "Gadget Pro", AvailabilityIssue.OUT_OF_STOCK))
        ).left()

        mockMvc.post("/api/v1/carts/$cartId/checkout") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
        }.andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("CART_VALIDATION_FAILED") }
            jsonPath("$.error") { value("Some items in your cart are no longer available") }
            jsonPath("$.validationErrors.length()") { value(1) }
            jsonPath("$.validationErrors[0].cartItemId") { value(itemId.toString()) }
            jsonPath("$.validationErrors[0].variantId") { value(variantId.toString()) }
            jsonPath("$.validationErrors[0].productName") { value("Gadget Pro") }
            jsonPath("$.validationErrors[0].issue") { value("OUT_OF_STOCK") }
        }
    }

    @Test
    fun `an availability lookup failure is a 503`() {
        every { checkoutUseCase.execute(any(), any()) } returns CartError.AvailabilityUnavailable(variantId).left()

        mockMvc.post("/api/v1/carts/$cartId/checkout") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
        }.andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("AVAILABILITY_UNAVAILABLE") }
        }
    }

    @Test
    fun `starting checkout without a session or token is a 404`() {
        mockMvc.post("/api/v1/carts/$cartId/checkout").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CART_NOT_FOUND") }
        }
        verify(exactly = 0) { checkoutUseCase.execute(any(), any()) }
    }

    @Test
    fun `a change to a cart in checkout is a 409 CART_LOCKED`() {
        every { clearUseCase.execute(any(), any()) } returns CartError.CartLocked(cartId).left()

        mockMvc.delete("/api/v1/carts/$cartId/items") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CART_LOCKED") }
        }
    }

    @Test
    fun `the current cart shows that it is locked for checkout, and viewing it extends the session`() {
        val cart = cartFor(sessionId)
        cart.startCheckout(java.time.Duration.ofMinutes(30))
        every { cartRepository.findBySessionIdAndStatusIn(sessionId, CartStatus.CURRENT) } returns cart
        val now = slot<Instant>()
        val expiresAt = slot<Instant>()
        every { cartRepository.extendCheckout(cartId, capture(now), capture(expiresAt)) } returns 1

        mockMvc.get("/api/v1/carts/current") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CHECKOUT") }
        }

        assertEquals(now.captured.plus(java.time.Duration.ofMinutes(30)), expiresAt.captured)
    }

    @Test
    fun `viewing a cart that is not in checkout extends nothing`() {
        every { cartRepository.findBySessionIdAndStatusIn(sessionId, CartStatus.CURRENT) } returns cartFor(sessionId)

        mockMvc.get("/api/v1/carts/current") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
        }.andExpect { status { isOk() } }

        verify(exactly = 0) { cartRepository.extendCheckout(any(), any(), any()) }
    }

    // --- PIN-330: leaving checkout -----------------------------------------------------

    @Test
    fun `leaving checkout returns the unlocked cart`() {
        val cart = cartFor(sessionId)
        val captured = slot<AbandonCheckoutCommand>()
        every { abandonUseCase.execute(capture(captured), any()) } returns cart.right()

        mockMvc.delete("/api/v1/carts/$cartId/checkout") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(cartId.toString()) }
            jsonPath("$.status") { value("ACTIVE") }
        }

        assertEquals(AbandonCheckoutCommand(CartOwner.Guest(sessionId), cartId), captured.captured)
    }

    @Test
    fun `leaving checkout on a cart the caller does not own is a 404`() {
        every { abandonUseCase.execute(any(), any()) } returns CartError.CartNotFound(cartId).left()

        mockMvc.delete("/api/v1/carts/$cartId/checkout") {
            cookie(Cookie(CartController.SESSION_COOKIE, sessionId))
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CART_NOT_FOUND") }
        }
    }

    @Test
    fun `leaving checkout without a session or token is a 404`() {
        mockMvc.delete("/api/v1/carts/$cartId/checkout").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CART_NOT_FOUND") }
        }
        verify(exactly = 0) { abandonUseCase.execute(any(), any()) }
    }
}
