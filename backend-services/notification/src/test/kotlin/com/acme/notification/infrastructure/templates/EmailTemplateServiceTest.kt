package com.acme.notification.infrastructure.templates

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.thymeleaf.TemplateEngine
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import org.thymeleaf.templatemode.TemplateMode
import java.time.Year

class EmailTemplateServiceTest {

    private lateinit var templateService: EmailTemplateService

    @BeforeEach
    fun setUp() {
        val templateResolver = ClassLoaderTemplateResolver().apply {
            prefix = "templates/"
            suffix = ".html"
            templateMode = TemplateMode.HTML
            characterEncoding = "UTF-8"
        }
        val templateEngine = TemplateEngine().apply {
            setTemplateResolver(templateResolver)
        }
        templateService = EmailTemplateService(templateEngine)
    }

    @Test
    fun `should render verification email template with all variables`() {
        val html = templateService.renderVerificationEmail(
            recipientName = "John",
            recipientEmail = "john@example.com",
            verificationUrl = "https://acme.com/verify?token=abc123",
            expirationHours = 24,
            supportEmail = "support@acme.com",
            companyName = "ACME Inc."
        )

        assertNotNull(html)
        assertTrue(html.contains("John"))
        assertTrue(html.contains("https://acme.com/verify?token=abc123"))
        assertTrue(html.contains("24"))
        assertTrue(html.contains("support@acme.com"))
        assertTrue(html.contains("ACME Inc."))
        assertTrue(html.contains(Year.now().value.toString()))
    }

    @Test
    fun `should include verification button with correct URL`() {
        val verificationUrl = "https://acme.com/verify?token=test123"

        val html = templateService.renderVerificationEmail(
            recipientName = "Jane",
            recipientEmail = "jane@example.com",
            verificationUrl = verificationUrl,
            expirationHours = 24,
            supportEmail = "support@acme.com",
            companyName = "ACME Inc."
        )

        assertTrue(html.contains("href=\"$verificationUrl\""))
        assertTrue(html.contains("Verify Email Address"))
    }

    @Test
    fun `should include expiration notice`() {
        val html = templateService.renderVerificationEmail(
            recipientName = "Test",
            recipientEmail = "test@example.com",
            verificationUrl = "https://acme.com/verify",
            expirationHours = 48,
            supportEmail = "support@acme.com",
            companyName = "ACME Inc."
        )

        assertTrue(html.contains("48"))
        assertTrue(html.contains("expires"))
    }

    @Test
    fun `should include support email link`() {
        val supportEmail = "help@acme.com"

        val html = templateService.renderVerificationEmail(
            recipientName = "User",
            recipientEmail = "user@example.com",
            verificationUrl = "https://acme.com/verify",
            expirationHours = 24,
            supportEmail = supportEmail,
            companyName = "ACME Inc."
        )

        assertTrue(html.contains("mailto:$supportEmail"))
        assertTrue(html.contains(supportEmail))
    }

    @Test
    fun `should include company name in footer`() {
        val companyName = "Test Company"

        val html = templateService.renderVerificationEmail(
            recipientName = "User",
            recipientEmail = "user@example.com",
            verificationUrl = "https://acme.com/verify",
            expirationHours = 24,
            supportEmail = "support@test.com",
            companyName = companyName
        )

        assertTrue(html.contains(companyName))
    }
}
