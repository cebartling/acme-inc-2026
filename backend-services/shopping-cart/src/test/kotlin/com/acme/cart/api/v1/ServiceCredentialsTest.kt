package com.acme.cart.api.v1

import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceCredentialsTest {

    private val credentials = ServiceCredentials("order-secret")

    @Test
    fun `only the exact token is the order service`() {
        assertTrue(credentials.isOrderService("order-secret"))
        listOf(null, "", "order-secre", "order-secret ", "ORDER-SECRET").forEach {
            assertFalse(credentials.isOrderService(it), "'$it' must not pass")
        }
    }

    @Test
    fun `a blank or padded token is rejected at startup`() {
        listOf("", "  ", " order-secret", "order-secret\n").forEach { token ->
            assertFailsWith<IllegalArgumentException> { ServiceCredentials(token) }
        }
    }
}
