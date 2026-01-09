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
        templateService = EmailTemplateService(
            templateEngine = templateEngine,
            supportEmail = "support@acme.com",
            companyName = "ACME Inc.",
            shopUrl = "https://www.acme.com/shop",
            profileUrl = "https://www.acme.com/profile"
        )
    }

    @Test
    fun `should render verification email template with all variables`() {
        val html = templateService.renderVerificationEmail(
            recipientName = "John",
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
            verificationUrl = "https://acme.com/verify",
            expirationHours = 24,
            supportEmail = "support@test.com",
            companyName = companyName
        )

        assertTrue(html.contains(companyName))
    }

    // Welcome email template tests

    @Test
    fun `should render transactional welcome email template`() {
        val html = templateService.renderWelcomeEmail(
            recipientName = "Jane",
            displayName = "Jane Doe",
            customerNumber = "ACME-202601-000142",
            marketingOptIn = false,
            showProfileCta = true
        )

        assertNotNull(html)
        assertTrue(html.contains("Jane"))
        assertTrue(html.contains("ACME-202601-000142"))
        assertTrue(html.contains("https://www.acme.com/shop"))
        assertTrue(html.contains("Start Shopping"))
    }

    @Test
    fun `should render marketing welcome email template when opted in`() {
        val html = templateService.renderWelcomeEmail(
            recipientName = "John",
            displayName = "John Smith",
            customerNumber = "ACME-202601-000143",
            marketingOptIn = true,
            showProfileCta = false
        )

        assertNotNull(html)
        assertTrue(html.contains("John"))
        assertTrue(html.contains("ACME-202601-000143"))
        assertTrue(html.contains("WELCOME15")) // Promo code
        assertTrue(html.contains("Unsubscribe"))
    }

    @Test
    fun `should include profile completion CTA when profile is incomplete`() {
        val html = templateService.renderWelcomeEmail(
            recipientName = "User",
            displayName = "Test User",
            customerNumber = "ACME-202601-000001",
            marketingOptIn = false,
            showProfileCta = true
        )

        assertTrue(html.contains("Complete Your Profile"))
        assertTrue(html.contains("https://www.acme.com/profile"))
    }

    @Test
    fun `should not include profile completion CTA when profile is complete`() {
        val html = templateService.renderWelcomeEmail(
            recipientName = "User",
            displayName = "Test User",
            customerNumber = "ACME-202601-000001",
            marketingOptIn = false,
            showProfileCta = false
        )

        // The profile CTA section should not be rendered when showProfileCta is false
        // The button text "Complete Your Profile" should not appear
        assertFalse(html.contains("Complete Your Profile"))
    }

    @Test
    fun `should include current year in welcome email footer`() {
        val html = templateService.renderWelcomeEmail(
            recipientName = "User",
            displayName = "Test User",
            customerNumber = "ACME-202601-000001",
            marketingOptIn = false,
            showProfileCta = false
        )

        assertTrue(html.contains(Year.now().value.toString()))
    }
}
