package com.acme.customer.domain

import java.time.Instant
import java.util.UUID

/**
 * Represents the completeness status of a single item within a profile section.
 *
 * @property name The identifier for this item (e.g., "firstName", "phoneNumber").
 * @property complete Whether this item has been completed.
 * @property action Suggested action if the item is incomplete (null if complete).
 */
data class ItemCompleteness(
    val name: String,
    val complete: Boolean,
    val action: String? = null
)

/**
 * Represents the completeness status of a profile section.
 *
 * @property name The internal identifier for this section (e.g., "basicInfo").
 * @property displayName Human-readable name for this section.
 * @property weight The percentage weight of this section in the overall score.
 * @property score The section's completion score (0 or 100).
 * @property isComplete Whether all required items in this section are complete.
 * @property items Individual items within this section.
 */
data class SectionCompleteness(
    val name: String,
    val displayName: String,
    val weight: Int,
    val score: Int,
    val isComplete: Boolean,
    val items: List<ItemCompleteness>
)

/**
 * Represents the next recommended action to complete the profile.
 *
 * @property section The section name where action is needed.
 * @property action Description of the action to take.
 * @property url The URL path to navigate to for completing this action.
 */
data class NextAction(
    val section: String,
    val action: String,
    val url: String
)

/**
 * Represents the complete profile completeness status for a customer.
 *
 * Profile completeness is calculated based on weighted sections:
 * - Basic Info (25%): First name, last name, verified email
 * - Contact Info (15%): Phone number added
 * - Personal Details (15%): Date of birth OR gender provided
 * - Address (20%): At least one validated address
 * - Preferences (15%): Communication preferences set
 * - Consent (10%): All required consents granted
 *
 * @property customerId The customer's unique identifier.
 * @property overallScore The weighted overall completion percentage (0-100).
 * @property sections Detailed breakdown by section.
 * @property nextAction The next recommended action to improve the score.
 * @property updatedAt When the completeness was last calculated.
 */
data class ProfileCompleteness(
    val customerId: UUID,
    val overallScore: Int,
    val sections: List<SectionCompleteness>,
    val nextAction: NextAction?,
    val updatedAt: Instant = Instant.now()
) {
    /**
     * Checks if the profile is complete (100%).
     */
    fun isComplete(): Boolean = overallScore >= 100

    /**
     * Checks if the profile needs attention (below 80%).
     */
    fun needsAttention(): Boolean = overallScore < 80
}
