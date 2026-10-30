package com.acme.identity.api.v1

import com.acme.identity.api.v1.dto.DeviceRevocationErrorResponse
import com.acme.identity.application.DeviceTrustService
import com.acme.identity.application.TokenService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import kotlin.test.assertEquals

class DeviceControllerTest {
    private lateinit var tokenService: TokenService
    private lateinit var controller: DeviceController

    @BeforeEach
    fun setUp() {
        tokenService = mockk()
        every { tokenService.parseAccessToken(any()) } returns null
        controller = DeviceController(mockk<DeviceTrustService>(), tokenService)
    }

    private fun errorOf(response: ResponseEntity<Any>): String {
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        return (response.body as DeviceRevocationErrorResponse).error
    }

    @Test
    fun `an expired token is a 401 TOKEN_EXPIRED so the client refreshes and retries`() {
        every { tokenService.isExpiredAccessToken("stale") } returns true

        assertEquals("TOKEN_EXPIRED", errorOf(controller.listDevices("stale", null)))
    }

    @Test
    fun `a token that is not expired but fails verification is a 401 UNAUTHORIZED`() {
        every { tokenService.isExpiredAccessToken("forged") } returns false

        assertEquals("UNAUTHORIZED", errorOf(controller.listDevices("forged", null)))
    }

    @Test
    fun `a missing token is a 401 UNAUTHORIZED`() {
        assertEquals("UNAUTHORIZED", errorOf(controller.listDevices(null, null)))
    }
}
