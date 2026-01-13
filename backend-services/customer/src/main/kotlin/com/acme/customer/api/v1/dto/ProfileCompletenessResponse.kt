package com.acme.customer.api.v1.dto

import com.acme.customer.domain.ProfileCompleteness
import java.time.Instant

/**
 * Response DTO for profile completeness item.
 */
data class ItemCompletenessDto(
    val name: String,
    val complete: Boolean,
    val action: String? = null
)

/**
 * Response DTO for profile completeness section.
 */
data class SectionCompletenessDto(
    val name: String,
    val displayName: String,
    val weight: Int,
    val score: Int,
    val isComplete: Boolean,
    val items: List<ItemCompletenessDto>
)

/**
 * Response DTO for the next action suggestion.
 */
data class NextActionDto(
    val section: String,
    val action: String,
    val url: String
)

/**
 * Response DTO for the GET /api/v1/customers/{id}/profile/completeness endpoint.
 *
 * Returns detailed profile completeness information including
 * overall score, section breakdown, and next recommended action.
 */
data class ProfileCompletenessResponse(
    val customerId: String,
    val overallScore: Int,
    val sections: List<SectionCompletenessDto>,
    val nextAction: NextActionDto?,
    val updatedAt: Instant
) {
    companion object {
        /**
         * Converts a domain ProfileCompleteness to an API response DTO.
         *
         * @param domain The domain model to convert.
         * @return The API response DTO.
         */
        fun fromDomain(domain: ProfileCompleteness): ProfileCompletenessResponse {
            return ProfileCompletenessResponse(
                customerId = domain.customerId.toString(),
                overallScore = domain.overallScore,
                sections = domain.sections.map { section ->
                    SectionCompletenessDto(
                        name = section.name,
                        displayName = section.displayName,
                        weight = section.weight,
                        score = section.score,
                        isComplete = section.isComplete,
                        items = section.items.map { item ->
                            ItemCompletenessDto(
                                name = item.name,
                                complete = item.complete,
                                action = item.action
                            )
                        }
                    )
                },
                nextAction = domain.nextAction?.let {
                    NextActionDto(
                        section = it.section,
                        action = it.action,
                        url = it.url
                    )
                },
                updatedAt = domain.updatedAt
            )
        }
    }
}
