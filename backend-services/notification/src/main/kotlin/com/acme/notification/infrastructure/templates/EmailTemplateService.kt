package com.acme.notification.infrastructure.templates

import org.springframework.beans.factory.annotation.Value
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
    private val templateEngine: TemplateEngine,
    @Value("\${notification.email.support-email}")
    private val supportEmail: String,
    @Value("\${notification.email.company-name}")
    private val companyName: String,
    @Value("\${notification.email.welcome.shop-url}")
    private val shopUrl: String,
    @Value("\${notification.email.welcome.profile-url}")
    private val profileUrl: String,
    @Value("\${notification.email.social-media.facebook-url}")
    private val facebookUrl: String,
    @Value("\${notification.email.social-media.twitter-url}")
    private val twitterUrl: String,
    @Value("\${notification.email.social-media.instagram-url}")
    private val instagramUrl: String,
    @Value("\${notification.email.preferences.unsubscribe-url}")
    private val unsubscribeUrl: String,
    @Value("\${notification.email.preferences.manage-preferences-url}")
    private val managePreferencesUrl: String
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

    /**
     * Renders the welcome email template.
     *
     * Selects between transactional-only or marketing-included template
     * based on the customer's marketing preference.
     *
     * @param recipientName The recipient's first name.
     * @param displayName The recipient's display name.
     * @param customerNumber The customer number for reference.
     * @param marketingOptIn Whether the customer opted in to marketing.
     * @param showProfileCta Whether to show the profile completion CTA.
     * @return The rendered HTML content.
     */
    fun renderWelcomeEmail(
        recipientName: String,
        displayName: String,
        customerNumber: String,
        marketingOptIn: Boolean,
        showProfileCta: Boolean
    ): String {
        val context = Context(Locale.US).apply {
            setVariable("recipientName", recipientName)
            setVariable("displayName", displayName)
            setVariable("customerNumber", customerNumber)
            setVariable("shopUrl", shopUrl)
            setVariable("profileUrl", profileUrl)
            setVariable("supportEmail", supportEmail)
            setVariable("companyName", companyName)
            setVariable("currentYear", Year.now().value)
            setVariable("marketingOptIn", marketingOptIn)
            setVariable("showProfileCta", showProfileCta)
            setVariable("facebookUrl", facebookUrl)
            setVariable("twitterUrl", twitterUrl)
            setVariable("instagramUrl", instagramUrl)
            setVariable("unsubscribeUrl", unsubscribeUrl)
            setVariable("managePreferencesUrl", managePreferencesUrl)
        }

        // Select template based on marketing preference
        val templateName = if (marketingOptIn) {
            "email/welcome-marketing"
        } else {
            "email/welcome-transactional"
        }

        return templateEngine.process(templateName, context)
    }
}
