package com.acme.notification.infrastructure.templates

import org.springframework.stereotype.Service
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.time.Year
import java.util.Locale

/**
 * Service for rendering email templates using Thymeleaf.
 */
@Service
class EmailTemplateService(
    private val templateEngine: TemplateEngine
) {

    /**
     * Renders the verification email template.
     *
     * @param recipientName The recipient's first name.
     * @param verificationUrl The full verification URL.
     * @param expirationHours Number of hours until the link expires.
     * @param supportEmail Support email address.
     * @param companyName Company name for the footer.
     * @return The rendered HTML content.
     */
    fun renderVerificationEmail(
        recipientName: String,
        verificationUrl: String,
        expirationHours: Int,
        supportEmail: String,
        companyName: String
    ): String {
        val context = Context(Locale.US).apply {
            setVariable("recipientName", recipientName)
            setVariable("verificationUrl", verificationUrl)
            setVariable("expirationHours", expirationHours)
            setVariable("supportEmail", supportEmail)
            setVariable("companyName", companyName)
            setVariable("currentYear", Year.now().value)
        }

        return templateEngine.process("email/verification", context)
    }
}
