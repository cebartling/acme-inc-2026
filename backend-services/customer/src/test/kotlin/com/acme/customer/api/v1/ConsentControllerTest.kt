package com.acme.customer.api.v1

import com.acme.customer.api.v1.dto.GrantConsentRequest
import com.acme.customer.application.ConsentHistoryEntry
import com.acme.customer.application.ConsentHistoryExport
import com.acme.customer.application.ConsentUpdateContext
import com.acme.customer.application.CurrentConsentStatus
import com.acme.customer.application.ExportConsentHistoryResult
import com.acme.customer.application.ExportConsentHistoryUseCase
import com.acme.customer.application.GetConsentsResult
import com.acme.customer.application.GetConsentsUseCase
import com.acme.customer.application.GrantConsentResult
import com.acme.customer.application.GrantConsentUseCase
import com.acme.customer.domain.ConsentRecord
import com.acme.customer.domain.ConsentSource
import com.acme.customer.domain.ConsentType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConsentControllerTest {

    private lateinit var grantConsentUseCase: GrantConsentUseCase
    private lateinit var getConsentsUseCase: GetConsentsUseCase
    private lateinit var exportConsentHistoryUseCase: ExportConsentHistoryUseCase
    private lateinit var controller: ConsentController

    private val customerId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        grantConsentUseCase = mockk()
        getConsentsUseCase = mockk()
        exportConsentHistoryUseCase = mockk()

        controller = ConsentController(
            grantConsentUseCase = grantConsentUseCase,
            getConsentsUseCase = getConsentsUseCase,
            exportConsentHistoryUseCase = exportConsentHistoryUseCase
        )
    }

    // --- Grant/Revoke Consent Tests ---

    @Test
    fun `grantOrRevokeConsent should return 400 for invalid customer ID`() {
        // Given
        val request = GrantConsentRequest(
            consentType = "MARKETING",
            granted = true,
            source = "PROFILE_WIZARD",
            ipAddress = "192.168.1.1"
        )

        // When
        val response = controller.grantOrRevokeConsent(
            customerId = "invalid-uuid",
            userId = userId.toString(),
            correlationId = null,
            request = request
        )

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `grantOrRevokeConsent should return 400 for invalid user ID`() {
        // Given
        val request = GrantConsentRequest(
            consentType = "MARKETING",
            granted = true,
            source = "PROFILE_WIZARD",
            ipAddress = "192.168.1.1"
        )

        // When
        val response = controller.grantOrRevokeConsent(
            customerId = customerId.toString(),
            userId = "invalid-uuid",
            correlationId = null,
            request = request
        )

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `grantOrRevokeConsent should return 404 when customer not found`() {
        // Given
        val request = GrantConsentRequest(
            consentType = "MARKETING",
            granted = true,
            source = "PROFILE_WIZARD",
            ipAddress = "192.168.1.1"
        )

        every {
            grantConsentUseCase.execute(
                customerId = customerId,
                userId = userId,
                consentTypeString = "MARKETING",
                granted = true,
                sourceString = "PROFILE_WIZARD",
                correlationId = any(),
                context = any()
            )
        } returns GrantConsentResult.CustomerNotFound(customerId)

        // When
        val response = controller.grantOrRevokeConsent(
            customerId = customerId.toString(),
            userId = userId.toString(),
            correlationId = null,
            request = request
        )

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `grantOrRevokeConsent should return 403 when unauthorized`() {
        // Given
        val request = GrantConsentRequest(
            consentType = "MARKETING",
            granted = true,
            source = "PROFILE_WIZARD",
            ipAddress = "192.168.1.1"
        )

        every {
            grantConsentUseCase.execute(
                customerId = customerId,
                userId = userId,
                consentTypeString = "MARKETING",
                granted = true,
                sourceString = "PROFILE_WIZARD",
                correlationId = any(),
                context = any()
            )
        } returns GrantConsentResult.Unauthorized(customerId, userId)

        // When
        val response = controller.grantOrRevokeConsent(
            customerId = customerId.toString(),
            userId = userId.toString(),
            correlationId = null,
            request = request
        )

        // Then
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `grantOrRevokeConsent should return 400 for required consent revocation`() {
        // Given
        val request = GrantConsentRequest(
            consentType = "DATA_PROCESSING",
            granted = false,
            source = "PRIVACY_SETTINGS",
            ipAddress = "192.168.1.1"
        )

        every {
            grantConsentUseCase.execute(
                customerId = customerId,
                userId = userId,
                consentTypeString = "DATA_PROCESSING",
                granted = false,
                sourceString = "PRIVACY_SETTINGS",
                correlationId = any(),
                context = any()
            )
        } returns GrantConsentResult.RequiredConsentCannotBeRevoked(ConsentType.DATA_PROCESSING)

        // When
        val response = controller.grantOrRevokeConsent(
            customerId = customerId.toString(),
            userId = userId.toString(),
            correlationId = null,
            request = request
        )

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals("REQUIRED_CONSENT", body["error"])
    }

    @Test
    fun `grantOrRevokeConsent should return 200 on success`() {
        // Given
        val request = GrantConsentRequest(
            consentType = "MARKETING",
            granted = true,
            source = "PROFILE_WIZARD",
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0"
        )

        val consentRecord = ConsentRecord.create(
            id = UUID.randomUUID(),
            customerId = customerId,
            consentType = ConsentType.MARKETING,
            granted = true,
            source = ConsentSource.PROFILE_WIZARD,
            ipAddress = "192.168.1.1",
            userAgent = "Mozilla/5.0",
            version = 1
        )

        every {
            grantConsentUseCase.execute(
                customerId = customerId,
                userId = userId,
                consentTypeString = "MARKETING",
                granted = true,
                sourceString = "PROFILE_WIZARD",
                correlationId = any(),
                context = any()
            )
        } returns GrantConsentResult.Success(consentRecord)

        // When
        val response = controller.grantOrRevokeConsent(
            customerId = customerId.toString(),
            userId = userId.toString(),
            correlationId = null,
            request = request
        )

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
    }

    // --- Get Consents Tests ---

    @Test
    fun `getConsents should return 200 with consent statuses`() {
        // Given
        val consents = listOf(
            CurrentConsentStatus(
                consentType = ConsentType.DATA_PROCESSING,
                currentStatus = true,
                grantedAt = Instant.now(),
                expiresAt = null,
                source = "REGISTRATION",
                required = true,
                version = 1
            ),
            CurrentConsentStatus(
                consentType = ConsentType.MARKETING,
                currentStatus = false,
                grantedAt = null,
                expiresAt = null,
                source = "NONE",
                required = false,
                version = 0
            )
        )

        every {
            getConsentsUseCase.execute(customerId, userId)
        } returns GetConsentsResult.Success(customerId, consents)

        // When
        val response = controller.getConsents(
            customerId = customerId.toString(),
            userId = userId.toString()
        )

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
    }

    @Test
    fun `getConsents should return 404 when customer not found`() {
        // Given
        every {
            getConsentsUseCase.execute(customerId, userId)
        } returns GetConsentsResult.CustomerNotFound(customerId)

        // When
        val response = controller.getConsents(
            customerId = customerId.toString(),
            userId = userId.toString()
        )

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    // --- Export Consent History Tests ---

    @Test
    fun `exportConsentHistory should return 200 with history`() {
        // Given
        val export = ConsentHistoryExport(
            customerId = customerId,
            exportedAt = Instant.now(),
            totalRecords = 2,
            consentHistory = listOf(
                ConsentHistoryEntry(
                    consentId = UUID.randomUUID(),
                    consentType = "DATA_PROCESSING",
                    granted = true,
                    timestamp = Instant.now(),
                    source = "REGISTRATION",
                    ipAddress = "192.168.1.1",
                    userAgent = "Mozilla/5.0"
                )
            )
        )

        every {
            exportConsentHistoryUseCase.execute(customerId, userId)
        } returns ExportConsentHistoryResult.Success(export)

        // When
        val response = controller.exportConsentHistory(
            customerId = customerId.toString(),
            userId = userId.toString(),
            format = "json"
        )

        // Then
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
    }

    @Test
    fun `exportConsentHistory should return 400 for unsupported format`() {
        // When
        val response = controller.exportConsentHistory(
            customerId = customerId.toString(),
            userId = userId.toString(),
            format = "xml"
        )

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as Map<*, *>
        assertEquals("UNSUPPORTED_FORMAT", body["error"])
    }

    @Test
    fun `exportConsentHistory should return 404 when customer not found`() {
        // Given
        every {
            exportConsentHistoryUseCase.execute(customerId, userId)
        } returns ExportConsentHistoryResult.CustomerNotFound(customerId)

        // When
        val response = controller.exportConsentHistory(
            customerId = customerId.toString(),
            userId = userId.toString(),
            format = "json"
        )

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }
}
