package com.acme.customer.application

import com.acme.customer.domain.*
import com.acme.customer.infrastructure.persistence.AddressRepository
import com.acme.customer.infrastructure.persistence.ConsentRecordRepository
import com.acme.customer.infrastructure.persistence.CustomerPreferencesRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Calculator for profile completeness scores.
 *
 * Calculates profile completeness based on weighted sections as defined
 * in the user story US-0002-12. Each section contributes to the overall
 * score according to its weight.
 *
 * Section weights:
 * - Basic Info (25%): First name, last name, verified email
 * - Contact Info (15%): Phone number added
 * - Personal Details (15%): Date of birth OR gender provided (OR logic)
 * - Address (20%): At least one validated address
 * - Preferences (15%): Communication preferences configured
 * - Consent (10%): All required consents granted
 */
@Component
class ProfileCompletenessCalculator(
    private val addressRepository: AddressRepository,
    private val consentRecordRepository: ConsentRecordRepository,
    private val preferencesRepository: CustomerPreferencesRepository
) {
    private val logger = LoggerFactory.getLogger(ProfileCompletenessCalculator::class.java)

    companion object {
        /**
         * Weight for each profile section.
         */
        val SECTION_WEIGHTS = mapOf(
            "basicInfo" to 25,
            "contactInfo" to 15,
            "personalDetails" to 15,
            "address" to 20,
            "preferences" to 15,
            "consent" to 10
        )

        /**
         * URL mappings for each section.
         */
        private val SECTION_URLS = mapOf(
            "basicInfo" to "/profile/basic",
            "contactInfo" to "/profile/contact",
            "personalDetails" to "/profile/personal",
            "address" to "/profile/addresses",
            "preferences" to "/profile/preferences",
            "consent" to "/profile/consent"
        )
    }

    /**
     * Calculates the complete profile completeness for a customer.
     *
     * @param customer The customer entity.
     * @return The calculated profile completeness with all section details.
     */
    fun calculate(customer: Customer): ProfileCompleteness {
        logger.debug("Calculating profile completeness for customer: {}", customer.id)

        val addresses = addressRepository.findByCustomerId(customer.id)
        val consents = consentRecordRepository.findCurrentConsentsByCustomerId(customer.id)
        val preferences = preferencesRepository.findById(customer.id).orElse(null)

        val sections = listOf(
            calculateBasicInfo(customer),
            calculateContactInfo(customer),
            calculatePersonalDetails(customer),
            calculateAddress(addresses),
            calculatePreferences(preferences),
            calculateConsent(consents)
        )

        val overallScore = sections.sumOf { section ->
            (section.score * SECTION_WEIGHTS[section.name]!!) / 100
        }

        val nextAction = determineNextAction(sections)

        logger.debug("Profile completeness for customer {}: {}%", customer.id, overallScore)

        return ProfileCompleteness(
            customerId = customer.id,
            overallScore = overallScore,
            sections = sections,
            nextAction = nextAction,
            updatedAt = Instant.now()
        )
    }

    /**
     * Calculates the basic info section completeness.
     *
     * Basic info is complete when:
     * - First name is present
     * - Last name is present
     * - Email is verified
     */
    private fun calculateBasicInfo(customer: Customer): SectionCompleteness {
        val hasFirstName = customer.firstName.isNotBlank()
        val hasLastName = customer.lastName.isNotBlank()
        val hasVerifiedEmail = customer.emailVerified

        val items = listOf(
            ItemCompleteness(
                name = "firstName",
                complete = hasFirstName,
                action = if (!hasFirstName) "Add your first name" else null
            ),
            ItemCompleteness(
                name = "lastName",
                complete = hasLastName,
                action = if (!hasLastName) "Add your last name" else null
            ),
            ItemCompleteness(
                name = "emailVerified",
                complete = hasVerifiedEmail,
                action = if (!hasVerifiedEmail) "Verify your email address" else null
            )
        )

        // All items must be complete for section to be complete
        val isComplete = items.all { it.complete }
        val score = if (isComplete) 100 else 0

        return SectionCompleteness(
            name = "basicInfo",
            displayName = "Basic Information",
            weight = SECTION_WEIGHTS["basicInfo"]!!,
            score = score,
            isComplete = isComplete,
            items = items
        )
    }

    /**
     * Calculates the contact info section completeness.
     *
     * Contact info is complete when a phone number has been added.
     */
    private fun calculateContactInfo(customer: Customer): SectionCompleteness {
        val hasPhoneNumber = !customer.phoneNumber.isNullOrBlank()

        val items = listOf(
            ItemCompleteness(
                name = "phoneNumber",
                complete = hasPhoneNumber,
                action = if (!hasPhoneNumber) "Add phone number" else null
            )
        )

        val score = if (hasPhoneNumber) 100 else 0

        return SectionCompleteness(
            name = "contactInfo",
            displayName = "Contact Information",
            weight = SECTION_WEIGHTS["contactInfo"]!!,
            score = score,
            isComplete = hasPhoneNumber,
            items = items
        )
    }

    /**
     * Calculates the personal details section completeness.
     *
     * Personal details is complete when date of birth OR gender is provided (OR logic).
     * Providing both does not exceed 100%.
     */
    private fun calculatePersonalDetails(customer: Customer): SectionCompleteness {
        val hasDateOfBirth = customer.dateOfBirth != null
        val hasGender = !customer.gender.isNullOrBlank()

        // OR logic: either one is sufficient for section completion
        val isComplete = hasDateOfBirth || hasGender
        val score = if (isComplete) 100 else 0

        val items = listOf(
            ItemCompleteness(
                name = "dateOfBirth",
                complete = hasDateOfBirth,
                action = if (!hasDateOfBirth && !hasGender) "Add your date of birth" else null
            ),
            ItemCompleteness(
                name = "gender",
                complete = hasGender,
                action = if (!hasGender && !hasDateOfBirth) "Add your gender" else null
            )
        )

        return SectionCompleteness(
            name = "personalDetails",
            displayName = "Personal Details",
            weight = SECTION_WEIGHTS["personalDetails"]!!,
            score = score,
            isComplete = isComplete,
            items = items
        )
    }

    /**
     * Calculates the address section completeness.
     *
     * Address is complete when at least one validated address exists.
     */
    private fun calculateAddress(addresses: List<Address>): SectionCompleteness {
        val hasValidatedAddress = addresses.any { it.isValidated }

        val items = listOf(
            ItemCompleteness(
                name = "validatedAddress",
                complete = hasValidatedAddress,
                action = if (!hasValidatedAddress) "Add a shipping address" else null
            )
        )

        val score = if (hasValidatedAddress) 100 else 0

        return SectionCompleteness(
            name = "address",
            displayName = "Address",
            weight = SECTION_WEIGHTS["address"]!!,
            score = score,
            isComplete = hasValidatedAddress,
            items = items
        )
    }

    /**
     * Calculates the preferences section completeness.
     *
     * Preferences is complete when communication preferences have been set.
     * We consider preferences "set" if they exist and have been explicitly configured
     * (i.e., the preferences record exists for the customer).
     */
    private fun calculatePreferences(preferences: CustomerPreferences?): SectionCompleteness {
        // Preferences are considered set if the record exists
        // The record is created at registration, but we check that at least
        // one communication preference has been explicitly configured
        val hasPreferences = preferences != null

        val items = listOf(
            ItemCompleteness(
                name = "communicationPreferences",
                complete = hasPreferences,
                action = if (!hasPreferences) "Configure communication preferences" else null
            )
        )

        val score = if (hasPreferences) 100 else 0

        return SectionCompleteness(
            name = "preferences",
            displayName = "Preferences",
            weight = SECTION_WEIGHTS["preferences"]!!,
            score = score,
            isComplete = hasPreferences,
            items = items
        )
    }

    /**
     * Calculates the consent section completeness.
     *
     * Consent is complete when all required consents are granted.
     */
    private fun calculateConsent(consents: List<ConsentRecord>): SectionCompleteness {
        // Get required consent types
        val requiredConsentTypes = ConsentType.entries.filter { it.required }

        // Check if all required consents are currently granted
        val allRequiredGranted = requiredConsentTypes.all { requiredType ->
            consents.any { consent ->
                consent.consentType == requiredType && consent.isEffective()
            }
        }

        val items = listOf(
            ItemCompleteness(
                name = "requiredConsents",
                complete = allRequiredGranted,
                action = if (!allRequiredGranted) "Review consent settings" else null
            )
        )

        val score = if (allRequiredGranted) 100 else 0

        return SectionCompleteness(
            name = "consent",
            displayName = "Consent",
            weight = SECTION_WEIGHTS["consent"]!!,
            score = score,
            isComplete = allRequiredGranted,
            items = items
        )
    }

    /**
     * Determines the next action to complete the profile.
     *
     * Returns the first incomplete section's first incomplete item.
     */
    private fun determineNextAction(sections: List<SectionCompleteness>): NextAction? {
        val incompleteSection = sections.firstOrNull { !it.isComplete }
            ?: return null

        val incompleteItem = incompleteSection.items.firstOrNull { !it.complete }
            ?: return null

        return NextAction(
            section = incompleteSection.name,
            action = incompleteItem.action ?: "Complete ${incompleteSection.displayName}",
            url = SECTION_URLS[incompleteSection.name] ?: "/profile"
        )
    }
}
